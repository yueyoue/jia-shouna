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

<div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;flex-wrap:wrap;gap:12px;">
    <div>
        <div class="page-title" style="font-size:22px;font-weight:700">用户与家庭组管理</div>
    </div>
    <?php if ($tab === 'users'): ?>
    <button class="btn btn-primary btn-sm" onclick="showModal('addUserModal')">+ 添加用户</button>
    <?php endif; ?>
</div>

<?php if (!empty($msg)): ?>
    <div class="alert alert-success" style="margin-bottom:16px;">
        <span class="alert-icon">✅</span>
        <div><?= $msg ?></div>
    </div>
<?php endif; ?>

<!-- Tabs -->
<div class="tabs">
    <a href="?p=users&tab=users" class="tab <?= $tab === 'users' ? 'active' : '' ?>">👥 用户管理</a>
    <a href="?p=users&tab=houses" class="tab <?= $tab === 'houses' ? 'active' : '' ?>">🏠 家庭组</a>
    <a href="?p=users&tab=logs" class="tab <?= $tab === 'logs' ? 'active' : '' ?>">📋 操作日志</a>
</div>

<?php if ($tab === 'users'): ?>
<!-- 用户管理 -->
<div class="card" style="overflow:hidden;">
    <div class="table-wrapper">
        <table>
            <thead><tr><th>用户名</th><th>昵称</th><th>手机号</th><th>角色</th><th>状态</th><th>最后登录</th><th>操作</th></tr></thead>
            <tbody>
                <?php foreach ($users as $u): ?>
                <tr>
                    <td>
                        <div style="display:flex;align-items:center;gap:10px">
                            <div style="width:36px;height:36px;border-radius:50%;background:linear-gradient(135deg,#FFB07A,#4ECDC4);display:flex;align-items:center;justify-content:center;color:#fff;font-weight:600;font-size:14px;flex-shrink:0"><?= mb_substr(htmlspecialchars($u['nickname'] ?: $u['username']), 0, 1) ?></div>
                            <strong><?= htmlspecialchars($u['username']) ?></strong>
                        </div>
                    </td>
                    <td><?= htmlspecialchars($u['nickname']) ?></td>
                    <td><?= $u['phone'] ?: '-' ?></td>
                    <td><span class="tag <?= $u['role'] == 1 ? 'tag-orange' : 'tag-blue' ?>"><?= $u['role'] == 1 ? '管理员' : '普通用户' ?></span></td>
                    <td><?= $u['status'] ? '<span class="tag tag-green">正常</span>' : '<span class="tag tag-red">禁用</span>' ?></td>
                    <td style="font-size:12px;color:var(--text-3)"><?= $u['last_login_time'] ? date('m-d H:i', $u['last_login_time']) : '-' ?></td>
                    <td>
                        <div style="display:flex;gap:4px;">
                            <form method="POST" style="display:inline;">
                                <input type="hidden" name="action" value="toggle_user">
                                <input type="hidden" name="id" value="<?= $u['id'] ?>">
                                <input type="hidden" name="status" value="<?= $u['status'] ? 0 : 1 ?>">
                                <button type="submit" class="btn btn-sm btn-outline" style="padding:4px 8px;font-size:11px"><?= $u['status'] ? '禁用' : '启用' ?></button>
                            </form>
                            <form method="POST" style="display:inline;" onsubmit="return confirm('确定重置密码为123456？')">
                                <input type="hidden" name="action" value="reset_password">
                                <input type="hidden" name="id" value="<?= $u['id'] ?>">
                                <button type="submit" class="btn btn-sm btn-outline" style="padding:4px 8px;font-size:11px">重置密码</button>
                            </form>
                        </div>
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
<div class="card" style="overflow:hidden;">
    <div class="table-wrapper">
        <table>
            <thead><tr><th>名称</th><th>邀请码</th><th>创建者</th><th>成员数</th><th>空间数</th><th>物品数</th><th>创建时间</th></tr></thead>
            <tbody>
                <?php foreach ($houses as $h): ?>
                <tr>
                    <td><strong><?= htmlspecialchars($h['name']) ?></strong></td>
                    <td><code style="background:var(--bg);padding:2px 8px;border-radius:4px;font-size:12px;"><?= $h['invite_code'] ?></code></td>
                    <td><?= htmlspecialchars($h['creator_name'] ?? '-') ?></td>
                    <td><strong><?= $h['member_count'] ?></strong></td>
                    <td><?= $h['space_count'] ?></td>
                    <td><?= $h['item_count'] ?></td>
                    <td style="font-size:12px;color:var(--text-3)"><?= date('Y-m-d', $h['created_at']) ?></td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>

<?php else: ?>
<!-- 操作日志 -->
<div class="card" style="overflow:hidden;">
    <div class="card-header">
        <div class="card-title">操作日志</div>
    </div>
    <div style="padding:4px 0;">
        <?php
        $logIcons = ['add' => ['icon' => '➕', 'bg' => 'rgba(72,187,120,.12)', 'color' => '#22543D'],
                     'edit' => ['icon' => '✎', 'bg' => 'rgba(91,159,237,.12)', 'color' => '#2C5282'],
                     'delete' => ['icon' => '🗑', 'bg' => 'rgba(245,101,101,.12)', 'color' => '#9B2C2C'],
                     'login' => ['icon' => '🔑', 'bg' => 'rgba(255,140,66,.12)', 'color' => '#C25A1E']];
        foreach ($logs as $log):
            $li = $logIcons[$log['action']] ?? ['icon' => '📋', 'bg' => 'var(--bg)', 'color' => 'var(--text-2)'];
        ?>
        <div style="padding:14px 20px;display:flex;gap:12px;border-bottom:1px solid var(--border-2);font-size:13px;transition:background .15s;" onmouseover="this.style.background='#FAFBFC'" onmouseout="this.style.background='transparent'">
            <div style="width:32px;height:32px;border-radius:8px;background:<?= $li['bg'] ?>;color:<?= $li['color'] ?>;display:flex;align-items:center;justify-content:center;font-size:14px;flex-shrink:0"><?= $li['icon'] ?></div>
            <div style="flex:1;min-width:0">
                <div style="color:var(--text);line-height:1.5"><strong style="color:var(--primary)"><?= htmlspecialchars($log['username']) ?></strong> <?= htmlspecialchars($log['action']) ?> · <?= htmlspecialchars($log['content']) ?></div>
                <div style="display:flex;gap:10px;margin-top:4px;font-size:11px;color:var(--text-4);">
                    <span><?= date('m-d H:i:s', $log['created_at']) ?></span>
                    <span class="tag tag-blue" style="font-size:10px"><?= $log['module'] ?></span>
                    <span style="font-family:monospace"><?= $log['ip'] ?></span>
                </div>
            </div>
        </div>
        <?php endforeach; ?>
        <?php if (empty($logs)): ?>
        <div class="empty-state">
            <div class="empty-icon">📋</div>
            <div class="empty-text">暂无操作日志</div>
        </div>
        <?php endif; ?>
    </div>
</div>
<?php endif; ?>
