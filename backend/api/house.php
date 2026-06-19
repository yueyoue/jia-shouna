<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
/**
 * 房屋/家庭组管理接口
 */
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'list':
        $stmt = $db->prepare("SELECT h.*, hm.role as member_role, hm.is_current 
            FROM house h 
            LEFT JOIN house_member hm ON h.id = hm.house_id 
            WHERE hm.user_id = ? AND h.status = 1 
            ORDER BY hm.is_current DESC, h.created_at DESC");
        $stmt->execute([$user['id']]);
        $houses = $stmt->fetchAll();
        success(['list' => $houses]);
        break;

    case 'create':
        $input = getJsonInput();
        $name = trim($input['name'] ?? '');
        if (empty($name)) error('请输入房屋名称');

        $now = time();
        $inviteCode = generateInviteCode();
        $db->beginTransaction();
        try {
            $stmt = $db->prepare("INSERT INTO house (name, invite_code, creator_id, member_count, created_at, updated_at) VALUES (?, ?, ?, 1, ?, ?)");
            $stmt->execute([$name, $inviteCode, $user['id'], $now, $now]);
            $houseId = $db->lastInsertId();

            // 取消其他房屋的当前状态
            $stmt = $db->prepare("UPDATE house_member SET is_current = 0 WHERE user_id = ?");
            $stmt->execute([$user['id']]);

            $stmt = $db->prepare("INSERT INTO house_member (house_id, user_id, role, is_current, joined_at) VALUES (?, ?, 1, 1, ?)");
            $stmt->execute([$houseId, $user['id'], $now]);

            $db->commit();
            logOperation($user['id'], 'house', 'create', $houseId, "创建房屋: $name");
            success(['id' => $houseId, 'name' => $name, 'invite_code' => $inviteCode]);
        } catch (Exception $e) {
            $db->rollBack();
            error('创建失败: ' . $e->getMessage());
        }
        break;

    case 'join':
        $input = getJsonInput();
        $inviteCode = trim($input['invite_code'] ?? '');
        if (empty($inviteCode)) error('请输入邀请码');

        $stmt = $db->prepare("SELECT * FROM house WHERE invite_code = ? AND status = 1");
        $stmt->execute([$inviteCode]);
        $house = $stmt->fetch();
        if (!$house) error('邀请码无效或房屋已解散');

        // 检查是否已是成员
        $stmt = $db->prepare("SELECT id FROM house_member WHERE house_id = ? AND user_id = ?");
        $stmt->execute([$house['id'], $user['id']]);
        if ($stmt->fetch()) error('你已是该房屋成员');

        $now = time();
        $db->beginTransaction();
        try {
            $stmt = $db->prepare("INSERT INTO house_member (house_id, user_id, role, is_current, joined_at) VALUES (?, ?, 3, 0, ?)");
            $stmt->execute([$house['id'], $user['id'], $now]);

            $stmt = $db->prepare("UPDATE house SET member_count = member_count + 1, updated_at = ? WHERE id = ?");
            $stmt->execute([$now, $house['id']]);

            $db->commit();
            logOperation($user['id'], 'house', 'join', $house['id'], "加入房屋: {$house['name']}");
            success(['house' => $house]);
        } catch (Exception $e) {
            $db->rollBack();
            error('加入失败');
        }
        break;

    case 'switch':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);

        $stmt = $db->prepare("SELECT * FROM house_member WHERE house_id = ? AND user_id = ?");
        $stmt->execute([$houseId, $user['id']]);
        if (!$stmt->fetch()) error('你不是该房屋成员');

        $db->beginTransaction();
        try {
            $stmt = $db->prepare("UPDATE house_member SET is_current = 0 WHERE user_id = ?");
            $stmt->execute([$user['id']]);
            $stmt = $db->prepare("UPDATE house_member SET is_current = 1 WHERE house_id = ? AND user_id = ?");
            $stmt->execute([$houseId, $user['id']]);
            $db->commit();
            success(['house_id' => $houseId]);
        } catch (Exception $e) {
            $db->rollBack();
            error('切换失败');
        }
        break;

    case 'members':
        $houseId = intval($_GET['house_id'] ?? 0);
        if (!$houseId) error('缺少参数house_id');

        $role = getUserHouseRole($user['id'], $houseId);
        if (!$role) error('你不是该房屋成员', 403);

        $stmt = $db->prepare("SELECT u.id, u.username, u.nickname, u.phone, u.avatar, hm.role, hm.joined_at
            FROM house_member hm 
            LEFT JOIN sys_user u ON hm.user_id = u.id 
            WHERE hm.house_id = ? 
            ORDER BY hm.role ASC, hm.joined_at ASC");
        $stmt->execute([$houseId]);
        $members = $stmt->fetchAll();
        foreach ($members as &$m) {
            $m['avatar'] = $m['avatar'] ? IMAGE_URL_PREFIX . $m['avatar'] : '';
        }
        success(['list' => $members]);
        break;

    case 'updateRole':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $targetUserId = intval($input['user_id'] ?? 0);
        $newRole = intval($input['role'] ?? 0);

        if (!in_array($newRole, [1, 2, 3])) error('无效的角色');

        // 只有管理员可以改角色
        $myRole = getUserHouseRole($user['id'], $houseId);
        if ($myRole != 1) error('仅管理员可修改角色', 403);

        $stmt = $db->prepare("UPDATE house_member SET role = ? WHERE house_id = ? AND user_id = ?");
        $stmt->execute([$newRole, $houseId, $targetUserId]);
        logOperation($user['id'], 'house', 'updateRole', $houseId, "修改用户{$targetUserId}角色为{$newRole}");
        success(null, '修改成功');
        break;

    case 'removeMember':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $targetUserId = intval($input['user_id'] ?? 0);

        $myRole = getUserHouseRole($user['id'], $houseId);
        if ($myRole != 1) error('仅管理员可移除成员', 403);
        if ($targetUserId == $user['id']) error('不能移除自己');

        $db->beginTransaction();
        try {
            $stmt = $db->prepare("DELETE FROM house_member WHERE house_id = ? AND user_id = ?");
            $stmt->execute([$houseId, $targetUserId]);

            $stmt = $db->prepare("UPDATE house SET member_count = GREATEST(member_count - 1, 0), updated_at = ? WHERE id = ?");
            $stmt->execute([time(), $houseId]);

            $db->commit();
            logOperation($user['id'], 'house', 'removeMember', $houseId, "移除用户{$targetUserId}");
            success(null, '已移除');
        } catch (Exception $e) {
            $db->rollBack();
            error('操作失败');
        }
        break;

    default:
        error('未知操作');
}
