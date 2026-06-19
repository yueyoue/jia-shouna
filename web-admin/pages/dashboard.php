<?php
$db = getDB();

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
$stmt = $db->query("SELECT COUNT(*) as cnt FROM api_log WHERE DATE(FROM_UNIXTIME(created_at)) = CURDATE()");
$stats['api_today'] = $stmt->fetch()['cnt'];
$stmt = $db->query("SELECT COUNT(*) as cnt FROM api_log WHERE status = 1 AND DATE(FROM_UNIXTIME(created_at)) = CURDATE()");
$stats['api_success'] = $stmt->fetch()['cnt'];

// 临期物品
$stmt = $db->query("SELECT g.*, s.name as space_name FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id WHERE g.status = 1 AND g.expiry_date IS NOT NULL AND g.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 7 DAY) AND g.expiry_date >= CURDATE() ORDER BY g.expiry_date ASC LIMIT 10");
$expiringItems = $stmt->fetchAll();

// 物品分类统计
$categories = $db->query("SELECT category, COUNT(*) as cnt FROM goods WHERE status = 1 AND category IS NOT NULL AND category != '' GROUP BY category ORDER BY cnt DESC LIMIT 5")->fetchAll();

// 最近活动
$recentLogs = $db->query("SELECT * FROM operate_log ORDER BY created_at DESC LIMIT 5")->fetchAll();

// 成员排行
$members = $db->query("SELECT u.nickname, u.username, COUNT(g.id) as cnt FROM sys_user u LEFT JOIN goods g ON g.creator_id = u.id AND g.status = 1 WHERE u.status = 1 GROUP BY u.id ORDER BY cnt DESC LIMIT 5")->fetchAll();

$dotColors = ['#FF8C42','#4ECDC4','#5B9FED','#ED8936','#48BB78'];
$medalBgs = ['linear-gradient(135deg,#FFD700,#FF8C42)','linear-gradient(135deg,#C0C0C0,#A0AEC0)','linear-gradient(135deg,#CD7F32,#A0522D)'];
$medalColors = ['#FF8C42','#A0AEC0','#A0522D'];
$medals = ['🥇','🥈','🥉'];
?>

<!-- Welcome card - 照抄 UI -->
<div class="welcome-card">
    <div class="welcome-text">
        <h2>下午好，管理员 👋</h2>
        <p>今天有 <strong style="color:#FFE8D6"><?= $stats['expiring'] ?> 件</strong> 物品即将过期</p>
    </div>
    <div class="welcome-actions">
        <button class="btn-welcome" onclick="location.href='?p=items&filter=expiring'">⚡ 立即处理</button>
        <button class="btn-welcome" onclick="location.href='?p=backup'">📊 数据报告</button>
    </div>
</div>

<!-- Stats grid - 照抄 UI -->
<div class="stat-grid">
    <div class="stat-card c1">
        <div class="stat-icon">📦</div>
        <div class="stat-label">物品总数量</div>
        <div class="stat-value"><?= number_format($stats['items']) ?></div>
        <div class="stat-trend up">↑ 物品总数</div>
    </div>
    <div class="stat-card c2">
        <div class="stat-icon">🏠</div>
        <div class="stat-label">收纳空间</div>
        <div class="stat-value"><?= number_format($stats['spaces']) ?></div>
        <div class="stat-trend up">↑ 空间总数</div>
    </div>
    <div class="stat-card c3">
        <div class="stat-icon">👨‍👩‍👧</div>
        <div class="stat-label">家庭成员</div>
        <div class="stat-value"><?= number_format($stats['users']) ?></div>
        <div class="stat-trend up">↑ 成员总数</div>
    </div>
    <div class="stat-card c4">
        <div class="stat-icon">⏰</div>
        <div class="stat-label">即将过期</div>
        <div class="stat-value"><?= number_format($stats['expiring']) ?></div>
        <div class="stat-trend down">↓ 7 天内过期</div>
    </div>
    <div class="stat-card c5">
        <div class="stat-icon">🔌</div>
        <div class="stat-label">今日接口调用</div>
        <div class="stat-value"><?= number_format($stats['api_today']) ?></div>
        <div class="stat-trend <?= $stats['api_today'] > 0 && $stats['api_success'] / $stats['api_today'] > 0.9 ? 'up' : 'down' ?>">
            成功率 <?= $stats['api_today'] > 0 ? round($stats['api_success'] / $stats['api_today'] * 100) : 0 ?>%
        </div>
    </div>
</div>

<!-- Charts row - 照抄 UI -->
<div class="row-2col">
    <!-- API Stats -->
    <div class="card">
        <div class="card-header">
            <div>
                <div class="card-title">接口调用统计</div>
                <div style="font-size:12px;color:#718096;margin-top:2px">条码查询 + 图像识别</div>
            </div>
            <a href="?p=api-config" class="btn btn-outline btn-sm">查看配置 →</a>
        </div>
        <div class="card-body">
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:24px;margin-bottom:20px;">
                <div>
                    <div style="font-size:12px;color:#718096;margin-bottom:4px">今日调用</div>
                    <div style="font-size:28px;font-weight:700"><?= number_format($stats['api_today']) ?></div>
                </div>
                <div>
                    <div style="font-size:12px;color:#718096;margin-bottom:4px">成功率</div>
                    <div style="font-size:28px;font-weight:700;color:#48BB78">
                        <?= $stats['api_today'] > 0 ? round($stats['api_success'] / $stats['api_today'] * 100) : 0 ?>%
                    </div>
                </div>
            </div>
            <div class="progress" style="margin-bottom:8px">
                <div class="progress-bar <?= $stats['api_today'] > 0 && $stats['api_success'] / $stats['api_today'] > 0.9 ? 'success' : 'warning' ?>" style="width:<?= $stats['api_today'] > 0 ? round($stats['api_success'] / $stats['api_today'] * 100) : 0 ?>%"></div>
            </div>
            <div style="display:flex;justify-content:space-between;font-size:11px;color:#718096">
                <span>成功 <?= $stats['api_success'] ?></span>
                <span>总计 <?= $stats['api_today'] ?></span>
            </div>
        </div>
    </div>

    <!-- Category Distribution - 照抄 UI -->
    <div class="card">
        <div class="card-header">
            <div class="card-title">物品分类分布</div>
        </div>
        <div class="card-body" style="display:flex;flex-direction:column;align-items:center">
            <?php if (!empty($categories)): ?>
            <div class="donut">
                <div class="donut-center">
                    <div class="num"><?= number_format($stats['items']) ?></div>
                    <div class="lbl">总物品数</div>
                </div>
            </div>
            <div class="legend" style="width:100%">
                <?php
                $catColors = ['#FF8C42','#4ECDC4','#5B9FED','#ED8936','#A0AEC0'];
                foreach ($categories as $idx => $cat):
                ?>
                <div class="legend-item">
                    <span class="legend-dot" style="background:<?= $catColors[$idx % 5] ?>"></span>
                    <span class="legend-label"><?= htmlspecialchars($cat['category']) ?></span>
                    <span class="legend-value"><?= number_format($cat['cnt']) ?></span>
                </div>
                <?php endforeach; ?>
            </div>
            <?php else: ?>
            <div class="empty">
                <div class="empty-icon">📊</div>
                <div>暂无分类数据</div>
            </div>
            <?php endif; ?>
        </div>
    </div>
</div>

<!-- Quick actions + Expiring - 照抄 UI -->
<div class="row-2col">
    <!-- Quick actions -->
    <div class="card">
        <div class="card-header">
            <div class="card-title">快捷入口</div>
            <span style="font-size:12px;color:#718096">常用的管理操作</span>
        </div>
        <div class="card-body">
            <div class="quick-actions">
                <a href="?p=items" class="quick-action qa1">
                    <div class="quick-icon">📦</div>
                    <div class="quick-label">物品管理</div>
                </a>
                <a href="?p=spaces" class="quick-action qa2">
                    <div class="quick-icon">🏠</div>
                    <div class="quick-label">空间管理</div>
                </a>
                <a href="?p=backup" class="quick-action qa3">
                    <div class="quick-icon">💾</div>
                    <div class="quick-label">立即备份</div>
                </a>
                <a href="?p=api-config" class="quick-action qa4">
                    <div class="quick-icon">🔌</div>
                    <div class="quick-label">接口配置</div>
                </a>
            </div>

            <!-- API usage -->
            <div style="margin-top:20px">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
                    <div style="font-size:14px;font-weight:600">接口额度使用</div>
                    <a href="?p=api-config" style="font-size:12px;color:#FF8C42">查看详情 →</a>
                </div>
                <div class="api-usage-list">
                    <div class="api-usage-item">
                        <div class="api-usage-head">
                            <div class="api-usage-name">📊 条码查询</div>
                            <span class="api-usage-status">正常</span>
                        </div>
                        <div class="progress"><div class="progress-bar success" style="width:32%"></div></div>
                        <div class="api-usage-meta"><span>已用 320 / 1000 次</span><span>32%</span></div>
                    </div>
                    <div class="api-usage-item">
                        <div class="api-usage-head">
                            <div class="api-usage-name">📷 图像识别</div>
                            <span class="api-usage-status warn">预警</span>
                        </div>
                        <div class="progress"><div class="progress-bar warning" style="width:78%"></div></div>
                        <div class="api-usage-meta"><span>已用 780 / 1000 次</span><span>78%</span></div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Expiring items - 照抄 UI -->
    <div class="card">
        <div class="card-header">
            <div>
                <div class="card-title">⏰ 临期物品预警</div>
                <div style="font-size:12px;color:#718096;margin-top:2px">近 7 天即将过期</div>
            </div>
            <a href="?p=items&filter=expiring" style="font-size:12px;color:#FF8C42;font-weight:500">查看全部 →</a>
        </div>
        <div class="card-body" style="padding:14px">
            <?php if (empty($expiringItems)): ?>
            <div class="empty">
                <div class="empty-icon">✅</div>
                <div>暂无临期物品</div>
            </div>
            <?php else: ?>
            <div class="expiring-list">
                <?php foreach ($expiringItems as $item):
                    $daysLeft = floor((strtotime($item['expiry_date']) - time()) / 86400);
                    $isUrgent = $daysLeft <= 1;
                ?>
                <div class="expiring-item <?= $isUrgent ? 'urgent' : '' ?>" onclick="location.href='?p=items&action=edit&id=<?= $item['id'] ?>'">
                    <div class="expiring-img">📦</div>
                    <div class="expiring-info">
                        <div class="expiring-name"><?= htmlspecialchars($item['name']) ?></div>
                        <div class="expiring-loc"><?= htmlspecialchars($item['space_name'] ?? '-') ?></div>
                    </div>
                    <div class="expiring-days <?= $isUrgent ? 'urgent' : '' ?>">
                        <?= $daysLeft <= 0 ? '今天过期' : $daysLeft . ' 天后' ?>
                    </div>
                </div>
                <?php endforeach; ?>
            </div>
            <?php endif; ?>
        </div>
    </div>
</div>

<!-- Recent activity - 照抄 UI -->
<div class="row-2col-equal">
    <div class="card">
        <div class="card-header">
            <div class="card-title">📈 最近活动</div>
            <a href="?p=users&tab=logs" style="font-size:12px;color:#FF8C42;font-weight:500">查看全部 →</a>
        </div>
        <div class="card-body">
            <?php if (empty($recentLogs)): ?>
            <div class="empty">
                <div class="empty-icon">📋</div>
                <div>暂无活动记录</div>
            </div>
            <?php else: ?>
            <div class="activity-list">
                <?php foreach ($recentLogs as $idx => $log): ?>
                <div class="activity-item">
                    <div class="activity-dot" style="background:<?= $dotColors[$idx % 5] ?>;box-shadow:0 0 0 3px <?= $dotColors[$idx % 5] ?>33"></div>
                    <div class="activity-content">
                        <div class="activity-text"><strong><?= htmlspecialchars($log['username']) ?></strong> <?= htmlspecialchars($log['action']) ?> · <?= htmlspecialchars($log['content']) ?></div>
                        <div class="activity-time"><?= date('m-d H:i', $log['created_at']) ?></div>
                    </div>
                </div>
                <?php endforeach; ?>
            </div>
            <?php endif; ?>
        </div>
    </div>

    <div class="card">
        <div class="card-header">
            <div class="card-title">🏆 家庭收纳排行</div>
            <span style="font-size:12px;color:#718096">本月成员贡献度</span>
        </div>
        <div class="card-body">
            <div style="display:flex;flex-direction:column;gap:14px">
                <?php foreach ($members as $idx => $m): ?>
                <div style="display:flex;align-items:center;gap:12px">
                    <div class="avatar" style="<?= $idx < 3 ? 'background:'.$medalBgs[$idx] : '' ?>"><?= $idx < 3 ? $medals[$idx] : mb_substr(htmlspecialchars($m['nickname'] ?: $m['username']), 0, 1) ?></div>
                    <div style="flex:1">
                        <div style="font-size:13px;font-weight:600"><?= htmlspecialchars($m['nickname'] ?: $m['username']) ?></div>
                        <div style="font-size:11px;color:#718096">本月录入 <?= $m['cnt'] ?> 件物品</div>
                    </div>
                    <div style="font-size:16px;font-weight:700;color:<?= $idx < 3 ? $medalColors[$idx] : '#718096' ?>"><?= $m['cnt'] ?></div>
                </div>
                <?php endforeach; ?>
                <?php if (empty($members)): ?>
                <div class="empty">
                    <div class="empty-icon">🏆</div>
                    <div>暂无数据</div>
                </div>
                <?php endif; ?>
            </div>
        </div>
    </div>
</div>
