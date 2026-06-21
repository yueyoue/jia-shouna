<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
corsHeaders();
/**
 * 操作日志接口
 */
$action = $_GET['action'] ?? 'list';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'list':
        $houseId = intval($_GET['house_id'] ?? 0);
        $module = $_GET['module'] ?? '';
        $page = max(1, intval($_GET['page'] ?? 1));
        $limit = min(50, max(1, intval($_GET['limit'] ?? 20)));
        $offset = ($page - 1) * $limit;

        $where = ["ol.user_id = ?"];
        $params = [$user['id']];

        if ($module) {
            $where[] = "ol.module = ?";
            $params[] = $module;
        }

        // 如果指定了house_id，只查该房屋相关的操作日志（通过target_id关联）
        // 但操作日志不直接关联house_id，所以我们通过user_id过滤

        $whereStr = implode(' AND ', $where);

        $stmt = $db->prepare("SELECT ol.* FROM operate_log ol WHERE $whereStr ORDER BY ol.created_at DESC LIMIT $limit OFFSET $offset");
        $stmt->execute($params);
        $list = $stmt->fetchAll();

        // 格式化时间
        foreach ($list as &$item) {
            if (isset($item['created_at']) && is_numeric($item['created_at'])) {
                $item['created_at'] = date('Y-m-d H:i:s', intval($item['created_at']));
            }
        }

        success(['list' => $list, 'page' => $page, 'limit' => $limit]);
        break;

    default:
        error('未知操作');
}
