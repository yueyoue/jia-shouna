<?php
/**
 * 公共辅助函数
 */

// CORS 头
function corsHeaders() {
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With');
    header('Content-Type: application/json; charset=utf-8');
    if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
        http_response_code(200);
        exit;
    }
}

// JSON 响应
function jsonResponse($code, $msg, $data = null) {
    $response = ['code' => $code, 'msg' => $msg];
    if ($data !== null) {
        $response['data'] = $data;
    }
    echo json_encode($response, JSON_UNESCAPED_UNICODE);
    exit;
}

// 成功响应
function success($data = null, $msg = 'success') {
    jsonResponse(0, $msg, $data);
}

// 错误响应
function error($msg, $code = 400) {
    jsonResponse($code, $msg);
}

// 获取请求体JSON
function getJsonInput() {
    $raw = file_get_contents('php://input');
    return json_decode($raw, true) ?: [];
}

// 获取GET参数
function getParam($key, $default = null) {
    return isset($_GET[$key]) ? $_GET[$key] : $default;
}

// 获取当前用户(从JWT Token解析)
function getCurrentUser() {
    static $user = null;
    if ($user !== null) return $user;

    $authHeader = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (preg_match('/^Bearer\s+(.+)$/i', $authHeader, $matches)) {
        $token = $matches[1];
    } elseif (isset($_GET['token'])) {
        $token = $_GET['token'];
    } else {
        return null;
    }

    require_once __DIR__ . '/jwt.php';
    $payload = JWT::decode($token);
    if (!$payload || !isset($payload['user_id'])) {
        return null;
    }

    $db = getDB();
    $stmt = $db->prepare("SELECT * FROM sys_user WHERE id = ? AND status = 1");
    $stmt->execute([$payload['user_id']]);
    $user = $stmt->fetch();
    return $user;
}

// 要求登录
function requireLogin() {
    $user = getCurrentUser();
    if (!$user) {
        error('请先登录', 401);
    }
    return $user;
}

// 要求管理员权限
function requireAdmin() {
    $user = requireLogin();
    if ($user['role'] != 1) {
        error('权限不足', 403);
    }
    return $user;
}

// 获取当前用户在指定房屋的角色
function getUserHouseRole($userId, $houseId) {
    $db = getDB();
    $stmt = $db->prepare("SELECT role FROM house_member WHERE user_id = ? AND house_id = ?");
    $stmt->execute([$userId, $houseId]);
    $row = $stmt->fetch();
    return $row ? $row['role'] : null;
}

// 检查用户是否有空间访问权限
function hasSpaceAccess($userId, $spaceId) {
    $db = getDB();
    $stmt = $db->prepare("SELECT s.*, h.id as house_id FROM storage_space s 
        LEFT JOIN house h ON s.house_id = h.id 
        WHERE s.id = ?");
    $stmt->execute([$spaceId]);
    $space = $stmt->fetch();
    if (!$space) return false;

    // 检查是否是房屋成员
    $role = getUserHouseRole($userId, $space['house_id']);
    if (!$role) return false;

    // 如果空间不共享，只有创建者和管理员可以访问
    if (!$space['shared'] && $space['creator_id'] != $userId && $role != 1) {
        // 检查是否有单独授权
        $stmt2 = $db->prepare("SELECT id FROM space_member WHERE space_id = ? AND user_id = ?");
        $stmt2->execute([$spaceId, $userId]);
        return $stmt2->fetch() ? true : false;
    }
    return true;
}

// 生成唯一邀请码
function generateInviteCode() {
    return strtoupper(substr(md5(uniqid(mt_rand(), true)), 0, 8));
}

// 生成唯一文件名
function generateFileName($ext) {
    return date('YmdHis') . '_' . substr(md5(uniqid(mt_rand(), true)), 0, 8) . '.' . $ext;
}

// 记录操作日志
function logOperation($userId, $module, $action, $targetId = 0, $content = '') {
    $db = getDB();
    $stmt = $db->prepare("INSERT INTO operate_log (user_id, username, module, action, target_id, content, ip, created_at) 
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
    $username = '';
    $userStmt = $db->prepare("SELECT username FROM sys_user WHERE id = ?");
    $userStmt->execute([$userId]);
    $u = $userStmt->fetch();
    if ($u) $username = $u['username'];
    
    $stmt->execute([
        $userId, $username, $module, $action, $targetId, $content,
        $_SERVER['REMOTE_ADDR'] ?? '', time()
    ]);
}

// 生成缩略图(简单等比缩放)
function createThumbnail($srcPath, $thumbPath, $maxWidth = 200) {
    $info = getimagesize($srcPath);
    if (!$info) return false;
    
    $mime = $info['mime'];
    switch ($mime) {
        case 'image/jpeg': $src = imagecreatefromjpeg($srcPath); break;
        case 'image/png': $src = imagecreatefrompng($srcPath); break;
        case 'image/gif': $src = imagecreatefromgif($srcPath); break;
        case 'image/webp': $src = imagecreatefromwebp($srcPath); break;
        default: return false;
    }
    
    $width = imagesx($src);
    $height = imagesy($src);
    $ratio = $maxWidth / $width;
    $newWidth = $maxWidth;
    $newHeight = intval($height * $ratio);
    
    $thumb = imagecreatetruecolor($newWidth, $newHeight);
    imagecopyresampled($thumb, $src, 0, 0, 0, 0, $newWidth, $newHeight, $width, $height);
    
    switch ($mime) {
        case 'image/jpeg': imagejpeg($thumb, $thumbPath, 85); break;
        case 'image/png': imagepng($thumb, $thumbPath, 6); break;
        case 'image/gif': imagegif($thumb, $thumbPath); break;
        case 'image/webp': imagewebp($thumb, $thumbPath, 85); break;
    }
    
    imagedestroy($src);
    imagedestroy($thumb);
    return true;
}
