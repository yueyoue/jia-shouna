<?php
$db = getDB();

// 获取所有设置
$settings = [];
$rows = $db->query("SELECT * FROM sys_setting")->fetchAll();
foreach ($rows as $row) { $settings[$row['skey']] = $row['svalue']; }

// 保存设置
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    if ($action === 'save') {
        $fields = ['site_name', 'open_register', 'default_remind_days', 'ip_whitelist', 'ip_whitelist_enabled', 'auto_backup_enabled', 'auto_backup_cycle', 'auto_backup_keep'];
        foreach ($fields as $field) {
            if (isset($_POST[$field])) {
                $val = $_POST[$field];
                if (is_string($val)) $val = trim($val);
                $db->prepare("UPDATE sys_setting SET svalue = ?, updated_at = ? WHERE skey = ?")->execute([$val, time(), $field]);
                $settings[$field] = $val;
            }
        }
        $msg = '设置已保存';
    } elseif ($action === 'change_password') {
        $oldPwd = $_POST['old_password'] ?? '';
        $newPwd = $_POST['new_password'] ?? '';
        $confirmPwd = $_POST['confirm_password'] ?? '';
        
        $admin = $db->prepare("SELECT * FROM sys_user WHERE id = ?")->execute([$_SESSION['admin_id']]);
        $admin = $db->prepare("SELECT * FROM sys_user WHERE id = ?")->fetch();
        
        if (!password_verify($oldPwd, $admin['password'])) {
            $error = '原密码错误';
        } elseif (strlen($newPwd) < 6) {
            $error = '新密码长度至少6位';
        } elseif ($newPwd !== $confirmPwd) {
            $error = '两次密码不一致';
        } else {
            $hashed = password_hash($newPwd, PASSWORD_DEFAULT);
            $db->prepare("UPDATE sys_user SET password = ?, updated_at = ? WHERE id = ?")->execute([$hashed, time(), $_SESSION['admin_id']]);
            $msg = '密码修改成功';
        }
    } elseif ($action === 'cleanup') {
        $type = $_POST['type'] ?? '';
        if ($type === 'expired') {
            $stmt = $db->exec("UPDATE goods SET status = 0 WHERE expiry_date < CURDATE() AND status = 1");
            $msg = "已清理过期物品记录";
        } elseif ($type === 'logs') {
            $db->exec("DELETE FROM operate_log WHERE created_at < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 90 DAY))");
            $db->exec("DELETE FROM api_log WHERE created_at < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 90 DAY))");
            $msg = "已清理90天前的日志";
        }
    }
}
?>

<div class="card-header" style="margin-bottom: 16px;">
    <h2 style="font-size: 18px;">系统设置</h2>
</div>

<?php if (!empty($msg)): ?>
    <div class="toast toast-success" style="position: relative; margin-bottom: 16px;"><?= $msg ?></div>
<?php endif; ?>
<?php if (!empty($error)): ?>
    <div class="toast toast-error" style="position: relative; margin-bottom: 16px;"><?= $error ?></div>
<?php endif; ?>

<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
    <!-- 基础配置 -->
    <div class="card">
        <div class="card-title" style="margin-bottom: 16px;">📋 基础配置</div>
        <form method="POST">
            <input type="hidden" name="action" value="save">
            <div class="form-group">
                <label class="form-label">站点名称</label>
                <input type="text" name="site_name" class="form-control" value="<?= htmlspecialchars($settings['site_name'] ?? '家收纳') ?>">
            </div>
            <div class="form-group">
                <label class="form-label">开放注册</label>
                <label class="switch">
                    <input type="checkbox" name="open_register" value="1" <?= ($settings['open_register'] ?? '') == '1' ? 'checked' : '' ?>>
                    <span class="switch-slider"></span>
                </label>
                <span style="font-size: 12px; color: #999; margin-left: 8px;">关闭后仅管理员可添加用户</span>
            </div>
            <div class="form-group">
                <label class="form-label">默认提前提醒天数</label>
                <input type="number" name="default_remind_days" class="form-control" value="<?= $settings['default_remind_days'] ?? 7 ?>">
            </div>
            <button type="submit" class="btn btn-primary">💾 保存</button>
        </form>
    </div>

    <!-- 安全设置 -->
    <div class="card">
        <div class="card-title" style="margin-bottom: 16px;">🔐 安全设置</div>
        <form method="POST">
            <input type="hidden" name="action" value="save">
            <div class="form-group">
                <label class="form-label">IP白名单</label>
                <input type="text" name="ip_whitelist" class="form-control" value="<?= htmlspecialchars($settings['ip_whitelist'] ?? '') ?>" placeholder="多个IP用逗号分隔，留空不限制">
            </div>
            <div class="form-group">
                <label class="form-label">启用IP白名单</label>
                <label class="switch">
                    <input type="checkbox" name="ip_whitelist_enabled" value="1" <?= ($settings['ip_whitelist_enabled'] ?? '') == '1' ? 'checked' : '' ?>>
                    <span class="switch-slider"></span>
                </label>
            </div>
            <button type="submit" class="btn btn-primary">💾 保存</button>
        </form>

        <hr style="margin: 20px 0; border: none; border-top: 1px solid #eee;">
        
        <div class="card-title" style="margin-bottom: 12px;">🔑 修改密码</div>
        <form method="POST">
            <input type="hidden" name="action" value="change_password">
            <div class="form-group">
                <label class="form-label">原密码</label>
                <input type="password" name="old_password" class="form-control" required>
            </div>
            <div class="form-group">
                <label class="form-label">新密码</label>
                <input type="password" name="new_password" class="form-control" required>
            </div>
            <div class="form-group">
                <label class="form-label">确认新密码</label>
                <input type="password" name="confirm_password" class="form-control" required>
            </div>
            <button type="submit" class="btn btn-primary">🔑 修改密码</button>
        </form>
    </div>
</div>

<!-- 清理功能 -->
<div class="card" style="margin-top: 20px;">
    <div class="card-title" style="margin-bottom: 16px;">🧹 数据清理</div>
    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px;">
        <div style="padding: 16px; background: #f8f9fa; border-radius: 8px;">
            <div style="font-weight: 600; margin-bottom: 8px;">清理过期物品</div>
            <p style="font-size: 12px; color: #999; margin-bottom: 12px;">将已过期的物品标记为失效</p>
            <form method="POST" style="display: inline;">
                <input type="hidden" name="action" value="cleanup">
                <input type="hidden" name="type" value="expired">
                <button type="submit" class="btn btn-outline btn-sm" onclick="return confirm('确定清理过期物品？')">🧹 清理</button>
            </form>
        </div>
        <div style="padding: 16px; background: #f8f9fa; border-radius: 8px;">
            <div style="font-weight: 600; margin-bottom: 8px;">清理历史日志</div>
            <p style="font-size: 12px; color: #999; margin-bottom: 12px;">清理90天前的操作日志和接口日志</p>
            <form method="POST" style="display: inline;">
                <input type="hidden" name="action" value="cleanup">
                <input type="hidden" name="type" value="logs">
                <button type="submit" class="btn btn-outline btn-sm" onclick="return confirm('确定清理历史日志？')">🧹 清理</button>
            </form>
        </div>
        <div style="padding: 16px; background: #f8f9fa; border-radius: 8px;">
            <div style="font-weight: 600; margin-bottom: 8px;">恢复默认设置</div>
            <p style="font-size: 12px; color: #999; margin-bottom: 12px;">将所有设置恢复为默认值</p>
            <button class="btn btn-outline btn-sm" onclick="return confirm('确定恢复默认设置？')">↩ 恢复默认</button>
        </div>
    </div>
</div>
