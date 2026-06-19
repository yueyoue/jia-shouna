<?php
/**
 * 数据库配置
 */

define('DB_HOST', 'localhost');
define('DB_PORT', '3306');
define('DB_NAME', 'jia_shouna');
define('DB_USER', 'root');
define('DB_PASS', '');
define('DB_CHARSET', 'utf8mb4');

// JWT 配置
define('JWT_SECRET', 'jia_shouna_jwt_secret_key_change_in_production');
define('JWT_EXPIRE', 86400 * 30); // 30天过期

// 文件上传配置
define('UPLOAD_PATH', dirname(__DIR__) . '/uploads/');
define('IMAGE_MAX_SIZE', 2 * 1024 * 1024); // 2MB
define('APK_MAX_SIZE', 100 * 1024 * 1024); // 100MB
define('IMAGE_URL_PREFIX', 'https://sn.tthsdd.top/backend/uploads/');

// 调试模式
define('DEBUG', true);

// 连接数据库
function getDB() {
    static $pdo = null;
    if ($pdo === null) {
        $dsn = "mysql:host=" . DB_HOST . ";port=" . DB_PORT . ";dbname=" . DB_NAME . ";charset=" . DB_CHARSET;
        $options = [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ];
        try {
            $pdo = new PDO($dsn, DB_USER, DB_PASS, $options);
        } catch (PDOException $e) {
            die(json_encode(['code' => 500, 'msg' => '数据库连接失败']));
        }
    }
    return $pdo;
}
