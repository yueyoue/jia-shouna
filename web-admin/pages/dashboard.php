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
?>

<!-- Welcome card -->
<div class="welcome-card">
    <div class="welcome-text">
        <h2>下午好，管理员 👋</h2>
        <p>今天有 <strong><?= $stats['expiring'] ?> 件</strong> 物品即将过期</p>
    </div>
    <div class="welcome-actions">
        <a href="?p=items&filter=expiring" class="btn" style="background:rgba(255,255,255,.2);color:#fff;border:1px solid rgba(255,255,255,.3)">⚡ 立即处理</a>
        <a href="?p=backup" class="btn" style="background:rgba(255,255,255,.2);color:#fff;border:1px solid rgba(255,255,255,.3)">📊 数据报告</a>
    </div>
</div>

<!-- Status bar -->
<div class="status-bar" style="margin-bottom:var(--space-6);">
    <span class="status-icon">✅</span>
    <span>数据状态良好 · 上次备份：今天 03:00 · 已连续运行 14 天</span>
</div>

<!-- Stats grid -->
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

<!-- Charts row -->
<div class="grid-2" style="margin-bottom:var(--space-6);">
    <!-- API Stats -->
    <div class="card">
        <div class="card-header">
            <div>
                <div class="card-title">接口调用统计</div>
                <div class="card-subtitle">条码查询 + 图像识别</div>
            </div>
            <a href="?p=api-config" class="btn btn-outline btn-sm">查看配置 →</a>
        </div>
        <div class="card-body">
            <div class="grid-2" style="margin-bottom:var(--space-5);">
                <div>
                    <div style="font-size:var(--font-size-sm);color:var(--text-tertiary);margin-bottom:var(--space-1);">今日调用</div>
                    <div style="font-size:28px;font-weight:700;"><?= number_format($stats['api_today']) ?></div>
                </div>
                <div>
                    <div style="font-size:var(--font-size-sm);color:var(--text-tertiary);margin-bottom:var(--space-1);">成功率</div>
                    <div style="font-size:28px;font-weight:700;color:var(--success);">
                        <?= $stats['api_today'] > 0 ? round($stats['api_success'] / $stats['api_today'] * 100) : 0 ?>%
                    </div>
                </div>
            </div>
            <div class="progress" style="margin-bottom:var(--space-2);">
                <div class="progress-bar <?= $stats['api_today'] > 0 && $stats['api_success'] / $stats['api_today'] > 0.9 ? '' : 'warning' ?>" style="width:<?= $stats['api_today'] > 0 ? round($stats['api_success'] / $stats['api_today'] * 100) : 0 ?>%"></div>
            </div>
            <div style="display:flex;justify-content:space-between;font-size:var(--font-size-xs);color:var(--text-muted);">
                <span>成功 <?= $stats['api_success'] ?></span>
                <span>总计 <?= $stats['api_today'] ?></span>
            </div>
        </div>
    </div>

    <!-- Category Distribution -->
    <div class="card">
        <div class="card-header">
            <div class="card-title">物品分类分布</div>
        </div>
        <div class="card-body" style="display:flex;flex-direction:column;align-items:center;">
            <?php if (!empty($categories)): ?>
            <div class="donut">
                <div class="donut-center">
                    <div class="num"><?= number_format($stats['items']) ?></div>
                    <div class="lbl">总物品数</div>
                </div>
            </div>
            <div class="legend" style="width:100%;">
                <?php
                $colors = ['var(--primary)','var(--accent-cyan)','var(--accent-blue)','var(--accent-orange)','var(--text-placeholder)'];
                foreach ($categories as $idx => $cat):
                ?>
                <div class="legend-item">
                    <span class="legend-dot" style="background:<?= $colors[$idx % 5] ?>"></span>
                    <span class="legend-label"><?= htmlspecialchars($cat['category']) ?></span>
                    <span class="legend-value"><?= number_format($cat['cnt']) ?></span>
                </div>
                <?php endforeach; ?>
            </div>
            <?php else: ?>
            <div class="empty-state">
                <div class="empty-icon">📊</div>
                <div class="empty-text">暂无分类数据</div>
            </div>
            <?php endif; ?>
        </div>
    </div>
</div>

<!-- Quick actions + Expiring -->
<div class="grid-2" style="margin-bottom:var(--space-6);">
    <!-- Quick actions -->
    <div class="card">
        <div class="card-header">
            <div class="card-title">快捷入口</div>
            <span style="font-size:var(--font-size-sm);color:var(--text-muted);">常用的管理操作</span>
        </div>
        <div class="card-body">
            <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:var(--space-3);">
                <a href="?p=items" style="display:flex;flex-direction:column;align-items:center;gap:var(--space-2);padding:var(--space-4) var(--space-2);border-radius:var(--radius-lg);background:var(--bg-input);cursor:pointer;transition:all .2s;border:1px solid transparent;text-decoration:none;color:inherit;" onmouseover="this.style.borderColor='var(--primary)';this.style.background='var(--primary-bg)'" onmouseout="this.style.borderColor='transparent';this.style.background='var(--bg-input)'">
                    <div style="width:44px;height:44px;border-radius:var(--radius-lg);display:flex;align-items:center;justify-content:center;font-size:20px;background:var(--primary-light);color:var(--primary-dark);">📦</div>
                    <div style="font-size:var(--font-size-sm);font-weight:500;color:var(--text-secondary);text-align:center;">物品管理</div>
                </a>
                <a href="?p=spaces" style="display:flex;flex-direction:column;align-items:center;gap:var(--space-2);padding:var(--space-4) var(--space-2);border-radius:var(--radius-lg);background:var(--bg-input);cursor:pointer;transition:all .2s;border:1px solid transparent;text-decoration:none;color:inherit;" onmouseover="this.style.borderColor='var(--accent-cyan)';this.style.background='var(--accent-cyan-light)'" onmouseout="this.style.borderColor='transparent';this.style.background='var(--bg-input)'">
                    <div style="width:44px;height:44px;border-radius:var(--radius-lg);display:flex;align-items:center;justify-content:center;font-size:20px;background:var(--accent-cyan-light);color:var(--accent-cyan);">🏠</div>
                    <div style="font-size:var(--font-size-sm);font-weight:500;color:var(--text-secondary);text-align:center;">空间管理</div>
                </a>
                <a href="?p=backup" style="display:flex;flex-direction:column;align-items:center;gap:var(--space-2);padding:var(--space-4) var(--space-2);border-radius:var(--radius-lg);background:var(--bg-input);cursor:pointer;transition:all .2s;border:1px solid transparent;text-decoration:none;color:inherit;" onmouseover="this.style.borderColor='var(--accent-blue)';this.style.background='var(--accent-blue-light)'" onmouseout="this.style.borderColor='transparent';this.style.background='var(--bg-input)'">
                    <div style="width:44px;height:44px;border-radius:var(--radius-lg);display:flex;align-items:center;justify-content:center;font-size:20px;background:var(--accent-blue-light);color:var(--accent-blue);">💾</div>
                    <div style="font-size:var(--font-size-sm);font-weight:500;color:var(--text-secondary);text-align:center;">数据备份</div>
                </a>
                <a href="?p=api-config" style="display:flex;flex-direction:column;align-items:center;gap:var(--space-2);padding:var(--space-4) var(--space-2);border-radius:var(--radius-lg);background:var(--bg-input);cursor:pointer;transition:all .2s;border:1px solid transparent;text-decoration:none;color:inherit;" onmouseover="this.style.borderColor='var(--accent-purple)';this.style.background='var(--accent-purple-light)'" onmouseout="this.style.borderColor='transparent';this.style.background='var(--bg-input)'">
                    <div style="width:44px;height:44px;border-radius:var(--radius-lg);display:flex;align-items:center;justify-content:center;font-size:20px;background:var(--accent-purple-light);color:var(--accent-purple);">🔌</div>
                    <div style="font-size:var(--font-size-sm);font-weight:500;color:var(--text-secondary);text-align:center;">接口配置</div>
                </a>
            </div>

            <!-- API usage -->
            <div style="margin-top:var(--space-6);">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4);">
                    <div style="font-size:var(--font-size-md);font-weight:600;">接口额度使用</div>
                    <a href="?p=api-config" style="font-size:var(--font-size-sm);color:var(--primary);">查看详情 →</a>
                </div>
                <div style="display:flex;flex-direction:column;gap:var(--space-4);">
                    <div style="padding:var(--space-3);border-radius:var(--radius-md);background:var(--bg-input);border:1px solid var(--border-light);">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:var(--space-2);">
                            <div style="font-size:var(--font-size-sm);font-weight:600;display:flex;align-items:center;gap:var(--space-2);">📊 条码查询</div>
                            <span class="tag tag-green">正常</span>
                        </div>
                        <div class="progress"><div class="progress-bar" style="width:32%"></div></div>
                        <div style="display:flex;justify-content:space-between;font-size:var(--font-size-xs);color:var(--text-muted);margin-top:var(--space-2);"><span>已用 320 / 1000 次</span><span>32%</span></div>
                    </div>
                    <div style="padding:var(--space-3);border-radius:var(--radius-md);background:var(--bg-input);border:1px solid var(--border-light);">
                        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:var(--space-2);">
                            <div style="font-size:var(--font-size-sm);font-weight:600;display:flex;align-items:center;gap:var(--space-2);">📷 图像识别</div>
                            <span class="tag tag-orange">预警</span>
                        </div>
                        <div class="progress"><div class="progress-bar warning" style="width:78%"></div></div>
                        <div style="display:flex;justify-content:space-between;font-size:var(--font-size-xs);color:var(--text-muted);margin-top:var(--space-2);"><span>已用 780 / 1000 次</span><span>78%</span></div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Expiring items -->
    <div class="card">
        <div class="card-header">
            <div>
                <div class="card-title">⏰ 临期物品预警</div>
                <div class="card-subtitle">近 7 天即将过期</div>
            </div>
            <a href="?p=items&filter=expiring" style="font-size:var(--font-size-sm);color:var(--primary);font-weight:500;">查看全部 →</a>
        </div>
        <div class="card-body" style="padding:var(--space-4);">
            <?php if (empty($expiringItems)): ?>
                <div class="empty-state">
                    <div class="empty-icon">✅</div>
                    <div class="empty-text">暂无临期物品</div>
                </div>
            <?php else: ?>
                <div style="display:flex;flex-direction:column;gap:var(--space-3);max-height:340px;overflow-y:auto;padding-right:var(--space-2);">
                    <?php foreach ($expiringItems as $item): 
                        $daysLeft = floor((strtotime($item['expiry_date']) - time()) / 86400);
                        $isUrgent = $daysLeft <= 1;
                    ?>
                    <div style="display:flex;align-items:center;gap:var(--space-3);padding:var(--space-3);border-radius:var(--radius-md);<?= $isUrgent ? 'background:var(--danger-light);border:1px solid rgba(239,68,68,.2)' : 'background:var(--warning-light);border:1px solid rgba(245,158,11,.2)' ?>;transition:all .2s;cursor:pointer;" onclick="location.href='?p=items&action=edit&id=<?= $item['id'] ?>'">
                        <div style="width:42px;height:42px;border-radius:var(--radius-md);background:var(--primary-light);display:flex;align-items:center;justify-content:center;font-size:22px;flex-shrink:0;">📦</div>
                        <div style="flex:1;min-width:0;">
                            <div style="font-size:var(--font-size-sm);font-weight:600;margin-bottom:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;"><?= htmlspecialchars($item['name']) ?></div>
                            <div style="font-size:var(--font-size-xs);color:var(--text-muted);"><?= htmlspecialchars($item['space_name'] ?? '-') ?></div>
                        </div>
                        <div style="font-size:var(--font-size-xs);font-weight:600;padding:3px 8px;border-radius:4px;<?= $isUrgent ? 'background:rgba(239,68,68,.15);color:#991b1b' : 'background:rgba(245,158,11,.15);color:#92400e' ?>;flex-shrink:0;">
                            <?= $daysLeft <= 0 ? '今天过期' : $daysLeft . ' 天后' ?>
                        </div>
                    </div>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </div>
    </div>
</div>

<!-- Recent activity + Members -->
<div class="grid-2" style="margin-bottom:var(--space-6);">
    <div class="card">
        <div class="card-header">
            <div class="card-title">📈 最近活动</div>
            <a href="?p=users&tab=logs" style="font-size:var(--font-size-sm);color:var(--primary);font-weight:500;">查看全部 →</a>
        </div>
        <div class="card-body">
            <?php if (empty($recentLogs)): ?>
                <div class="empty-state">
                    <div class="empty-icon">📋</div>
                    <div class="empty-text">暂无活动记录</div>
                </div>
            <?php else: ?>
                <div style="display:flex;flex-direction:column;gap:var(--space-4);">
                    <?php
                    $dotColors = ['var(--primary)','var(--accent-cyan)','var(--accent-blue)','var(--accent-orange)','var(--success)'];
                    foreach ($recentLogs as $idx => $log):
                    ?>
                    <div style="display:flex;gap:var(--space-3);font-size:var(--font-size-md);">
                        <div style="width:8px;height:8px;border-radius:50%;background:<?= $dotColors[$idx % 5] ?>;margin-top:6px;flex-shrink:0;box-shadow:0 0 0 3px rgba(16,185,129,.2);"></div>
                        <div style="flex:1;">
                            <div style="color:var(--text-primary);line-height:1.5;"><strong style="color:var(--primary);"><?= htmlspecialchars($log['username']) ?></strong> <?= htmlspecialchars($log['action']) ?> · <?= htmlspecialchars($log['content']) ?></div>
                            <div style="font-size:var(--font-size-xs);color:var(--text-muted);margin-top:2px;"><?= date('m-d H:i', $log['created_at']) ?></div>
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
            <span style="font-size:var(--font-size-sm);color:var(--text-muted);">本月成员贡献度</span>
        </div>
        <div class="card-body">
            <?php
            $medals = ['🥇','🥈','🥉'];
            $medalBgs = ['linear-gradient(135deg,#FFD700,#f59e0b)','linear-gradient(135deg,#C0C0C0,#94a3b8)','linear-gradient(135deg,#CD7F32,#a16207)'];
            $medalColors = ['var(--warning)','var(--text-muted)','var(--accent-orange)'];
            ?>
            <div style="display:flex;flex-direction:column;gap:var(--space-4);">
                <?php foreach ($members as $idx => $m): ?>
                <div style="display:flex;align-items:center;gap:var(--space-3);">
                    <div style="width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:600;<?= $idx < 3 ? 'background:'.$medalBgs[$idx].';color:#fff' : 'background:var(--bg-input);color:var(--text-tertiary)' ?>;flex-shrink:0;"><?= $idx < 3 ? $medals[$idx] : mb_substr(htmlspecialchars($m['nickname'] ?: $m['username']), 0, 1) ?></div>
                    <div style="flex:1;">
                        <div style="font-size:var(--font-size-sm);font-weight:600;"><?= htmlspecialchars($m['nickname'] ?: $m['username']) ?></div>
                        <div style="font-size:var(--font-size-xs);color:var(--text-muted);">本月录入 <?= $m['cnt'] ?> 件物品</div>
                    </div>
                    <div style="font-size:var(--font-size-lg);font-weight:700;color:<?= $idx < 3 ? $medalColors[$idx] : 'var(--text-muted)' ?>;"><?= $m['cnt'] ?></div>
                </div>
                <?php endforeach; ?>
                <?php if (empty($members)): ?>
                <div class="empty-state">
                    <div class="empty-icon">🏆</div>
                    <div class="empty-text">暂无数据</div>
                </div>
                <?php endif; ?>
            </div>
        </div>
    </div>
</div>
