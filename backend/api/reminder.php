<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 提醒接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'list':
        $houseId = intval($_GET['house_id'] ?? 0);
        $type = $_GET['type'] ?? '';
        $page = max(1, intval($_GET['page'] ?? 1));
        $pageSize = 20;

        $where = ["r.user_id = ?"];
        $params = [$user['id']];
        if ($houseId) { $where[] = "r.house_id = ?"; $params[] = $houseId; }
        if ($type) { $where[] = "r.type = ?"; $params[] = $type; }

        $whereStr = implode(' AND ', $where);
        $offset = ($page - 1) * $pageSize;
        $stmt = $db->prepare("SELECT r.*, g.name as goods_name, s.name as space_name 
            FROM reminder r 
            LEFT JOIN goods g ON r.goods_id = g.id 
            LEFT JOIN storage_space s ON r.space_id = s.id 
            WHERE $whereStr ORDER BY r.is_read ASC, r.remind_time ASC 
            LIMIT $pageSize OFFSET $offset");
        $stmt->execute($params);
        success(['list' => $stmt->fetchAll()]);
        break;

    case 'create':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $type = $input['type'] ?? 'custom';
        $title = trim($input['title'] ?? '');
        $content = $input['content'] ?? '';
        $remindTime = intval($input['remind_time'] ?? 0);
        $goodsId = intval($input['goods_id'] ?? 0);
        $spaceId = intval($input['space_id'] ?? 0);

        if (!$houseId || empty($title) || !$remindTime) error('请填写完整信息');

        $stmt = $db->prepare("INSERT INTO reminder (user_id, house_id, goods_id, space_id, type, title, content, remind_time, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        $stmt->execute([$user['id'], $houseId, $goodsId ?: null, $spaceId ?: null, $type, $title, $content, $remindTime, time()]);
        success(['id' => $db->lastInsertId()]);
        break;

    case 'handle':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        if (!$id) error('缺少参数id');
        $stmt = $db->prepare("UPDATE reminder SET is_handled = 1 WHERE id = ? AND user_id = ?");
        $stmt->execute([$id, $user['id']]);
        success(null, '已处理');
        break;

    case 'stats':
        $houseId = intval($_GET['house_id'] ?? 0);
        $where = ["r.user_id = ?"];
        $params = [$user['id']];
        if ($houseId) { $where[] = "r.house_id = ?"; $params[] = $houseId; }
        $whereStr = implode(' AND ', $where);

        $stmt = $db->prepare("SELECT type, COUNT(*) as cnt FROM reminder r WHERE $whereStr AND r.is_handled = 0 GROUP BY type");
        $stmt->execute($params);
        $stats = [];
        foreach ($stmt->fetchAll() as $row) {
            $stats[$row['type']] = $row['cnt'];
        }

        // 临期统计
        $expWhere = ["g.status = 1", "g.expiry_date IS NOT NULL", "g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY)"];
        $expParams = [];
        if ($houseId) { $expWhere[] = "g.house_id = ?"; $expParams[] = $houseId; }
        $expWhere[] = "(g.is_private = 0 OR g.creator_id = ?)";
        $expParams[] = $user['id'];
        $expWhereStr = implode(' AND ', $expWhere);
        $stmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods g WHERE $expWhereStr");
        $stmt->execute($expParams);
        $stats['expiring_7days'] = $stmt->fetch()['cnt'];

        // 库存不足统计
        $stmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods g WHERE g.status = 1 AND g.stock_threshold > 0 AND g.quantity <= g.stock_threshold AND (g.is_private = 0 OR g.creator_id = ?)" . ($houseId ? " AND g.house_id = ?" : ""));
        $params2 = [$user['id']];
        if ($houseId) $params2[] = $houseId;
        $stmt->execute($params2);
        $stats['low_stock'] = $stmt->fetch()['cnt'];

        success(['stats' => $stats]);
        break;

    default:
        error('未知操作');
}
