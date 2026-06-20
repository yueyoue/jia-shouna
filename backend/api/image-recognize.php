<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 图像识别接口 - 拍照识物
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'recognize':
        // 接收上传的图片
        if (empty($_FILES['image'])) {
            error('请上传图片');
        }

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

        // 获取已启用的图像识别API配置
        $stmt = $db->prepare("SELECT * FROM api_config WHERE type = 'image' AND is_active = 1 ORDER BY priority DESC LIMIT 1");
        $stmt->execute();
        $apiConfig = $stmt->fetch();

        if (!$apiConfig) {
            // 没有配置图像识别API，返回提示让用户手动输入
            success([
                'recognized' => false,
                'message' => '图像识别API未配置，请手动输入物品信息',
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
                    // 通用HTTP接口调用
                    $result = recognizeGeneric($apiConfig, $base64Image);
                    break;
            }
        } catch (Exception $e) {
            $result = ['success' => false, 'error' => $e->getMessage()];
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

        if ($result && $result['success']) {
            success([
                'recognized' => true,
                'suggested_name' => $result['name'] ?? '',
                'suggested_category' => $result['category'] ?? '',
                'suggested_brand' => $result['brand'] ?? '',
                'barcode' => $result['barcode'] ?? '',
                'confidence' => $result['confidence'] ?? 0,
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
    $tokenUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id={$apiKey}&client_secret={$secretKey}";
    $tokenResp = httpPost($tokenUrl, '', ['Content-Type: application/x-www-form-urlencoded']);
    $tokenData = json_decode($tokenResp, true);
    if (!isset($tokenData['access_token'])) {
        return ['success' => false, 'error' => '获取百度AI Token失败'];
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
 * HTTP POST请求
 */
function httpPost($url, $postData, $headers = []) {
    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_POST, true);
    curl_setopt($ch, CURLOPT_POSTFIELDS, $postData);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_TIMEOUT, 15);
    curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
    if (!empty($headers)) {
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
    }
    $response = curl_exec($ch);
    $error = curl_error($ch);
    curl_close($ch);

    if ($error) {
        throw new Exception("HTTP请求失败: $error");
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
