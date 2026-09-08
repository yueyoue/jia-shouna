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

    // 删除单张图片（供APP端取消识别时调用）
    case 'delete_image':
        $imagePath = $_POST['image_path'] ?? $_GET['image_path'] ?? '';
        if (empty($imagePath)) error('缺少图片路径');

        // 安全检查：只允许删除images目录下的文件
        $imagePath = str_replace('..', '', $imagePath);
        if (strpos($imagePath, 'images/') !== 0) error('无效的图片路径');

        $fullPath = UPLOAD_PATH . $imagePath;
        $deleted = false;
        if (file_exists($fullPath)) {
            @unlink($fullPath);
            $deleted = true;
        }
        // 删除缩略图
        $thumbPath = dirname($fullPath) . '/thumb/' . basename($fullPath);
        if (file_exists($thumbPath)) @unlink($thumbPath);

        success(['deleted' => $deleted]);
        break;

    // 清理孤立图片（未被goods_image引用的图片文件）
    case 'cleanup_orphan':
        $imagesDir = UPLOAD_PATH . 'images/';
        if (!is_dir($imagesDir)) {
            success(['deleted' => 0, 'freed' => 0]);
            break;
        }

        // 获取所有被引用的图片路径
        $referenced = [];
        $stmt = $db->query("SELECT image_path FROM goods_image");
        while ($row = $stmt->fetch()) {
            $referenced[UPLOAD_PATH . $row['image_path']] = true;
        }
        // 也获取document_image引用的图片
        try {
            $stmt2 = $db->query("SELECT image_path FROM document_image");
            while ($row = $stmt2->fetch()) {
                $referenced[UPLOAD_PATH . $row['image_path']] = true;
            }
        } catch (Exception $e) {}

        $deletedCount = 0;
        $freedBytes = 0;

        $iter = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($imagesDir));
        foreach ($iter as $file) {
            if (!$file->isFile()) continue;
            $basename = $file->getBasename();
            // 跳过缩略图目录（缩略图跟随主图一起删）
            if (strpos($file->getPathname(), '/thumb/') !== false) continue;
            // 跳过.gitkeep等
            if ($basename[0] === '.') continue;

            $fullPath = $file->getPathname();
            if (!isset($referenced[$fullPath])) {
                $freedBytes += $file->getSize();
                @unlink($fullPath);
                // 删除对应缩略图
                $thumbPath = dirname($fullPath) . '/thumb/' . $basename;
                if (file_exists($thumbPath)) @unlink($thumbPath);
                $deletedCount++;
            }
        }

        // 清理空目录
        $months = glob($imagesDir . '*');
        foreach ($months as $monthDir) {
            if (!is_dir($monthDir)) continue;
            $thumbDir = $monthDir . '/thumb';
            if (is_dir($thumbDir) && count(scandir($thumbDir)) <= 2) @rmdir($thumbDir);
            if (count(scandir($monthDir)) <= 2) @rmdir($monthDir);
        }

        success([
            'deleted' => $deletedCount,
            'freed' => $freedBytes,
            'freed_display' => round($freedBytes / 1024 / 1024, 2) . ' MB'
        ]);
        break;

    default:
        error('未知操作');
}
