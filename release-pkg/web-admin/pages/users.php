<?php
$db = getDB();
$tab = $_GET['tab'] ?? 'users';

// 处理操作
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    if ($action === 'add_user') {
        $username = trim($_POST['username'] ?? '');
        $password = $_POST['password'] ?? '';
        $nickname = trim($_POST['nickname'] ?? $username);
        $phone = $_POST['phone'] ?? '';
        $role = intval($_POST['role'] ?? 2);
        if ($username && $password) {
            $hashed = password_hash($password, PASSWORD_DEFAULT);
            $now = time();
            $stmt = $db->prepare("INSERT INTO sys_user (username, password, nickname, phone, role, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 1, ?, ?)");
            $stmt->execute([$username, $hashed, $nickname, $phone, $role, $now, $now]);
            $msg = '添加成功';
        }
    } elseif ($action === 'toggle_user') {
        $id = intval($_POST['id'] ?? 0);
        $status = intval($_POST['status'] ?? 1);
        $db->prepare("UPDATE sys_user SET status = ?, updated_at = ? WHERE id = ?")->execute([$status, time(), $id]);
        $msg = $status ? '已启用' : '已禁用';
    } elseif ($action === 'reset_password') {
        $id = intval($_POST['id'] ?? 0);
        $newPwd = password_hash('123456', PASSWORD_DEFAULT);
        $db->prepare("UPDATE sys_user SET password = ?, updated_at = ? WHERE id = ?")->execute([$newPwd, time(), $id]);
        $msg = '密码已重置为 123456';
    }
}

$users = $db->query("SELECT * FROM sys_user ORDER BY created_at DESC")->fetchAll();
$houses = $db->query("SELECT h.*, u.username as creator_name FROM house h LEFT JOIN sys_user u ON h.creator_id = u.id WHERE h.status = 1 ORDER BY h.created_at DESC")->fetchAll();

// 操作日志
$logs = $db->query("SELECT * FROM operate_log ORDER BY created_at DESC LIMIT 50")->fetchAll();
?>

<div class="card-header" style="margin-bottom: 16px;">
    <h2 style="font-size: 18px;">用户与家庭组管理</h2>
</div>

<?php if (!empty($msg)): ?>
    <div class="toast toast-success" style="position: relative; margin-bottom: 16px;"><?= $msg ?></div>
<?php endif; ?>

<!-- Tab切换 -->
<div style="display: flex; gap: 0; margin-bottom: 20px; border-bottom: 2px solid #eee;">
    <a href="?p=users&tab=users" style="padding: 10px 20px; font-size: 14px; font-weight: 600; border-bottom: 2px solid <?= $tab === 'users' ? '#FF8C42' : 'transparent' ?>; margin-bottom: -2px; color: <?= $tab === 'users' ? '#FF8C42' : '#636E72' ?>;">👥 用户管理</a>
    <a href="?p=users&tab=houses" style="padding: 10px 20px; font-size: 14px; font-weight: 600; border-bottom: 2px solid <?= $tab === 'houses' ? '#FF8C42' : 'transparent' ?>; margin-bottom: -2px; color: <?= $tab === 'houses' ? '#FF8C42' : '#636E72' ?>;">🏠 家庭组</a>
    <a href="?p=users&tab=logs" style="padding: 10px 20px; font-size: 14px; font-weight: 600; border-bottom: 2px solid <?= $tab === 'logs' ? '#FF8C42' : 'transparent' ?>; margin-bottom: -2px; color: <?= $tab === 'logs' ? '#FF8C42' : '#636E72' ?>;">📋 操作日志</a>
</div>

<?php if ($tab === 'users'): ?>
<!-- 用户管理 -->
<div class="card">
    <div class="card-header">
        <div class="card-title">用户列表</div>
        <button class="btn btn-primary btn-sm" onclick="showModal('addUserModal')">+ 添加用户</button>
    </div>
    <div class="table-wrapper">
        <table>
            <thead><tr><th>用户名</th><th>昵称</th><th>手机号</th><th>角色</th><th>状态</th><th>最后登录</th><th>操作</th></tr></thead>
            <tbody>
                <?php foreach ($users as $u): ?>
                <tr>
                    <td><strong><?= htmlspecialchars($u['username']) ?></strong></td>
                    <td><?= htmlspecialchars($u['nickname']) ?></td>
                    <td><?= $u['phone'] ?: '-' ?></td>
                    <td><span class="badge <?= $u['role'] == 1 ? 'badge-warning' : 'badge-info' ?>"><?= $u['role'] == 1 ? '管理员' : '普通用户' ?></span></td>
                    <td><?= $u['status'] ? '<span class="badge badge-success">正常</span>' : '<span class="badge badge-danger">禁用</span>' ?></td>
                    <td><?= $u['last_login_time'] ? date('m-d H:i', $u['last_login_time']) : '-' ?></td>
                    <td>
                        <form method="POST" style="display: inline;">
                            <input type="hidden" name="action" value="toggle_user">
                            <input type="hidden" name="id" value="<?= $u['id'] ?>">
                            <input type="hidden" name="status" value="<?= $u['status'] ? 0 : 1 ?>">
                            <button type="submit" class="btn btn-sm btn-outline"><?= $u['status'] ? '禁用' : '启用' ?></button>
                        </form>
                        <form method="POST" style="display: inline;" onsubmit="return confirm('确定重置密码为123456？')">
                            <input type="hidden" name="action" value="reset_password">
                            <input type="hidden" name="id" value="<?= $u['id'] ?>">
                            <button type="submit" class="btn btn-sm btn-outline">重置密码</button>
                        </form>
                    </td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>

<!-- 添加用户弹窗 -->
<div class="modal-overlay" id="addUserModal">
    <div class="modal">
        <div class="modal-title">添加用户</div>
        <form method="POST">
            <input type="hidden" name="action" value="add_user">
            <div class="form-group">
                <label class="form-label">用户名</label>
                <input type="text" name="username" class="form-control" required>
            </div>
            <div class="form-group">
                <label class="form-label">密码</label>
                <input type="password" name="password" class="form-control" required>
            </div>
            <div class="form-group">
                <label class="form-label">昵称</label>
                <input type="text" name="nickname" class="form-control">
            </div>
            <div class="form-group">
                <label class="form-label">手机号</label>
                <input type="text" name="phone" class="form-control">
            </div>
            <div class="form-group">
                <label class="form-label">角色</label>
                <select name="role" class="form-control">
                    <option value="2">普通用户</option>
                    <option value="1">管理员</option>
                </select>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-outline" onclick="hideModal('addUserModal')">取消</button>
                <button type="submit" class="btn btn-primary">确定</button>
            </div>
        </form>
    </div>
</div>

<?php elseif ($tab === 'houses'): ?>
<!-- 家庭组 -->
<div class="card">
    <div class="card-header">
        <div class="card-title">家庭组列表</div>
    </div>
    <div class="table-wrapper">
        <table>
            <thead><tr><th>名称</th><th>邀请码</th><th>创建者</th><th>成员数</th><th>空间数</th><th>物品数</th><th>创建时间</th></tr></thead>
            <tbody>
                <?php foreach ($houses as $h): ?>
                <tr>
                    <td><strong><?= htmlspecialchars($h['name']) ?></strong></td>
                    <td><code style="background: #f8f9fa; padding: 2px 8px; border-radius: 4px;"><?= $h['invite_code'] ?></code></td>
                    <td><?= htmlspecialchars($h['creator_name'] ?? '-') ?></td>
                    <td><?= $h['member_count'] ?></td>
                    <td><?= $h['space_count'] ?></td>
                    <td><?= $h['item_count'] ?></td>
                    <td><?= date('Y-m-d', $h['created_at']) ?></td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>

<?php else: ?>
<!-- 操作日志 -->
<div class="card">
    <div class="card-header">
        <div class="card-title">操作日志</div>
    </div>
    <div class="table-wrapper">
        <table>
            <thead><tr><th>时间</th><th>操作人</th><th>模块</th><th>操作</th><th>内容</th><th>IP</th></tr></thead>
            <tbody>
                <?php foreach ($logs as $log): ?>
                <tr>
                    <td><?= date('m-d H:i:s', $log['created_at']) ?></td>
                    <td><?= htmlspecialchars($log['username']) ?></td>
                    <td><span class="badge badge-info"><?= $log['module'] ?></span></td>
                    <td><?= $log['action'] ?></td>
                    <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis;"><?= htmlspecialchars($log['content']) ?></td>
                    <td><?= $log['ip'] ?></td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>
<?php endif; ?>
