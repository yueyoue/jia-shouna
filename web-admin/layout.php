<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>家收纳 · 管理后台</title>
    <link rel="stylesheet" href="assets/css/admin.css">
</head>
<body>
    <div class="admin-layout">
        <!-- 侧边栏 -->
        <aside class="sidebar">
            <div class="sidebar-header">
                <span class="logo">🏠</span>
                <span class="logo-text">家收纳</span>
                <span class="version">管理后台 v1.0</span>
            </div>
            <nav class="sidebar-nav">
                <div class="nav-section">
                    <div class="nav-section-title">概览</div>
                    <a href="?p=dashboard" class="nav-item <?= $page === 'dashboard' ? 'active' : '' ?>">
                        <span class="nav-icon">📊</span> 数据看板
                    </a>
                </div>
                <div class="nav-section">
                    <div class="nav-section-title">数据管理</div>
                    <a href="?p=spaces" class="nav-item <?= $page === 'spaces' ? 'active' : '' ?>">
                        <span class="nav-icon">🏠</span> 收纳空间
                    </a>
                    <a href="?p=items" class="nav-item <?= $page === 'items' ? 'active' : '' ?>">
                        <span class="nav-icon">📦</span> 物品管理
                    </a>
                </div>
                <div class="nav-section">
                    <div class="nav-section-title">系统</div>
                    <a href="?p=api-config" class="nav-item <?= $page === 'api-config' ? 'active' : '' ?>">
                        <span class="nav-icon">🔌</span> 接口配置
                    </a>
                    <a href="?p=backup" class="nav-item <?= $page === 'backup' ? 'active' : '' ?>">
                        <span class="nav-icon">💾</span> 数据备份
                    </a>
                    <a href="?p=users" class="nav-item <?= $page === 'users' ? 'active' : '' ?>">
                        <span class="nav-icon">👥</span> 用户家庭
                    </a>
                    <a href="?p=version" class="nav-item <?= $page === 'version' ? 'active' : '' ?>">
                        <span class="nav-icon">📱</span> 版本更新
                    </a>
                </div>
                <div class="nav-section">
                    <div class="nav-section-title">设置</div>
                    <a href="?p=settings" class="nav-item <?= $page === 'settings' ? 'active' : '' ?>">
                        <span class="nav-icon">⚙</span> 系统设置
                    </a>
                </div>
            </nav>
            <div class="sidebar-footer">
                <div class="admin-info">
                    <span class="admin-avatar">👤</span>
                    <div>
                        <div class="admin-name"><?= htmlspecialchars($adminUser['nickname'] ?: $adminUser['username']) ?></div>
                        <div class="admin-role">超级管理员</div>
                    </div>
                </div>
            </div>
        </aside>

        <!-- 主内容区 -->
        <main class="main-content">
            <div class="content-header">
                <div class="breadcrumb">
                    <span>🏠</span> · 
                    <?php
                    $titles = [
                        'dashboard' => '数据看板',
                        'spaces' => '收纳空间',
                        'items' => '物品管理',
                        'api-config' => '接口配置',
                        'backup' => '数据备份',
                        'users' => '用户家庭',
                        'version' => '版本更新',
                        'settings' => '系统设置'
                    ];
                    echo $titles[$page] ?? '页面';
                    ?>
                </div>
                <div class="header-actions">
                    <a href="?p=settings" class="btn-icon" title="设置">⚙</a>
                    <a href="?p=login&action=logout" class="btn-icon" title="退出">🚪</a>
                </div>
            </div>
            <div class="content-body">
                <?php require __DIR__ . '/' . $pageFile; ?>
            </div>
        </main>
    </div>
    <script src="assets/js/admin.js"></script>
</body>
</html>
