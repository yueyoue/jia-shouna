<?php
$db = getDB();

// 获取当前房屋
$houseId = intval($_GET['house_id'] ?? 0);
$allHouses = $db->query('SELECT h.id, h.name FROM house h WHERE h.status = 1 ORDER BY h.created_at DESC')->fetchAll();
if (!$houseId && !empty($allHouses)) $houseId = $allHouses[0]['id'];

$houseFilter = $houseId ? " AND g.house_id = $houseId" : "";
$baseWhere = "g.status = 1";

// === 总览数据 ===
$stats = [];
$stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter");
$stats['total_items'] = $stmt->fetch()['cnt'];

$stmt = $db->query("SELECT COALESCE(SUM(g.purchase_price * g.quantity), 0) as total FROM goods g WHERE $baseWhere$houseFilter AND g.purchase_price IS NOT NULL");
$stats['total_value'] = floatval($stmt->fetch()['total']);

$stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter AND g.purchase_price IS NULL");
$stats['pending_value'] = $stmt->fetch()['cnt'];

$stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter AND g.expiry_date IS NOT NULL AND g.expiry_date < CURDATE()");
$stats['expired'] = $stmt->fetch()['cnt'];

$stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter AND g.expiry_date IS NOT NULL AND g.expiry_date >= CURDATE() AND g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY)");
$stats['expiring_7days'] = $stmt->fetch()['cnt'];

$stmt = $db->query("SELECT COUNT(*) as cnt FROM goods g WHERE $baseWhere$houseFilter AND g.stock_threshold > 0 AND g.quantity <= g.stock_threshold");
$stats['low_stock'] = $stmt->fetch()['cnt'];

$stmt = $db->query("SELECT COUNT(*) as cnt FROM storage_space" . ($houseId ? " WHERE house_id = $houseId" : ""));
$stats['total_spaces'] = $stmt->fetch()['cnt'];

// === 按分类统计 ===
$byCategory = $db->query("SELECT g.category, COUNT(*) as cnt, COALESCE(SUM(g.purchase_price * g.quantity), 0) as value
    FROM goods g WHERE $baseWhere$houseFilter GROUP BY g.category ORDER BY cnt DESC")->fetchAll();

// === 按空间统计 ===
$spaceWhere = $houseId ? " WHERE s.house_id = $houseId" : "";
$bySpace = $db->query("SELECT s.name, COUNT(g.id) as cnt, COALESCE(SUM(g.purchase_price * g.quantity), 0) as value
    FROM storage_space s LEFT JOIN goods g ON g.space_id = s.id AND g.status = 1
    $spaceWhere GROUP BY s.id ORDER BY cnt DESC LIMIT 10")->fetchAll();

// === 按用户统计 ===
$byUser = $db->query("SELECT u.nickname, u.username, COUNT(g.id) as cnt
    FROM sys_user u INNER JOIN goods g ON g.creator_id = u.id AND g.status = 1
    WHERE 1=1$houseFilter GROUP BY u.id ORDER BY cnt DESC LIMIT 10")->fetchAll();

// === 临期物品 ===
$expiringList = $db->query("SELECT g.id, g.name, g.expiry_date, g.category, s.name as space_name,
    DATEDIFF(g.expiry_date, CURDATE()) as days_left
    FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id
    WHERE $baseWhere$houseFilter AND g.expiry_date IS NOT NULL AND g.expiry_date >= CURDATE()
    AND g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY)
    ORDER BY g.expiry_date ASC LIMIT 10")->fetchAll();

// === 库存不足 ===
$lowStockList = $db->query("SELECT g.id, g.name, g.quantity, g.unit, g.stock_threshold, g.category, s.name as space_name
    FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id
    WHERE $baseWhere$houseFilter AND g.stock_threshold > 0 AND g.quantity <= g.stock_threshold
    ORDER BY (g.quantity / g.stock_threshold) ASC LIMIT 10")->fetchAll();

// 饼图颜色
$chartColors = ['#FF8C42','#4A90D9','#27AE60','#9B59B6','#E74C3C','#F39C12','#1ABC9C','#34495E','#E91E63','#00BCD4'];
?>

<style>
.stats-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:24px}
.stat-card{background:#fff;border-radius:12px;padding:20px;border:1px solid #E2E8F0;box-shadow:0 1px 3px rgba(0,0,0,.04)}
.stat-card .emoji{font-size:28px;margin-bottom:8px}
.stat-card .value{font-size:28px;font-weight:700;margin-bottom:4px}
.stat-card .label{font-size:13px;color:#718096}
.stat-card.danger .value{color:#E53E3E}
.stat-card.warning .value{color:#DD6B20}
.stat-card.success .value{color:#38A169}
.stat-card.info .value{color:#3182CE}
.chart-card{background:#fff;border-radius:12px;padding:20px;border:1px solid #E2E8F0;box-shadow:0 1px 3px rgba(0,0,0,.04);margin-bottom:16px}
.chart-card h3{font-size:15px;font-weight:600;margin-bottom:16px;color:#2D3748}
.bar-row{display:flex;align-items:center;margin-bottom:10px}
.bar-label{width:80px;font-size:13px;color:#4A5568;flex-shrink:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.bar-track{flex:1;height:24px;background:#F7FAFC;border-radius:4px;overflow:hidden;margin:0 12px}
.bar-fill{height:100%;border-radius:4px;transition:width .6s;display:flex;align-items:center;padding-left:8px;font-size:11px;color:#fff;font-weight:600}
.bar-value{font-size:13px;color:#718096;width:50px;text-align:right;flex-shrink:0}
.list-table{width:100%;border-collapse:collapse;font-size:13px}
.list-table th{text-align:left;padding:8px 12px;background:#F7FAFC;color:#718096;font-weight:500;border-bottom:1px solid #E2E8F0}
.list-table td{padding:8px 12px;border-bottom:1px solid #EDF2F7}
.list-table tr:hover{background:#FAFBFC}
.badge{display:inline-block;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:600}
.badge-danger{background:#FED7D7;color:#C53030}
.badge-warning{background:#FEEBC8;color:#C05621}
.badge-info{background:#BEE3F8;color:#2B6CB0}
.house-filter{margin-bottom:16px;display:flex;align-items:center;gap:12px}
.house-filter select{padding:6px 12px;border:1px solid #E2E8F0;border-radius:6px;font-size:13px}
.two-col{display:grid;grid-template-columns:1fr 1fr;gap:16px}
@media(max-width:900px){.stats-grid{grid-template-columns:repeat(2,1fr)}.two-col{grid-template-columns:1fr}}
</style>

<div class="page-header">
    <div>
        <div class="page-title">📈 数据统计</div>
        <div class="page-desc">全方位了解您的收纳数据</div>
    </div>
</div>

<!-- 房屋筛选 -->
<div class="house-filter">
    <span style="font-size:13px;color:#718096">当前家庭：</span>
    <select onchange="location.href='?p=stats&house_id='+this.value">
        <?php foreach ($allHouses as $h): ?>
        <option value="<?= $h['id'] ?>" <?= $houseId == $h['id'] ? 'selected' : '' ?>><?= htmlspecialchars($h['name']) ?></option>
        <?php endforeach; ?>
    </select>
</div>

<!-- 总览卡片 -->
<div class="stats-grid">
    <div class="stat-card info">
        <div class="emoji">📦</div>
        <div class="value"><?= number_format($stats['total_items']) ?></div>
        <div class="label">物品总数</div>
    </div>
    <div class="stat-card success">
        <div class="emoji">💰</div>
        <div class="value">¥<?= $stats['total_value'] >= 10000 ? number_format($stats['total_value'], 0) : number_format($stats['total_value'], 2) ?></div>
        <div class="label">物品总价</div>
    </div>
    <div class="stat-card">
        <div class="emoji">🏠</div>
        <div class="value"><?= $stats['total_spaces'] ?></div>
        <div class="label">收纳空间</div>
    </div>
    <div class="stat-card danger">
        <div class="emoji">⏰</div>
        <div class="value"><?= $stats['expired'] ?></div>
        <div class="label">已过期</div>
    </div>
    <div class="stat-card warning">
        <div class="emoji">⚠️</div>
        <div class="value"><?= $stats['expiring_7days'] ?></div>
        <div class="label">7天内到期</div>
    </div>
    <div class="stat-card" style="border-left:3px solid #E91E63">
        <div class="emoji">📉</div>
        <div class="value" style="color:#E91E63"><?= $stats['low_stock'] ?></div>
        <div class="label">库存不足</div>
    </div>
</div>

<!-- 分类分布 + 空间分布 -->
<div class="two-col">
    <div class="chart-card">
        <h3>📊 分类分布</h3>
        <?php if (!empty($byCategory)):
            $maxCat = max(array_column($byCategory, 'cnt'));
            $totalCat = array_sum(array_column($byCategory, 'cnt'));
            foreach ($byCategory as $i => $cat):
                $pct = $totalCat > 0 ? $cat['cnt'] / $totalCat * 100 : 0;
                $barW = $maxCat > 0 ? $cat['cnt'] / $maxCat * 100 : 0;
                $color = $chartColors[$i % count($chartColors)];
        ?>
        <div class="bar-row">
            <div class="bar-label"><?= htmlspecialchars($cat['category'] ?: '未分类') ?></div>
            <div class="bar-track">
                <div class="bar-fill" style="width:<?= $barW ?>%;background:<?= $color ?>"><?= $cat['cnt'] ?>件</div>
            </div>
            <div class="bar-value"><?= number_format($pct, 0) ?>%</div>
        </div>
        <?php endforeach; else: ?>
        <div style="text-align:center;color:#A0AEC0;padding:20px">暂无数据</div>
        <?php endif; ?>
    </div>

    <div class="chart-card">
        <h3>🏠 空间分布</h3>
        <?php if (!empty($bySpace)):
            $maxSp = max(array_column($bySpace, 'cnt'));
            $totalSp = array_sum(array_column($bySpace, 'cnt'));
            foreach ($bySpace as $i => $sp):
                $pct = $totalSp > 0 ? $sp['cnt'] / $totalSp * 100 : 0;
                $barW = $maxSp > 0 ? $sp['cnt'] / $maxSp * 100 : 0;
                $color = $chartColors[$i % count($chartColors)];
        ?>
        <div class="bar-row">
            <div class="bar-label"><?= htmlspecialchars($sp['name']) ?></div>
            <div class="bar-track">
                <div class="bar-fill" style="width:<?= $barW ?>%;background:<?= $color ?>"><?= $sp['cnt'] ?>件</div>
            </div>
            <div class="bar-value"><?= number_format($pct, 0) ?>%</div>
        </div>
        <?php endforeach; else: ?>
        <div style="text-align:center;color:#A0AEC0;padding:20px">暂无数据</div>
        <?php endif; ?>
    </div>
</div>

<!-- 用户录入统计 -->
<div class="chart-card">
    <h3>👤 用户录入统计</h3>
    <?php if (!empty($byUser)):
        $maxUser = max(array_column($byUser, 'cnt'));
        foreach ($byUser as $i => $u):
            $barW = $maxUser > 0 ? $u['cnt'] / $maxUser * 100 : 0;
            $color = $chartColors[$i % count($chartColors)];
    ?>
    <div class="bar-row">
        <div class="bar-label"><?= htmlspecialchars($u['nickname'] ?: $u['username']) ?></div>
        <div class="bar-track">
            <div class="bar-fill" style="width:<?= $barW ?>%;background:<?= $color ?>"><?= $u['cnt'] ?>件</div>
        </div>
        <div class="bar-value"><?= $u['cnt'] ?>件</div>
    </div>
    <?php endforeach; else: ?>
    <div style="text-align:center;color:#A0AEC0;padding:20px">暂无数据</div>
    <?php endif; ?>
</div>

<!-- 临期物品 + 库存不足 -->
<div class="two-col">
    <div class="chart-card">
        <h3>⏰ 临期物品（7天内）</h3>
        <?php if (!empty($expiringList)): ?>
        <table class="list-table">
            <thead><tr><th>物品</th><th>分类</th><th>空间</th><th>剩余</th></tr></thead>
            <tbody>
            <?php foreach ($expiringList as $item): ?>
            <tr>
                <td><strong><?= htmlspecialchars($item['name']) ?></strong></td>
                <td><?= htmlspecialchars($item['category']) ?></td>
                <td style="color:#718096"><?= htmlspecialchars($item['space_name'] ?? '') ?></td>
                <td>
                    <?php if ($item['days_left'] <= 0): ?>
                        <span class="badge badge-danger">已过期</span>
                    <?php elseif ($item['days_left'] <= 3): ?>
                        <span class="badge badge-warning"><?= $item['days_left'] ?>天</span>
                    <?php else: ?>
                        <span class="badge badge-info"><?= $item['days_left'] ?>天</span>
                    <?php endif; ?>
                </td>
            </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
        <?php else: ?>
        <div style="text-align:center;color:#A0AEC0;padding:20px">暂无临期物品 👍</div>
        <?php endif; ?>
    </div>

    <div class="chart-card">
        <h3>📉 库存不足</h3>
        <?php if (!empty($lowStockList)): ?>
        <table class="list-table">
            <thead><tr><th>物品</th><th>当前/阈值</th><th>空间</th></tr></thead>
            <tbody>
            <?php foreach ($lowStockList as $item): ?>
            <tr>
                <td><strong><?= htmlspecialchars($item['name']) ?></strong></td>
                <td>
                    <span style="color:#E91E63;font-weight:600"><?= floatval($item['quantity']) ?></span>
                    <span style="color:#A0AEC0">/ <?= floatval($item['stock_threshold']) ?><?= $item['unit'] ?: '件' ?></span>
                </td>
                <td style="color:#718096"><?= htmlspecialchars($item['space_name'] ?? '') ?></td>
            </tr>
            <?php endforeach; ?>
            </tbody>
        </table>
        <?php else: ?>
        <div style="text-align:center;color:#A0AEC0;padding:20px">库存充足 👍</div>
        <?php endif; ?>
    </div>
</div>
