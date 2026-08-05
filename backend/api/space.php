<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 收纳空间接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'list':
        $houseId = intval($_GET['house_id'] ?? 0);
        if (!$houseId) error('缺少参数house_id');

        $role = getUserHouseRole($user['id'], $houseId);
        if (!$role) error('你不是该房屋成员', 403);

        $parentId = intval($_GET['parent_id'] ?? 0);
        $sql = "SELECT * FROM storage_space WHERE house_id = ?";
        $params = [$houseId];
        if ($parentId > 0) {
            $sql .= " AND parent_id = ?";
            $params[] = $parentId;
        } else {
            $sql .= " AND parent_id = 0";
        }
        $sql .= " ORDER BY sort_order ASC, created_at ASC";

        $stmt = $db->prepare($sql);
        $stmt->execute($params);
        $list = $stmt->fetchAll();
        success(['list' => $list]);
        break;

    case 'tree':
        $houseId = intval($_GET['house_id'] ?? 0);
        if (!$houseId) error('缺少参数house_id');

        $stmt = $db->prepare("SELECT * FROM storage_space WHERE house_id = ? ORDER BY level ASC, sort_order ASC, created_at ASC");
        $stmt->execute([$houseId]);
        $all = $stmt->fetchAll();

        // 问题4: 计算每个空间的直接物品数量
        foreach ($all as &$spaceItem) {
            $cntStmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods WHERE space_id = ? AND status = 1");
            $cntStmt->execute([$spaceItem['id']]);
            $spaceItem['item_count'] = $cntStmt->fetch()['cnt'];
        }
        unset($spaceItem);

        // 构建树形结构
        $tree = [];
        $map = [];
        foreach ($all as $item) {
            $item['children'] = [];
            $map[$item['id']] = $item;
        }
        foreach ($map as &$item) {
            if ($item['parent_id'] == 0) {
                $tree[] = &$item;
            } elseif (isset($map[$item['parent_id']])) {
                $map[$item['parent_id']]['children'][] = &$item;
            }
        }
        success(['tree' => $tree]);
        break;

    case 'detail':
        $id = intval($_GET['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $stmt = $db->prepare("SELECT * FROM storage_space WHERE id = ?");
        $stmt->execute([$id]);
        $space = $stmt->fetch();
        if (!$space) error('空间不存在');

        // 获取子空间
        $stmt = $db->prepare("SELECT * FROM storage_space WHERE parent_id = ? ORDER BY sort_order ASC");
        $stmt->execute([$id]);
        $space['children'] = $stmt->fetchAll();

        // 获取物品数量
        $stmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods WHERE space_id = ? AND status = 1");
        $stmt->execute([$id]);
        $space['item_count'] = $stmt->fetch()['cnt'];

        success(['space' => $space]);
        break;

    case 'create':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $parentId = intval($input['parent_id'] ?? 0);
        $name = trim($input['name'] ?? '');
        $icon = $input['icon'] ?? '🏠';
        $color = $input['color'] ?? '#FF8C42';
        $shared = intval($input['shared'] ?? 1);

        if (!$houseId || empty($name)) error('请填写完整信息');

        $role = getUserHouseRole($user['id'], $houseId);
        if (!$role || $role == 3) error('无权限创建空间', 403);

        // 计算层级
        $level = 1;
        if ($parentId > 0) {
            $stmt = $db->prepare("SELECT level FROM storage_space WHERE id = ? AND house_id = ?");
            $stmt->execute([$parentId, $houseId]);
            $parent = $stmt->fetch();
            if (!$parent) error('父级空间不存在');
            $level = $parent['level'] + 1;
            if ($level > 3) error('最多支持3级空间');
        }

        $now = time();
        $stmt = $db->prepare("INSERT INTO storage_space (house_id, parent_id, name, level, icon, color, shared, creator_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        $spaceCode = generateSpaceCode($db);
        $stmt->execute([$houseId, $parentId, $name, $level, $icon, $color, $shared, $user['id'], $now, $now]);
        $spaceId = $db->lastInsertId();
        // 更新空间编码
        $db->prepare("UPDATE storage_space SET space_code = ? WHERE id = ?")->execute([$spaceCode, $spaceId]);

        // 更新房屋空间计数
        $stmt = $db->prepare("UPDATE house SET space_count = space_count + 1, updated_at = ? WHERE id = ?");
        $stmt->execute([$now, $houseId]);

        logOperation($user['id'], 'space', 'create', $spaceId, "创建空间: $name");
        success(['id' => $spaceId]);
        break;

    case 'update':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $fields = [];
        $params = [];
        $allowedFields = ['name', 'icon', 'color', 'sort_order', 'shared'];
        foreach ($allowedFields as $field) {
            if (isset($input[$field])) {
                $fields[] = "$field = ?";
                $params[] = $input[$field];
            }
        }
        if (empty($fields)) error('没有要更新的内容');

        $fields[] = "updated_at = ?";
        $params[] = time();
        $params[] = $id;

        $stmt = $db->prepare("UPDATE storage_space SET " . implode(', ', $fields) . " WHERE id = ?");
        $stmt->execute($params);
        logOperation($user['id'], 'space', 'update', $id, "更新空间");
        success(null, '更新成功');
        break;

    case 'delete':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        $deleteGoods = intval($input['delete_goods'] ?? 0);
        if (!$id) error('缺少参数id');

        $stmt = $db->prepare("SELECT * FROM storage_space WHERE id = ?");
        $stmt->execute([$id]);
        $space = $stmt->fetch();
        if (!$space) error('空间不存在');

        // 检查是否有子空间
        $stmt = $db->prepare("SELECT COUNT(*) as cnt FROM storage_space WHERE parent_id = ?");
        $stmt->execute([$id]);
        if ($stmt->fetch()['cnt'] > 0) error('请先删除子空间');

        // 检查是否有物品
        $stmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods WHERE space_id = ? AND status = 1");
        $stmt->execute([$id]);
        $goodsCount = $stmt->fetch()['cnt'];

        if ($goodsCount > 0 && !$deleteGoods) {
            error("该空间内有 {$goodsCount} 件物品，请确认处理方式");
        }

        $db->beginTransaction();
        try {
            if ($deleteGoods && $goodsCount > 0) {
                $stmt = $db->prepare("UPDATE goods SET status = 0, updated_at = ? WHERE space_id = ?");
                $stmt->execute([time(), $id]);
            }

            $stmt = $db->prepare("DELETE FROM storage_space WHERE id = ?");
            $stmt->execute([$id]);

            $stmt = $db->prepare("UPDATE house SET space_count = GREATEST(space_count - 1, 0), updated_at = ? WHERE id = ?");
            $stmt->execute([time(), $space['house_id']]);

            $db->commit();
            logOperation($user['id'], 'space', 'delete', $id, "删除空间: {$space['name']}");
            success(null, '删除成功');
        } catch (Exception $e) {
            $db->rollBack();
            error('删除失败');
        }
        break;

    case 'generate_code':
        $id = intval($_GET['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $stmt = $db->prepare("SELECT * FROM storage_space WHERE id = ?");
        $stmt->execute([$id]);
        $space = $stmt->fetch();
        if (!$space) error('空间不存在');

        if (!empty($space['space_code'])) {
            success(['space_code' => $space['space_code'], 'space_name' => $space['name']]);
            break;
        }

        $code = generateSpaceCode($db);
        $db->prepare("UPDATE storage_space SET space_code = ? WHERE id = ?")->execute([$code, $id]);
        success(['space_code' => $code, 'space_name' => $space['name']]);
        break;

    case 'lookup_code':
        $code = trim($_GET['code'] ?? '');
        if (empty($code)) error('缺少参数code');

        $stmt = $db->prepare("SELECT s.*, h.name as house_name FROM storage_space s LEFT JOIN house h ON s.house_id = h.id WHERE s.space_code = ?");
        $stmt->execute([$code]);
        $space = $stmt->fetch();
        if (!$space) error('空间不存在');

        // 检查用户是否是该房屋成员
        $role = getUserHouseRole($user['id'], $space['house_id']);
        if (!$role) error('你不是该家庭成员', 403);

        success(['space' => $space]);
        break;

    case 'recommend_location':
        $houseId = intval($_GET['house_id'] ?? 0);
        $tags = trim($_GET['tags'] ?? '');
        $itemName = trim($_GET['name'] ?? '');
        if (!$houseId) error('缺少参数house_id');

        $recommendations = [];

        // 根据标签查找同类物品的存放位置
        if (!empty($tags)) {
            $tagArr = array_map('trim', explode(',', $tags));
            foreach ($tagArr as $tagName) {
                if (empty($tagName)) continue;
                $stmt = $db->prepare("SELECT g.space_id, COUNT(*) as cnt, s.name as space_name, s.space_code
                    FROM goods g
                    JOIN goods_tag gt ON g.id = gt.goods_id
                    JOIN tag t ON gt.tag_id = t.id
                    LEFT JOIN storage_space s ON g.space_id = s.id
                    WHERE g.house_id = ? AND g.status = 1 AND g.space_id > 0 AND t.name = ?
                    GROUP BY g.space_id
                    ORDER BY cnt DESC
                    LIMIT 3");
                $stmt->execute([$houseId, $tagName]);
                while ($row = $stmt->fetch()) {
                    $sid = $row['space_id'];
                    if (!isset($recommendations[$sid])) {
                        $recommendations[$sid] = ['space_id' => $sid, 'space_name' => $row['space_name'], 'space_code' => $row['space_code'], 'count' => 0, 'reason' => ''];
                    }
                    $recommendations[$sid]['count'] += $row['cnt'];
                    $recommendations[$sid]['reason'] = '同类「' . $tagName . '」物品存放处';
                }
            }
        }

        // 根据物品名称模糊匹配
        if (!empty($itemName) && mb_strlen($itemName) >= 2) {
            $like = '%' . $itemName . '%';
            $stmt = $db->prepare("SELECT g.space_id, COUNT(*) as cnt, s.name as space_name, s.space_code
                FROM goods g
                LEFT JOIN storage_space s ON g.space_id = s.id
                WHERE g.house_id = ? AND g.status = 1 AND g.space_id > 0 AND g.name LIKE ?
                GROUP BY g.space_id
                ORDER BY cnt DESC
                LIMIT 3");
            $stmt->execute([$houseId, $like]);
            while ($row = $stmt->fetch()) {
                $sid = $row['space_id'];
                if (!isset($recommendations[$sid])) {
                    $recommendations[$sid] = ['space_id' => $sid, 'space_name' => $row['space_name'], 'space_code' => $row['space_code'], 'count' => 0, 'reason' => ''];
                }
                $recommendations[$sid]['count'] += $row['cnt'];
                if (empty($recommendations[$sid]['reason'])) {
                    $recommendations[$sid]['reason'] = '相似物品存放处';
                }
            }
        }

        // 按数量排序，取前3
        usort($recommendations, function($a, $b) { return $b['count'] - $a['count']; });
        $recommendations = array_slice($recommendations, 0, 3);

        // 至少有2件同类物品才推荐
        $recommendations = array_filter($recommendations, function($r) { return $r['count'] >= 2; });

        success(['recommendations' => array_values($recommendations)]);
        break;

    default:
        error('未知操作');
}

/**
 * 生成6位空间编码 (大写字母+数字, 排除易混淆字符)
 */
function generateSpaceCode($db) {
    $chars = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
    for ($attempt = 0; $attempt < 100; $attempt++) {
        $code = '';
        for ($i = 0; $i < 6; $i++) {
            $code .= $chars[random_int(0, strlen($chars) - 1)];
        }
        $check = $db->prepare("SELECT id FROM storage_space WHERE space_code = ?");
        $check->execute([$code]);
        if (!$check->fetch()) return $code;
    }
    return strtoupper(substr(md5(uniqid()), 0, 6));
}
