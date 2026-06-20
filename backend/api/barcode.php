<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 条码查询接口 - 转发到配置的第三方接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'lookup':
        $barcode = trim($_GET['barcode'] ?? '');
        if (empty($barcode)) error('请提供条码');

        // 获取启用的条码查询接口
        $stmt = $db->prepare("SELECT * FROM api_config WHERE type = 'barcode' AND is_active = 1 ORDER BY priority DESC");
        $stmt->execute();
        $apis = $stmt->fetchAll();

        if (empty($apis)) {
            // 没有配置接口，返回空结果
            success(['found' => false, 'barcode' => $barcode, 'msg' => '暂未配置条码查询接口，请在管理后台配置']);
        }

        $startTime = microtime(true);
        $api = $apis[0]; // 使用优先级最高的接口

        try {
            // Build URL - support {barcode} placeholder or simple append
            $apiUrl = $api['api_url'];
            if (strpos($apiUrl, '{barcode}') !== false) {
                $url = str_replace('{barcode}', urlencode($barcode), $apiUrl);
            } else {
                $url = $apiUrl . $barcode;
            }
            $headers = ['Accept: application/json'];
            if ($api['api_key']) {
                $headers[] = 'Authorization: Bearer ' . $api['api_key'];
            }

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

            // curl错误处理
            if ($curlErrno) {
                success(['found' => false, 'barcode' => $barcode, 'msg' => '条码查询服务连接失败: ' . $curlError]);
            }

            // 记录日志
            $logStmt = $db->prepare("INSERT INTO api_log (api_config_id, type, request_url, response_body, status, duration, user_id, created_at) VALUES (?, 'barcode', ?, ?, ?, ?, ?, ?)");
            $success = ($httpCode >= 200 && $httpCode < 300) ? 1 : 0;
            $logStmt->execute([$api['id'], $url, substr($response ?: '', 0, 2000), $success, $duration, $user['id'], time()]);

            // 更新统计
            $db->prepare("UPDATE api_config SET total_calls = total_calls + 1, success_calls = success_calls + ?, last_call_time = ? WHERE id = ?")
                ->execute([$success, time(), $api['id']]);

            if ($success) {
                $data = json_decode($response, true);
                // 根据不同接口解析返回数据(这里做通用处理)
                success(['found' => true, 'barcode' => $barcode, 'data' => $data, 'api' => $api['name']]);
            } else {
                $errMsg = '查询失败';
                if ($httpCode == 404) {
                    $errMsg = '该条码在数据库中未找到';
                } elseif ($httpCode >= 500) {
                    $errMsg = '条码查询服务暂时不可用';
                } elseif ($httpCode == 0) {
                    $errMsg = '无法连接到条码查询服务';
                }
                success(['found' => false, 'barcode' => $barcode, 'msg' => $errMsg]);
            }
        } catch (Exception $e) {
            $duration = round((microtime(true) - $startTime) * 1000);
            $db->prepare("INSERT INTO api_log (api_config_id, type, request_url, status, error_msg, duration, user_id, created_at) VALUES (?, 'barcode', ?, 0, ?, ?, ?, ?)")
                ->execute([$api['id'], $url ?? '', $e->getMessage(), $duration, $user['id'], time()]);
            success(['found' => false, 'barcode' => $barcode, 'msg' => '接口调用失败']);
        }
        break;

    default:
        error('未知操作');
}
