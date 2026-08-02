<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 条码查询接口 - 转发到配置的第三方接口，支持多接口回退
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

// 自动修复: 检测并更新旧的ApiZero URL
try {
    $fixStmt = $db->prepare("UPDATE api_config SET api_url = 'https://v1.apizero.cn/api/barcode-lookup?barcode={barcode}', updated_at = ? WHERE type = 'barcode' AND name = 'ApiZero' AND api_url LIKE '%apizero.cn/marketplace/barcode-gs1%'");
    $fixStmt->execute([time()]);
} catch (Exception $e) { /* ignore */ }

switch ($action) {
    case 'lookup':
        $barcode = trim($_GET['barcode'] ?? '');
        if (empty($barcode)) error('请提供条码');

        // 获取所有启用的条码查询接口，按优先级降序
        $stmt = $db->prepare("SELECT * FROM api_config WHERE type = 'barcode' AND is_active = 1 ORDER BY priority DESC");
        $stmt->execute();
        $apis = $stmt->fetchAll();

        if (empty($apis)) {
            success(['found' => false, 'barcode' => $barcode, 'msg' => '暂未配置条码查询接口，请在管理后台配置']);
        }

        // 依次尝试每个启用的接口，找到有实际数据的就返回
        $errors = [];
        $bestResult = null; // 保存 found=true 但无数据的结果
        foreach ($apis as $api) {
            $result = tryBarcodeLookup($db, $user, $barcode, $api);
            if ($result['found']) {
                $name = $result['name'] ?? '';
                if (!empty($name)) {
                    // 有实际商品名，直接返回
                    success($result);
                }
                // found=true 但 name 为空，保存为候选，继续尝试下一个接口
                if (!$bestResult) $bestResult = $result;
            }
            $errors[] = $api['name'] . ': ' . ($result['msg'] ?? '查询失败');
        }

        // 如果有候选结果（found但无数据），尝试用 ApiZero Pro 补充
        if ($bestResult) {
            // 查找 ApiZero Pro 接口
            foreach ($apis as $api) {
                if (strpos($api['api_url'], 'barcode-gs1') !== false) {
                    $proResult = tryBarcodeLookup($db, $user, $barcode, $api);
                    if ($proResult['found'] && !empty($proResult['name'] ?? '')) {
                        // 合并：Pro 的数据补充到候选结果
                        foreach (['name','brand','category','spec','manufacturer','description','image'] as $k) {
                            if (empty($bestResult[$k] ?? '') && !empty($proResult[$k] ?? '')) {
                                $bestResult[$k] = $proResult[$k];
                            }
                        }
                    }
                    break;
                }
            }
            success($bestResult);
        }

        success(['found' => false, 'barcode' => $barcode, 'msg' => '所有接口均未找到: ' . implode('; ', $errors)]);
        break;

    default:
        error('未知操作');
}

/**
 * 尝试用单个接口查询条码
 */
function tryBarcodeLookup($db, $user, $barcode, $api) {
    $startTime = microtime(true);
    $apiUrl = $api['api_url'];

    if (strpos($apiUrl, '{barcode}') !== false) {
        $url = str_replace('{barcode}', urlencode($barcode), $apiUrl);
    } else {
        $url = $apiUrl . urlencode($barcode);
    }

    // 处理需要 app_id/app_secret 的接口 (RollToolsApi)
    if (!empty($api['api_key']) || !empty($api['api_secret'])) {
        if (strpos($url, 'mxnzp.com') !== false) {
            $url = str_replace('app_id=&app_secret=', 'app_id=' . urlencode($api['api_key']) . '&app_secret=' . urlencode($api['api_secret'] ?? ''), $url);
        } elseif (strpos($url, 'apizero.cn/marketplace') !== false) {
            // 旧版ApiZero接口：key拼在URL参数里
            $url .= urlencode($api['api_key']);
        }
    }

    $headers = ['Accept: application/json'];
    // v1.apizero.cn 和其他接口用 Authorization header
    if (!empty($api['api_key']) && strpos($url, 'mxnzp.com') === false && strpos($url, 'apizero.cn/marketplace') === false) {
        $headers[] = 'Authorization: ' . $api['api_key'];
    }

    try {
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_TIMEOUT, 10);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 5);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
        $response = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $curlError = curl_error($ch);
        $curlErrno = curl_errno($ch);
        curl_close($ch);

        $duration = round((microtime(true) - $startTime) * 1000);

        // 记录日志
        $success = ($httpCode >= 200 && $httpCode < 300) ? 1 : 0;
        $logStmt = $db->prepare("INSERT INTO api_log (api_config_id, type, request_url, response_body, status, duration, user_id, created_at) VALUES (?, 'barcode', ?, ?, ?, ?, ?, ?)");
        $logStmt->execute([$api['id'], $url, substr($response ?: '', 0, 2000), $success, $duration, $user['id'], time()]);

        // 更新统计
        $db->prepare("UPDATE api_config SET total_calls = total_calls + 1, success_calls = success_calls + ?, last_call_time = ? WHERE id = ?")
            ->execute([$success, time(), $api['id']]);

        if ($curlErrno) {
            return ['found' => false, 'barcode' => $barcode, 'msg' => '连接失败: ' . $curlError];
        }

        if ($httpCode == 404) {
            return ['found' => false, 'barcode' => $barcode, 'msg' => '该条码未找到'];
        }

        if ($httpCode < 200 || $httpCode >= 300) {
            return ['found' => false, 'barcode' => $barcode, 'msg' => "HTTP $httpCode"];
        }

        $data = json_decode($response, true);
        if (!$data) {
            return ['found' => false, 'barcode' => $barcode, 'msg' => '响应解析失败'];
        }

        // 统一解析不同接口的返回格式
        $parsed = normalizeBarcodeResponse($data);
        if ($parsed) {
            return array_merge(['found' => true, 'barcode' => $barcode, 'api' => $api['name']], $parsed);
        }

        return ['found' => false, 'barcode' => $barcode, 'msg' => '返回数据格式无法识别'];

    } catch (Exception $e) {
        $duration = round((microtime(true) - $startTime) * 1000);
        $db->prepare("INSERT INTO api_log (api_config_id, type, request_url, status, error_msg, duration, user_id, created_at) VALUES (?, 'barcode', ?, 0, ?, ?, ?, ?)")
            ->execute([$api['id'], $url ?? '', $e->getMessage(), $duration, $user['id'], time()]);
        return ['found' => false, 'barcode' => $barcode, 'msg' => '接口调用异常'];
    }
}

/**
 * 统一解析不同接口的返回格式
 */
function normalizeBarcodeResponse($data) {
    // RollToolsApi (mxnzp): {"code": 0, "data": {"goodsName": "...", "goodsBrand": "..."}}
    // ApiZero: {"code": 0, "data": {"found": false/true, "name": "..."}}
    if (isset($data['code']) && $data['code'] == 0 && isset($data['data'])) {
        $d = $data['data'];
        // ApiZero 返回 found=false 表示未找到
        if (isset($d['found']) && !$d['found']) return null;
        return [
            'name' => $d['goodsName'] ?? $d['name'] ?? '',
            'brand' => $d['goodsBrand'] ?? $d['brand'] ?? '',
            'category' => $d['goodsCategory'] ?? $d['category'] ?? '',
            'spec' => $d['goodsSpec'] ?? $d['specification'] ?? $d['spec'] ?? '',
            'price' => $d['price'] ?? '',
            'image' => extractFirstImage($d),
            'manufacturer' => $d['manufacturer'] ?? '',
            'description' => $d['feature'] ?? $d['description'] ?? '',
        ];
    }

    // Open Food Facts: {"product": {"product_name": "...", "brands": "..."}}
    if (isset($data['product'])) {
        $p = $data['product'];
        $status = $data['status'] ?? 1;
        if ($status == 0) return null;
        return [
            'name' => $p['product_name'] ?? $p['product_name_zh'] ?? '',
            'brand' => $p['brands'] ?? '',
            'category' => $p['categories'] ?? '',
            'spec' => $p['quantity'] ?? '',
            'price' => '',
            'image' => $p['image_url'] ?? $p['image_front_url'] ?? '',
            'manufacturer' => $p['manufacturer'] ?? '',
            'description' => $p['generic_name'] ?? '',
        ];
    }

    // ApiZero: {"data": {"goodsName": "...", "brand": "..."}}
    if (isset($data['data']['goodsName'])) {
        $d = $data['data'];
        return [
            'name' => $d['goodsName'] ?? '',
            'brand' => $d['brand'] ?? '',
            'category' => $d['category'] ?? '',
            'spec' => $d['specification'] ?? $d['spec'] ?? '',
            'price' => $d['price'] ?? '',
            'image' => extractFirstImage($d),
            'manufacturer' => $d['manufacturer'] ?? '',
            'description' => $d['feature'] ?? $d['description'] ?? '',
        ];
    }

    // 通用格式
    if (isset($data['goodsName']) || isset($data['name']) || isset($data['product_name'])) {
        return [
            'name' => $data['goodsName'] ?? $data['name'] ?? $data['product_name'] ?? '',
            'brand' => $data['brand'] ?? $data['brands'] ?? '',
            'category' => $data['category'] ?? $data['categories'] ?? '',
            'spec' => $data['specification'] ?? $data['spec'] ?? $data['quantity'] ?? '',
            'price' => $data['price'] ?? '',
            'image' => extractFirstImage($data),
            'manufacturer' => $data['manufacturer'] ?? '',
            'description' => $data['feature'] ?? $data['description'] ?? '',
        ];
    }

    return null;
}

/**
 * 从API响应中提取第一张图片URL
 * 支持 images数组、image字符串、image_url等多种格式
 */
function extractFirstImage($d) {
    // images数组 (ApiZero Pro格式)
    if (isset($d['images']) && is_array($d['images']) && !empty($d['images'])) {
        return $d['images'][0];
    }
    // 单个image字段
    if (isset($d['image']) && !empty($d['image'])) {
        return $d['image'];
    }
    // image_url字段
    if (isset($d['image_url']) && !empty($d['image_url'])) {
        return $d['image_url'];
    }
    return '';
}
