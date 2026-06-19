<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 标签管理接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'list':
        $houseId = intval($_GET['house_id'] ?? 0);
        if (!$houseId) error('缺少参数house_id');
        $stmt = $db->prepare("SELECT t.*, (SELECT COUNT(*) FROM goods_tag WHERE tag_id = t.id) as usage_count FROM tag t WHERE t.house_id = ? ORDER BY t.name ASC");
        $stmt->execute([$houseId]);
        success(['list' => $stmt->fetchAll()]);
        break;

    case 'create':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $name = trim($input['name'] ?? '');
        $color = $input['color'] ?? '#5B9FED';
        if (!$houseId || empty($name)) error('请填写标签名称');

        $stmt = $db->prepare("INSERT INTO tag (house_id, name, color, created_at) VALUES (?, ?, ?, ?)");
        $stmt->execute([$houseId, $name, $color, time()]);
        success(['id' => $db->lastInsertId()]);
        break;

    case 'delete':
        $id = intval($_GET['id'] ?? 0);
        if (!$id) error('缺少参数id');
        $stmt = $db->prepare("DELETE FROM tag WHERE id = ?");
        $stmt->execute([$id]);
        $stmt = $db->prepare("DELETE FROM goods_tag WHERE tag_id = ?");
        $stmt->execute([$id]);
        success(null, '删除成功');
        break;

    default:
        error('未知操作');
}
