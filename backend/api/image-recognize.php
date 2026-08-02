<?php
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
ini_set('log_errors', 0);
error_reporting(E_ALL);
header('Content-Type: application/json; charset=utf-8');

// 临时调试：记录到文件
function debug_log($msg) {
    file_put_contents(__DIR__ . '/debug_ai.log', date('H:i:s') . ' ' . $msg . "\n", FILE_APPEND);
}
debug_log('=== 新请求 === METHOD=' . $_SERVER['REQUEST_METHOD'] . ' FILES=' . json_encode(array_keys($_FILES)) . ' GET=' . json_encode($_GET));

try {
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
debug_log('DB/Helpers loaded');
/**
 * 图像识别接口 - 拍照识物
 */
$action = $_GET['action'] ?? '';
$db = getDB();
debug_log('DB connected, action=' . $action);
$user = requireLogin();
debug_log('User logged in, user_id=' . ($user['id'] ?? 'null'));

switch ($action) {
    case 'recognize':
        debug_log('进入recognize分支');
        // 接收上传的图片
        if (empty($_FILES['image'])) {
            debug_log('没有图片文件');
            error('请上传图片');
        }
        debug_log('图片文件存在, name=' . $_FILES['image']['name'] . ' size=' . $_FILES['image']['size'] . ' error=' . $_FILES['image']['error']);

        $file = $_FILES['image'];
        $allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
        if (!in_array($file['type'], $allowedTypes)) {
            error('仅支持 JPG/PNG/GIF/WebP 格式');
        }
        if ($file['size'] > IMAGE_MAX_SIZE) {
            error('图片大小不能超过2MB');
        }

        // 保存图片到临时文件
        $tmpPath = $file['tmp_name'];
        $imageData = file_get_contents($tmpPath);
        $base64Image = base64_encode($imageData);

        // 优先获取已启用的 AI 大模型配置（type='ai'）
        $aiConfig = null;
        try {
            require_once __DIR__ . '/../config/ai.php';
            $aiConfig = get_ai_config();
        } catch (Exception $e) {
            // AI 配置不可用，忽略
        }

        // 如果有 AI 大模型配置，走 Agent 类识别
        if ($aiConfig) {
            // 保存图片到服务器
            $ext = pathinfo($file['name'], PATHINFO_EXTENSION);
            $dir = UPLOAD_PATH . 'images/' . date('Ym') . '/';
            if (!is_dir($dir)) mkdir($dir, 0755, true);
            $filename = generateFileName($ext);
            $filepath = $dir . $filename;
            $relativePath = 'images/' . date('Ym') . '/' . $filename;
            move_uploaded_file($tmpPath, $filepath);

            $imageUrl = IMAGE_URL_PREFIX . $relativePath;

            try {
                debug_log('开始AI识别(直接调用), imageUrl=' . $imageUrl);

                // 读取图片并压缩
                $imgData = file_get_contents($filepath);
                if (function_exists('imagecreatefromstring') && function_exists('imagejpeg')) {
                    $img = @imagecreatefromstring($imgData);
                    if ($img) {
                        $origW = imagesx($img);
                        $origH = imagesy($img);
                        if ($origW > 1600 || $origH > 1600) {
                            $ratio = min(1600 / $origW, 1600 / $origH);
                            $newW = (int)($origW * $ratio);
                            $newH = (int)($origH * $ratio);
                            $newImg = imagecreatetruecolor($newW, $newH);
                            imagecopyresampled($newImg, $img, 0, 0, 0, 0, $newW, $newH, $origW, $origH);
                            ob_start();
                            imagejpeg($newImg, null, 75);
                            $imgData = ob_get_clean();
                            imagedestroy($newImg);
                            debug_log('图片已压缩: ' . $origW . 'x' . $origH . ' -> ' . $newW . 'x' . $newH);
                        }
                        imagedestroy($img);
                    }
                }
                $base64Image = base64_encode($imgData);

                // 动态获取分类列表
                $customCategories = null;
                if (!empty($_POST['categories'])) {
                    $customCategories = json_decode($_POST['categories'], true);
                }
                require_once __DIR__ . '/../config/ai.php';
                $categories = get_ai_categories($customCategories);
                $categoryStr = implode('、', $categories);

                // 构建 prompt
                $systemPrompt = '你是家庭收纳物品识别助手。用户会上传家中物品的照片，你需要识别出这是什么物品。'
                    . '严格只返回一个JSON对象，不要有其他文字：'
                    . '{"goods_name":"物品名称","brand":"品牌或null","spec":"规格或null","category":"分类(' . $categoryStr . ')","barcode":"条码或null","expire_date":"日期或null","storage_tip":"存放建议","confidence":0.85}'
                    . '无信息的字段返回null，不要猜。';

                // 直接调用 AI API
                $startTime = microtime(true);
                $payload = json_encode([
                    'model' => $aiConfig['model'],
                    'messages' => [
                        ['role' => 'user', 'content' => [
                            ['type' => 'image_url', 'image_url' => ['url' => 'data:image/jpeg;base64,' . $base64Image]],
                            ['type' => 'text', 'text' => $systemPrompt]
                        ]]
                    ],
                    'temperature' => 0.2,
                    'max_tokens' => 500,
                ]);

                $ch = curl_init($aiConfig['api_url']);
                curl_setopt($ch, CURLOPT_POST, true);
                curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
                curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch, CURLOPT_TIMEOUT, 60);
                curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 15);
                curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
                curl_setopt($ch, CURLOPT_HTTPHEADER, [
                    'Content-Type: application/json',
                    'Authorization: Bearer ' . $aiConfig['api_key'],
                ]);
                $response = curl_exec($ch);
                $curlErr = curl_error($ch);
                $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
                curl_close($ch);
                $duration = intval((microtime(true) - $startTime) * 1000);

                debug_log('AI HTTP=' . $httpCode . ' err=' . $curlErr . ' resp=' . substr($response, 0, 200));

                if ($curlErr) throw new Exception('AI 请求失败: ' . $curlErr);

                $respData = json_decode($response, true);
                if (!$respData) throw new Exception('AI 返回无效JSON');
                if (isset($respData['error'])) {
                    $errMsg = $respData['error']['message'] ?? json_encode($respData['error']);
                    throw new Exception('AI 服务错误: ' . $errMsg);
                }

                $content = $respData['choices'][0]['message']['content'] ?? '';
                // 移除 <think> 标签
                $content = preg_replace('/<think>[\s\S]*?<\/think>/i', '', $content);
                $content = trim($content);
                // 移除 markdown 代码块
                $content = preg_replace('/^```(?:json)?\s*/m', '', $content);
                $content = preg_replace('/^```\s*$/m', '', $content);
                $content = trim($content);

                // 解析 JSON
                $aiResult = json_decode($content, true);
                if (!$aiResult) {
                    // 尝试提取 JSON
                    $start = strpos($content, '{');
                    $end = strrpos($content, '}');
                    if ($start !== false && $end > $start) {
                        $aiResult = json_decode(substr($content, $start, $end - $start + 1), true);
                    }
                }
                if (!$aiResult || !isset($aiResult['goods_name'])) {
                    throw new Exception('AI 返回结果无法解析: ' . substr($content, 0, 200));
                }

                debug_log('AI识别成功: ' . json_encode($aiResult, JSON_UNESCAPED_UNICODE));

                // 更新统计
                try {
                    $db->prepare("UPDATE api_config SET total_calls = total_calls + 1, success_calls = success_calls + 1, last_call_time = ? WHERE id = ?")
                        ->execute([time(), $aiConfig['id']]);
                } catch (Exception $e) { /* 忽略统计错误 */ }

                // 记录日志
                try {
                    $usage = $respData['usage'] ?? [];
                    $db->prepare("INSERT INTO ai_call_log (user_id, type, image_url, ai_provider, ai_model, prompt_tokens, completion_tokens, total_tokens, status, duration, created_at) VALUES (?, 'recognize', ?, ?, ?, ?, ?, ?, 1, ?, ?)")
                        ->execute([$user['id'], $imageUrl, $aiConfig['provider'], $aiConfig['model'], $usage['prompt_tokens'] ?? 0, $usage['completion_tokens'] ?? 0, $usage['total_tokens'] ?? 0, $duration, time()]);
                } catch (Exception $e) { error_log('ai_call_log error: ' . $e->getMessage()); }

                success([
                    'recognized' => true,
                    'suggested_name' => $aiResult['goods_name'] ?? '',
                    'suggested_category' => $aiResult['category'] ?? '',
                    'suggested_brand' => $aiResult['brand'] ?? '',
                    'suggested_tags' => [],
                    'barcode' => $aiResult['barcode'] ?? '',
                    'confidence' => floatval($aiResult['confidence'] ?? 0),
                    'image_path' => $relativePath,
                    'image_url' => $imageUrl
                ]);
                break;

            } catch (Exception $e) {
                debug_log('AI识别失败: ' . $e->getMessage());
                error_log('AI recognize failed: ' . $e->getMessage());
                try {
                    $db->prepare("UPDATE api_config SET total_calls = total_calls + 1, last_call_time = ? WHERE id = ?")
                        ->execute([time(), $aiConfig['id']]);
                } catch (Exception $e2) { /* 忽略 */ }
                // 继续走传统图像识别
            }
        }

        // 降级：获取已启用的传统图像识别API配置（type='image'）
        $stmt = $db->prepare("SELECT * FROM api_config WHERE type = 'image' AND is_active = 1 ORDER BY priority DESC LIMIT 1");
        $stmt->execute();
        $apiConfig = $stmt->fetch();

        if (!$apiConfig) {
            // 没有配置图像识别API，返回提示让用户手动输入
            success([
                'recognized' => false,
                'message' => '图像识别API未配置，请在管理后台配置 AI 识别或图像识别接口',
                'suggested_name' => '',
                'suggested_category' => '',
                'barcode' => ''
            ]);
            break;
        }

        // 同时保存图片到服务器
        $ext = pathinfo($file['name'], PATHINFO_EXTENSION);
        $dir = UPLOAD_PATH . 'images/' . date('Ym') . '/';
        if (!is_dir($dir)) mkdir($dir, 0755, true);
        $filename = generateFileName($ext);
        $filepath = $dir . $filename;
        $relativePath = 'images/' . date('Ym') . '/' . $filename;
        move_uploaded_file($tmpPath, $filepath);

        $result = null;
        $startTime = microtime(true);

        try {
            switch ($apiConfig['name']) {
                case '百度AI图像识别':
                    $result = recognizeBaiduAI($apiConfig, $base64Image);
                    break;
                case '腾讯云图像识别':
                    $result = recognizeTencent($apiConfig, $base64Image);
                    break;
                default:
                    $result = recognizeGeneric($apiConfig, $base64Image);
                    break;
            }
        } catch (Exception $e) {
            $result = ['success' => false, 'error' => '识别服务异常: ' . $e->getMessage()];
        }

        $duration = intval((microtime(true) - $startTime) * 1000);

        // 记录调用日志
        $logStmt = $db->prepare("INSERT INTO api_log (api_config_id, type, request_url, request_params, response_body, status, error_msg, duration, user_id, created_at) VALUES (?, 'image', ?, ?, ?, ?, ?, ?, ?, ?)");
        $logStmt->execute([
            $apiConfig['id'],
            $apiConfig['api_url'],
            'image_upload',
            $result ? json_encode($result, JSON_UNESCAPED_UNICODE) : '',
            ($result && $result['success']) ? 1 : 0,
            ($result && !$result['success']) ? ($result['error'] ?? '') : '',
            $duration,
            $user['id'],
            time()
        ]);

        // 更新API调用统计
        $db->prepare("UPDATE api_config SET total_calls = total_calls + 1, last_call_time = ? WHERE id = ?")
            ->execute([time(), $apiConfig['id']]);
        if ($result && $result['success']) {
            $db->prepare("UPDATE api_config SET success_calls = success_calls + 1 WHERE id = ?")
                ->execute([$apiConfig['id']]);
        }

        // 同时尝试OCR识别包装上的文字（商品名、品牌等）
        $ocrResult = null;
        try {
            $ocrResult = recognizeTextFromImage($apiConfig, $base64Image);
        } catch (Exception $e) {
            // OCR失败不影响主流程
        }

        if ($result && $result['success']) {
            // 合并OCR结果：优先使用OCR识别到的商品名
            $suggestedName = $result['name'] ?? '';
            $suggestedBrand = $result['brand'] ?? '';
            $suggestedCategory = $result['category'] ?? '';

            if ($ocrResult && !empty($ocrResult['product_name'])) {
                $suggestedName = $ocrResult['product_name'];
            }
            if ($ocrResult && !empty($ocrResult['brand'])) {
                $suggestedBrand = $ocrResult['brand'];
            }
            // 根据OCR识别的商品名自动推断标签
            $suggestedTags = [];
            if (!empty($suggestedName)) {
                $suggestedTags = inferTagsFromName($suggestedName, $suggestedCategory);
            }

            success([
                'recognized' => true,
                'suggested_name' => $suggestedName,
                'suggested_category' => $suggestedCategory,
                'suggested_brand' => $suggestedBrand,
                'suggested_tags' => $suggestedTags,
                'barcode' => $result['barcode'] ?? '',
                'confidence' => $result['confidence'] ?? 0,
                'image_path' => $relativePath,
                'image_url' => IMAGE_URL_PREFIX . $relativePath
            ]);
        } else {
            // 主识别失败，尝试仅用OCR结果
            if ($ocrResult && !empty($ocrResult['product_name'])) {
                $suggestedTags = inferTagsFromName($ocrResult['product_name'], '');
                success([
                    'recognized' => true,
                    'suggested_name' => $ocrResult['product_name'],
                    'suggested_category' => mapCategory($ocrResult['product_name']),
                    'suggested_brand' => $ocrResult['brand'] ?? '',
                    'suggested_tags' => $suggestedTags,
                    'barcode' => '',
                    'confidence' => 0.5,
                    'image_path' => $relativePath,
                    'image_url' => IMAGE_URL_PREFIX . $relativePath
                ]);
            } else {
                success([
                    'recognized' => false,
                    'message' => '识别失败: ' . ($result['error'] ?? '未知错误') . '，请手动输入',
                    'image_path' => $relativePath,
                    'image_url' => IMAGE_URL_PREFIX . $relativePath
                ]);
            }
        }
        break;

    default:
        error('未知操作');
}

/**
 * 百度AI图像识别
 */
function recognizeBaiduAI($config, $base64Image) {
    $apiKey = $config['api_key'];
    $secretKey = $config['api_secret'];

    if (empty($apiKey) || empty($secretKey)) {
        return ['success' => false, 'error' => '百度AI API Key未配置'];
    }

    // 获取access_token
    $tokenUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=" . urlencode($apiKey) . "&client_secret=" . urlencode($secretKey);
    $tokenResp = httpPost($tokenUrl, '', ['Content-Type: application/x-www-form-urlencoded']);
    $tokenData = json_decode($tokenResp, true);
    if (!$tokenData || !isset($tokenData['access_token'])) {
        $errDetail = isset($tokenData['error_description']) ? $tokenData['error_description'] : ($tokenResp ?: '无响应');
        return ['success' => false, 'error' => '获取百度AI Token失败: ' . $errDetail];
    }
    $accessToken = $tokenData['access_token'];

    // 调用通用物体识别
    $url = $config['api_url'] . "?access_token={$accessToken}";
    $postData = http_build_query(['image' => $base64Image, 'baike_num' => 1]);
    $resp = httpPost($url, $postData, ['Content-Type: application/x-www-form-urlencoded']);
    $data = json_decode($resp, true);

    if (isset($data['result']) && count($data['result']) > 0) {
        $top = $data['result'][0];
        $name = $top['keyword'] ?? $top['root'] ?? '';
        $category = mapCategory($name);
        return [
            'success' => true,
            'name' => $name,
            'category' => $category,
            'confidence' => $top['score'] ?? 0
        ];
    }

    return ['success' => false, 'error' => '未能识别图中物品'];
}

/**
 * 腾讯云图像识别
 */
function recognizeTencent($config, $base64Image) {
    // 腾讯云图像识别实现
    $apiKey = $config['api_key'];
    if (empty($apiKey)) {
        return ['success' => false, 'error' => '腾讯云API Key未配置'];
    }

    $url = $config['api_url'];
    $postData = json_encode(['image' => $base64Image]);
    $resp = httpPost($url, $postData, [
        'Content-Type: application/json',
        'Authorization: Bearer ' . $apiKey
    ]);
    $data = json_decode($resp, true);

    if (isset($data['tags']) && count($data['tags']) > 0) {
        $topTag = $data['tags'][0];
        $name = $topTag['tag_name'] ?? '';
        return [
            'success' => true,
            'name' => $name,
            'category' => mapCategory($name),
            'confidence' => $topTag['tag_confidence'] ?? 0
        ];
    }

    return ['success' => false, 'error' => '未能识别图中物品'];
}

/**
 * 通用HTTP接口调用
 */
function recognizeGeneric($config, $base64Image) {
    $url = $config['api_url'];
    $extraParams = json_decode($config['extra_params'] ?? '{}', true);

    $postData = json_encode(array_merge([
        'image' => $base64Image
    ], $extraParams));

    $headers = ['Content-Type: application/json'];
    if (!empty($config['api_key'])) {
        $headers[] = 'Authorization: Bearer ' . $config['api_key'];
    }

    $resp = httpPost($url, $postData, $headers);
    $data = json_decode($resp, true);

    if ($data && isset($data['name'])) {
        return [
            'success' => true,
            'name' => $data['name'] ?? '',
            'category' => $data['category'] ?? mapCategory($data['name'] ?? ''),
            'brand' => $data['brand'] ?? '',
            'confidence' => $data['confidence'] ?? 0
        ];
    }

    return ['success' => false, 'error' => '识别接口返回异常'];
}

/**
 * OCR文字识别 - 识别包装上的商品名和品牌
 */
function recognizeTextFromImage($config, $base64Image) {
    $apiKey = $config['api_key'];
    $secretKey = $config['api_secret'];

    if (empty($apiKey) || empty($secretKey)) {
        return null;
    }

    // 获取access_token
    $tokenUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=" . urlencode($apiKey) . "&client_secret=" . urlencode($secretKey);
    $tokenResp = httpPost($tokenUrl, '', ['Content-Type: application/x-www-form-urlencoded']);
    $tokenData = json_decode($tokenResp, true);
    if (!$tokenData || !isset($tokenData['access_token'])) {
        return null;
    }
    $accessToken = $tokenData['access_token'];

    // 调用通用文字识别（高精度版）
    $ocrUrl = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic?access_token={$accessToken}";
    $postData = http_build_query(['image' => $base64Image]);
    $resp = httpPost($ocrUrl, $postData, ['Content-Type: application/x-www-form-urlencoded']);
    $data = json_decode($resp, true);

    if (!isset($data['words_result']) || empty($data['words_result'])) {
        return null;
    }

    // 提取所有识别到的文字
    $allWords = [];
    foreach ($data['words_result'] as $item) {
        $allWords[] = $item['words'];
    }
    $fullText = implode(' ', $allWords);

    // 尝试从文字中提取商品名和品牌
    $productName = '';
    $brand = '';

    // 常见品牌关键词匹配
    $knownBrands = [
        '蒙牛', '伊利', '光明', '完达山', '三元', '君乐宝', '旺仔',
        '康师傅', '统一', '今麦郎', '白象', '日清',
        '可口可乐', '百事可乐', '雪碧', '芬达', '美年达',
        '雀巢', '星巴克', '瑞幸',
        '奥利奥', '趣多多', '达利园', '盼盼', '徐福记',
        '华为', '小米', '苹果', '三星', 'OPPO', 'vivo',
        '宝洁', '联合利华', '花王', '立白', '蓝月亮',
        '双汇', '金锣', '雨润',
        '农夫山泉', '怡宝', '百岁山',
        '伊利', '蒙牛', '光明', '完达山', '三元',
        '海天', '李锦记', '太太乐', '家乐',
    ];

    foreach ($knownBrands as $b) {
        if (mb_strpos($fullText, $b) !== false) {
            $brand = $b;
            break;
        }
    }

    // 商品名通常是第一行或前几行中较长的文字
    // 排除日期、条码、地址等干扰信息
    $skipPatterns = ['/', '\\', '生产日期', '保质期', '配料', '成分', '地址', '电话', '传真', '邮编', 'http', 'www', '净含量', '规格', '批号', 'GB ', 'QB ', 'SB '];
    foreach ($allWords as $word) {
        $word = trim($word);
        if (mb_strlen($word) < 2 || mb_strlen($word) > 30) continue;

        $skip = false;
        foreach ($skipPatterns as $pattern) {
            if (mb_strpos($word, $pattern) !== false) {
                $skip = true;
                break;
            }
        }
        // 跳过纯数字、纯日期
        if (preg_match('/^\d+$/', $word) || preg_match('/^\d{4}[-\/]\d{1,2}/', $word)) {
            $skip = true;
        }
        if (!$skip && empty($productName)) {
            $productName = $word;
        }
    }

    if (empty($productName) && empty($brand)) {
        return null;
    }

    // 如果有品牌但没有商品名，用品牌+第一行有意义文字组合
    if (empty($productName) && !empty($brand)) {
        $productName = $brand;
    }

    return [
        'product_name' => $productName,
        'brand' => $brand,
        'all_text' => $fullText
    ];
}

/**
 * 根据商品名推断标签
 */
function inferTagsFromName($name, $category) {
    $tags = [];
    $name = strtolower($name);

    // 食品相关
    $foodKeywords = ['牛奶', '酸奶', '面包', '饼干', '薯片', '巧克力', '糖果', '方便面', '火腿', '香肠', '奶酪', '果汁', '咖啡', '茶', '可乐', '雪碧', '矿泉水', '坚果', '蛋糕', '月饼'];
    foreach ($foodKeywords as $kw) {
        if (mb_strpos($name, $kw) !== false) {
            $tags[] = '食品';
            break;
        }
    }

    // 药品相关
    $medicineKeywords = ['药', '胶囊', '片剂', '口服液', '冲剂', '创可贴', '碘伏', '酒精'];
    foreach ($medicineKeywords as $kw) {
        if (mb_strpos($name, $kw) !== false) {
            $tags[] = '药品';
            break;
        }
    }

    // 日用品
    $dailyKeywords = ['洗发水', '沐浴露', '牙膏', '牙刷', '洗衣液', '洗洁精', '纸巾', '抽纸', '湿巾', '垃圾袋'];
    foreach ($dailyKeywords as $kw) {
        if (mb_strpos($name, $kw) !== false) {
            $tags[] = '日用品';
            break;
        }
    }

    // 饮料
    $drinkKeywords = ['水', '汁', '奶', '茶', '咖啡', '可乐', '雪碧', '啤酒'];
    foreach ($drinkKeywords as $kw) {
        if (mb_strpos($name, $kw) !== false) {
            if (!in_array('食品', $tags)) $tags[] = '饮品';
            break;
        }
    }

    return $tags;
}

/**
 * HTTP POST请求
 */
function httpPost($url, $postData, $headers = []) {
    if (!function_exists('curl_init')) {
        throw new Exception('curl扩展未安装');
    }
    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, $postData);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 15);
    curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 10);
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
    curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
    if (!empty($headers)) {
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
    }
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $error = curl_error($ch);
    $errno = curl_errno($ch);
    curl_close($ch);

    if ($errno) {
        throw new Exception("HTTP请求失败[curl {$errno}]: {$error}");
    }
    return $response;
}

/**
 * 根据识别结果映射分类
 */
function mapCategory($name) {
    $categoryMap = [
        '食品' => ['食品', '零食', '饮料', '水果', '蔬菜', '肉类', '面包', '饼干', '糖果', '巧克力', '牛奶', '果汁', '咖啡', '茶', '方便面', '薯片', '坚果', '蛋糕'],
        '衣物' => ['衣服', '裤子', '鞋', '帽子', '围巾', '手套', '袜子', '内衣', '外套', 'T恤', '裙子', '衬衫'],
        '药品' => ['药', '感冒药', '止痛药', '创可贴', '维生素', '保健品', '口罩', '体温计'],
        '日用品' => ['纸巾', '洗衣液', '洗洁精', '牙膏', '牙刷', '洗发水', '沐浴露', '毛巾', '垃圾袋', '保鲜膜'],
        '数码' => ['手机', '电脑', '平板', '充电器', '耳机', '数据线', '充电宝', '键盘', '鼠标', 'U盘'],
        '证件' => ['身份证', '护照', '驾照', '银行卡', '社保卡', '毕业证'],
        '厨具' => ['锅', '碗', '盘', '筷子', '勺子', '刀', '砧板', '电饭煲', '微波炉'],
        '玩具' => ['玩具', '积木', '娃娃', '拼图', '遥控车', '球'],
        '文具' => ['笔', '本子', '橡皮', '尺子', '胶带', '剪刀', '文件夹'],
    ];

    $name = strtolower($name);
    foreach ($categoryMap as $category => $keywords) {
        foreach ($keywords as $keyword) {
            if (strpos($name, $keyword) !== false) {
                return $category;
            }
        }
    }
    return '其他';
}

} catch (Throwable $e) {
    debug_log('致命错误: ' . $e->getMessage() . ' in ' . $e->getFile() . ':' . $e->getLine());
    http_response_code(500);
    echo json_encode(['code' => 500, 'msg' => '服务器错误: ' . $e->getMessage()], JSON_UNESCAPED_UNICODE);
    exit;
}
