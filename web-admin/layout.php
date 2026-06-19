<?php
if (!isset($_SESSION)) session_start();
if (!isset($_SESSION['admin_id'])) { header('Location: index.php?p=login'); exit; }

$db = getDB();
$admin = $db->prepare("SELECT * FROM sys_user WHERE id = ?");
$admin->execute([$_SESSION['admin_id']]);
$admin = $admin->fetch();

$currentPage = $_GET['p'] ?? 'dashboard';

// Navigation structure
$navGroups = [
    '概览' => [
        ['p' => 'dashboard', 'icon' => '📊', 'label' => '数据看板'],
    ],
    '数据管理' => [
        ['p' => 'items', 'icon' => '📦', 'label' => '物品管理'],
        ['p' => 'spaces', 'icon' => '🏠', 'label' => '收纳空间'],
    ],
    '系统' => [
        ['p' => 'api-config', 'icon' => '🔌', 'label' => '接口配置'],
        ['p' => 'backup', 'icon' => '💾', 'label' => '数据备份'],
        ['p' => 'users', 'icon' => '👥', 'label' => '用户管理'],
    ],
    '设置' => [
        ['p' => 'settings', 'icon' => '⚙', 'label' => '系统设置'],
        ['p' => 'version', 'icon' => '📋', 'label' => '版本信息'],
    ],
];

// Page titles
$pageTitles = [
    'dashboard' => ['title' => '数据看板', 'desc' => '家庭收纳数据一览'],
    'items' => ['title' => '物品管理', 'desc' => '管理所有物品信息'],
    'spaces' => ['title' => '收纳空间', 'desc' => '管理家庭收纳空间'],
    'api-config' => ['title' => '接口配置', 'desc' => '管理第三方接口服务'],
    'backup' => ['title' => '数据备份', 'desc' => '保障家庭数据安全'],
    'users' => ['title' => '用户管理', 'desc' => '管理家庭成员与权限'],
    'settings' => ['title' => '系统设置', 'desc' => '配置系统参数'],
    'version' => ['title' => '版本信息', 'desc' => '系统版本与更新'],
];

$pageInfo = $pageTitles[$currentPage] ?? ['title' => '管理后台', 'desc' => ''];
?>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= htmlspecialchars($pageInfo['title']) ?> - 家收纳管理后台</title>
    <link rel="stylesheet" href="assets/css/admin.css">
</head>
<body>
<div class="admin-layout">
    <!-- Sidebar -->
    <aside class="sidebar">
        <div class="sidebar-brand">
            <div class="brand-icon">🏠</div>
            <div class="brand-text">
                <span class="brand-name">家收纳</span>
                <span class="brand-version">管理后台 v1.0</span>
            </div>
        </div>

        <nav class="sidebar-nav">
            <?php foreach ($navGroups as $groupTitle => $items): ?>
            <div class="nav-group">
                <div class="nav-group-title"><?= $groupTitle ?></div>
                <?php foreach ($items as $item): ?>
                <a href="?p=<?= $item['p'] ?>" class="nav-item <?= $currentPage === $item['p'] ? 'active' : '' ?>">
                    <span class="nav-icon"><?= $item['icon'] ?></span>
                    <span class="nav-label"><?= $item['label'] ?></span>
                </a>
                <?php endforeach; ?>
            </div>
            <?php endforeach; ?>
        </nav>

        <div class="sidebar-footer">
            <div class="user-avatar"><?= mb_substr(htmlspecialchars($admin['nickname'] ?: $admin['username']), 0, 1) ?></div>
            <div class="user-info">
                <div class="user-name"><?= htmlspecialchars($admin['nickname'] ?: $admin['username']) ?></div>
                <div class="user-role"><?= $admin['role'] == 1 ? '超级管理员' : '管理员' ?></div>
            </div>
        </div>
    </aside>

    <!-- Main content -->
    <main class="main-content">
        <!-- Top header -->
        <header class="top-header">
            <div class="breadcrumb">
                <span>🏠</span>
                <span class="separator">/</span>
                <a href="?p=dashboard">系统</a>
                <span class="separator">/</span>
                <span class="current"><?= htmlspecialchars($pageInfo['title']) ?></span>
            </div>
            <div class="header-actions">
                <?php if ($currentPage === 'backup'): ?>
                    <button class="btn btn-outline btn-sm">↩ 恢复历史</button>
                    <button class="btn btn-primary btn-sm">💾 备份策略</button>
                <?php elseif ($currentPage === 'items'): ?>
                    <button class="btn btn-outline btn-sm">📥 Excel导入</button>
                    <button class="btn btn-primary btn-sm">+ 添加物品</button>
                <?php elseif ($currentPage === 'users'): ?>
                    <button class="btn btn-primary btn-sm">+ 添加用户</button>
                <?php endif; ?>
            </div>
        </header>

        <!-- Page content -->
        <div class="page-content">
            <?php include "pages/{$currentPage}.php"; ?>
        </div>
    </main>
</div>

<script>
// Modal functions
function showModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.add('show');
}

function hideModal(id) {
    const el = document.getElementById(id);
    if (el) el.classList.remove('show');
}

// Toast notification
function showToast(msg, type = 'info') {
    const toast = document.createElement('div');
    toast.style.cssText = `
        position: fixed; top: 20px; right: 20px; z-index: 9999;
        padding: 12px 20px; border-radius: 8px; font-size: 14px;
        color: #fff; box-shadow: 0 4px 12px rgba(0,0,0,.15);
        animation: slideIn .3s ease; max-width: 320px;
    `;
    const colors = {success: '#22c55e', error: '#ef4444', warning: '#f59e0b', info: '#3b82f6'};
    toast.style.background = colors[type] || colors.info;
    toast.textContent = msg;
    document.body.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity .3s';
        setTimeout(() => toast.remove(), 300);
    }, 3000);
}

// API helper
async function api(url, options = {}) {
    try {
        const resp = await fetch(url, options);
        const data = await resp.json();
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

// Pagination helper
function renderPagination(total, page, pageSize, fn) {
    const totalPages = Math.ceil(total / pageSize);
    if (totalPages <= 1) return '';
    
    let html = '<div class="pagination">';
    
    // Previous
    if (page > 1) {
        html += `<a href="javascript:${fn}(${page - 1})">‹ 上一页</a>`;
    } else {
        html += '<span class="disabled">‹ 上一页</span>';
    }
    
    // Page numbers
    let start = Math.max(1, page - 2);
    let end = Math.min(totalPages, start + 4);
    if (end - start < 4) start = Math.max(1, end - 4);
    
    if (start > 1) {
        html += `<a href="javascript:${fn}(1)">1</a>`;
        if (start > 2) html += '<span>...</span>';
    }
    
    for (let i = start; i <= end; i++) {
        if (i === page) {
            html += `<span class="active">${i}</span>`;
        } else {
            html += `<a href="javascript:${fn}(${i})">${i}</a>`;
        }
    }
    
    if (end < totalPages) {
        if (end < totalPages - 1) html += '<span>...</span>';
        html += `<a href="javascript:${fn}(${totalPages})">${totalPages}</a>`;
    }
    
    // Next
    if (page < totalPages) {
        html += `<a href="javascript:${fn}(${page + 1})">下一页 ›</a>`;
    } else {
        html += '<span class="disabled">下一页 ›</span>';
    }
    
    html += '</div>';
    return html;
}

// Format file size
function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
    return (bytes / 1073741824).toFixed(2) + ' GB';
}

// Close modal on overlay click
document.addEventListener('click', function(e) {
    if (e.target.classList.contains('modal-overlay')) {
        e.target.classList.remove('show');
    }
});
</script>
</body>
</html>
