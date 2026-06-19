<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 数据同步接口 - 离线数据同步
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'push':
        // APP端推送离线数据到服务器
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $items = $input['items'] ?? []; // 离线创建的物品列表
        $spaces = $input['spaces'] ?? []; // 离线创建的空间列表

        if (!$houseId) error('缺少参数house_id');

        $now = time();
        $results = ['synced_items' => 0, 'synced_spaces' => 0, 'errors' => []];

        $db->beginTransaction();
        try {
            // 同步空间
            foreach ($spaces as $sp) {
                $offlineId = $sp['offline_id'] ?? '';
                if (empty($sp['name'])) continue;

                $parentId = intval($sp['parent_id'] ?? 0);
                $level = 1;
                if ($parentId > 0) {
                    $stmt = $db->prepare("SELECT level FROM storage_space WHERE id = ?");
                    $stmt->execute([$parentId]);
                    $p = $stmt->fetch();
                    if ($p) $level = $p['level'] + 1;
                }

                $stmt = $db->prepare("INSERT INTO storage_space (house_id, parent_id, name, level, icon, color, shared, creator_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                $stmt->execute([
                    $houseId, $parentId, $sp['name'], $level,
                    $sp['icon'] ?? '🏠', $sp['color'] ?? '#FF8C42',
                    intval($sp['shared'] ?? 1), $user['id'], $now, $now
                ]);
                $newSpaceId = $db->lastInsertId();

                // 更新离线物品的空间ID映射
                if ($offlineId) {
                    foreach ($items as &$item) {
                        if (isset($item['offline_space_id']) && $item['offline_space_id'] == $offlineId) {
                            $item['space_id'] = $newSpaceId;
                        }
                    }
                }
                $results['synced_spaces']++;
            }

            // 同步物品
            foreach ($items as $item) {
                if (empty($item['name'])) continue;
                $spaceId = intval($item['space_id'] ?? 0);
                if (!$spaceId) {
                    $results['errors'][] = "物品 '{$item['name']}' 未关联空间";
                    continue;
                }

                $stmt = $db->prepare("INSERT INTO goods (house_id, space_id, creator_id, name, barcode, category, brand, spec, quantity, unit, purchase_date, expiry_date, purchase_price, stock_threshold, note, is_private, is_offline, offline_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 1, ?, ?)");
                $stmt->execute([
                    $houseId, $spaceId, $user['id'], $item['name'],
                    $item['barcode'] ?? '', $item['category'] ?? '', $item['brand'] ?? '',
                    $item['spec'] ?? '', floatval($item['quantity'] ?? 1), $item['unit'] ?? '个',
                    $item['purchase_date'] ?? null, $item['expiry_date'] ?? null,
                    $item['purchase_price'] ?? null, $item['stock_threshold'] ?? null,
                    $item['note'] ?? '', intval($item['is_private'] ?? 0),
                    $item['offline_id'] ?? '', $now, $now
                ]);
                $newGoodsId = $db->lastInsertId();

                // 更新空间和房屋计数
                $stmt = $db->prepare("UPDATE storage_space SET item_count = item_count + 1, updated_at = ? WHERE id = ?");
                $stmt->execute([$now, $spaceId]);

                $results['synced_items']++;
            }

            $stmt = $db->prepare("UPDATE house SET item_count = item_count + ?, updated_at = ? WHERE id = ?");
            $stmt->execute([$results['synced_items'], $now, $houseId]);

            // 记录同步日志
            $stmt = $db->prepare("INSERT INTO sync_log (user_id, house_id, type, direction, item_count, status, last_sync_time) VALUES (?, ?, 'incremental', 'upload', ?, 1, ?)");
            $stmt->execute([$user['id'], $houseId, $results['synced_items'], $now]);

            $db->commit();
            success($results);
        } catch (Exception $e) {
            $db->rollBack();
            error('同步失败: ' . $e->getMessage());
        }
        break;

    case 'pull':
        // APP端拉取服务器最新数据
        $houseId = intval($_GET['house_id'] ?? 0);
        $lastSync = intval($_GET['last_sync'] ?? 0); // 上次同步时间戳

        if (!$houseId) error('缺少参数house_id');

        $role = getUserHouseRole($user['id'], $houseId);
        if (!$role) error('你不是该房屋成员', 403);

        // 获取增量数据
        $where = ["house_id = ?", "updated_at > ?"];
        $params = [$houseId, $lastSync];

        // 空间
        $stmt = $db->prepare("SELECT * FROM storage_space WHERE " . implode(' AND ', $where) . " ORDER BY level ASC");
        $stmt->execute($params);
        $spaces = $stmt->fetchAll();

        // 物品(过滤隐私)
        $goodsWhere = array_merge($where, ["status = 1", "(is_private = 0 OR creator_id = ?)"]);
        $goodsParams = array_merge($params, [$user['id']]);
        $stmt = $db->prepare("SELECT * FROM goods WHERE " . implode(' AND ', $goodsWhere) . " ORDER BY updated_at DESC");
        $stmt->execute($goodsParams);
        $goods = $stmt->fetchAll();

        // 标签
        $stmt = $db->prepare("SELECT * FROM tag WHERE house_id = ?");
        $stmt->execute([$houseId]);
        $tags = $stmt->fetchAll();

        // 记录同步
        $now = time();
        $stmt = $db->prepare("INSERT INTO sync_log (user_id, house_id, type, direction, item_count, status, last_sync_time) VALUES (?, ?, 'incremental', 'download', ?, 1, ?)");
        $stmt->execute([$user['id'], $houseId, count($goods), $now]);

        success([
            'spaces' => $spaces,
            'goods' => $goods,
            'tags' => $tags,
            'sync_time' => $now
        ]);
        break;

    default:
        error('未知操作');
}
