<?php
/**
 * AI 识别结果确认入库接口
 * POST /api/ai/confirm
 * 复用原有物品创建逻辑
 */
require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../config/helpers.php';
require_once __DIR__ . '/../../config/ai.php';

corsHeaders();
$db = getDB();
$user = requireLogin();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    error('请求方式错误', 405);
}

$input = getJsonInput();
$houseId = intval($input['house_id'] ?? 0);
$spaceId = intval($input['space_id'] ?? 0);
$name = trim($input['name'] ?? $input['goods_name'] ?? '');

if (!$houseId || !$spaceId || empty($name)) {
    error('请填写物品名称并选择存放位置');
}

// 权限检查
$role = getUserHouseRole($user['id'], $houseId);
if (!$role) {
    error('你不是该房屋成员', 403);
}

$now = time();

// 复用 goods.php 的创建逻辑
$stmt = $db->prepare("INSERT INTO goods (house_id, space_id, creator_id, name, barcode, category, brand, spec, quantity, unit, purchase_date, expiry_date, purchase_price, stock_threshold, note, is_private, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)");

$purchaseDate = !empty($input['purchase_date']) ? $input['purchase_date'] : null;
$expiryDate = !empty($input['expiry_date']) ? $input['expiry_date'] : (!empty($input['expire_date']) ? $input['expire_date'] : null);
$purchasePrice = isset($input['purchase_price']) && $input['purchase_price'] !== '' ? floatval($input['purchase_price']) : null;
$stockThreshold = isset($input['stock_threshold']) && $input['stock_threshold'] !== '' ? floatval($input['stock_threshold']) : null;

$stmt->execute([
    $houseId, $spaceId, $user['id'], $name,
    $input['barcode'] ?? '', $input['category'] ?? '', $input['brand'] ?? '',
    $input['spec'] ?? '', floatval($input['quantity'] ?? 1), $input['unit'] ?? '个',
    $purchaseDate, $expiryDate,
    $purchasePrice, $stockThreshold,
    $input['note'] ?? ($input['storage_tip'] ?? ''), intval($input['is_private'] ?? 0),
    $now, $now
]);
$goodsId = $db->lastInsertId();

// 处理图片
if (!empty($input['image_path'])) {
    $stmt2 = $db->prepare("INSERT INTO goods_image (goods_id, image_path, sort_order, created_at) VALUES (?, ?, 0, ?)");
    $stmt2->execute([$goodsId, $input['image_path'], $now]);
}
if (!empty($input['images'])) {
    foreach ($input['images'] as $idx => $imgPath) {
        $stmt3 = $db->prepare("INSERT INTO goods_image (goods_id, image_path, sort_order, created_at) VALUES (?, ?, ?, ?)");
        $stmt3->execute([$goodsId, $imgPath, $idx, $now]);
    }
}

// 处理标签
if (!empty($input['tags'])) {
    foreach ($input['tags'] as $tagId) {
        $stmt4 = $db->prepare("INSERT IGNORE INTO goods_tag (goods_id, tag_id) VALUES (?, ?)");
        $stmt4->execute([$goodsId, intval($tagId)]);
    }
}

// 更新空间物品计数
$db->prepare("UPDATE storage_space SET item_count = item_count + 1, updated_at = ? WHERE id = ?")
    ->execute([$now, $spaceId]);
$db->prepare("UPDATE house SET item_count = item_count + 1, updated_at = ? WHERE id = ?")
    ->execute([$now, $houseId]);

// 如果有保质期，创建提醒
if (!empty($expiryDate)) {
    $expiryTs = strtotime($expiryDate);
    $remindDays = 7;
    $shelfLifeDays = 0;
    if (!empty($purchaseDate)) {
        $purchaseTs = strtotime($purchaseDate);
        if ($purchaseTs && $expiryTs) {
            $shelfLifeDays = intval(($expiryTs - $purchaseTs) / 86400);
        }
    }
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
    } catch (Exception $e) {}

    $remindTime = $expiryTs - ($remindDays * 86400);
    if ($remindTime > $now) {
        $db->prepare("INSERT INTO reminder (user_id, house_id, goods_id, type, title, content, remind_time, created_at) VALUES (?, ?, ?, 'expiry', ?, ?, ?, ?)")
            ->execute([$user['id'], $houseId, $goodsId, "物品即将过期: $name", "保质期至 $expiryDate", $remindTime, $now]);
    }
}

// 记录 AI 入库日志
$callLogId = intval($input['ai_call_log_id'] ?? 0);
if ($callLogId > 0) {
    $db->prepare("UPDATE ai_call_log SET type = 'confirm' WHERE id = ?")->execute([$callLogId]);
}

logOperation($user['id'], 'goods', 'create', $goodsId, "AI智能录入物品: $name");

success(['id' => $goodsId, 'message' => '入库成功']);
