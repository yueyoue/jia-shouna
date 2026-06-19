<?php
/**
 * 认证接口 - 登录/注册/刷新Token
 */
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
require_once __DIR__ . '/../config/jwt.php';

$action = $_GET['action'] ?? '';
$db = getDB();

switch ($action) {
    case 'login':
        $input = getJsonInput();
        $username = trim($input['username'] ?? '');
        $password = $input['password'] ?? '';

        if (empty($username) || empty($password)) {
            error('请输入用户名和密码');
        }

        $stmt = $db->prepare("SELECT * FROM sys_user WHERE username = ? AND status = 1");
        $stmt->execute([$username]);
        $user = $stmt->fetch();

        if (!$user || !password_verify($password, $user['password'])) {
            error('用户名或密码错误');
        }

        // 生成JWT Token
        $token = JWT::encode([
            'user_id' => $user['id'],
            'username' => $user['username'],
            'role' => $user['role']
        ]);

        // 更新Token到数据库
        $stmt = $db->prepare("UPDATE sys_user SET token = ?, token_expire = ?, last_login_time = ?, last_login_ip = ? WHERE id = ?");
        $stmt->execute([$token, time() + JWT_EXPIRE, time(), $_SERVER['REMOTE_ADDR'] ?? '', $user['id']]);

        // 获取用户的房屋列表
        $stmt = $db->prepare("SELECT h.*, hm.role as member_role, hm.is_current 
            FROM house h 
            LEFT JOIN house_member hm ON h.id = hm.house_id 
            WHERE hm.user_id = ? AND h.status = 1 
            ORDER BY hm.is_current DESC, h.created_at DESC");
        $stmt->execute([$user['id']]);
        $houses = $stmt->fetchAll();

        success([
            'token' => $token,
            'user' => [
                'id' => $user['id'],
                'username' => $user['username'],
                'nickname' => $user['nickname'],
                'phone' => $user['phone'],
                'avatar' => $user['avatar'] ? IMAGE_URL_PREFIX . $user['avatar'] : '',
                'role' => $user['role']
            ],
            'houses' => $houses
        ], '登录成功');
        break;

    case 'register':
        $input = getJsonInput();
        $username = trim($input['username'] ?? '');
        $password = $input['password'] ?? '';
        $nickname = trim($input['nickname'] ?? $username);
        $phone = trim($input['phone'] ?? '');
        $houseName = trim($input['house_name'] ?? '我的家');

        if (empty($username) || empty($password)) {
            error('请输入用户名和密码');
        }
        if (strlen($username) < 3 || strlen($username) > 50) {
            error('用户名长度为3-50个字符');
        }
        if (strlen($password) < 6) {
            error('密码长度至少6位');
        }

        // 检查是否开放注册
        $stmt = $db->prepare("SELECT svalue FROM sys_setting WHERE skey = 'open_register'");
        $stmt->execute();
        $setting = $stmt->fetch();
        if ($setting && $setting['svalue'] == '0') {
            error('系统暂未开放注册，请联系管理员');
        }

        // 检查用户名是否已存在
        $stmt = $db->prepare("SELECT id FROM sys_user WHERE username = ?");
        $stmt->execute([$username]);
        if ($stmt->fetch()) {
            error('用户名已存在');
        }

        // 创建用户
        $hashedPassword = password_hash($password, PASSWORD_DEFAULT);
        $now = time();
        $stmt = $db->prepare("INSERT INTO sys_user (username, password, nickname, phone, role, status, created_at, updated_at) VALUES (?, ?, ?, ?, 2, 1, ?, ?)");
        $stmt->execute([$username, $hashedPassword, $nickname, $phone, $now, $now]);
        $userId = $db->lastInsertId();

        // 创建默认房屋
        $inviteCode = generateInviteCode();
        $stmt = $db->prepare("INSERT INTO house (name, invite_code, creator_id, member_count, created_at, updated_at) VALUES (?, ?, ?, 1, ?, ?)");
        $stmt->execute([$houseName, $inviteCode, $userId, $now, $now]);
        $houseId = $db->lastInsertId();

        // 加入房屋成员
        $stmt = $db->prepare("INSERT INTO house_member (house_id, user_id, role, is_current, joined_at) VALUES (?, ?, 1, 1, ?)");
        $stmt->execute([$houseId, $userId, $now]);

        // 生成Token
        $token = JWT::encode([
            'user_id' => $userId,
            'username' => $username,
            'role' => 2
        ]);

        $stmt = $db->prepare("UPDATE sys_user SET token = ?, token_expire = ? WHERE id = ?");
        $stmt->execute([$token, $now + JWT_EXPIRE, $userId]);

        success([
            'token' => $token,
            'user' => [
                'id' => $userId,
                'username' => $username,
                'nickname' => $nickname,
                'phone' => $phone,
                'avatar' => '',
                'role' => 2
            ],
            'houses' => [[
                'id' => $houseId,
                'name' => $houseName,
                'invite_code' => $inviteCode,
                'creator_id' => $userId,
                'member_role' => 1,
                'is_current' => 1
            ]]
        ], '注册成功');
        break;

    case 'refresh':
        $user = requireLogin();
        $token = JWT::encode([
            'user_id' => $user['id'],
            'username' => $user['username'],
            'role' => $user['role']
        ]);
        $stmt = $db->prepare("UPDATE sys_user SET token = ?, token_expire = ? WHERE id = ?");
        $stmt->execute([$token, time() + JWT_EXPIRE, $user['id']]);
        success(['token' => $token]);
        break;

    default:
        error('未知操作');
}
