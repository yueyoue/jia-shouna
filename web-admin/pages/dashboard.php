<?php
$db = getDB();
$now = time();

// 统计数据
$stats = [];
$stmt = $db->query("SELECT COUNT(*) as cnt FROM goods WHERE status = 1");
$stats['items'] = $stmt->fetch()['cnt'];
$stmt = $db->query("SELECT COUNT(*) as cnt FROM storage_space");
$stats['spaces'] = $stmt->fetch()['cnt'];
$stmt = $db->query("SELECT COUNT(*) as cnt FROM sys_user WHERE status = 1");
$stats['users'] = $stmt->fetch()['cnt'];
$stmt = $db->query("SELECT COUNT(*) as cnt FROM goods WHERE status = 1 AND expiry_date IS NOT NULL AND expiry_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY) AND expiry_date >= CURDATE()");
$stats['expiring'] = $stmt->fetch()['cnt'];

// 接口调用统计(今日)
$stmt = $db->query("SELECT COUNT(*) as cnt FROM api_log WHERE DATE(FROM_UNIXTIME(created_at)) = CURDATE()");
$stats['api_today'] = $stmt->fetch()['cnt'];
$stmt = $db->query("SELECT COUNT(*) as cnt FROM api_log WHERE status = 1 AND DATE(FROM_UNIXTIME(created_at)) = CURDATE()");
$stats['api_success'] = $stmt->fetch()['cnt'];

// 临期物品
$stmt = $db->query("SELECT g.*, s.name as space_name FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id WHERE g.status = 1 AND g.expiry_date IS NOT NULL AND g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY) AND g.expiry_date >= CURDATE() ORDER BY g.expiry_date ASC LIMIT 10");
$expiringItems = $stmt->fetchAll();
?>

<div style="margin-bottom: 20px;">
    <h2 style="font-size: 20px;">下午好，管理员 👋</h2>
    <p style="color: #999; font-size: 13px; margin-top: 4px;">今天有 <?= $stats['expiring'] ?> 件物品即将过期</p>
</div>

<div class="stats-grid">
    <div class="stat-card">
        <div class="stat-icon">📦</div>
        <div class="stat-value"><?= number_format($stats['items']) ?></div>
        <div class="stat-label">物品总数</div>
    </div>
    <div class="stat-card">
        <div class="stat-icon">🏠</div>
        <div class="stat-value"><?= number_format($stats['spaces']) ?></div>
        <div class="stat-label">收纳空间</div>
    </div>
    <div class="stat-card">
        <div class="stat-icon">👥</div>
        <div class="stat-value"><?= number_format($stats['users']) ?></div>
        <div class="stat-label">家庭成员</div>
    </div>
    <div class="stat-card">
        <div class="stat-icon">⏰</div>
        <div class="stat-value" style="color: #F56565;"><?= number_format($stats['expiring']) ?></div>
        <div class="stat-label">即将过期</div>
    </div>
</div>

<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
    <!-- 接口调用统计 -->
    <div class="card">
        <div class="card-header">
            <div class="card-title">🔌 接口调用统计</div>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
            <div>
                <div style="font-size: 12px; color: #999;">今日调用</div>
                <div style="font-size: 24px; font-weight: bold;"><?= $stats['api_today'] ?></div>
            </div>
            <div>
                <div style="font-size: 12px; color: #999;">成功率</div>
                <div style="font-size: 24px; font-weight: bold; color: #48BB78;">
                    <?= $stats['api_today'] > 0 ? round($stats['api_success'] / $stats['api_today'] * 100) : 0 ?>%
                </div>
            </div>
        </div>
        <div style="margin-top: 16px;">
            <a href="?p=api-config" class="btn btn-outline btn-sm">查看配置 →</a>
        </div>
    </div>

    <!-- 快捷入口 -->
    <div class="card">
        <div class="card-header">
            <div class="card-title">⚡ 快捷入口</div>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
            <a href="?p=items" class="btn btn-outline" style="padding: 16px;">📦 物品管理</a>
            <a href="?p=backup" class="btn btn-outline" style="padding: 16px;">💾 数据备份</a>
            <a href="?p=api-config" class="btn btn-outline" style="padding: 16px;">🔌 接口配置</a>
            <a href="?p=version" class="btn btn-outline" style="padding: 16px;">📱 版本更新</a>
        </div>
    </div>
</div>

<!-- 临期物品预警 -->
<div class="card" style="margin-top: 20px;">
    <div class="card-header">
        <div>
            <div class="card-title">⚠️ 临期物品预警</div>
            <div class="card-subtitle">近 7 天即将过期的物品</div>
        </div>
        <a href="?p=items&filter=expiring" class="btn btn-outline btn-sm">查看全部 →</a>
    </div>
    <?php if (empty($expiringItems)): ?>
        <div class="empty-state">
            <div class="empty-icon">✅</div>
            <div class="empty-text">暂无临期物品</div>
        </div>
    <?php else: ?>
        <div class="table-wrapper">
            <table>
                <thead>
                    <tr><th>物品名称</th><th>存放位置</th><th>数量</th><th>保质期</th><th>剩余天数</th><th>操作</th></tr>
                </thead>
                <tbody>
                    <?php foreach ($expiringItems as $item): 
                        $daysLeft = floor((strtotime($item['expiry_date']) - time()) / 86400);
                    ?>
                    <tr>
                        <td><strong><?= htmlspecialchars($item['name']) ?></strong></td>
                        <td><?= htmlspecialchars($item['space_name'] ?? '-') ?></td>
                        <td><?= $item['quantity'] ?> <?= $item['unit'] ?></td>
                        <td><?= $item['expiry_date'] ?></td>
                        <td><span class="badge <?= $daysLeft <= 1 ? 'badge-danger' : 'badge-warning' ?>"><?= $daysLeft <= 0 ? '今天' : $daysLeft . '天' ?></span></td>
                        <td><a href="?p=items&action=edit&id=<?= $item['id'] ?>" class="btn btn-sm btn-outline">编辑</a></td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    <?php endif; ?>
</div>
