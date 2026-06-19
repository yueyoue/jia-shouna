<?php
/**
 * Web管理后台入口 - 路由分发
 */
session_start();

// 检查是否已安装（多路径检测）
$installed = false;
$lockPaths = [
    __DIR__ . '/install.lock',           // web-admin/install.lock
    dirname(__DIR__) . '/install.lock',   // 上级/install.lock
];
foreach ($lockPaths as $lp) {
    if (file_exists($lp)) { $installed = true; break; }
}
// 也检查数据库是否已初始化（有管理员账号）
if (!$installed) {
    try {
        require_once __DIR__ . '/../backend/config/database.php';
        $db = getDB();
        $stmt = $db->query("SELECT COUNT(*) as c FROM sys_user WHERE role=1");
        $row = $stmt->fetch();
        if ($row && $row['c'] > 0) {
            // 数据库已初始化，自动创建锁文件
            @file_put_contents(__DIR__ . '/install.lock', date('Y-m-d H:i:s'));
            $installed = true;
        }
    } catch (Exception $e) {
        // 数据库未配置，继续安装流程
    }
}
if (!$installed && basename($_SERVER['SCRIPT_FILENAME']) !== 'install.php') {
    header('Location: install.php');
    exit;
}

require_once __DIR__ . '/../backend/config/database.php';

// 检查登录状态(管理后台用Session)
$publicPages = ['login'];
$page = $_GET['p'] ?? 'dashboard';
$isLoginPage = in_array($page, $publicPages);

if (!$isLoginPage && empty($_SESSION['admin_id'])) {
    header('Location: index.php?p=login');
    exit;
}

// 页面路由映射
$pages = [
    'login' => 'pages/login.php',
    'dashboard' => 'pages/dashboard.php',
    'spaces' => 'pages/spaces.php',
    'items' => 'pages/items.php',
    'api-config' => 'pages/api-config.php',
    'backup' => 'pages/backup.php',
    'users' => 'pages/users.php',
    'settings' => 'pages/settings.php',
    'version' => 'pages/version.php',
];

$pageFile = $pages[$page] ?? null;
if (!$pageFile || !file_exists(__DIR__ . '/' . $pageFile)) {
    $page = 'dashboard';
    $pageFile = $pages['dashboard'];
}

// 登录页不包含布局
if ($isLoginPage) {
    require __DIR__ . '/' . $pageFile;
    exit;
}

// 获取管理员信息
$adminUser = null;
if (isset($_SESSION['admin_id'])) {
    $db = getDB();
    $stmt = $db->prepare("SELECT * FROM sys_user WHERE id = ? AND role = 1 AND status = 1");
    $stmt->execute([$_SESSION['admin_id']]);
    $adminUser = $stmt->fetch();
}
if (!$adminUser) {
    session_destroy();
    header('Location: index.php?p=login');
    exit;
}

// 公共函数
function formatSize($bytes) {
    if ($bytes < 1024) return $bytes . ' B';
    if ($bytes < 1048576) return round($bytes / 1024, 1) . ' KB';
    if ($bytes < 1073741824) return round($bytes / 1048576, 1) . ' MB';
    return round($bytes / 1073741824, 2) . ' GB';
}

// 包含布局和页面
require __DIR__ . '/layout.php';
