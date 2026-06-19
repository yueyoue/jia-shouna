<?php
/**
 * APP版本检查接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();

switch ($action) {
    case 'check':
        $currentCode = intval($_GET['version_code'] ?? 0);
        if (!$currentCode) error('缺少version_code参数');

        $stmt = $db->prepare("SELECT * FROM app_version WHERE status = 1 ORDER BY version_code DESC LIMIT 1");
        $stmt->execute();
        $latest = $stmt->fetch();

        if (!$latest) {
            success(['has_update' => false]);
        }

        $hasUpdate = $latest['version_code'] > $currentCode;
        success([
            'has_update' => $hasUpdate,
            'latest' => [
                'version_code' => $latest['version_code'],
                'version_name' => $latest['version_name'],
                'changelog' => $latest['changelog'],
                'is_force' => intval($latest['is_force']),
                'apk_url' => IMAGE_URL_PREFIX . $latest['apk_path'],
                'apk_size' => $latest['apk_size'],
            ]
        ]);
        break;

    case 'latest':
        $stmt = $db->prepare("SELECT * FROM app_version WHERE status = 1 ORDER BY version_code DESC LIMIT 1");
        $stmt->execute();
        $latest = $stmt->fetch();
        if (!$latest) {
            success(['has_version' => false]);
        }
        success([
            'has_version' => true,
            'version_code' => $latest['version_code'],
            'version_name' => $latest['version_name'],
            'changelog' => $latest['changelog'],
            'is_force' => intval($latest['is_force']),
            'apk_url' => IMAGE_URL_PREFIX . $latest['apk_path'],
            'apk_size' => $latest['apk_size'],
            'download_count' => $latest['download_count'],
            'published_at' => $latest['published_at']
        ]);
        break;

    default:
        error('未知操作');
}
