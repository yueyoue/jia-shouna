<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 数据统计接口
 */
$db = getDB();
$user = requireLogin();

$houseId = intval($_GET['house_id'] ?? 0);
if (!$houseId) error('缺少参数house_id');

// 检查权限
$role = getUserHouseRole($user['id'], $houseId);
if (!$role) error('你不是该房屋成员', 403);

// 按分类统计
$stmt = $db->prepare("SELECT category as name, COUNT(*) as count FROM goods WHERE house_id = ? AND status = 1 AND category != '' GROUP BY category ORDER BY count DESC");
$stmt->execute([$houseId]);
$categories = $stmt->fetchAll();

// 按空间统计
$stmt = $db->prepare("SELECT s.name, COUNT(g.id) as count FROM storage_space s LEFT JOIN goods g ON g.space_id = s.id AND g.status = 1 WHERE s.house_id = ? GROUP BY s.id ORDER BY count DESC LIMIT 10");
$stmt->execute([$houseId]);
$spaces = $stmt->fetchAll();

// 总数统计
$stmt = $db->prepare("SELECT COUNT(*) as total FROM goods WHERE house_id = ? AND status = 1");
$stmt->execute([$houseId]);
$total = $stmt->fetch()['total'];

// 临期统计
$stmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods WHERE house_id = ? AND status = 1 AND expiry_date IS NOT NULL AND expiry_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY) AND expiry_date >= CURDATE()");
$stmt->execute([$houseId]);
$expiring = $stmt->fetch()['cnt'];

success([
    'total' => intval($total),
    'expiring' => intval($expiring),
    'categories' => $categories,
    'spaces' => $spaces
]);
