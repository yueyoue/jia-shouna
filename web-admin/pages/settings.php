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
            $db->exec("UPDATE goods SET status = 0 WHERE expiry_date < CURDATE() AND status = 1");
            $msg = "已清理过期物品记录";
        } elseif ($type === 'logs') {
            $db->exec("DELETE FROM operate_log WHERE created_at < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 90 DAY))");
            $db->exec("DELETE FROM api_log WHERE created_at < UNIX_TIMESTAMP(DATE_SUB(NOW(), INTERVAL 90 DAY))");
            $msg = "已清理90天前的日志";
        }
    }
}
?>

<div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;">
    <div>
        <div class="page-title" style="font-size:22px;font-weight:700">系统设置</div>
    </div>
</div>

<?php if (!empty($msg)): ?>
    <div class="alert alert-success" style="margin-bottom:16px;">
        <span class="alert-icon">✅</span>
        <div><?= $msg ?></div>
    </div>
<?php endif; ?>
<?php if (!empty($error)): ?>
    <div class="alert alert-danger" style="margin-bottom:16px;">
        <span class="alert-icon">⚠</span>
        <div><?= $error ?></div>
    </div>
<?php endif; ?>

<div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;">
    <!-- 基础配置 -->
    <div class="card" style="overflow:hidden;">
        <div class="card-header" style="background:linear-gradient(90deg,#FAFBFC 0%,#fff 100%);">
            <div style="display:flex;align-items:center;gap:10px">
                <div style="width:36px;height:36px;border-radius:10px;background:rgba(255,140,66,.12);color:var(--primary);display:flex;align-items:center;justify-content:center;font-size:18px">📋</div>
                <div>
                    <div class="card-title">基础配置</div>
                    <div style="font-size:11px;color:var(--text-3);margin-top:2px">站点基本参数设置</div>
                </div>
            </div>
        </div>
        <div style="padding:20px;">
            <form method="POST">
                <input type="hidden" name="action" value="save">
                <div style="display:grid;grid-template-columns:200px 1fr;gap:20px;padding:14px 0;border-bottom:1px dashed var(--border-2);align-items:center;">
                    <div style="font-size:13px;font-weight:600;color:var(--text)">站点名称</div>
                    <div style="max-width:400px;">
                        <input type="text" name="site_name" class="form-control" value="<?= htmlspecialchars($settings['site_name'] ?? '家收纳') ?>">
                    </div>
                </div>
                <div style="display:grid;grid-template-columns:200px 1fr 80px;gap:20px;padding:14px 0;border-bottom:1px dashed var(--border-2);align-items:center;">
                    <div>
                        <div style="font-size:13px;font-weight:600;color:var(--text)">开放注册</div>
                        <div style="font-size:11px;color:var(--text-3);margin-top:4px">关闭后仅管理员可添加用户</div>
                    </div>
                    <div>
                        <label class="switch">
                            <input type="checkbox" name="open_register" value="1" <?= ($settings['open_register'] ?? '') == '1' ? 'checked' : '' ?>>
                            <span class="switch-slider"></span>
                        </label>
                    </div>
                    <div></div>
                </div>
                <div style="display:grid;grid-template-columns:200px 1fr;gap:20px;padding:14px 0;border-bottom:1px dashed var(--border-2);align-items:center;">
                    <div style="font-size:13px;font-weight:600;color:var(--text)">默认提前提醒天数</div>
                    <div style="max-width:400px;">
                        <input type="number" name="default_remind_days" class="form-control" value="<?= $settings['default_remind_days'] ?? 7 ?>">
                    </div>
                </div>
                <div style="padding-top:16px;">
                    <button type="submit" class="btn btn-primary">💾 保存</button>
                </div>
            </form>
        </div>
    </div>

    <!-- 安全设置 -->
    <div class="card" style="overflow:hidden;">
        <div class="card-header" style="background:linear-gradient(90deg,#FAFBFC 0%,#fff 100%);">
            <div style="display:flex;align-items:center;gap:10px">
                <div style="width:36px;height:36px;border-radius:10px;background:rgba(78,205,196,.12);color:var(--secondary-dark);display:flex;align-items:center;justify-content:center;font-size:18px">🔐</div>
                <div>
                    <div class="card-title">安全设置</div>
                    <div style="font-size:11px;color:var(--text-3);margin-top:2px">访问控制与密码管理</div>
                </div>
            </div>
        </div>
        <div style="padding:20px;">
            <form method="POST">
                <input type="hidden" name="action" value="save">
                <div style="display:grid;grid-template-columns:200px 1fr;gap:20px;padding:14px 0;border-bottom:1px dashed var(--border-2);align-items:center;">
                    <div>
                        <div style="font-size:13px;font-weight:600;color:var(--text)">IP白名单</div>
                        <div style="font-size:11px;color:var(--text-3);margin-top:4px">多个IP用逗号分隔</div>
                    </div>
                    <div style="max-width:400px;">
                        <input type="text" name="ip_whitelist" class="form-control" value="<?= htmlspecialchars($settings['ip_whitelist'] ?? '') ?>" placeholder="留空不限制">
                    </div>
                </div>
                <div style="display:grid;grid-template-columns:200px 1fr 80px;gap:20px;padding:14px 0;border-bottom:1px dashed var(--border-2);align-items:center;">
                    <div style="font-size:13px;font-weight:600;color:var(--text)">启用IP白名单</div>
                    <div>
                        <label class="switch">
                            <input type="checkbox" name="ip_whitelist_enabled" value="1" <?= ($settings['ip_whitelist_enabled'] ?? '') == '1' ? 'checked' : '' ?>>
                            <span class="switch-slider"></span>
                        </label>
                    </div>
                    <div></div>
                </div>
                <div style="padding-top:16px;">
                    <button type="submit" class="btn btn-primary">💾 保存</button>
                </div>
            </form>

            <div style="height:1px;background:var(--border-2);margin:20px 0;"></div>
            
            <div style="font-size:15px;font-weight:600;margin-bottom:16px;display:flex;align-items:center;gap:6px">🔑 修改密码</div>
            <form method="POST">
                <input type="hidden" name="action" value="change_password">
                <div class="form-group">
                    <label class="form-label">原密码</label>
                    <input type="password" name="old_password" class="form-control" required>
                </div>
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
                    <div class="form-group">
                        <label class="form-label">新密码</label>
                        <input type="password" name="new_password" class="form-control" required>
                    </div>
                    <div class="form-group">
                        <label class="form-label">确认新密码</label>
                        <input type="password" name="confirm_password" class="form-control" required>
                    </div>
                </div>
                <button type="submit" class="btn btn-primary">🔑 修改密码</button>
            </form>
        </div>
    </div>
</div>

<!-- 清理功能 -->
<div class="card" style="margin-top:16px;overflow:hidden;">
    <div class="card-header" style="background:linear-gradient(90deg,#FAFBFC 0%,#fff 100%);">
        <div style="display:flex;align-items:center;gap:10px">
            <div style="width:36px;height:36px;border-radius:10px;background:rgba(245,101,101,.12);color:var(--danger);display:flex;align-items:center;justify-content:center;font-size:18px">🧹</div>
            <div>
                <div class="card-title">数据清理</div>
                <div style="font-size:11px;color:var(--text-3);margin-top:2px">清理过期数据，释放存储空间</div>
            </div>
        </div>
    </div>
    <div style="padding:20px;">
        <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;">
            <div style="padding:14px;border-radius:10px;background:var(--bg);border:1px solid var(--border-2);display:flex;align-items:flex-start;gap:10px;">
                <div style="width:36px;height:36px;border-radius:8px;background:rgba(245,101,101,.12);color:var(--danger);display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0">🗑</div>
                <div style="flex:1">
                    <div style="font-size:13px;font-weight:600">清理过期物品</div>
                    <div style="font-size:11px;color:var(--text-3);margin-top:2px;line-height:1.5">将已过期的物品标记为失效</div>
                    <form method="POST" style="margin-top:8px;">
                        <input type="hidden" name="action" value="cleanup">
                        <input type="hidden" name="type" value="expired">
                        <button type="submit" class="btn btn-outline btn-sm" style="width:100%;font-size:11px;padding:4px 0;" onclick="return confirm('确定清理过期物品？')">🧹 清理</button>
                    </form>
                </div>
            </div>
            <div style="padding:14px;border-radius:10px;background:var(--bg);border:1px solid var(--border-2);display:flex;align-items:flex-start;gap:10px;">
                <div style="width:36px;height:36px;border-radius:8px;background:rgba(237,137,54,.12);color:var(--warning);display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0">📋</div>
                <div style="flex:1">
                    <div style="font-size:13px;font-weight:600">清理历史日志</div>
                    <div style="font-size:11px;color:var(--text-3);margin-top:2px;line-height:1.5">清理90天前的操作日志和接口日志</div>
                    <form method="POST" style="margin-top:8px;">
                        <input type="hidden" name="action" value="cleanup">
                        <input type="hidden" name="type" value="logs">
                        <button type="submit" class="btn btn-outline btn-sm" style="width:100%;font-size:11px;padding:4px 0;" onclick="return confirm('确定清理历史日志？')">🧹 清理</button>
                    </form>
                </div>
            </div>
            <div style="padding:14px;border-radius:10px;background:var(--bg);border:1px solid var(--border-2);display:flex;align-items:flex-start;gap:10px;">
                <div style="width:36px;height:36px;border-radius:8px;background:rgba(72,187,120,.12);color:var(--success);display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0">↩</div>
                <div style="flex:1">
                    <div style="font-size:13px;font-weight:600">恢复默认设置</div>
                    <div style="font-size:11px;color:var(--text-3);margin-top:2px;line-height:1.5">将所有设置恢复为默认值</div>
                    <button class="btn btn-outline btn-sm" style="width:100%;font-size:11px;padding:4px 0;margin-top:8px;" onclick="return confirm('确定恢复默认设置？')">↩ 恢复默认</button>
                </div>
            </div>
        </div>
    </div>
</div>
