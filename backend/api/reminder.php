<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
corsHeaders();
/**
 * 提醒接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'list':
        try {
        $houseId = intval($_GET['house_id'] ?? 0);
        $type = $_GET['type'] ?? '';
        $page = max(1, intval($_GET['page'] ?? 1));
        $pageSize = 20;

        // 获取用户所有家庭
        $houseIds = [];
        $hStmt = $db->prepare("SELECT house_id FROM house_member WHERE user_id = ?");
        $hStmt->execute([$user['id']]);
        while ($row = $hStmt->fetch()) $houseIds[] = intval($row['house_id']);
        $hStmt2 = $db->prepare("SELECT id FROM house WHERE creator_id = ? AND status = 1");
        $hStmt2->execute([$user['id']]);
        while ($row = $hStmt2->fetch()) $houseIds[] = intval($row['id']);
        $houseIds = array_values(array_unique($houseIds));

        $reminders = [];

        // 1. 获取手动创建的提醒
        $where = ["r.user_id = ?"];
        $params = [$user['id']];
        if ($houseId > 0) { $where[] = "r.house_id = ?"; $params[] = $houseId; }
        elseif (!empty($houseIds)) {
            $placeholders = implode(',', array_fill(0, count($houseIds), '?'));
            $where[] = "r.house_id IN ($placeholders)";
            $params = array_merge($params, $houseIds);
        }
        if ($type) { $where[] = "r.type = ?"; $params[] = $type; }
        $whereStr = implode(' AND ', $where);
        $offset = ($page - 1) * $pageSize;
        $stmt = $db->prepare("SELECT r.*, g.name as goods_name, s.name as space_name, 'manual' as source
            FROM reminder r 
            LEFT JOIN goods g ON r.goods_id = g.id 
            LEFT JOIN storage_space s ON r.space_id = s.id 
            WHERE $whereStr AND r.is_handled = 0");
        $stmt->execute($params);
        $reminders = $stmt->fetchAll();

        // 2. 自动生成：临期物品提醒（使用动态规则）
        if (!$type || $type === 'expiry') {
            // 获取临期提醒规则
            $reminderRules = getExpiryReminderRules($db);
            // 计算所有规则中最大的提醒天数，用于数据库查询范围
            $maxRemindDays = 0;
            foreach ($reminderRules as $rule) {
                if ($rule['remind_days'] > $maxRemindDays) $maxRemindDays = $rule['remind_days'];
            }
            if ($maxRemindDays <= 0) $maxRemindDays = 45; // 默认最大45天

            $expWhere = ["g.status = 1", "g.expiry_date IS NOT NULL", "g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL ? DAY)", "g.expiry_date >= DATE_SUB(CURDATE(), INTERVAL 90 DAY)"];
            $expParams = [$maxRemindDays];
            if ($houseId > 0) { $expWhere[] = "g.house_id = ?"; $expParams[] = $houseId; }
            elseif (!empty($houseIds)) {
                $placeholders = implode(',', array_fill(0, count($houseIds), '?'));
                $expWhere[] = "g.house_id IN ($placeholders)";
                $expParams = array_merge($expParams, $houseIds);
            }
            $expWhere[] = "(g.is_private = 0 OR g.creator_id = ?)";
            $expParams[] = $user['id'];
            $expWhereStr = implode(' AND ', $expWhere);
            $stmt = $db->prepare("SELECT g.id as goods_id, g.name as goods_name, g.expiry_date, g.purchase_date, s.name as space_name,
                DATEDIFF(g.expiry_date, CURDATE()) as days_left,
                DATEDIFF(g.expiry_date, g.purchase_date) as shelf_life_days
                FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id
                WHERE $expWhereStr ORDER BY g.expiry_date ASC LIMIT 10");
            $stmt->execute($expParams);
            foreach ($stmt->fetchAll() as $item) {
                $daysLeft = intval($item['days_left']);

                // 计算该物品的保质期总天数
                $shelfLifeDays = intval($item['shelf_life_days'] ?? 0);
                if ($shelfLifeDays <= 0 && !empty($item['purchase_date']) && !empty($item['expiry_date'])) {
                    $purchaseTs = strtotime($item['purchase_date']);
                    $expiryTs = strtotime($item['expiry_date']);
                    if ($purchaseTs && $expiryTs) {
                        $shelfLifeDays = intval(($expiryTs - $purchaseTs) / 86400);
                    }
                }

                // 根据保质期时长匹配提醒规则
                $remindDaysForItem = getRemindDaysForShelfLife($reminderRules, $shelfLifeDays);

                // 只有当剩余天数 <= 提醒天数时才加入提醒列表
                if ($daysLeft > $remindDaysForItem) continue;

                $title = $daysLeft < 0 ? $item['goods_name'] . ' 已过期' : $item['goods_name'] . ' 将在' . $daysLeft . '天后过期';
                $reminders[] = [
                    'id' => 'exp_' . $item['goods_id'],
                    'type' => 'expiry',
                    'title' => $title,
                    'content' => '存放于: ' . ($item['space_name'] ?? '未分类'),
                    'goods_name' => $item['goods_name'],
                    'space_name' => $item['space_name'] ?? '',
                    'location' => $item['space_name'] ?? '',
                    'remind_time' => time(),
                    'is_read' => 0,
                    'is_handled' => 0,
                    'source' => 'auto',
                    'goods_id' => $item['goods_id'],
                    'days_left' => $daysLeft
                ];
            }
        }

        // 3. 自动生成：库存不足提醒
        if (!$type || $type === 'low_stock') {
            $lsWhere = ["g.status = 1", "g.stock_threshold > 0", "g.quantity <= g.stock_threshold"];
            $lsParams = [];
            if ($houseId) { $lsWhere[] = "g.house_id = ?"; $lsParams[] = $houseId; }
            $lsWhere[] = "(g.is_private = 0 OR g.creator_id = ?)";
            $lsParams[] = $user['id'];
            $lsWhereStr = implode(' AND ', $lsWhere);
            $stmt = $db->prepare("SELECT g.id as goods_id, g.name as goods_name, g.quantity, g.unit, g.stock_threshold, s.name as space_name
                FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id
                WHERE $lsWhereStr LIMIT 10");
            $stmt->execute($lsParams);
            foreach ($stmt->fetchAll() as $item) {
                $reminders[] = [
                    'id' => 'ls_' . $item['goods_id'],
                    'type' => 'low_stock',
                    'title' => $item['goods_name'] . ' 库存不足',
                    'content' => '当前: ' . $item['quantity'] . ($item['unit'] ?: '件') . ' / 阈值: ' . $item['stock_threshold'],
                    'goods_name' => $item['goods_name'],
                    'space_name' => $item['space_name'] ?? '',
                    'location' => $item['space_name'] ?? '',
                    'remind_time' => time(),
                    'is_read' => 0,
                    'is_handled' => 0,
                    'source' => 'auto',
                    'goods_id' => $item['goods_id'],
                    'current_qty' => $item['quantity'],
                    'threshold' => $item['stock_threshold']
                ];
            }
        }

        // 按类型排序：未读优先，然后按类型
        usort($reminders, function($a, $b) {
            $readA = isset($a['is_read']) ? intval($a['is_read']) : 0;
            $readB = isset($b['is_read']) ? intval($b['is_read']) : 0;
            if ($readA != $readB) return $readA - $readB;
            $typeOrder = ['expiry' => 0, 'low_stock' => 1, 'custom' => 2];
            $oa = isset($typeOrder[$a['type']]) ? $typeOrder[$a['type']] : 3;
            $ob = isset($typeOrder[$b['type']]) ? $typeOrder[$b['type']] : 3;
            return $oa - $ob;
        });

        success(['list' => array_slice($reminders, $offset, $pageSize)]);
        } catch (Exception $e) {
            error('提醒列表加载失败: ' . $e->getMessage(), 500);
        }
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

/**
 * 获取临期提醒规则配置
 */
function getExpiryReminderRules($db) {
    $defaults = [
        ['min_days' => 365, 'max_days' => 99999, 'remind_days' => 45, 'label' => '≥1年'],
        ['min_days' => 180, 'max_days' => 364,  'remind_days' => 20, 'label' => '6个月~1年'],
        ['min_days' => 90,  'max_days' => 179,  'remind_days' => 15, 'label' => '90天~6个月'],
        ['min_days' => 30,  'max_days' => 89,   'remind_days' => 10, 'label' => '30天~90天'],
        ['min_days' => 16,  'max_days' => 29,   'remind_days' => 5,  'label' => '16天~30天'],
        ['min_days' => 0,   'max_days' => 15,   'remind_days' => 2,  'label' => '<15天'],
    ];

    // 从数据库读取配置
    $settings = [];
    try {
        $stmt = $db->prepare("SELECT skey, svalue FROM sys_setting WHERE skey IN ('rule_days_365','rule_days_180','rule_days_90','rule_days_30','rule_days_16','rule_days_short')");
        $stmt->execute();
        while ($row = $stmt->fetch()) {
            $settings[$row['skey']] = intval($row['svalue']);
        }
    } catch (Exception $e) {}

    // 合并默认值和数据库值
    $rules = [];
    $rules[] = ['min_days' => 365, 'max_days' => 99999, 'remind_days' => $settings['rule_days_365'] ?? 45];
    $rules[] = ['min_days' => 180, 'max_days' => 364,  'remind_days' => $settings['rule_days_180'] ?? 20];
    $rules[] = ['min_days' => 90,  'max_days' => 179,  'remind_days' => $settings['rule_days_90'] ?? 15];
    $rules[] = ['min_days' => 30,  'max_days' => 89,   'remind_days' => $settings['rule_days_30'] ?? 10];
    $rules[] = ['min_days' => 16,  'max_days' => 29,   'remind_days' => $settings['rule_days_16'] ?? 5];
    $rules[] = ['min_days' => 0,   'max_days' => 15,   'remind_days' => $settings['rule_days_short'] ?? 2];

    return $rules;
}

/**
 * 根据保质期总天数获取对应的提醒天数
 * 当保质期未知(0天)时，使用默认7天提醒窗口
 */
function getRemindDaysForShelfLife($rules, $shelfLifeDays) {
    // 保质期未知时，使用默认7天提醒窗口
    if ($shelfLifeDays <= 0) return 7;
    foreach ($rules as $rule) {
        if ($shelfLifeDays >= $rule['min_days'] && $shelfLifeDays <= $rule['max_days']) {
            return $rule['remind_days'];
        }
    }
    return 7; // 默认7天
}
