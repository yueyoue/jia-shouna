<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
corsHeaders();
/**
 * 物品管理接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'list':
        $houseId = intval($_GET['house_id'] ?? 0);
        $spaceId = intval($_GET['space_id'] ?? 0);
        $category = $_GET['category'] ?? '';
        $tagId = intval($_GET['tag_id'] ?? 0);
        $keyword = $_GET['keyword'] ?? '';
        $page = max(1, intval($_GET['page'] ?? 1));
        $pageSize = min(50, max(1, intval($_GET['page_size'] ?? 20)));

        $where = ["g.status = 1"];
        $params = [];

        // 如果未指定house_id，自动获取用户所属的第一个房屋
        if (!$houseId) {
            $houseStmt = $db->prepare("SELECT house_id FROM house_member WHERE user_id = ? ORDER BY joined_at ASC LIMIT 1");
            $houseStmt->execute([$user['id']]);
            $houseRow = $houseStmt->fetch();
            if ($houseRow) $houseId = intval($houseRow['house_id']);
        }

        if ($houseId) {
            $where[] = "g.house_id = ?";
            $params[] = $houseId;
        }
        if ($spaceId) {
            $includeChildren = intval($_GET['include_children'] ?? 0);
            if ($includeChildren) {
                // 包含子空间的物品
                $childIds = getDescendantSpaceIds($db, $spaceId, $houseId);
                $childIds[] = $spaceId;
                $placeholders = implode(',', array_fill(0, count($childIds), '?'));
                $where[] = "g.space_id IN ($placeholders)";
                $params = array_merge($params, $childIds);
            } else {
                $where[] = "g.space_id = ?";
                $params[] = $spaceId;
            }
        }
        if ($category) {
            $where[] = "g.category = ?";
            $params[] = $category;
        }
        if ($keyword) {
            $where[] = "(g.name LIKE ? OR g.barcode LIKE ? OR g.note LIKE ?)";
            $kw = "%$keyword%";
            $params[] = $kw;
            $params[] = $kw;
            $params[] = $kw;
        }
        // 隐私过滤: 非管理员只能看自己创建的或非隐私的
        $where[] = "(g.is_private = 0 OR g.creator_id = ?)";
        $params[] = $user['id'];

        $whereStr = implode(' AND ', $where);

        // 总数
        $countStmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods g WHERE $whereStr");
        $countStmt->execute($params);
        $total = $countStmt->fetch()['cnt'];

        // 列表
        $offset = ($page - 1) * $pageSize;
        $sql = "SELECT g.*, s.name as space_name, s.icon as space_icon,
                (SELECT image_path FROM goods_image WHERE goods_id = g.id ORDER BY sort_order ASC LIMIT 1) as cover_image
                FROM goods g 
                LEFT JOIN storage_space s ON g.space_id = s.id 
                WHERE $whereStr 
                ORDER BY g.updated_at DESC 
                LIMIT $pageSize OFFSET $offset";
        $stmt = $db->prepare($sql);
        $stmt->execute($params);
        $list = $stmt->fetchAll();

        // 处理图片URL
        foreach ($list as &$item) {
            $item['cover_image'] = $item['cover_image'] ? IMAGE_URL_PREFIX . $item['cover_image'] : '';
        }

        // 标签过滤额外处理
        if ($tagId) {
            $tagSql = "SELECT goods_id FROM goods_tag WHERE tag_id = ?";
            $tagStmt = $db->prepare($tagSql);
            $tagStmt->execute([$tagId]);
            $tagGoodsIds = $tagStmt->fetchAll(PDO::FETCH_COLUMN);
            $list = array_filter($list, function($item) use ($tagGoodsIds) {
                return in_array($item['id'], $tagGoodsIds);
            });
        }

        success(['list' => $list, 'total' => $total, 'page' => $page, 'page_size' => $pageSize]);
        break;

    case 'detail':
        $id = intval($_GET['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $stmt = $db->prepare("SELECT g.*, s.name as space_name, s.icon as space_icon, s.color as space_color,
            u.nickname as creator_name,
            (SELECT image_path FROM goods_image WHERE goods_id = g.id ORDER BY sort_order ASC LIMIT 1) as cover_image
            FROM goods g 
            LEFT JOIN storage_space s ON g.space_id = s.id 
            LEFT JOIN sys_user u ON g.creator_id = u.id 
            WHERE g.id = ? AND g.status = 1");
        $stmt->execute([$id]);
        $goods = $stmt->fetch();
        if (!$goods) error('物品不存在');

        // 隐私检查
        if ($goods['is_private'] && $goods['creator_id'] != $user['id']) {
            $role = getUserHouseRole($user['id'], $goods['house_id']);
            if ($role != 1) error('无权查看此物品', 403);
        }

        // 获取图片
        $stmt = $db->prepare("SELECT * FROM goods_image WHERE goods_id = ? ORDER BY sort_order ASC");
        $stmt->execute([$id]);
        $images = $stmt->fetchAll();
        foreach ($images as &$img) {
            $img['image_path'] = IMAGE_URL_PREFIX . $img['image_path'];
            $img['thumb_path'] = $img['thumb_path'] ? IMAGE_URL_PREFIX . $img['thumb_path'] : '';
        }
        // 处理cover_image
        if (!empty($goods['cover_image'])) {
            $goods['cover_image'] = IMAGE_URL_PREFIX . $goods['cover_image'];
        }

        $goods['images'] = $images;

        // 获取标签
        $stmt = $db->prepare("SELECT t.* FROM tag t LEFT JOIN goods_tag gt ON t.id = gt.tag_id WHERE gt.goods_id = ?");
        $stmt->execute([$id]);
        $goods['tags'] = $stmt->fetchAll();

        // 获取领用记录
        $stmt = $db->prepare("SELECT gb.*, u.nickname as user_name FROM goods_borrow gb 
            LEFT JOIN sys_user u ON gb.user_id = u.id 
            WHERE gb.goods_id = ? ORDER BY gb.borrow_time DESC LIMIT 10");
        $stmt->execute([$id]);
        $goods['borrow_records'] = $stmt->fetchAll();

        // 获取空间路径
        $spacePath = [];
        $spaceId = $goods['space_id'];
        while ($spaceId > 0) {
            $stmt = $db->prepare("SELECT id, name, parent_id FROM storage_space WHERE id = ?");
            $stmt->execute([$spaceId]);
            $sp = $stmt->fetch();
            if (!$sp) break;
            array_unshift($spacePath, ['id' => $sp['id'], 'name' => $sp['name']]);
            $spaceId = $sp['parent_id'];
        }
        $goods['space_path'] = $spacePath;

        success(['goods' => $goods]);
        break;

    case 'create':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $spaceId = intval($input['space_id'] ?? 0);
        $name = trim($input['name'] ?? '');

        if (!$houseId || !$spaceId || empty($name)) error('请填写物品名称并选择存放位置');

        $now = time();
        $stmt = $db->prepare("INSERT INTO goods (house_id, space_id, creator_id, name, barcode, category, brand, spec, quantity, unit, purchase_date, expiry_date, purchase_price, stock_threshold, note, is_private, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)");
        // 清理空字符串为null（避免DATE/DECIMAL列插入空字符串报错）
        $purchaseDate = !empty($input['purchase_date']) ? $input['purchase_date'] : null;
        $expiryDate = !empty($input['expiry_date']) ? $input['expiry_date'] : null;
        $purchasePrice = isset($input['purchase_price']) && $input['purchase_price'] !== '' ? floatval($input['purchase_price']) : null;
        $stockThreshold = isset($input['stock_threshold']) && $input['stock_threshold'] !== '' ? floatval($input['stock_threshold']) : null;

        $stmt->execute([
            $houseId, $spaceId, $user['id'], $name,
            $input['barcode'] ?? '', $input['category'] ?? '', $input['brand'] ?? '',
            $input['spec'] ?? '', floatval($input['quantity'] ?? 1), $input['unit'] ?? '个',
            $purchaseDate, $expiryDate,
            $purchasePrice, $stockThreshold,
            $input['note'] ?? '', intval($input['is_private'] ?? 0),
            $now, $now
        ]);
        $goodsId = $db->lastInsertId();

        // 处理标签
        if (!empty($input['tags'])) {
            foreach ($input['tags'] as $tagId) {
                $stmt = $db->prepare("INSERT IGNORE INTO goods_tag (goods_id, tag_id) VALUES (?, ?)");
                $stmt->execute([$goodsId, $tagId]);
            }
        }

        // 处理图片
        if (!empty($input['images'])) {
            foreach ($input['images'] as $idx => $imgPath) {
                $stmt = $db->prepare("INSERT INTO goods_image (goods_id, image_path, sort_order, created_at) VALUES (?, ?, ?, ?)");
                $stmt->execute([$goodsId, $imgPath, $idx, $now]);
            }
        }

        // 更新空间物品计数
        $stmt = $db->prepare("UPDATE storage_space SET item_count = item_count + 1, updated_at = ? WHERE id = ?");
        $stmt->execute([$now, $spaceId]);
        // 更新房屋物品计数
        $stmt = $db->prepare("UPDATE house SET item_count = item_count + 1, updated_at = ? WHERE id = ?");
        $stmt->execute([$now, $houseId]);

        // 如果有保质期，根据规则创建提醒
        if (!empty($input['expiry_date'])) {
            $expiryTs = strtotime($input['expiry_date']);
            $remindDays = 7;

            // 根据保质期时长匹配规则
            $shelfLifeDays = 0;
            if (!empty($input['purchase_date'])) {
                $purchaseTs = strtotime($input['purchase_date']);
                if ($purchaseTs && $expiryTs) {
                    $shelfLifeDays = intval(($expiryTs - $purchaseTs) / 86400);
                }
            }

            // 读取规则
            try {
                $ruleStmt = $db->prepare("SELECT skey, svalue FROM sys_setting WHERE skey IN ('rule_days_365','rule_days_180','rule_days_90','rule_days_30','rule_days_16','rule_days_short')");
                $ruleStmt->execute();
                $ruleSettings = [];
                while ($row = $ruleStmt->fetch()) {
                    $ruleSettings[$row['skey']] = intval($row['svalue']);
                }

                if ($shelfLifeDays >= 365) $remindDays = $ruleSettings['rule_days_365'] ?? 45;
                elseif ($shelfLifeDays >= 180) $remindDays = $ruleSettings['rule_days_180'] ?? 20;
                elseif ($shelfLifeDays >= 90) $remindDays = $ruleSettings['rule_days_90'] ?? 15;
                elseif ($shelfLifeDays >= 30) $remindDays = $ruleSettings['rule_days_30'] ?? 10;
                elseif ($shelfLifeDays >= 16) $remindDays = $ruleSettings['rule_days_16'] ?? 5;
                else $remindDays = $ruleSettings['rule_days_short'] ?? 2;
            } catch (Exception $e) {
                // 使用默认值
                $stmt2 = $db->prepare("SELECT svalue FROM sys_setting WHERE skey = 'default_remind_days'");
                $stmt2->execute();
                $s = $stmt2->fetch();
                if ($s) $remindDays = intval($s['svalue']);
            }

            $remindTime = $expiryTs - ($remindDays * 86400);
            if ($remindTime > $now) {
                $stmt3 = $db->prepare("INSERT INTO reminder (user_id, house_id, goods_id, type, title, content, remind_time, created_at) VALUES (?, ?, ?, 'expiry', ?, ?, ?, ?)");
                $stmt3->execute([$user['id'], $houseId, $goodsId, "物品即将过期: $name", "保质期至 {$input['expiry_date']}", $remindTime, $now]);
            }
        }

        logOperation($user['id'], 'goods', 'create', $goodsId, "录入物品: $name");
        success(['id' => $goodsId]);
        break;

    case 'update':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $stmt = $db->prepare("SELECT * FROM goods WHERE id = ? AND status = 1");
        $stmt->execute([$id]);
        $goods = $stmt->fetch();
        if (!$goods) error('物品不存在');

        $allowedFields = ['name', 'barcode', 'category', 'brand', 'spec', 'quantity', 'unit', 'purchase_date', 'expiry_date', 'purchase_price', 'stock_threshold', 'note', 'is_private'];
        $fields = [];
        $params = [];
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

        $stmt = $db->prepare("UPDATE goods SET " . implode(', ', $fields) . " WHERE id = ?");
        $stmt->execute($params);

        // 更新标签
        if (isset($input['tags'])) {
            $stmt = $db->prepare("DELETE FROM goods_tag WHERE goods_id = ?");
            $stmt->execute([$id]);
            foreach ($input['tags'] as $tagId) {
                $stmt = $db->prepare("INSERT IGNORE INTO goods_tag (goods_id, tag_id) VALUES (?, ?)");
                $stmt->execute([$id, $tagId]);
            }
        }

        // 追加图片
        if (!empty($input['images'])) {
            $now = time();
            // 获取当前最大排序号
            $stmt2 = $db->prepare("SELECT COALESCE(MAX(sort_order), -1) as max_order FROM goods_image WHERE goods_id = ?");
            $stmt2->execute([$id]);
            $maxOrder = intval($stmt2->fetch()['max_order']);
            foreach ($input['images'] as $idx => $imgPath) {
                $stmt3 = $db->prepare("INSERT INTO goods_image (goods_id, image_path, sort_order, created_at) VALUES (?, ?, ?, ?)");
                $stmt3->execute([$id, $imgPath, $maxOrder + 1 + $idx, $now]);
            }
        }

        logOperation($user['id'], 'goods', 'update', $id, "更新物品: {$goods['name']}");
        success(null, '更新成功');
        break;

    case 'delete':
        $input = getJsonInput();
        $ids = $input['ids'] ?? [];
        $id = intval($input['id'] ?? 0);
        if ($id) $ids[] = $id;
        if (empty($ids)) error('请选择要删除的物品');

        $now = time();
        $db->beginTransaction();
        try {
            foreach ($ids as $gid) {
                $gid = intval($gid);
                $stmt = $db->prepare("SELECT * FROM goods WHERE id = ? AND status = 1");
                $stmt->execute([$gid]);
                $g = $stmt->fetch();
                if (!$g) continue;

                $stmt = $db->prepare("UPDATE goods SET status = 0, updated_at = ? WHERE id = ?");
                $stmt->execute([$now, $gid]);

                // 更新计数
                $stmt = $db->prepare("UPDATE storage_space SET item_count = GREATEST(item_count - 1, 0), updated_at = ? WHERE id = ?");
                $stmt->execute([$now, $g['space_id']]);
                $stmt = $db->prepare("UPDATE house SET item_count = GREATEST(item_count - 1, 0), updated_at = ? WHERE id = ?");
                $stmt->execute([$now, $g['house_id']]);
            }
            $db->commit();
            logOperation($user['id'], 'goods', 'delete', 0, "删除物品: " . count($ids) . "件");
            success(null, '删除成功');
        } catch (Exception $e) {
            $db->rollBack();
            error('删除失败');
        }
        break;

    case 'move':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        $newSpaceId = intval($input['space_id'] ?? 0);
        if (!$id || !$newSpaceId) error('参数不完整');

        $stmt = $db->prepare("SELECT * FROM goods WHERE id = ? AND status = 1");
        $stmt->execute([$id]);
        $goods = $stmt->fetch();
        if (!$goods) error('物品不存在');

        $oldSpaceId = $goods['space_id'];
        $now = time();

        $db->beginTransaction();
        try {
            $stmt = $db->prepare("UPDATE goods SET space_id = ?, updated_at = ? WHERE id = ?");
            $stmt->execute([$newSpaceId, $now, $id]);

            $stmt = $db->prepare("UPDATE storage_space SET item_count = GREATEST(item_count - 1, 0), updated_at = ? WHERE id = ?");
            $stmt->execute([$now, $oldSpaceId]);
            $stmt = $db->prepare("UPDATE storage_space SET item_count = item_count + 1, updated_at = ? WHERE id = ?");
            $stmt->execute([$now, $newSpaceId]);

            $db->commit();
            logOperation($user['id'], 'goods', 'move', $id, "移动物品到空间$newSpaceId");
            success(null, '移动成功');
        } catch (Exception $e) {
            $db->rollBack();
            error('移动失败');
        }
        break;

    case 'copy':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $stmt = $db->prepare("SELECT * FROM goods WHERE id = ? AND status = 1");
        $stmt->execute([$id]);
        $goods = $stmt->fetch();
        if (!$goods) error('物品不存在');

        $now = time();
        $stmt = $db->prepare("INSERT INTO goods (house_id, space_id, creator_id, name, barcode, category, brand, spec, quantity, unit, purchase_date, expiry_date, purchase_price, stock_threshold, note, is_private, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)");
        $stmt->execute([
            $goods['house_id'], $goods['space_id'], $user['id'], $goods['name'] . ' (副本)',
            $goods['barcode'], $goods['category'], $goods['brand'], $goods['spec'],
            $goods['quantity'], $goods['unit'], $goods['purchase_date'], $goods['expiry_date'],
            $goods['purchase_price'], $goods['stock_threshold'], $goods['note'], $goods['is_private'],
            $now, $now
        ]);
        $newId = $db->lastInsertId();

        $stmt = $db->prepare("UPDATE storage_space SET item_count = item_count + 1, updated_at = ? WHERE id = ?");
        $stmt->execute([$now, $goods['space_id']]);
        $stmt = $db->prepare("UPDATE house SET item_count = item_count + 1, updated_at = ? WHERE id = ?");
        $stmt->execute([$now, $goods['house_id']]);

        success(['id' => $newId]);
        break;

    case 'search':
        $keyword = $_GET['keyword'] ?? '';
        $houseId = intval($_GET['house_id'] ?? 0);
        if (empty($keyword)) error('请输入搜索关键词');

        $where = ["g.status = 1", "(g.name LIKE ? OR g.barcode LIKE ? OR g.note LIKE ? OR g.category LIKE ?)"];
        $kw = "%$keyword%";
        $params = [$kw, $kw, $kw, $kw];

        if ($houseId) {
            $where[] = "g.house_id = ?";
            $params[] = $houseId;
        }
        $where[] = "(g.is_private = 0 OR g.creator_id = ?)";
        $params[] = $user['id'];

        $whereStr = implode(' AND ', $where);
        $stmt = $db->prepare("SELECT g.*, s.name as space_name, s.icon as space_icon 
            FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id 
            WHERE $whereStr ORDER BY g.updated_at DESC LIMIT 50");
        $stmt->execute($params);
        $list = $stmt->fetchAll();
        success(['list' => $list]);
        break;

    case 'expiring':
        $houseId = intval($_GET['house_id'] ?? 0);
        $days = intval($_GET['days'] ?? 0);

        // 如果未指定house_id，自动获取用户所属的第一个房屋
        if (!$houseId) {
            $houseStmt = $db->prepare("SELECT house_id FROM house_member WHERE user_id = ? ORDER BY joined_at ASC LIMIT 1");
            $houseStmt->execute([$user['id']]);
            $houseRow = $houseStmt->fetch();
            if ($houseRow) $houseId = intval($houseRow['house_id']);
        }

        // 获取临期提醒规则
        $reminderRules = [];
        $ruleSettings = [];
        try {
            $ruleStmt = $db->prepare("SELECT skey, svalue FROM sys_setting WHERE skey IN ('rule_days_365','rule_days_180','rule_days_90','rule_days_30','rule_days_16','rule_days_short')");
            $ruleStmt->execute();
            while ($row = $ruleStmt->fetch()) {
                $ruleSettings[$row['skey']] = intval($row['svalue']);
            }
        } catch (Exception $e) {}

        $reminderRules[] = ['min_days' => 365, 'max_days' => 99999, 'remind_days' => $ruleSettings['rule_days_365'] ?? 45];
        $reminderRules[] = ['min_days' => 180, 'max_days' => 364,  'remind_days' => $ruleSettings['rule_days_180'] ?? 20];
        $reminderRules[] = ['min_days' => 90,  'max_days' => 179,  'remind_days' => $ruleSettings['rule_days_90'] ?? 15];
        $reminderRules[] = ['min_days' => 30,  'max_days' => 89,   'remind_days' => $ruleSettings['rule_days_30'] ?? 10];
        $reminderRules[] = ['min_days' => 16,  'max_days' => 29,   'remind_days' => $ruleSettings['rule_days_16'] ?? 5];
        $reminderRules[] = ['min_days' => 0,   'max_days' => 15,   'remind_days' => $ruleSettings['rule_days_short'] ?? 2];

        // 计算最大提醒天数
        $maxRemindDays = 0;
        foreach ($reminderRules as $rule) {
            if ($rule['remind_days'] > $maxRemindDays) $maxRemindDays = $rule['remind_days'];
        }
        if ($days > 0) $maxRemindDays = $days;

        $where = ["g.status = 1", "g.expiry_date IS NOT NULL", "g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL ? DAY)", "g.expiry_date >= CURDATE()"];
        $params = [$maxRemindDays];

        if ($houseId) {
            $where[] = "g.house_id = ?";
            $params[] = $houseId;
        }
        $where[] = "(g.is_private = 0 OR g.creator_id = ?)";
        $params[] = $user['id'];

        $whereStr = implode(' AND ', $where);
        $stmt = $db->prepare("SELECT g.*, s.name as space_name, DATEDIFF(g.expiry_date, CURDATE()) as days_left, DATEDIFF(g.expiry_date, g.purchase_date) as shelf_life_days FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id WHERE $whereStr ORDER BY g.expiry_date ASC");
        $stmt->execute($params);
        $allItems = $stmt->fetchAll();

        // 根据规则过滤
        $list = [];
        foreach ($allItems as $item) {
            $shelfLifeDays = intval($item['shelf_life_days'] ?? 0);
            $daysLeft = intval($item['days_left'] ?? 0);
            $remindDays = 7;
            foreach ($reminderRules as $rule) {
                if ($shelfLifeDays >= $rule['min_days'] && $shelfLifeDays <= $rule['max_days']) {
                    $remindDays = $rule['remind_days'];
                    break;
                }
            }
            if ($daysLeft <= $remindDays) {
                $list[] = $item;
            }
        }

        success(['list' => $list]);
        break;

    case 'borrow':
        $input = getJsonInput();
        $goodsId = intval($input['goods_id'] ?? 0);
        $quantity = floatval($input['quantity'] ?? 1);
        $note = $input['note'] ?? '';

        $stmt = $db->prepare("SELECT * FROM goods WHERE id = ? AND status = 1");
        $stmt->execute([$goodsId]);
        $goods = $stmt->fetch();
        if (!$goods) error('物品不存在');
        if ($goods['quantity'] < $quantity) error('库存不足');

        $now = time();
        $db->beginTransaction();
        try {
            $stmt = $db->prepare("INSERT INTO goods_borrow (goods_id, user_id, quantity, borrow_time, status, note) VALUES (?, ?, ?, ?, 1, ?)");
            $stmt->execute([$goodsId, $user['id'], $quantity, $now, $note]);

            $stmt = $db->prepare("UPDATE goods SET quantity = quantity - ?, updated_at = ? WHERE id = ?");
            $stmt->execute([$quantity, $now, $goodsId]);

            $db->commit();
            success(null, '领用成功');
        } catch (Exception $e) {
            $db->rollBack();
            error('领用失败');
        }
        break;

    case 'return':
        $input = getJsonInput();
        $borrowId = intval($input['borrow_id'] ?? 0);

        $stmt = $db->prepare("SELECT * FROM goods_borrow WHERE id = ? AND status = 1");
        $stmt->execute([$borrowId]);
        $borrow = $stmt->fetch();
        if (!$borrow) error('领用记录不存在');

        $now = time();
        $db->beginTransaction();
        try {
            $stmt = $db->prepare("UPDATE goods_borrow SET status = 2, return_time = ? WHERE id = ?");
            $stmt->execute([$now, $borrowId]);

            $stmt = $db->prepare("UPDATE goods SET quantity = quantity + ?, updated_at = ? WHERE id = ?");
            $stmt->execute([$borrow['quantity'], $now, $borrow['goods_id']]);

            $db->commit();
            success(null, '归还成功');
        } catch (Exception $e) {
            $db->rollBack();
            error('归还失败');
        }
        break;

    case 'borrowList':
        $goodsId = intval($_GET['goods_id'] ?? 0);
        $where = ["gb.status = 1"];
        $params = [];
        if ($goodsId) {
            $where[] = "gb.goods_id = ?";
            $params[] = $goodsId;
        }
        $whereStr = implode(' AND ', $where);
        $stmt = $db->prepare("SELECT gb.*, g.name as goods_name, u.nickname as user_name 
            FROM goods_borrow gb 
            LEFT JOIN goods g ON gb.goods_id = g.id 
            LEFT JOIN sys_user u ON gb.user_id = u.id 
            WHERE $whereStr ORDER BY gb.borrow_time DESC LIMIT 50");
        $stmt->execute($params);
        success(['list' => $stmt->fetchAll()]);
        break;

    case 'import':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $items = $input['items'] ?? [];
        if (!$houseId || empty($items)) error('请提供房屋ID和物品数据');

        $role = getUserHouseRole($user['id'], $houseId);
        if (!$role) error('你不是该房屋成员', 403);

        $now = time();
        $imported = 0;
        $errors = [];

        $db->beginTransaction();
        try {
            foreach ($items as $idx => $item) {
                $name = trim($item['name'] ?? '');
                if (empty($name)) {
                    $errors[] = '第' . ($idx + 1) . '行: 物品名称为空';
                    continue;
                }

                $spaceId = intval($item['space_id'] ?? 0);
                if (!$spaceId) {
                    $spaceName = trim($item['space_name'] ?? '');
                    if ($spaceName) {
                        $spStmt = $db->prepare('SELECT id FROM storage_space WHERE house_id = ? AND name = ? LIMIT 1');
                        $spStmt->execute([$houseId, $spaceName]);
                        $sp = $spStmt->fetch();
                        if ($sp) $spaceId = $sp['id'];
                    }
                    if (!$spaceId) {
                        $errors[] = '第' . ($idx + 1) . '行: 未找到空间';
                        continue;
                    }
                }

                $stmt = $db->prepare('INSERT INTO goods (house_id, space_id, creator_id, name, barcode, category, brand, spec, quantity, unit, purchase_date, expiry_date, purchase_price, stock_threshold, note, is_private, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)');
                $stmt->execute([
                    $houseId, $spaceId, $user['id'], $name,
                    $item['barcode'] ?? '', $item['category'] ?? '', $item['brand'] ?? '',
                    $item['spec'] ?? '', floatval($item['quantity'] ?? 1), $item['unit'] ?? '个',
                    $item['purchase_date'] ?? null, $item['expiry_date'] ?? null,
                    $item['purchase_price'] ?? null, $item['stock_threshold'] ?? null,
                    $item['note'] ?? '', 0,
                    $now, $now
                ]);

                $db->prepare('UPDATE storage_space SET item_count = item_count + 1, updated_at = ? WHERE id = ?')
                    ->execute([$now, $spaceId]);
                $db->prepare('UPDATE house SET item_count = item_count + 1, updated_at = ? WHERE id = ?')
                    ->execute([$now, $houseId]);

                $imported++;
            }

            $db->commit();
            logOperation($user['id'], 'goods', 'import', $houseId, '批量导入物品: ' . $imported . '件');
            success(['imported' => $imported, 'errors' => $errors]);
        } catch (Exception $e) {
            $db->rollBack();
            error('导入失败: ' . $e->getMessage());
        }
        break;

    case 'export-template':
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="goods_import_template.csv"');
        echo "\xEF\xBB\xBF";
        echo "物品名称,条形码,分类,品牌,规格,数量,单位,购买日期,保质期,价格,备注,空间名称\n";
        echo "趣多多饼干,6901234567890,食品,趣多多,100g,2,盒,2025-01-15,2025-07-15,12.5,好吃,厨房\n";
        exit;

    default:
        error('未知操作');
}

/**
 * 递归获取所有子孙空间ID
 */
function getDescendantSpaceIds($db, $spaceId, $houseId) {
    $ids = [];
    $stmt = $db->prepare('SELECT id FROM storage_space WHERE parent_id = ? AND house_id = ?');
    $stmt->execute([$spaceId, $houseId]);
    $children = $stmt->fetchAll();
    foreach ($children as $child) {
        $ids[] = $child['id'];
        $ids = array_merge($ids, getDescendantSpaceIds($db, $child['id'], $houseId));
    }
    return $ids;
}
