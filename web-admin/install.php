<?php
/**
 * 家收纳 - 安装向导
 */
session_start();
$step = intval($_GET['step'] ?? 1);
$error = '';
$success = '';

// 处理安装提交
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';

    if ($action === 'test_db') {
        $host = trim($_POST['db_host'] ?? 'localhost');
        $port = trim($_POST['db_port'] ?? '3306');
        $user = trim($_POST['db_user'] ?? 'root');
        $pass = $_POST['db_pass'] ?? '';
        $dbname = trim($_POST['db_name'] ?? 'jia_shouna');

        try {
            $dsn = "mysql:host=$host;port=$port;charset=utf8mb4";
            $pdo = new PDO($dsn, $user, $pass, [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
            
            // 创建数据库
            $pdo->exec("CREATE DATABASE IF NOT EXISTS `$dbname` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci");
            $pdo->exec("USE `$dbname`");

            // 导入SQL
            $sqlFile = dirname(__DIR__) . '/database/schema.sql';
            if (!file_exists($sqlFile)) {
                $sqlFile = dirname(__DIR__) . '/schema.sql';
            }
            if (!file_exists($sqlFile)) {
                $error = '找不到 schema.sql 文件，请确认文件位置';
            } else {
                $sql = file_get_contents($sqlFile);
                // 去掉SQL注释
                $sql = preg_replace('/--.*$/m', '', $sql);
                $sql = preg_replace('/\\/\\*.*?\\*\\//s', '', $sql);
                // 按分号分割执行
                $statements = array_filter(array_map('trim', explode(';', $sql)));
                foreach ($statements as $stmt) {
                    $stmt = trim($stmt);
                    if (empty($stmt)) continue;
                    if (strtoupper(substr($stmt, 0, 11)) === 'SET NAMES ') continue;
                    if (strtoupper(substr($stmt, 0, 19)) === 'SET FOREIGN_KEY_CH') continue;
                    try {
                        $pdo->exec($stmt);
                    } catch (PDOException $e) {
                        // 忽略重复表等错误
                    }
                }
                
                // 保存数据库配置到session
                $_SESSION['install_db'] = [
                    'host' => $host, 'port' => $port, 'user' => $user, 'pass' => $pass, 'name' => $dbname
                ];
                $success = '数据库连接成功，表已创建！';
                header('Location: install.php?step=3');
                exit;
            }
        } catch (PDOException $e) {
            $error = '数据库连接失败：' . $e->getMessage();
        }
    }

    if ($action === 'create_admin') {
        $adminUser = trim($_POST['admin_user'] ?? '');
        $adminPass = $_POST['admin_pass'] ?? '';
        $adminPass2 = $_POST['admin_pass2'] ?? '';
        $siteName = trim($_POST['site_name'] ?? '家收纳');

        if (empty($adminUser) || empty($adminPass)) {
            $error = '请填写管理员账号和密码';
        } elseif (strlen($adminPass) < 6) {
            $error = '密码长度至少6位';
        } elseif ($adminPass !== $adminPass2) {
            $error = '两次密码不一致';
        } else {
            $dbInfo = $_SESSION['install_db'] ?? null;
            if (!$dbInfo) {
                $error = '请先完成数据库配置';
            } else {
                try {
                    $dsn = "mysql:host={$dbInfo['host']};port={$dbInfo['port']};dbname={$dbInfo['name']};charset=utf8mb4";
                    $pdo = new PDO($dsn, $dbInfo['user'], $dbInfo['pass'], [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);

                    // 更新管理员密码
                    $hashed = password_hash($adminPass, PASSWORD_DEFAULT);
                    $stmt = $pdo->prepare("UPDATE sys_user SET username = ?, password = ?, nickname = ? WHERE id = 1");
                    $stmt->execute([$adminUser, $hashed, '管理员']);

                    // 更新站点名称
                    $stmt = $pdo->prepare("UPDATE sys_setting SET svalue = ? WHERE skey = 'site_name'");
                    $stmt->execute([$siteName]);

                    // 写入配置文件
                    $configFile = dirname(__DIR__) . '/backend/config/database.php';
                    $configContent = file_get_contents($configFile);
                    $configContent = preg_replace("/define\('DB_HOST',\s*'[^']*'\)/", "define('DB_HOST', '{$dbInfo['host']}')", $configContent);
                    $configContent = preg_replace("/define\('DB_PORT',\s*'[^']*'\)/", "define('DB_PORT', '{$dbInfo['port']}')", $configContent);
                    $configContent = preg_replace("/define\('DB_NAME',\s*'[^']*'\)/", "define('DB_NAME', '{$dbInfo['name']}')", $configContent);
                    $configContent = preg_replace("/define\('DB_USER',\s*'[^']*'\)/", "define('DB_USER', '{$dbInfo['user']}')", $configContent);
                    $configContent = preg_replace("/define\('DB_PASS',\s*'[^']*'\)/", "define('DB_PASS', '{$dbInfo['pass']}')", $configContent);
                    file_put_contents($configFile, $configContent);

                    // 标记安装完成
                    $lockFile = dirname(__DIR__) . '/install.lock';
                    file_put_contents($lockFile, date('Y-m-d H:i:s'));

                    unset($_SESSION['install_db']);
                    header('Location: install.php?step=4');
                    exit;
                } catch (PDOException $e) {
                    $error = '创建管理员失败：' . $e->getMessage();
                }
            }
        }
    }
}

// 检查是否已安装
if (file_exists(__DIR__ . '/../install.lock') && $step !== 4) {
    echo '<!DOCTYPE html><html><head><meta charset="utf-8"><title>已安装</title></head><body style="display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;background:#F5F6FA"><div style="text-align:center"><h2>✅ 系统已安装</h2><p>如需重新安装，请删除 <code>install.lock</code> 文件</p><a href="index.php" style="color:#FF8C42">进入管理后台 →</a></div></body></html>';
    exit;
}

// 环境检测
function checkEnv() {
    $checks = [];
    $checks[] = ['PHP版本 ≥ 7.4', version_compare(PHP_VERSION, '7.4.0', '>='), PHP_VERSION];
    $checks[] = ['PDO MySQL扩展', extension_loaded('pdo_mysql'), extension_loaded('pdo_mysql') ? '已安装' : '未安装'];
    $checks[] = ['JSON扩展', extension_loaded('json'), extension_loaded('json') ? '已安装' : '未安装'];
    $checks[] = ['GD扩展(图片处理)', extension_loaded('gd'), extension_loaded('gd') ? '已安装' : '未安装'];
    $checks[] = ['uploads目录可写', is_writable(dirname(__DIR__) . '/backend/uploads') || is_writable(dirname(__DIR__)), ''];
    return $checks;
}
?>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>安装向导 - 家收纳</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #F5F6FA; min-height: 100vh; display: flex; align-items: center; justify-content: center; }
        .installer { background: #fff; border-radius: 16px; box-shadow: 0 8px 40px rgba(0,0,0,0.12); max-width: 600px; width: 90%; padding: 40px; }
        .header { text-align: center; margin-bottom: 30px; }
        .header h1 { font-size: 24px; color: #FF8C42; margin-bottom: 4px; }
        .header p { color: #999; font-size: 13px; }
        .steps { display: flex; justify-content: center; gap: 8px; margin-bottom: 30px; }
        .step-dot { width: 32px; height: 32px; border-radius: 50%; background: #eee; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; color: #999; }
        .step-dot.active { background: #FF8C42; color: #fff; }
        .step-dot.done { background: #48BB78; color: #fff; }
        .step-line { width: 40px; height: 2px; background: #eee; align-self: center; }
        .step-line.done { background: #48BB78; }
        .form-group { margin-bottom: 16px; }
        .form-label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #2D3436; }
        .form-control { width: 100%; padding: 10px 12px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px; }
        .form-control:focus { outline: none; border-color: #FF8C42; }
        .btn { display: inline-flex; align-items: center; justify-content: center; padding: 12px 24px; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; border: none; transition: all 0.2s; }
        .btn-primary { background: #FF8C42; color: #fff; width: 100%; }
        .btn-primary:hover { background: #e67e3a; }
        .btn-success { background: #48BB78; color: #fff; width: 100%; font-size: 16px; padding: 14px; }
        .error { background: #FEE; color: #F56565; padding: 10px 14px; border-radius: 8px; font-size: 13px; margin-bottom: 16px; }
        .success { background: #E6F9F0; color: #48BB78; padding: 10px 14px; border-radius: 8px; font-size: 13px; margin-bottom: 16px; }
        .check-item { display: flex; align-items: center; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
        .check-item:last-child { border-bottom: none; }
        .check-label { flex: 1; font-size: 14px; }
        .check-status { font-size: 13px; font-weight: 600; }
        .check-status.ok { color: #48BB78; }
        .check-status.fail { color: #F56565; }
        .result-card { text-align: center; padding: 20px; }
        .result-card .icon { font-size: 60px; margin-bottom: 12px; }
        .result-card h2 { margin-bottom: 8px; }
        .result-card p { color: #636E72; margin-bottom: 20px; }
        .info-box { background: #f8f9fa; padding: 14px; border-radius: 8px; font-size: 13px; margin-bottom: 16px; }
        .info-box code { background: #fff; padding: 2px 6px; border-radius: 4px; }
    </style>
</head>
<body>
    <div class="installer">
        <div class="header">
            <h1>🏠 家收纳 安装向导</h1>
            <p>几步完成系统部署</p>
        </div>

        <!-- 步骤指示器 -->
        <div class="steps">
            <div class="step-dot <?= $step >= 1 ? ($step > 1 ? 'done' : 'active') : '' ?>">1</div>
            <div class="step-line <?= $step > 1 ? 'done' : '' ?>"></div>
            <div class="step-dot <?= $step >= 2 ? ($step > 2 ? 'done' : 'active') : '' ?>">2</div>
            <div class="step-line <?= $step > 2 ? 'done' : '' ?>"></div>
            <div class="step-dot <?= $step >= 3 ? ($step > 3 ? 'done' : 'active') : '' ?>">3</div>
            <div class="step-line <?= $step > 3 ? 'done' : '' ?>"></div>
            <div class="step-dot <?= $step >= 4 ? 'done' : '' ?>">4</div>
        </div>

        <?php if ($error): ?><div class="error">⚠ <?= htmlspecialchars($error) ?></div><?php endif; ?>
        <?php if ($success): ?><div class="success">✅ <?= htmlspecialchars($success) ?></div><?php endif; ?>

        <?php if ($step === 1): ?>
        <!-- 步骤1: 环境检测 -->
        <h3 style="margin-bottom: 16px;">📋 环境检测</h3>
        <?php $checks = checkEnv(); $allOk = true; ?>
        <?php foreach ($checks as $check): ?>
        <div class="check-item">
            <span class="check-label"><?= $check[0] ?></span>
            <span class="check-status <?= $check[1] ? 'ok' : 'fail' ?>">
                <?= $check[1] ? '✅ ' . ($check[2] ?: '通过') : '❌ ' . ($check[2] ?: '不通过') ?>
            </span>
        </div>
        <?php if (!$check[1]) $allOk = false; endforeach; ?>

        <?php if ($allOk): ?>
            <div class="success" style="margin-top: 16px;">✅ 环境检测全部通过</div>
            <a href="?step=2" class="btn btn-primary" style="margin-top: 16px; text-decoration: none; display: block; text-align: center;">下一步 →</a>
        <?php else: ?>
            <div class="error" style="margin-top: 16px;">⚠ 请先解决以上环境问题再继续</div>
        <?php endif; ?>

        <?php elseif ($step === 2): ?>
        <!-- 步骤2: 数据库配置 -->
        <h3 style="margin-bottom: 16px;">🗄️ 数据库配置</h3>
        <form method="POST">
            <input type="hidden" name="action" value="test_db">
            <div class="form-group">
                <label class="form-label">数据库主机</label>
                <input type="text" name="db_host" class="form-control" value="localhost">
            </div>
            <div class="form-group">
                <label class="form-label">端口</label>
                <input type="text" name="db_port" class="form-control" value="3306">
            </div>
            <div class="form-group">
                <label class="form-label">数据库名</label>
                <input type="text" name="db_name" class="form-control" value="jia_shouna">
            </div>
            <div class="form-group">
                <label class="form-label">用户名</label>
                <input type="text" name="db_user" class="form-control" value="root">
            </div>
            <div class="form-group">
                <label class="form-label">密码</label>
                <input type="password" name="db_pass" class="form-control" placeholder="如无密码可留空">
            </div>
            <div class="info-box">
                💡 系统会自动创建数据库并导入表结构，无需手动导入SQL
            </div>
            <button type="submit" class="btn btn-primary">测试连接并初始化 →</button>
        </form>

        <?php elseif ($step === 3): ?>
        <!-- 步骤3: 管理员设置 -->
        <h3 style="margin-bottom: 16px;">👤 管理员设置</h3>
        <form method="POST">
            <input type="hidden" name="action" value="create_admin">
            <div class="form-group">
                <label class="form-label">站点名称</label>
                <input type="text" name="site_name" class="form-control" value="家收纳">
            </div>
            <div class="form-group">
                <label class="form-label">管理员账号</label>
                <input type="text" name="admin_user" class="form-control" value="admin">
            </div>
            <div class="form-group">
                <label class="form-label">管理员密码</label>
                <input type="password" name="admin_pass" class="form-control" placeholder="至少6位">
            </div>
            <div class="form-group">
                <label class="form-label">确认密码</label>
                <input type="password" name="admin_pass2" class="form-control" placeholder="再次输入密码">
            </div>
            <button type="submit" class="btn btn-primary">完成安装 →</button>
        </form>

        <?php elseif ($step === 4): ?>
        <!-- 步骤4: 安装完成 -->
        <div class="result-card">
            <div class="icon">🎉</div>
            <h2>安装成功！</h2>
            <p>家收纳系统已部署完成</p>
            <div class="info-box" style="text-align: left;">
                <p><strong>管理后台：</strong><a href="index.php" style="color:#FF8C42">点击进入</a></p>
                <p style="margin-top: 8px;"><strong>API地址：</strong><code>http://<?= $_SERVER['HTTP_HOST'] ?>/backend/api/</code></p>
                <p style="margin-top: 8px;"><strong>安全建议：</strong></p>
                <ul style="padding-left: 20px; margin-top: 4px; color: #636E72;">
                    <li>登录后立即修改管理员密码</li>
                    <li>删除 <code>install.php</code> 文件</li>
                    <li>配置 Nginx 伪静态规则</li>
                </ul>
            </div>
            <div style="display: flex; gap: 12px;">
                <a href="index.php" class="btn btn-success" style="flex: 1; text-decoration: none; display: block; text-align: center;">进入管理后台</a>
                <a href="../" class="btn btn-primary" style="flex: 1; text-decoration: none; display: block; text-align: center; background: #5B9FED;">查看首页</a>
            </div>
        </div>
        <?php endif; ?>
    </div>
</body>
</html>
