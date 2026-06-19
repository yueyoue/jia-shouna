<?php
if (!isset($_SESSION)) session_start();
if (!isset($_SESSION['admin_id'])) { header('Location: index.php?p=login'); exit; }

$db = getDB();
$admin = $db->prepare("SELECT * FROM sys_user WHERE id = ?");
$admin->execute([$_SESSION['admin_id']]);
$admin = $admin->fetch();

$currentPage = $_GET['p'] ?? 'dashboard';

// Navigation structure - 照抄 UI 的导航
$navGroups = [
    '概览' => [
        ['p' => 'dashboard', 'icon' => '📊', 'label' => '数据看板'],
    ],
    '数据管理' => [
        ['p' => 'spaces', 'icon' => '🏠', 'label' => '收纳空间'],
        ['p' => 'items', 'icon' => '📦', 'label' => '物品管理'],
    ],
    '系统' => [
        ['p' => 'api-config', 'icon' => '🔌', 'label' => '接口配置'],
        ['p' => 'backup', 'icon' => '💾', 'label' => '数据备份'],
        ['p' => 'users', 'icon' => '👥', 'label' => '用户家庭'],
    ],
    '设置' => [
        ['p' => 'settings', 'icon' => '⚙', 'label' => '系统设置'],
    ],
];

// Page titles
$pageTitles = [
    'dashboard' => '数据看板',
    'items' => '物品管理',
    'spaces' => '收纳空间',
    'api-config' => '接口配置',
    'backup' => '数据备份',
    'users' => '用户家庭',
    'settings' => '系统设置',
    'version' => '版本信息',
];

$pageTitle = $pageTitles[$currentPage] ?? '管理后台';
?>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= htmlspecialchars($pageTitle) ?> - 家收纳管理后台</title>
    <link rel="stylesheet" href="assets/css/common.css">
</head>
<body>
<div class="app">

    <!-- Sidebar - 照抄 UI -->
    <aside class="sidebar">
        <div class="sidebar-header">
            <div class="sidebar-logo">🏠</div>
            <div>
                <div class="sidebar-title">家收纳</div>
                <div class="sidebar-subtitle">管理后台 v1.0</div>
            </div>
        </div>
        <nav class="sidebar-nav">
            <?php foreach ($navGroups as $groupTitle => $items): ?>
            <div class="nav-group">
                <div class="nav-group-title"><?= $groupTitle ?></div>
                <?php foreach ($items as $item): ?>
                <a class="nav-item <?= $currentPage === $item['p'] ? 'active' : '' ?>" href="?p=<?= $item['p'] ?>">
                    <span class="nav-icon"><?= $item['icon'] ?></span>
                    <span><?= $item['label'] ?></span>
                </a>
                <?php endforeach; ?>
            </div>
            <?php endforeach; ?>
        </nav>
        <div class="sidebar-footer">
            <div class="avatar"><?= mb_substr(htmlspecialchars($admin['nickname'] ?: $admin['username']), 0, 1) ?></div>
            <div class="user-info">
                <div class="user-name"><?= htmlspecialchars($admin['nickname'] ?: $admin['username']) ?></div>
                <div class="user-role"><?= $admin['role'] == 1 ? '超级管理员' : '管理员' ?></div>
            </div>
            <span style="color:#A0AEC0;cursor:pointer">⚙</span>
        </div>
    </aside>

    <!-- Main -->
    <main class="main">
        <header class="topbar">
            <div class="breadcrumb">
                <span>🏠</span>
                <span>·</span>
                <span class="current"><?= htmlspecialchars($pageTitle) ?></span>
            </div>
            <div class="topbar-actions">
                <button class="icon-btn">🔔<span class="dot"></span></button>
                <button class="icon-btn">❓</button>
            </div>
        </header>

        <div class="content">
            <?php include "pages/{$currentPage}.php"; ?>
        </div>
    </main>
</div>

<script>
function showModal(id) {
    const el = document.getElementById(id);
    if (el) { el.style.display = 'flex'; }
}
function hideModal(id) {
    const el = document.getElementById(id);
    if (el) { el.style.display = 'none'; }
}
function showToast(msg, type) {
    type = type || 'info';
    var colors = {success:'#48BB78', error:'#F56565', warning:'#ED8936', info:'#5B9FED'};
    var t = document.createElement('div');
    t.style.cssText = 'position:fixed;top:20px;right:20px;z-index:9999;padding:12px 20px;border-radius:8px;font-size:13px;color:#fff;box-shadow:0 4px 12px rgba(0,0,0,.15);';
    t.style.background = colors[type] || colors.info;
    t.textContent = msg;
    document.body.appendChild(t);
    setTimeout(function(){ t.style.opacity='0';t.style.transition='opacity .3s';setTimeout(function(){ t.remove(); },300); }, 3000);
}
async function api(url, options) {
    try {
        var resp = await fetch(url, options || {});
        var data = await resp.json();
        if (data.code === 0) return data.data;
        showToast(data.msg || '操作失败', 'error');
        return null;
    } catch (e) {
        showToast('网络错误', 'error');
        return null;
    }
}
async function postJSON(url, body) {
    return api(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(body)
    });
}
function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
    return (bytes / 1073741824).toFixed(2) + ' GB';
}
document.addEventListener('click', function(e) {
    if (e.target.classList.contains('modal-mask')) {
        e.target.style.display = 'none';
    }
});
</script>
</body>
</html>
