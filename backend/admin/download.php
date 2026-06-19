<?php
/**
 * 文件下载
 */
session_start();
require_once __DIR__ . '/../config/database.php';

if (empty($_SESSION['admin_id'])) {
    die('未授权');
}

$type = $_GET['type'] ?? '';
$file = $_GET['file'] ?? '';

if ($type === 'backup' && $file) {
    $filepath = UPLOAD_PATH . 'backups/' . basename($file);
    if (file_exists($filepath)) {
        header('Content-Type: application/octet-stream');
        header('Content-Disposition: attachment; filename="' . basename($filepath) . '"');
        header('Content-Length: ' . filesize($filepath));
        readfile($filepath);
        exit;
    }
    die('文件不存在');
}
die('无效请求');
