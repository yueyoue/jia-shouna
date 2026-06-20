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

<style>
.settings-layout{display:grid;grid-template-columns:220px 1fr;gap:16px}
@media(max-width:1024px){.settings-layout{grid-template-columns:1fr}}
.settings-nav{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:12px;height:fit-content;position:sticky;top:84px}
.settings-nav .nav-item{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:8px;color:#4A5568;font-size:13px;cursor:pointer;margin-bottom:2px;transition:all .2s}
.settings-nav .nav-item:hover{background:#F7FAFC}
.settings-nav .nav-item.active{background:linear-gradient(90deg,rgba(255,140,66,.1) 0%,transparent 100%);color:#FF8C42;font-weight:600;position:relative}
.settings-nav .nav-item.active::before{content:'';position:absolute;left:0;top:8px;bottom:8px;width:3px;background:#FF8C42;border-radius:0 3px 3px 0}
.settings-nav .nav-group-title{padding:14px 12px 6px;font-size:11px;font-weight:600;color:#A0AEC0;text-transform:uppercase;letter-spacing:.5px}
.settings-content{display:flex;flex-direction:column;gap:16px}
.settings-card{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);overflow:hidden}
.settings-card-header{padding:16px 20px;border-bottom:1px solid var(--border-2);display:flex;align-items:center;justify-content:space-between;background:linear-gradient(90deg,#FAFBFC 0%,#fff 100%)}
.settings-card-title{display:flex;align-items:center;gap:10px}
.settings-card-icon{width:36px;height:36px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:18px}
.sci1{background:rgba(255,140,66,.12);color:#FF8C42}
.sci2{background:rgba(78,205,196,.12);color:#0E9F8E}
.sci3{background:rgba(91,159,237,.12);color:#5B9FED}
.sci6{background:rgba(245,101,101,.12);color:#F56565}
.sci5{background:rgba(159,122,234,.12);color:#805AD5}
.settings-card h3{font-size:15px;font-weight:600}
.settings-card p{font-size:11px;color:#718096;margin-top:2px}
.settings-card-body{padding:20px}
.setting-row{display:grid;grid-template-columns:200px 1fr 80px;gap:20px;padding:14px 0;border-bottom:1px dashed #EDF2F7;align-items:center}
.setting-row:last-child{border-bottom:none}
.setting-row-full{display:grid;grid-template-columns:200px 1fr;gap:20px;padding:14px 0;border-bottom:1px dashed #EDF2F7;align-items:flex-start}
.setting-row-full:last-child{border-bottom:none}
.setting-label{font-size:13px;font-weight:600;color:#2D3748;display:flex;align-items:center;gap:6px}
.setting-label .required{color:#F56565}
.setting-desc{font-size:11px;color:#718096;margin-top:4px;line-height:1.5}
.setting-control{max-width:400px}
.cleanup-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;padding:4px 0}
.cleanup-item{padding:14px;border-radius:10px;background:#F7FAFC;border:1px solid var(--border-2);display:flex;align-items:flex-start;gap:10px}
.cleanup-icon{width:36px;height:36px;border-radius:8px;display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0}
.ci-red{background:rgba(245,101,101,.12);color:#F56565}
.ci-orange{background:rgba(237,137,54,.12);color:#ED8936}
.ci-green{background:rgba(72,187,120,.12);color:#48BB78}
.cleanup-info{flex:1;min-width:0}
.cleanup-name{font-size:13px;font-weight:600}
.cleanup-desc{font-size:11px;color:#718096;margin-top:2px;line-height:1.5}
.cleanup-count{font-size:11px;color:#A0AEC0;margin-top:4px}
.cleanup-btn{margin-top:8px;width:100%;font-size:11px;padding:4px 0}
</style>

<?php if (!empty($msg)): ?>
<div class="alert alert-success" style="margin-bottom:16px">
    <span class="alert-icon">✅</span>
    <div><?= $msg ?></div>
</div>
<?php endif; ?>
<?php if (!empty($error)): ?>
<div class="alert alert-danger" style="margin-bottom:16px">
    <span class="alert-icon">⚠</span>
    <div><?= $error ?></div>
</div>
<?php endif; ?>

<div class="settings-layout">
    <!-- Settings Nav - 隐藏，功能通过页面内锚点实现 -->
    <div class="settings-nav" style="display:none">
        <div class="nav-group-title">基础</div>
        <div class="nav-item active"><span>🏠</span><span>站点配置</span></div>
        <div class="nav-item"><span>🔔</span><span>提醒规则</span></div>
        <div class="nav-group-title">安全</div>
        <div class="nav-item"><span>🔐</span><span>密码安全</span></div>
        <div class="nav-item"><span>🛡</span><span>IP 白名单</span></div>
        <div class="nav-group-title">维护</div>
        <div class="nav-item"><span>🧹</span><span>数据清理</span></div>
        <div class="nav-item"><span>ℹ</span><span>系统信息</span></div>
    </div>

    <!-- Settings Content - 照抄 UI -->
    <div class="settings-content" style="max-width:960px">
        <form method="POST">
            <input type="hidden" name="action" value="save">

            <!-- Basic config -->
            <div class="settings-card">
                <div class="settings-card-header">
                    <div class="settings-card-title">
                        <div class="settings-card-icon sci1">🏠</div>
                        <div>
                            <h3>站点基础配置</h3>
                            <p>站点的基本信息和注册策略</p>
                        </div>
                    </div>
                </div>
                <div class="settings-card-body">
                    <div class="setting-row-full">
                        <div>
                            <div class="setting-label">站点名称 <span class="required">*</span></div>
                            <div class="setting-desc">显示在浏览器标题和登录页</div>
                        </div>
                        <div class="setting-control">
                            <input class="form-control" name="site_name" value="<?= htmlspecialchars($settings['site_name'] ?? '家收纳') ?>">
                        </div>
                    </div>
                    <div class="setting-row">
                        <div>
                            <div class="setting-label">开放注册 <span class="required">*</span></div>
                            <div class="setting-desc">家庭自用推荐关闭</div>
                        </div>
                        <div class="setting-control">
                            <select name="open_register" class="form-control">
                                <option value="0" <?= ($settings['open_register'] ?? '') == '0' ? 'selected' : '' ?>>关闭 - 仅管理员手动添加</option>
                                <option value="1" <?= ($settings['open_register'] ?? '') == '1' ? 'selected' : '' ?>>开放 - 任何人都可注册</option>
                            </select>
                        </div>
                        <label class="switch">
                            <input type="checkbox" name="open_register" value="1" <?= ($settings['open_register'] ?? '') == '1' ? 'checked' : '' ?>>
                            <span class="switch-slider"></span>
                        </label>
                    </div>
                    <div class="setting-row">
                        <div>
                            <div class="setting-label">默认提前提醒天数</div>
                            <div class="setting-desc">保质期到期前多少天提醒</div>
                        </div>
                        <div class="setting-control">
                            <div style="display:flex;align-items:center;gap:10px">
                                <input class="form-control" name="default_remind_days" type="number" value="<?= $settings['default_remind_days'] ?? 7 ?>" style="width:100px">
                                <span style="font-size:13px;color:#718096">天</span>
                            </div>
                        </div>
                        <div></div>
                    </div>
                </div>
            </div>

            <div style="padding-top:16px">
                <button type="submit" class="btn btn-primary">💾 保存所有更改</button>
            </div>
        </form>

        <!-- Security - Password -->
        <div class="settings-card">
            <div class="settings-card-header">
                <div class="settings-card-title">
                    <div class="settings-card-icon sci3">🔐</div>
                    <div>
                        <h3>安全设置</h3>
                        <p>密码、登录、IP 限制等安全策略</p>
                    </div>
                </div>
            </div>
            <div class="settings-card-body">
                <form method="POST">
                    <input type="hidden" name="action" value="change_password">
                    <div class="setting-row-full">
                        <div>
                            <div class="setting-label">修改管理员密码</div>
                            <div class="setting-desc">建议每 90 天更换一次密码</div>
                        </div>
                        <div class="setting-control" style="max-width:400px">
                            <div style="display:grid;gap:8px">
                                <input class="form-control" name="old_password" type="password" placeholder="当前密码">
                                <input class="form-control" name="new_password" type="password" placeholder="新密码（至少 6 位）">
                                <input class="form-control" name="confirm_password" type="password" placeholder="确认新密码">
                                <button type="submit" class="btn btn-primary btn-sm" style="margin-top:8px;align-self:flex-start">🔐 更新密码</button>
                            </div>
                        </div>
                        <div></div>
                    </div>
                </form>

                <div class="divider"></div>

                <!-- IP Whitelist -->
                <form method="POST">
                    <input type="hidden" name="action" value="save">
                    <div class="setting-row">
                        <div>
                            <div class="setting-label">IP 白名单</div>
                            <div class="setting-desc">多个IP用逗号分隔</div>
                        </div>
                        <div class="setting-control">
                            <input class="form-control" name="ip_whitelist" value="<?= htmlspecialchars($settings['ip_whitelist'] ?? '') ?>" placeholder="留空不限制">
                        </div>
                        <label class="switch">
                            <input type="checkbox" name="ip_whitelist_enabled" value="1" <?= ($settings['ip_whitelist_enabled'] ?? '') == '1' ? 'checked' : '' ?>>
                            <span class="switch-slider"></span>
                        </label>
                    </div>
                    <div style="padding-top:12px">
                        <button type="submit" class="btn btn-primary btn-sm">💾 保存</button>
                    </div>
                </form>
            </div>
        </div>

        <!-- Cleanup -->
        <div class="settings-card">
            <div class="settings-card-header">
                <div class="settings-card-title">
                    <div class="settings-card-icon sci6">🧹</div>
                    <div>
                        <h3>数据清理</h3>
                        <p>清理过期数据、失效图片、日志文件</p>
                    </div>
                </div>
            </div>
            <div class="settings-card-body">
                <div class="cleanup-grid">
                    <div class="cleanup-item">
                        <div class="cleanup-icon ci-red">📅</div>
                        <div class="cleanup-info">
                            <div class="cleanup-name">过期物品记录</div>
                            <div class="cleanup-desc">已过期超过 90 天的物品</div>
                            <form method="POST" style="margin-top:8px">
                                <input type="hidden" name="action" value="cleanup">
                                <input type="hidden" name="type" value="expired">
                                <button type="submit" class="btn btn-outline btn-sm cleanup-btn" onclick="return confirm('确定清理？')">🧹 立即清理</button>
                            </form>
                        </div>
                    </div>
                    <div class="cleanup-item">
                        <div class="cleanup-icon ci-green">📋</div>
                        <div class="cleanup-info">
                            <div class="cleanup-name">过期日志</div>
                            <div class="cleanup-desc">超过 90 天的操作日志</div>
                            <form method="POST" style="margin-top:8px">
                                <input type="hidden" name="action" value="cleanup">
                                <input type="hidden" name="type" value="logs">
                                <button type="submit" class="btn btn-outline btn-sm cleanup-btn" onclick="return confirm('确定清理？')">🧹 立即清理</button>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- System info -->
        <div class="settings-card">
            <div class="settings-card-header">
                <div class="settings-card-title">
                    <div class="settings-card-icon sci5">ℹ</div>
                    <div>
                        <h3>系统信息</h3>
                        <p>版本、环境、运行状态</p>
                    </div>
                </div>
            </div>
            <div class="settings-card-body">
                <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px">
                    <div style="padding:14px;background:#F7FAFC;border-radius:8px">
                        <div style="font-size:11px;color:#718096">系统版本</div>
                        <div style="font-size:16px;font-weight:600;margin-top:4px">v1.0.0</div>
                    </div>
                    <div style="padding:14px;background:#F7FAFC;border-radius:8px">
                        <div style="font-size:11px;color:#718096">PHP 版本</div>
                        <div style="font-size:16px;font-weight:600;margin-top:4px;font-family:monospace"><?= phpversion() ?></div>
                    </div>
                    <div style="padding:14px;background:#F7FAFC;border-radius:8px">
                        <div style="font-size:11px;color:#718096">MySQL 版本</div>
                        <div style="font-size:16px;font-weight:600;margin-top:4px;font-family:monospace"><?= $db->query("SELECT VERSION()")->fetchColumn() ?></div>
                    </div>
                    <div style="padding:14px;background:#F7FAFC;border-radius:8px">
                        <div style="font-size:11px;color:#718096">数据库大小</div>
                        <div style="font-size:16px;font-weight:600;margin-top:4px"><?= formatSize($db->query("SELECT SUM(data_length + index_length) FROM information_schema.tables WHERE table_schema = DATABASE()")->fetchColumn()) ?></div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
