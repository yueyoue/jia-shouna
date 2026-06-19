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
$logs = $db->query("SELECT * FROM operate_log ORDER BY created_at DESC LIMIT 20")->fetchAll();

$logIcons = ['add' => ['+','add'], 'edit' => ['✎','edit'], 'delete' => ['×','delete'], 'login' => ['→','login']];
$avatarColors = ['linear-gradient(135deg,#FFD700,#FF8C42)','linear-gradient(135deg,#FF8C42,#FF6B6B)','linear-gradient(135deg,#4ECDC4,#0E9F8E)','linear-gradient(135deg,#9F7AEA,#553C9A)','linear-gradient(135deg,#A0AEC0,#718096)'];
?>

<style>
.user-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px;padding:20px}
.user-card{background:#fff;border:1px solid var(--border-2);border-radius:var(--radius);padding:20px;transition:all .2s;cursor:pointer;position:relative}
.user-card:hover{border-color:#FF8C42;box-shadow:0 6px 20px rgba(255,140,66,.12);transform:translateY(-2px)}
.user-card.disabled{opacity:.6;background:#FAFAFA}
.user-card-head{display:flex;align-items:center;gap:12px;margin-bottom:12px}
.user-avatar-lg{width:56px;height:56px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#fff;font-size:22px;font-weight:600;flex-shrink:0}
.user-card-info{flex:1;min-width:0}
.user-card-name{font-size:16px;font-weight:600;display:flex;align-items:center;gap:6px}
.user-card-role{font-size:11px;color:#718096;margin-top:2px}
.role-badge{font-size:10px;padding:2px 6px;border-radius:4px;font-weight:600}
.role-admin{background:linear-gradient(135deg,#FF8C42,#FF6B6B);color:#fff}
.role-edit{background:rgba(78,205,196,.15);color:#0E9F8E}
.role-read{background:#F7FAFC;color:#718096}
.user-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;padding:10px 0;border-top:1px solid var(--border-2);border-bottom:1px solid var(--border-2);margin-bottom:10px}
.user-stat{text-align:center}
.user-stat-val{font-size:16px;font-weight:700;color:#2D3748}
.user-stat-lbl{font-size:10px;color:#718096;margin-top:2px}
.user-card-foot{display:flex;align-items:center;justify-content:space-between;font-size:11px;color:#718096}
.family-section{background:linear-gradient(135deg,#FFF7F0 0%,#F0FBFA 100%);border-radius:var(--radius);padding:24px;margin-bottom:20px;border:1px solid rgba(255,140,66,.15)}
.family-head{display:flex;align-items:center;gap:14px;margin-bottom:16px}
.family-icon{width:56px;height:56px;border-radius:14px;background:linear-gradient(135deg,#FF8C42,#FF6B6B);display:flex;align-items:center;justify-content:center;color:#fff;font-size:26px;box-shadow:0 6px 16px rgba(255,140,66,.3)}
.family-name{font-size:20px;font-weight:700}
.family-meta{font-size:12px;color:#718096;margin-top:2px}
.family-code{margin-left:auto;padding:10px 16px;background:#fff;border-radius:10px;border:1.5px dashed #FF8C42;display:flex;align-items:center;gap:10px}
.family-code-label{font-size:11px;color:#718096}
.family-code-val{font-size:18px;font-weight:700;color:#FF8C42;letter-spacing:2px;font-family:monospace}
.section-row{display:grid;grid-template-columns:1.4fr 1fr;gap:16px}
@media(max-width:1100px){.section-row{grid-template-columns:1fr}}
.log-list{padding:4px 0}
.log-item{padding:14px 20px;display:flex;gap:12px;border-bottom:1px solid var(--border-2);font-size:13px;transition:background .15s}
.log-item:hover{background:#FAFBFC}
.log-item:last-child{border-bottom:none}
.log-icon{width:32px;height:32px;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:14px;flex-shrink:0}
.log-icon.add{background:rgba(72,187,120,.12);color:#22543D}
.log-icon.edit{background:rgba(91,159,237,.12);color:#2C5282}
.log-icon.delete{background:rgba(245,101,101,.12);color:#9B2C2C}
.log-icon.login{background:rgba(255,140,66,.12);color:#C25A1E}
.log-content{flex:1;min-width:0}
.log-action{color:#2D3748}
.log-action strong{color:#FF8C42}
.log-meta{display:flex;gap:10px;margin-top:4px;font-size:11px;color:#A0AEC0}
.log-meta .ip{font-family:monospace}
</style>

<?php if (!empty($msg)): ?>
<div class="alert alert-success" style="margin-bottom:16px">
    <span class="alert-icon">✅</span>
    <div><?= $msg ?></div>
</div>
<?php endif; ?>

<div class="tabs">
    <a href="?p=users&tab=users" class="tab <?= $tab === 'users' ? 'active' : '' ?>">👥 家庭成员 (<?= count($users) ?>)</a>
    <a href="?p=users&tab=houses" class="tab <?= $tab === 'houses' ? 'active' : '' ?>">🏠 家庭组</a>
    <a href="?p=users&tab=logs" class="tab <?= $tab === 'logs' ? 'active' : '' ?>">📋 操作日志</a>
</div>

<?php if ($tab === 'users'): ?>
<!-- Family section - 照抄 UI -->
<div class="family-section">
    <div class="family-head">
        <div class="family-icon">🏠</div>
        <div>
            <div class="family-name">我的家</div>
            <div class="family-meta"><?= count($users) ?> 位成员 · 共享 <?= count($spaces ?? []) ?> 个空间</div>
        </div>
        <?php if (!empty($houses)): ?>
        <div class="family-code">
            <div>
                <div class="family-code-label">家庭邀请码</div>
                <div class="family-code-val"><?= $houses[0]['invite_code'] ?? '----' ?></div>
            </div>
        </div>
        <?php endif; ?>
    </div>
</div>

<!-- Section row - 照抄 UI -->
<div class="section-row">
    <!-- Members -->
    <div>
        <div class="card-header" style="background:#fff;border-radius:var(--radius) var(--radius) 0 0;border-bottom:1px solid var(--border-2)">
            <div class="card-title">👥 家庭成员</div>
        </div>
        <div class="user-grid" style="padding:20px;background:#fff;border-radius:0 0 var(--radius) var(--radius)">
            <?php foreach ($users as $idx => $u):
                $roleClass = $u['role'] == 1 ? 'role-admin' : 'role-edit';
                $roleName = $u['role'] == 1 ? '超级管理员' : '编辑成员';
            ?>
            <div class="user-card <?= $u['status'] ? '' : 'disabled' ?>">
                <div class="user-card-head">
                    <div class="user-avatar-lg" style="background:<?= $avatarColors[$idx % 5] ?>"><?= mb_substr(htmlspecialchars($u['nickname'] ?: $u['username']), 0, 1) ?></div>
                    <div class="user-card-info">
                        <div class="user-card-name">
                            <?= htmlspecialchars($u['nickname'] ?: $u['username']) ?>
                            <span class="role-badge <?= $roleClass ?>"><?= $roleName ?></span>
                            <?php if (!$u['status']): ?>
                            <span class="tag tag-gray" style="font-size:10px;padding:1px 5px;margin-left:4px">已禁用</span>
                            <?php endif; ?>
                        </div>
                        <div class="user-card-role"><?= htmlspecialchars($u['phone'] ?: $u['username']) ?></div>
                    </div>
                </div>
                <div class="user-stats">
                    <div class="user-stat">
                        <div class="user-stat-val"><?= $db->query("SELECT COUNT(*) FROM goods WHERE creator_id = {$u['id']} AND status = 1")->fetchColumn() ?></div>
                        <div class="user-stat-lbl">录入物品</div>
                    </div>
                    <div class="user-stat">
                        <div class="user-stat-val"><?= $db->query("SELECT COUNT(*) FROM storage_space WHERE creator_id = {$u['id']}")->fetchColumn() ?></div>
                        <div class="user-stat-lbl">创建空间</div>
                    </div>
                    <div class="user-stat">
                        <div class="user-stat-val"><?= $db->query("SELECT COUNT(*) FROM operate_log WHERE user_id = {$u['id']}")->fetchColumn() ?></div>
                        <div class="user-stat-lbl">操作次数</div>
                    </div>
                </div>
                <div class="user-card-foot">
                    <span>📅 <?= $u['last_login_time'] ? date('m-d H:i', $u['last_login_time']) : '从未登录' ?></span>
                    <span><?= $u['status'] ? '🟢 正常' : '⏸ 已禁用' ?></span>
                </div>
                <div style="display:flex;gap:6px;margin-top:12px">
                    <form method="POST" style="display:inline">
                        <input type="hidden" name="action" value="toggle_user">
                        <input type="hidden" name="id" value="<?= $u['id'] ?>">
                        <input type="hidden" name="status" value="<?= $u['status'] ? 0 : 1 ?>">
                        <button type="submit" class="btn btn-outline btn-sm" style="flex:1"><?= $u['status'] ? '禁用' : '启用' ?></button>
                    </form>
                    <form method="POST" style="display:inline" onsubmit="return confirm('确定重置密码为123456？')">
                        <input type="hidden" name="action" value="reset_password">
                        <input type="hidden" name="id" value="<?= $u['id'] ?>">
                        <button type="submit" class="btn btn-ghost btn-sm">🔑</button>
                    </form>
                </div>
            </div>
            <?php endforeach; ?>

            <!-- Add new -->
            <div class="user-card" style="border-style:dashed;background:#FAFAFA;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:8px;color:#A0AEC0;min-height:240px" onclick="showModal('addUserModal')">
                <div style="font-size:36px;opacity:.5">+</div>
                <div style="font-size:13px;font-weight:500">邀请新成员</div>
            </div>
        </div>
    </div>

    <!-- Logs -->
    <div class="card">
        <div class="card-header">
            <div>
                <div class="card-title">📋 操作日志</div>
                <div style="font-size:11px;color:#718096;margin-top:2px">最近操作记录</div>
            </div>
            <a href="?p=users&tab=logs" style="font-size:12px;color:#FF8C42;font-weight:500">查看全部 →</a>
        </div>
        <div class="log-list">
            <?php foreach (array_slice($logs, 0, 8) as $log):
                $li = $logIcons[$log['action']] ?? ['📋','edit'];
            ?>
            <div class="log-item">
                <div class="log-icon <?= $li[1] ?>"><?= $li[0] ?></div>
                <div class="log-content">
                    <div class="log-action">
                        <strong><?= htmlspecialchars($log['username']) ?></strong> <?= htmlspecialchars($log['action']) ?> · <?= htmlspecialchars($log['content']) ?>
                    </div>
                    <div class="log-meta">
                        <span>🕐 <?= date('m-d H:i', $log['created_at']) ?></span>
                        <span class="ip">🌐 <?= $log['ip'] ?></span>
                    </div>
                </div>
            </div>
            <?php endforeach; ?>
        </div>
    </div>
</div>

<!-- Add user modal -->
<div class="modal-mask" id="addUserModal" style="display:none">
    <div class="modal">
        <div class="modal-header">
            <div class="modal-title">添加用户</div>
            <span style="cursor:pointer;font-size:18px" onclick="hideModal('addUserModal')">×</span>
        </div>
        <form method="POST">
            <input type="hidden" name="action" value="add_user">
            <div class="modal-body">
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
                        <option value="2">编辑成员</option>
                        <option value="1">超级管理员</option>
                    </select>
                </div>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-outline" onclick="hideModal('addUserModal')">取消</button>
                <button type="submit" class="btn btn-primary">确定</button>
            </div>
        </form>
    </div>
</div>

<?php elseif ($tab === 'houses'): ?>
<!-- Houses -->
<div class="card">
    <div class="card-header">
        <div class="card-title">🏠 家庭组</div>
    </div>
    <table>
        <thead><tr><th>名称</th><th>邀请码</th><th>创建者</th><th>成员数</th><th>空间数</th><th>物品数</th><th>创建时间</th></tr></thead>
        <tbody>
            <?php foreach ($houses as $h): ?>
            <tr>
                <td><strong><?= htmlspecialchars($h['name']) ?></strong></td>
                <td><code style="background:var(--bg);padding:2px 8px;border-radius:4px;font-size:12px"><?= $h['invite_code'] ?></code></td>
                <td><?= htmlspecialchars($h['creator_name'] ?? '-') ?></td>
                <td><strong><?= $h['member_count'] ?></strong></td>
                <td><?= $h['space_count'] ?></td>
                <td><?= $h['item_count'] ?></td>
                <td style="font-size:12px;color:#718096"><?= date('Y-m-d', $h['created_at']) ?></td>
            </tr>
            <?php endforeach; ?>
        </tbody>
    </table>
</div>

<?php else: ?>
<!-- Logs -->
<div class="card">
    <div class="card-header">
        <div class="card-title">📋 操作日志</div>
    </div>
    <div class="log-list">
        <?php foreach ($logs as $log):
            $li = $logIcons[$log['action']] ?? ['📋','edit'];
        ?>
        <div class="log-item">
            <div class="log-icon <?= $li[1] ?>"><?= $li[0] ?></div>
            <div class="log-content">
                <div class="log-action">
                    <strong><?= htmlspecialchars($log['username']) ?></strong> <?= htmlspecialchars($log['action']) ?> · <?= htmlspecialchars($log['content']) ?>
                </div>
                <div class="log-meta">
                    <span>🕐 <?= date('Y-m-d H:i', $log['created_at']) ?></span>
                    <span class="ip">🌐 <?= $log['ip'] ?></span>
                </div>
            </div>
        </div>
        <?php endforeach; ?>
    </div>
</div>
<?php endif; ?>
