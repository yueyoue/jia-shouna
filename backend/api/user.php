<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 用户信息接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'profile':
        // 获取当前用户的房屋列表
        $stmt = $db->prepare("SELECT h.*, hm.role as member_role, hm.is_current 
            FROM house h LEFT JOIN house_member hm ON h.id = hm.house_id 
            WHERE hm.user_id = ? AND h.status = 1 ORDER BY hm.is_current DESC");
        $stmt->execute([$user['id']]);
        $houses = $stmt->fetchAll();

        // 获取统计数据(当前房屋)
        $currentHouse = null;
        foreach ($houses as $h) {
            if ($h['is_current']) { $currentHouse = $h; break; }
        }
        if (!$currentHouse && !empty($houses)) $currentHouse = $houses[0];

        $stats = ['items' => 0, 'spaces' => 0, 'operations' => 0];
        if ($currentHouse) {
            $stmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods WHERE house_id = ? AND creator_id = ? AND status = 1");
            $stmt->execute([$currentHouse['id'], $user['id']]);
            $stats['items'] = $stmt->fetch()['cnt'];

            $stmt = $db->prepare("SELECT COUNT(*) as cnt FROM storage_space WHERE house_id = ? AND creator_id = ?");
            $stmt->execute([$currentHouse['id'], $user['id']]);
            $stats['spaces'] = $stmt->fetch()['cnt'];

            $stmt = $db->prepare("SELECT COUNT(*) as cnt FROM operate_log WHERE user_id = ?");
            $stmt->execute([$user['id']]);
            $stats['operations'] = $stmt->fetch()['cnt'];
        }

        // 获取当前APP版本
        $stmt = $db->prepare("SELECT version_code, version_name FROM app_version WHERE status = 1 ORDER BY version_code DESC LIMIT 1");
        $stmt->execute();
        $appVersion = $stmt->fetch();

        success([
            'user' => [
                'id' => $user['id'],
                'username' => $user['username'],
                'nickname' => $user['nickname'],
                'phone' => $user['phone'],
                'avatar' => $user['avatar'] ? IMAGE_URL_PREFIX . $user['avatar'] : '',
                'role' => $user['role']
            ],
            'houses' => $houses,
            'stats' => $stats,
            'app_version' => $appVersion
        ]);
        break;

    case 'update':
        $input = getJsonInput();
        $fields = [];
        $params = [];

        if (isset($input['nickname'])) {
            $fields[] = "nickname = ?";
            $params[] = trim($input['nickname']);
        }
        if (isset($input['phone'])) {
            $fields[] = "phone = ?";
            $params[] = trim($input['phone']);
        }
        if (isset($input['avatar'])) {
            $fields[] = "avatar = ?";
            $params[] = $input['avatar'];
        }
        if (isset($input['password']) && !empty($input['password'])) {
            if (strlen($input['password']) < 6) error('密码长度至少6位');
            $fields[] = "password = ?";
            $params[] = password_hash($input['password'], PASSWORD_DEFAULT);
        }

        if (empty($fields)) error('没有要更新的内容');

        $fields[] = "updated_at = ?";
        $params[] = time();
        $params[] = $user['id'];

        $stmt = $db->prepare("UPDATE sys_user SET " . implode(', ', $fields) . " WHERE id = ?");
        $stmt->execute($params);
        success(null, '更新成功');
        break;

    default:
        error('未知操作');
}
