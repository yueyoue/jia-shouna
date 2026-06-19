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
        :root{
            --primary:#FF8C42;--primary-dark:#E6732A;
            --secondary:#4ECDC4;--accent:#5B9FED;
            --text:#2D3748;--text-2:#4A5568;--text-3:#718096;--text-4:#A0AEC0;
            --border:#E2E8F0;--border-2:#EDF2F7;
            --bg:#F7FAFC;--danger:#F56565;
        }
        *{box-sizing:border-box;margin:0;padding:0}
        body{
            font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;
            height:100vh;overflow:hidden;background:#fff;
        }
        .login-page{display:flex;height:100vh}

        /* Left brand panel */
        .brand-panel{
            flex:1.1;position:relative;overflow:hidden;
            background:linear-gradient(135deg,#FF8C42 0%,#FF6B35 50%,#4ECDC4 100%);
            display:flex;flex-direction:column;justify-content:space-between;
            padding:48px;color:#fff;
        }
        .brand-panel::before{
            content:'';position:absolute;top:-100px;right:-100px;
            width:400px;height:400px;border-radius:50%;
            background:rgba(255,255,255,.1);
        }
        .brand-panel::after{
            content:'';position:absolute;bottom:-150px;left:-100px;
            width:500px;height:500px;border-radius:50%;
            background:rgba(255,255,255,.08);
        }
        .brand-top{position:relative;z-index:1}
        .brand-logo{
            display:inline-flex;align-items:center;gap:12px;
            font-size:20px;font-weight:700;
        }
        .brand-logo-icon{
            width:48px;height:48px;border-radius:14px;
            background:rgba(255,255,255,.2);
            backdrop-filter:blur(10px);
            display:flex;align-items:center;justify-content:center;
            font-size:24px;
            border:1px solid rgba(255,255,255,.3);
        }
        .brand-content{position:relative;z-index:1;max-width:480px}
        .brand-content h1{
            font-size:42px;font-weight:800;line-height:1.2;
            margin-bottom:20px;letter-spacing:-.5px;
        }
        .brand-content p{font-size:16px;line-height:1.7;opacity:.92}
        .feature-list{margin-top:32px;display:flex;flex-direction:column;gap:14px}
        .feature-item{
            display:flex;align-items:center;gap:12px;
            font-size:14px;opacity:.95;
        }
        .feature-check{
            width:24px;height:24px;border-radius:50%;
            background:rgba(255,255,255,.25);
            display:flex;align-items:center;justify-content:center;
            font-size:14px;flex-shrink:0;
        }
        .brand-bottom{position:relative;z-index:1;font-size:12px;opacity:.7;margin-top:auto}

        /* Right form panel */
        .form-panel{
            flex:1;display:flex;align-items:center;justify-content:center;
            padding:40px;background:#fff;position:relative;
        }
        .form-wrapper{width:100%;max-width:380px}
        .form-header{margin-bottom:32px}
        .form-header h2{font-size:28px;font-weight:700;color:#2D3748;margin-bottom:8px;letter-spacing:-.5px}
        .form-header p{color:#718096;font-size:14px}

        .input-group{margin-bottom:16px;position:relative}
        .input-label{
            display:block;font-size:12px;font-weight:500;
            color:#4A5568;margin-bottom:6px;
        }
        .input-wrap{
            position:relative;display:flex;align-items:center;
            border:1.5px solid #E2E8F0;border-radius:10px;
            background:#fff;transition:all .2s;
        }
        .input-wrap:focus-within{
            border-color:#FF8C42;
            box-shadow:0 0 0 4px rgba(255,140,66,.1);
        }
        .input-icon{
            padding:0 12px;color:#A0AEC0;font-size:16px;
            border-right:1px solid #EDF2F7;align-self:stretch;
            display:flex;align-items:center;
        }
        .input-wrap input{
            flex:1;padding:12px 14px;font-size:14px;
            background:transparent;border:none;outline:none;
            color:#2D3748;
        }
        .input-wrap input::placeholder{color:#A0AEC0}

        .row-between{
            display:flex;align-items:center;justify-content:space-between;
            margin-bottom:24px;font-size:13px;
        }
        .checkbox{display:inline-flex;align-items:center;gap:6px;cursor:pointer;color:#4A5568}
        .checkbox input{width:16px;height:16px;accent-color:#FF8C42;cursor:pointer}

        .btn-login{
            width:100%;padding:13px;border-radius:10px;
            background:linear-gradient(135deg,#FF8C42 0%,#FF6B35 100%);
            color:#fff;font-size:15px;font-weight:600;
            cursor:pointer;border:none;
            box-shadow:0 4px 14px rgba(255,140,66,.4);
            transition:all .2s;
        }
        .btn-login:hover{transform:translateY(-1px);box-shadow:0 6px 20px rgba(255,140,66,.5)}

        .error-msg{
            background:rgba(245,101,101,.08);
            border:1px solid rgba(245,101,101,.2);
            color:#9B2C2C;padding:12px 14px;
            border-radius:10px;font-size:13px;margin-bottom:16px;
            display:flex;align-items:center;gap:8px;
        }

        .security-tips{
            margin-top:20px;padding:12px 14px;
            background:rgba(91,159,237,.06);
            border-radius:8px;font-size:12px;color:#4A5568;
            display:flex;align-items:flex-start;gap:8px;
            border:1px solid rgba(91,159,237,.15);
        }
        .security-tips .icon{color:#5B9FED;font-size:14px;line-height:1.4}

        @media(max-width:900px){
            .brand-panel{display:none}
        }
    </style>
</head>
<body>
<div class="login-page">
    <!-- Left: Brand panel -->
    <div class="brand-panel">
        <div class="brand-top">
            <div class="brand-logo">
                <div class="brand-logo-icon">🏠</div>
                <span>家收纳 · 管理后台</span>
            </div>
        </div>

        <div class="brand-content">
            <h1>让家庭收纳<br>变得简单有序</h1>
            <p>配套「家收纳」APP 的云端管理后台，<br>实现数据统一管理、第三方接口灵活配置、数据安全备份。</p>
            <div class="feature-list">
                <div class="feature-item">
                    <div class="feature-check">✓</div>
                    <span>云端数据同步，APP 与网页端数据互通</span>
                </div>
                <div class="feature-item">
                    <div class="feature-check">✓</div>
                    <span>可视化接口配置，密钥云端统一管理</span>
                </div>
                <div class="feature-item">
                    <div class="feature-check">✓</div>
                    <span>多维度数据备份与恢复，数据更安全</span>
                </div>
                <div class="feature-item">
                    <div class="feature-check">✓</div>
                    <span>家庭成员精细化权限管理</span>
                </div>
            </div>
        </div>

        <div class="brand-bottom">
            <p>© 2026 家收纳 · 仅供家庭内部使用</p>
        </div>
    </div>

    <!-- Right: Login form -->
    <div class="form-panel">
        <div class="form-wrapper">
            <div class="form-header">
                <h2>欢迎回来 👋</h2>
                <p>请使用管理员账号登录后台</p>
            </div>

            <?php if (!empty($error)): ?>
                <div class="error-msg">⚠ <?= htmlspecialchars($error) ?></div>
            <?php endif; ?>

            <form method="POST" action="">
                <div class="input-group">
                    <label class="input-label">账号</label>
                    <div class="input-wrap">
                        <span class="input-icon">👤</span>
                        <input type="text" name="username" placeholder="请输入管理员账号" value="<?= htmlspecialchars($_POST['username'] ?? '') ?>" required>
                    </div>
                </div>

                <div class="input-group">
                    <label class="input-label">密码</label>
                    <div class="input-wrap">
                        <span class="input-icon">🔒</span>
                        <input type="password" name="password" placeholder="请输入密码" required>
                    </div>
                </div>

                <div class="row-between">
                    <label class="checkbox">
                        <input type="checkbox" checked>
                        <span>7 天内自动登录</span>
                    </label>
                </div>

                <button type="submit" class="btn-login">登 录</button>
            </form>

            <div class="security-tips">
                <span class="icon">🛡</span>
                <div>
                    <strong>安全提示：</strong>本系统仅供家庭内部使用。如需在其他网络访问，请联系超级管理员。
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>
