<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
corsHeaders();

$action = $_GET['action'] ?? '';
$db = getDB();

switch ($action) {
    case 'upload':
        // APP端上报错误日志
        $input = getJsonInput();
        $logs = $input['logs'] ?? [];
        if (empty($logs)) {
            $logs = [$input]; // 兼容单条日志
        }

        $inserted = 0;
        $stmt = $db->prepare('INSERT INTO app_log (user_id, device_info, log_type, tag, message, stack_trace, app_version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
        foreach ($logs as $log) {
            $userId = intval($log['user_id'] ?? 0);
            $deviceInfo = $log['device_info'] ?? '';
            $logType = $log['type'] ?? 'error';
            $tag = $log['tag'] ?? '';
            $message = $log['message'] ?? '';
            $stackTrace = $log['stack_trace'] ?? '';
            $appVersion = $log['app_version'] ?? '';
            $now = time();
            $stmt->execute([$userId, $deviceInfo, $logType, $tag, $message, $stackTrace, $appVersion, $now]);
            $inserted++;
        }
        success(['inserted' => $inserted]);
        break;

    case 'list':
        // 网页端查看日志列表
        $page = max(1, intval($_GET['page'] ?? 1));
        $pageSize = min(100, max(10, intval($_GET['page_size'] ?? 50)));
        $logType = $_GET['type'] ?? '';

        $where = [];
        $params = [];
        if ($logType) {
            $where[] = 'log_type = ?';
            $params[] = $logType;
        }

        $whereStr = $where ? 'WHERE ' . implode(' AND ', $where) : '';

        $countStmt = $db->prepare("SELECT COUNT(*) as cnt FROM app_log $whereStr");
        $countStmt->execute($params);
        $total = $countStmt->fetch()['cnt'];

        $offset = ($page - 1) * $pageSize;
        $stmt = $db->prepare("SELECT * FROM app_log $whereStr ORDER BY id DESC LIMIT $pageSize OFFSET $offset");
        $stmt->execute($params);
        $list = $stmt->fetchAll();

        success(['list' => $list, 'total' => $total, 'page' => $page]);
        break;

    case 'stats':
        // 日志统计
        $total = $db->query('SELECT COUNT(*) as cnt FROM app_log')->fetch()['cnt'];
        $today = $db->query('SELECT COUNT(*) as cnt FROM app_log WHERE created_at > ' . (time() - 86400))->fetch()['cnt'];
        $errors = $db->query("SELECT COUNT(*) as cnt FROM app_log WHERE log_type = 'crash'")->fetch()['cnt'];
        success(['total' => $total, 'today' => $today, 'crashes' => $errors]);
        break;

    case 'clear':
        // 清空日志
        $db->exec('DELETE FROM app_log');
        success(null, '已清空');
        break;

    default:
        error('未知操作');
}
