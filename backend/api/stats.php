<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 统计数据接口
 */
$action = $_GET['action'] ?? 'overview';
$db = getDB();
$user = requireLogin();
$houseId = intval($_GET['house_id'] ?? 0);

if (!$houseId) {
    $houseStmt = $db->prepare("SELECT house_id FROM house_member WHERE user_id = ? ORDER BY joined_at ASC LIMIT 1");
    $houseStmt->execute([$user['id']]);
    $houseRow = $houseStmt->fetch();
    if ($houseRow) $houseId = intval($houseRow['house_id']);
}

$baseWhere = "g.status = 1";
$houseFilter = $houseId ? " AND g.house_id = $houseId" : "";
$privacyFilter = " AND (g.is_private = 0 OR g.creator_id = {$user['id']})";

switch ($action) {
    case 'overview':
        // 总览数据
        $stats = [];

        // 物品总数
        $stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter$privacyFilter");
        $stats['total_items'] = $stmt->fetch()['cnt'];

        // 物品总价
        $stmt = $db->query("SELECT COALESCE(SUM(g.purchase_price * g.quantity), 0) as total FROM goods g WHERE $baseWhere$houseFilter$privacyFilter AND g.purchase_price IS NOT NULL");
        $stats['total_value'] = floatval($stmt->fetch()['total']);

        // 待估价物品数
        $stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter$privacyFilter AND g.purchase_price IS NULL");
        $stats['pending_value'] = $stmt->fetch()['cnt'];

        // 已过期物品数
        $stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter$privacyFilter AND g.expiry_date IS NOT NULL AND g.expiry_date < CURDATE()");
        $stats['expired'] = $stmt->fetch()['cnt'];

        // 临期物品数（7天内）
        $stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter$privacyFilter AND g.expiry_date IS NOT NULL AND g.expiry_date >= CURDATE() AND g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY)");
        $stats['expiring_7days'] = $stmt->fetch()['cnt'];

        // 库存不足物品数
        $stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter$privacyFilter AND g.stock_threshold > 0 AND g.quantity <= g.stock_threshold");
        $stats['low_stock'] = $stmt->fetch()['cnt'];

        // 空间数量
        $spaceWhere = $houseId ? " WHERE house_id = $houseId" : "";
        $stmt = $db->query("SELECT COUNT(*) as cnt FROM storage_space$spaceWhere");
        $stats['total_spaces'] = $stmt->fetch()['cnt'];

        success($stats);
        break;

    case 'by_category':
        // 按分类统计
        $stmt = $db->query("SELECT g.category, COUNT(*) as cnt, COALESCE(SUM(g.purchase_price * g.quantity), 0) as value
            FROM goods g WHERE $baseWhere$houseFilter$privacyFilter
            GROUP BY g.category ORDER BY cnt DESC");
        $list = [];
        foreach ($stmt->fetchAll() as $row) {
            $list[] = [
                'name' => $row['category'] ?: '未分类',
                'count' => intval($row['cnt']),
                'value' => floatval($row['value'])
            ];
        }
        success(['list' => $list]);
        break;

    case 'by_space':
        // 按空间统计
        $stmt = $db->query("SELECT s.name, COUNT(g.id) as cnt, COALESCE(SUM(g.purchase_price * g.quantity), 0) as value
            FROM storage_space s LEFT JOIN goods g ON g.space_id = s.id AND g.status = 1
            WHERE 1=1" . ($houseId ? " AND s.house_id = $houseId" : "") . "
            GROUP BY s.id ORDER BY cnt DESC LIMIT 15");
        $list = [];
        foreach ($stmt->fetchAll() as $row) {
            $list[] = [
                'name' => $row['name'] ?: '未分配',
                'count' => intval($row['cnt']),
                'value' => floatval($row['value'])
            ];
        }
        success(['list' => $list]);
        break;

    case 'by_user':
        // 按录入用户统计
        $stmt = $db->query("SELECT u.nickname, u.username, COUNT(g.id) as cnt
            FROM sys_user u INNER JOIN goods g ON g.creator_id = u.id AND g.status = 1
            WHERE 1=1$houseFilter$privacyFilter
            GROUP BY u.id ORDER BY cnt DESC LIMIT 10");
        $list = [];
        foreach ($stmt->fetchAll() as $row) {
            $list[] = [
                'name' => $row['nickname'] ?: $row['username'],
                'count' => intval($row['cnt'])
            ];
        }
        success(['list' => $list]);
        break;

    case 'expiring_list':
        // 临期物品列表
        $days = intval($_GET['days'] ?? 7);
        $stmt = $db->query("SELECT g.id, g.name, g.expiry_date, g.category, s.name as space_name,
            DATEDIFF(g.expiry_date, CURDATE()) as days_left
            FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id
            WHERE $baseWhere$houseFilter$privacyFilter
            AND g.expiry_date IS NOT NULL AND g.expiry_date >= CURDATE()
            AND g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL $days DAY)
            ORDER BY g.expiry_date ASC LIMIT 20");
        $list = [];
        foreach ($stmt->fetchAll() as $row) {
            $list[] = [
                'id' => intval($row['id']),
                'name' => $row['name'],
                'expiry_date' => $row['expiry_date'],
                'category' => $row['category'],
                'space_name' => $row['space_name'] ?? '',
                'days_left' => intval($row['days_left'])
            ];
        }
        success(['list' => $list]);
        break;

    case 'low_stock_list':
        // 库存不足物品列表
        $stmt = $db->query("SELECT g.id, g.name, g.quantity, g.unit, g.stock_threshold, g.category, s.name as space_name
            FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id
            WHERE $baseWhere$houseFilter$privacyFilter
            AND g.stock_threshold > 0 AND g.quantity <= g.stock_threshold
            ORDER BY (g.quantity / g.stock_threshold) ASC LIMIT 20");
        $list = [];
        foreach ($stmt->fetchAll() as $row) {
            $list[] = [
                'id' => intval($row['id']),
                'name' => $row['name'],
                'quantity' => floatval($row['quantity']),
                'unit' => $row['unit'] ?: '件',
                'stock_threshold' => floatval($row['stock_threshold']),
                'category' => $row['category'],
                'space_name' => $row['space_name'] ?? ''
            ];
        }
        success(['list' => $list]);
        break;

    default:
        error('未知操作');
}
