<?php
$db = getDB();

// 获取所有家庭
$allHouses = $db->query('SELECT h.*, u.username as creator_name FROM house h LEFT JOIN sys_user u ON h.creator_id = u.id WHERE h.status = 1 ORDER BY h.created_at DESC')->fetchAll();
$filterHouse = intval($_GET['house_id'] ?? 0);

// 获取临期提醒规则
$settings = [];
$rows = $db->query("SELECT * FROM sys_setting")->fetchAll();
foreach ($rows as $row) { $settings[$row['skey']] = $row['svalue']; }

$rules = [
    ['label' => '≥1年', 'min_days' => 365, 'max_days' => 99999, 'remind_days' => intval($settings['rule_days_365'] ?? 45)],
    ['label' => '6个月~1年', 'min_days' => 180, 'max_days' => 364, 'remind_days' => intval($settings['rule_days_180'] ?? 20)],
    ['label' => '90天~6个月', 'min_days' => 90, 'max_days' => 179, 'remind_days' => intval($settings['rule_days_90'] ?? 15)],
    ['label' => '30天~90天', 'min_days' => 30, 'max_days' => 89, 'remind_days' => intval($settings['rule_days_30'] ?? 10)],
    ['label' => '16天~30天', 'min_days' => 16, 'max_days' => 29, 'remind_days' => intval($settings['rule_days_16'] ?? 5)],
    ['label' => '<15天', 'min_days' => 0, 'max_days' => 15, 'remind_days' => intval($settings['rule_days_short'] ?? 2)],
];

// 计算最大提醒天数
$maxRemindDays = 0;
foreach ($rules as $rule) {
    if ($rule['remind_days'] > $maxRemindDays) $maxRemindDays = $rule['remind_days'];
}

// 查询临期物品
$where = ["g.status = 1", "g.expiry_date IS NOT NULL", "g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL ? DAY)"];
$params = [$maxRemindDays];
if ($filterHouse) { $where[] = "g.house_id = ?"; $params[] = $filterHouse; }
$whereStr = implode(' AND ', $where);

$stmt = $db->prepare("SELECT g.*, s.name as space_name, h.name as house_name,
    DATEDIFF(g.expiry_date, CURDATE()) as days_left,
    DATEDIFF(g.expiry_date, g.purchase_date) as shelf_life_days,
    (SELECT image_path FROM goods_image WHERE goods_id = g.id ORDER BY sort_order ASC LIMIT 1) as cover_image
    FROM goods g 
    LEFT JOIN storage_space s ON g.space_id = s.id 
    LEFT JOIN house h ON g.house_id = h.id 
    WHERE $whereStr ORDER BY g.expiry_date ASC");
$stmt->execute($params);
$allItems = $stmt->fetchAll();

// 根据规则过滤
$expiringItems = [];
$expiredItems = [];
foreach ($allItems as $item) {
    $shelfLifeDays = intval($item['shelf_life_days'] ?? 0);
    $daysLeft = intval($item['days_left'] ?? 0);
    $remindDays = 7;
    foreach ($rules as $rule) {
        if ($shelfLifeDays >= $rule['min_days'] && $shelfLifeDays <= $rule['max_days']) {
            $remindDays = $rule['remind_days'];
            break;
        }
    }
    $item['matched_rule_days'] = $remindDays;
    if ($daysLeft < 0) {
        $expiredItems[] = $item;
    } elseif ($daysLeft <= $remindDays) {
        $expiringItems[] = $item;
    }
}

// 查询没有设置到期日的物品数量
$noExpiryWhere = ["g.status = 1", "(g.expiry_date IS NULL OR g.expiry_date = '')"];
$noExpiryParams = [];
if ($filterHouse) { $noExpiryWhere[] = "g.house_id = ?"; $noExpiryParams[] = $filterHouse; }
$noExpiryWhereStr = implode(' AND ', $noExpiryWhere);
$noExpiryStmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods g WHERE $noExpiryWhereStr");
$noExpiryStmt->execute($noExpiryParams);
$noExpiryCount = $noExpiryStmt->fetch()['cnt'];

// 查询有到期日但还没到临期的物品
$normalWhere = ["g.status = 1", "g.expiry_date IS NOT NULL", "g.expiry_date > DATE_ADD(CURDATE(), INTERVAL ? DAY)"];
$normalParams = [$maxRemindDays];
if ($filterHouse) { $normalWhere[] = "g.house_id = ?"; $normalParams[] = $filterHouse; }
$normalWhereStr = implode(' AND ', $normalWhere);
$normalStmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods g WHERE $normalWhereStr");
$normalStmt->execute($normalParams);
$normalCount = $normalStmt->fetch()['cnt'];

// 查询库存不足的物品
$lsWhere = ["g.status = 1", "g.stock_threshold > 0", "g.quantity <= g.stock_threshold"];
$lsParams = [];
if ($filterHouse) { $lsWhere[] = "g.house_id = ?"; $lsParams[] = $filterHouse; }
$lsWhereStr = implode(' AND ', $lsWhere);
$lsStmt = $db->prepare("SELECT g.*, s.name as space_name, h.name as house_name FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id LEFT JOIN house h ON g.house_id = h.id WHERE $lsWhereStr ORDER BY (g.quantity / NULLIF(g.stock_threshold, 0)) ASC LIMIT 50");
$lsStmt->execute($lsParams);
$lowStockItems = $lsStmt->fetchAll();
?>

<style>
.reminder-stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-bottom:16px}
.stat-card{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:16px;text-align:center}
.stat-icon{font-size:28px;margin-bottom:8px}
.stat-value{font-size:24px;font-weight:700}
.stat-label{font-size:12px;color:#718096;margin-top:4px}
.stat-card.danger{border-left:3px solid #F56565}
.stat-card.danger .stat-value{color:#F56565}
.stat-card.warning{border-left:3px solid #ED8936}
.stat-card.warning .stat-value{color:#ED8936}
.stat-card.success{border-left:3px solid #48BB78}
.stat-card.success .stat-value{color:#48BB78}
.stat-card.info{border-left:3px solid #5B9FED}
.stat-card.info .stat-value{color:#5B9FED}
.stat-card.muted{border-left:3px solid #A0AEC0}
.stat-card.muted .stat-value{color:#A0AEC0}

.filter-bar{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:16px 20px;margin-bottom:16px}

.reminder-section{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);overflow:hidden;margin-bottom:16px}
.section-header{padding:14px 20px;border-bottom:1px solid var(--border-2);display:flex;align-items:center;justify-content:space-between;background:linear-gradient(90deg,#FAFBFC 0%,#fff 100%)}
.section-title{display:flex;align-items:center;gap:8px;font-size:14px;font-weight:600}
.section-count{background:#ED8936;color:#fff;font-size:11px;padding:2px 8px;border-radius:10px;font-weight:600}
.section-count.danger{background:#F56565}
.section-count.success{background:#48BB78}

.item-row{display:flex;align-items:center;gap:14px;padding:12px 20px;border-bottom:1px solid #F7FAFC;transition:background .15s}
.item-row:hover{background:#FAFBFC}
.item-row:last-child{border-bottom:none}
.item-thumb{width:48px;height:48px;border-radius:8px;background:linear-gradient(135deg,#FFE8D6,#FFD3B0);display:flex;align-items:center;justify-content:center;font-size:22px;flex-shrink:0;overflow:hidden}
.item-thumb img{width:100%;height:100%;object-fit:cover;border-radius:8px}
.item-info{flex:1;min-width:0}
.item-name{font-size:13px;font-weight:600;color:#2D3748;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.item-meta{font-size:11px;color:#718096;margin-top:3px;display:flex;gap:12px;flex-wrap:wrap}
.item-badge{font-size:11px;font-weight:600;padding:3px 10px;border-radius:12px;white-space:nowrap}
.badge-expired{background:rgba(245,101,101,.12);color:#F56565}
.badge-urgent{background:rgba(237,137,54,.12);color:#ED8936}
.badge-normal{background:rgba(72,187,120,.12);color:#48BB78}
.badge-low{background:rgba(91,159,237,.12);color:#5B9FED}

.rules-table{width:100%;border-collapse:collapse;font-size:13px}
.rules-table th{text-align:left;padding:10px 12px;background:#F7FAFC;color:#718096;font-weight:600;font-size:11px;border-bottom:1px solid var(--border-2)}
.rules-table td{padding:10px 12px;border-bottom:1px solid #F7FAFC}

.diagnosis-box{background:#FFFAF0;border:1px solid #FED7AA;border-radius:8px;padding:16px;margin-bottom:16px}
.diagnosis-title{font-size:13px;font-weight:600;color:#C25A1E;margin-bottom:8px}
.diagnosis-item{font-size:12px;color:#718096;padding:4px 0;display:flex;align-items:center;gap:6px}
.diagnosis-item .check{color:#48BB78}
.diagnosis-item .warn{color:#ED8936}
.diagnosis-item .fail{color:#F56565}
</style>

<div class="page-header">
    <div>
        <div class="page-title">⏰ 临期提醒管理</div>
        <div class="page-desc">查看即将过期和已过期物品 · 库存不足提醒 · 规则诊断</div>
    </div>
    <div style="display:flex;gap:8px">
        <a href="?p=settings" class="btn btn-outline btn-sm">⚙ 提醒规则设置</a>
        <button class="btn btn-outline btn-sm" onclick="location.reload()">🔄 刷新</button>
    </div>
</div>

<!-- Filter -->
<div class="filter-bar">
    <form method="GET">
        <input type="hidden" name="p" value="reminders">
        <div style="display:flex;gap:12px;align-items:flex-end">
            <div class="form-group" style="margin:0">
                <label class="form-label">筛选家庭</label>
                <select name="house_id" class="form-control" onchange="this.form.submit()">
                    <option value="">全部家庭</option>
                    <?php foreach ($allHouses as $h): ?>
                    <option value="<?= $h['id'] ?>" <?= $filterHouse == $h['id'] ? 'selected' : '' ?>><?= htmlspecialchars($h['name']) ?></option>
                    <?php endforeach; ?>
                </select>
            </div>
        </div>
    </form>
</div>

<!-- Stats -->
<div class="reminder-stats">
    <div class="stat-card danger">
        <div class="stat-icon">🔴</div>
        <div class="stat-value"><?= count($expiredItems) ?></div>
        <div class="stat-label">已过期物品</div>
    </div>
    <div class="stat-card warning">
        <div class="stat-icon">🟠</div>
        <div class="stat-value"><?= count($expiringItems) ?></div>
        <div class="stat-label">即将临期</div>
    </div>
    <div class="stat-card success">
        <div class="stat-icon">🟢</div>
        <div class="stat-value"><?= $normalCount ?></div>
        <div class="stat-label">正常物品</div>
    </div>
    <div class="stat-card info">
        <div class="stat-icon">📉</div>
        <div class="stat-value"><?= count($lowStockItems) ?></div>
        <div class="stat-label">库存不足</div>
    </div>
    <div class="stat-card muted">
        <div class="stat-icon">❓</div>
        <div class="stat-value"><?= $noExpiryCount ?></div>
        <div class="stat-label">未设置到期日</div>
    </div>
</div>

<!-- Diagnosis -->
<div class="diagnosis-box">
    <div class="diagnosis-title">🔍 诊断信息 — 为什么 APP 看不到提醒？</div>
    <?php
    $totalGoods = $db->query("SELECT COUNT(*) as cnt FROM goods WHERE status = 1")->fetch()['cnt'];
    $hasExpiry = $db->query("SELECT COUNT(*) as cnt FROM goods WHERE status = 1 AND expiry_date IS NOT NULL AND expiry_date != ''")->fetch()['cnt'];
    $hasPurchase = $db->query("SELECT COUNT(*) as cnt FROM goods WHERE status = 1 AND purchase_date IS NOT NULL AND purchase_date != ''")->fetch()['cnt'];
    ?>
    <div class="diagnosis-item">
        <span class="<?= $totalGoods > 0 ? 'check' : 'fail' ?>"><?= $totalGoods > 0 ? '✅' : '❌' ?></span>
        物品总数：<strong><?= $totalGoods ?></strong> 件
    </div>
    <div class="diagnosis-item">
        <span class="<?= $hasExpiry > 0 ? 'check' : 'fail' ?>"><?= $hasExpiry > 0 ? '✅' : '❌' ?></span>
        有到期日的物品：<strong><?= $hasExpiry ?></strong> 件
        <?php if ($hasExpiry == 0): ?>
        <span style="color:#F56565;font-weight:600">← 这是关键！没有到期日就不会有临期提醒</span>
        <?php endif; ?>
    </div>
    <div class="diagnosis-item">
        <span class="<?= $hasPurchase > 0 ? 'check' : 'warn' ?>"><?= $hasPurchase > 0 ? '✅' : '⚠' ?></span>
        有购买日期的物品：<strong><?= $hasPurchase ?></strong> 件
        <span style="color:#718096">（影响保质期计算精度）</span>
    </div>
    <div class="diagnosis-item">
        <span class="check">✅</span>
        临期规则最大提醒范围：<strong><?= $maxRemindDays ?></strong> 天
        <span style="color:#718096">（到期前 <?= $maxRemindDays ?> 天内的物品才会显示）</span>
    </div>
    <div class="diagnosis-item">
        <span class="<?= count($expiredItems) + count($expiringItems) > 0 ? 'check' : 'warn' ?>">
            <?= count($expiredItems) + count($expiringItems) > 0 ? '✅' : '⚠' ?>
        </span>
        当前匹配到的临期/过期物品：<strong><?= count($expiredItems) + count($expiringItems) ?></strong> 件
    </div>
    <?php if ($hasExpiry > 0 && count($expiredItems) + count($expiringItems) == 0): ?>
    <div class="diagnosis-item">
        <span class="warn">⚠</span>
        <span style="color:#ED8936;font-weight:600">有到期日但没有临期物品 → 可能是所有物品的到期日都在 <?= $maxRemindDays ?> 天之后，或者规则配置的提醒天数太小</span>
    </div>
    <?php endif; ?>
</div>

<!-- Reminder Rules -->
<div class="reminder-section">
    <div class="section-header">
        <div class="section-title">📐 当前提醒规则</div>
        <a href="?p=settings" class="btn btn-outline btn-sm">修改规则</a>
    </div>
    <table class="rules-table">
        <thead>
            <tr>
                <th>保质期范围</th>
                <th>到期前几天提醒</th>
                <th>示例</th>
            </tr>
        </thead>
        <tbody>
            <?php
            $examples = ['≥1年' => '罐头、调味品', '6个月~1年' => '牛奶、方便面', '90天~6个月' => '零食、饮料', '30天~90天' => '鲜鸡蛋', '16天~30天' => '酸奶', '<15天' => '鲜奶'];
            foreach ($rules as $rule): ?>
            <tr>
                <td><strong><?= $rule['label'] ?></strong></td>
                <td><span style="color:#FF8C42;font-weight:600"><?= $rule['remind_days'] ?> 天</span></td>
                <td style="color:#718096;font-size:12px"><?= $examples[$rule['label']] ?? '' ?></td>
            </tr>
            <?php endforeach; ?>
        </tbody>
    </table>
</div>

<!-- Expired Items -->
<?php if (!empty($expiredItems)): ?>
<div class="reminder-section">
    <div class="section-header">
        <div class="section-title">
            🔴 已过期物品
            <span class="section-count danger"><?= count($expiredItems) ?></span>
        </div>
    </div>
    <?php foreach ($expiredItems as $item): ?>
    <div class="item-row">
        <div class="item-thumb">
            <?php if (!empty($item['cover_image'])): ?>
                <img src="<?= htmlspecialchars(IMAGE_URL_PREFIX . $item['cover_image']) ?>" onerror="this.outerHTML='⏰'">
            <?php else: ?>⏰<?php endif; ?>
        </div>
        <div class="item-info">
            <div class="item-name"><?= htmlspecialchars($item['name']) ?></div>
            <div class="item-meta">
                <span>📍 <?= htmlspecialchars($item['space_name'] ?? '未分类') ?></span>
                <span>🏠 <?= htmlspecialchars($item['house_name'] ?? '-') ?></span>
                <span>📅 到期日: <?= $item['expiry_date'] ?></span>
                <?php if ($item['purchase_date']): ?><span>🛒 购买: <?= $item['purchase_date'] ?></span><?php endif; ?>
            </div>
        </div>
        <span class="item-badge badge-expired">已过期 <?= abs($item['days_left']) ?> 天</span>
        <a href="?p=items&action=edit&id=<?= $item['id'] ?>" class="btn btn-outline btn-sm">编辑</a>
    </div>
    <?php endforeach; ?>
</div>
<?php endif; ?>

<!-- Expiring Items -->
<?php if (!empty($expiringItems)): ?>
<div class="reminder-section">
    <div class="section-header">
        <div class="section-title">
            🟠 即将临期物品
            <span class="section-count"><?= count($expiringItems) ?></span>
        </div>
    </div>
    <?php foreach ($expiringItems as $item): ?>
    <div class="item-row">
        <div class="item-thumb">
            <?php if (!empty($item['cover_image'])): ?>
                <img src="<?= htmlspecialchars(IMAGE_URL_PREFIX . $item['cover_image']) ?>" onerror="this.outerHTML='⏰'">
            <?php else: ?>⏰<?php endif; ?>
        </div>
        <div class="item-info">
            <div class="item-name"><?= htmlspecialchars($item['name']) ?></div>
            <div class="item-meta">
                <span>📍 <?= htmlspecialchars($item['space_name'] ?? '未分类') ?></span>
                <span>🏠 <?= htmlspecialchars($item['house_name'] ?? '-') ?></span>
                <span>📅 到期日: <?= $item['expiry_date'] ?></span>
                <span>📐 保质期: <?= $item['shelf_life_days'] ?>天 → 提醒规则: <?= $item['matched_rule_days'] ?>天</span>
            </div>
        </div>
        <span class="item-badge badge-urgent"><?= $item['days_left'] ?> 天后过期</span>
        <a href="?p=items&action=edit&id=<?= $item['id'] ?>" class="btn btn-outline btn-sm">编辑</a>
    </div>
    <?php endforeach; ?>
</div>
<?php endif; ?>

<?php if (empty($expiredItems) && empty($expiringItems)): ?>
<div class="reminder-section">
    <div class="section-header">
        <div class="section-title">✅ 暂无临期物品</div>
    </div>
    <div style="padding:40px;text-align:center;color:#A0AEC0">
        <div style="font-size:40px;margin-bottom:12px">🎉</div>
        <div style="font-size:14px">当前没有临期或过期物品</div>
        <div style="font-size:12px;margin-top:8px">
            <?php if ($hasExpiry == 0): ?>
            <span style="color:#ED8936">提示：物品需要设置「保质期」或「到期日」才能触发临期提醒</span>
            <?php else: ?>
            所有物品都在安全期内
            <?php endif; ?>
        </div>
    </div>
</div>
<?php endif; ?>

<!-- Low Stock Items -->
<?php if (!empty($lowStockItems)): ?>
<div class="reminder-section">
    <div class="section-header">
        <div class="section-title">
            📉 库存不足提醒
            <span class="section-count" style="background:#5B9FED"><?= count($lowStockItems) ?></span>
        </div>
    </div>
    <?php foreach ($lowStockItems as $item): ?>
    <div class="item-row">
        <div class="item-thumb" style="background:linear-gradient(135deg,#D6E8FF,#B0D3FF)">📦</div>
        <div class="item-info">
            <div class="item-name"><?= htmlspecialchars($item['name']) ?></div>
            <div class="item-meta">
                <span>📍 <?= htmlspecialchars($item['space_name'] ?? '未分类') ?></span>
                <span>🏠 <?= htmlspecialchars($item['house_name'] ?? '-') ?></span>
            </div>
        </div>
        <span class="item-badge badge-low">库存 <?= $item['quantity'] ?> / 阈值 <?= $item['stock_threshold'] ?></span>
        <a href="?p=items&action=edit&id=<?= $item['id'] ?>" class="btn btn-outline btn-sm">编辑</a>
    </div>
    <?php endforeach; ?>
</div>
<?php endif; ?>


<!-- Active Lendings -->
<?php
try {
    $lendItems = $db->query("SELECT gb.*, g.name as goods_name, g.space_id, s.name as space_name, h.name as house_name
        FROM goods_borrow gb
        LEFT JOIN goods g ON gb.goods_id = g.id
        LEFT JOIN storage_space s ON g.space_id = s.id
        LEFT JOIN house h ON g.house_id = h.id
        WHERE gb.status = 1 AND gb.lend_to IS NOT NULL AND gb.lend_to != ''
        ORDER BY gb.borrow_time DESC LIMIT 20")->fetchAll();
} catch (Exception $e) { $lendItems = []; }
?>
<?php if (!empty($lendItems)): ?>
<div class="reminder-section">
    <div class="section-header">
        <div class="section-title">
            🤝 借出中
            <span class="section-count" style="background:#38A169"><?= count($lendItems) ?></span>
        </div>
    </div>
    <?php foreach ($lendItems as $item): ?>
    <div class="item-row">
        <div class="item-thumb" style="background:linear-gradient(135deg,#E6FFFA,#B2F5EA)">📤</div>
        <div class="item-info">
            <div class="item-name"><?= htmlspecialchars($item['goods_name'] ?? '未知物品') ?></div>
            <div class="item-meta">
                <span>🤝 借给: <?= htmlspecialchars($item['lend_to']) ?></span>
                <span>📦 <?= $item['quantity'] ?>件</span>
                <span>📅 <?= date('Y-m-d', $item['borrow_time']) ?></span>
                <?php
                $daysSince = intval((time() - $item['borrow_time']) / 86400);
                if ($item['remind_at'] && $item['remind_at'] < time()):
                ?>
                <span style="color:#F56565;font-weight:600">⏰ 已到期需归还</span>
                <?php endif; ?>
            </div>
        </div>
        <span class="item-badge" style="background:#FFF5F0;color:#C25A1E">已借 <?= $daysSince ?> 天</span>
        <?php if ($item['goods_id']): ?>
        <a href="?p=items&action=edit&id=<?= $item['goods_id'] ?>" class="btn btn-outline btn-sm">查看</a>
        <?php endif; ?>
    </div>
    <?php endforeach; ?>
</div>
<?php endif; ?>
