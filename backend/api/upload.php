<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 文件上传接口
 */
$action = $_GET['action'] ?? '';
$user = requireLogin();

switch ($action) {
    case 'image':
        if (empty($_FILES['file'])) error('请选择文件');

        $file = $_FILES['file'];
        $allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];

        if (!in_array($file['type'], $allowedTypes)) {
            error('仅支持 JPG/PNG/GIF/WebP 格式');
        }
        if ($file['size'] > IMAGE_MAX_SIZE) {
            error('图片大小不能超过2MB');
        }

        $ext = pathinfo($file['name'], PATHINFO_EXTENSION);
        $dir = UPLOAD_PATH . 'images/' . date('Ym') . '/';
        if (!is_dir($dir)) mkdir($dir, 0755, true);

        $filename = generateFileName($ext);
        $filepath = $dir . $filename;
        $relativePath = 'images/' . date('Ym') . '/' . $filename;

        if (move_uploaded_file($file['tmp_name'], $filepath)) {
            // 生成缩略图
            $thumbDir = $dir . 'thumb/';
            if (!is_dir($thumbDir)) mkdir($thumbDir, 0755, true);
            $thumbPath = $thumbDir . $filename;
            createThumbnail($filepath, $thumbPath, 200);

            success([
                'image_path' => $relativePath,
                'image_url' => IMAGE_URL_PREFIX . $relativePath,
                'thumb_path' => 'images/' . date('Ym') . '/thumb/' . $filename,
                'thumb_url' => IMAGE_URL_PREFIX . 'images/' . date('Ym') . '/thumb/' . $filename
            ]);
        } else {
            error('上传失败');
        }
        break;

    default:
        error('未知操作');
}
