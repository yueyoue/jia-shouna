<?php
// 处理登录和退出
if (isset($_GET['action']) && $_GET['action'] === 'logout') {
    session_destroy();
    header('Location: index.php?p=login');
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $username = trim($_POST['username'] ?? '');
    $password = $_POST['password'] ?? '';

    if (!empty($username) && !empty($password)) {
        $db = getDB();
        $stmt = $db->prepare("SELECT * FROM sys_user WHERE username = ? AND role = 1 AND status = 1");
        $stmt->execute([$username]);
        $admin = $stmt->fetch();

        if ($admin && password_verify($password, $admin['password'])) {
            $_SESSION['admin_id'] = $admin['id'];
            $db->prepare("UPDATE sys_user SET last_login_time = ?, last_login_ip = ? WHERE id = ?")
                ->execute([time(), $_SERVER['REMOTE_ADDR'] ?? '', $admin['id']]);
            header('Location: index.php?p=dashboard');
            exit;
        }
        $error = '用户名或密码错误';
    } else {
        $error = '请输入用户名和密码';
    }
}
?>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>登录 - 家收纳管理后台</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #F5F6FA; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
        .login-container { display: flex; background: #fff; border-radius: 16px; box-shadow: 0 8px 40px rgba(0,0,0,0.12); overflow: hidden; max-width: 900px; width: 90%; }
        .login-left { flex: 1; background: linear-gradient(135deg, #FF8C42, #FF6B35); color: #fff; padding: 60px 40px; display: flex; flex-direction: column; justify-content: center; }
        .login-left h1 { font-size: 28px; margin-bottom: 12px; }
        .login-left p { font-size: 14px; opacity: 0.9; line-height: 1.8; }
        .login-left .feature-list { margin-top: 24px; }
        .login-left .feature-item { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; font-size: 13px; }
        .login-right { flex: 1; padding: 60px 40px; display: flex; flex-direction: column; justify-content: center; }
        .login-right h2 { font-size: 22px; margin-bottom: 8px; }
        .login-right .subtitle { color: #999; font-size: 13px; margin-bottom: 30px; }
        .form-group { margin-bottom: 20px; }
        .form-label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; }
        .form-control { width: 100%; padding: 12px 14px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px; transition: border-color 0.2s; }
        .form-control:focus { outline: none; border-color: #FF8C42; box-shadow: 0 0 0 3px rgba(255,140,66,0.1); }
        .btn-login { width: 100%; padding: 14px; background: #FF8C42; color: #fff; border: none; border-radius: 8px; font-size: 15px; font-weight: 600; cursor: pointer; transition: background 0.2s; }
        .btn-login:hover { background: #e67e3a; }
        .error-msg { background: #FEE; color: #F56565; padding: 10px 14px; border-radius: 8px; font-size: 13px; margin-bottom: 16px; }
        .footer-text { text-align: center; color: #999; font-size: 12px; margin-top: 24px; }
        @media (max-width: 768px) { .login-left { display: none; } .login-right { padding: 40px 24px; } }
    </style>
</head>
<body>
    <div class="login-container">
        <div class="login-left">
            <h1>🏠 家收纳 · 管理后台</h1>
            <p>配套「家收纳」APP 的云端管理后台，<br>实现数据统一管理、第三方接口灵活配置、数据安全备份。</p>
            <div class="feature-list">
                <div class="feature-item">✓ 云端数据同步，APP与网页端数据互通</div>
                <div class="feature-item">✓ 可视化接口配置，密钥云端统一管理</div>
                <div class="feature-item">✓ 多维度数据备份与恢复，数据更安全</div>
                <div class="feature-item">✓ 家庭成员精细化权限管理</div>
            </div>
        </div>
        <div class="login-right">
            <h2>欢迎回来 👋</h2>
            <p class="subtitle">请使用管理员账号登录后台</p>
            <?php if (!empty($error)): ?>
                <div class="error-msg">⚠ <?= htmlspecialchars($error) ?></div>
            <?php endif; ?>
            <form method="POST" action="">
                <div class="form-group">
                    <label class="form-label">👤 账号</label>
                    <input type="text" name="username" class="form-control" placeholder="请输入管理员账号" value="<?= htmlspecialchars($_POST['username'] ?? '') ?>" required>
                </div>
                <div class="form-group">
                    <label class="form-label">🔒 密码</label>
                    <input type="password" name="password" class="form-control" placeholder="请输入密码" required>
                </div>
                <button type="submit" class="btn-login">登 录</button>
            </form>
            <div class="footer-text">© 2026 家收纳 · 仅供家庭内部使用</div>
        </div>
    </div>
</body>
</html>
