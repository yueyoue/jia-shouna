<?php
$db = getDB();

// 处理发布操作
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    if ($action === 'publish') {
        $versionCode = intval($_POST['version_code'] ?? 0);
        $versionName = trim($_POST['version_name'] ?? '');
        $changelog = trim($_POST['changelog'] ?? '');
        $isForce = isset($_POST['is_force']) ? 1 : 0;

        if (!$versionCode || !$versionName) {
            $error = '请填写版本号和版本名';
        } else {
            // 处理APK上传
            $apkPath = '';
            $apkSize = 0;
            if (!empty($_FILES['apk_file']['name'])) {
                $file = $_FILES['apk_file'];
                if ($file['type'] !== 'application/vnd.android.package-archive' && pathinfo($file['name'], PATHINFO_EXTENSION) !== 'apk') {
                    $error = '请上传APK文件';
                } elseif ($file['size'] > APK_MAX_SIZE) {
                    $error = 'APK文件不能超过100MB';
                } else {
                    $apkDir = UPLOAD_PATH . 'apks/';
                    if (!is_dir($apkDir)) mkdir($apkDir, 0755, true);
                    $filename = 'v' . $versionCode . '_' . date('YmdHis') . '.apk';
                    $apkPath = 'apks/' . $filename;
                    if (move_uploaded_file($file['tmp_name'], $apkDir . $filename)) {
                        $apkSize = $file['size'];
                    } else {
                        $error = 'APK上传失败';
                    }
                }
            }

            if (!isset($error)) {
                $now = time();
                if ($apkPath) {
                    $stmt = $db->prepare("INSERT INTO app_version (version_code, version_name, changelog, is_force, apk_path, apk_size, status, published_at, created_at) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)");
                    $stmt->execute([$versionCode, $versionName, $changelog, $isForce, $apkPath, $apkSize, $now, $now]);
                } else {
                    // 没有上传新APK，只更新信息
                    $stmt = $db->prepare("SELECT id FROM app_version WHERE version_code = ?");
                    $stmt->execute([$versionCode]);
                    $existing = $stmt->fetch();
                    if ($existing) {
                        $stmt = $db->prepare("UPDATE app_version SET version_name = ?, changelog = ?, is_force = ?, published_at = ? WHERE id = ?");
                        $stmt->execute([$versionName, $changelog, $isForce, $now, $existing['id']]);
                    } else {
                        $stmt = $db->prepare("INSERT INTO app_version (version_code, version_name, changelog, is_force, apk_path, apk_size, status, published_at, created_at) VALUES (?, ?, ?, ?, '', 0, 1, ?, ?)");
                        $stmt->execute([$versionCode, $versionName, $changelog, $isForce, $now, $now]);
                    }
                }
                $msg = '版本发布成功';
            }
        }
    } elseif ($action === 'toggle') {
        $id = intval($_POST['id'] ?? 0);
        $status = intval($_POST['status'] ?? 1);
        $db->prepare("UPDATE app_version SET status = ? WHERE id = ?")->execute([$status, $id]);
        $msg = $status ? '已上架' : '已下架';
    }
}

$versions = $db->query("SELECT * FROM app_version ORDER BY version_code DESC")->fetchAll();
?>

<div class="card-header" style="margin-bottom: 16px;">
    <div>
        <h2 style="font-size: 18px;">📱 APP版本更新管理</h2>
        <p style="color: #999; font-size: 12px;">发布新版本，用户APP端会自动检测更新</p>
    </div>
</div>

<?php if (!empty($msg)): ?>
    <div class="toast toast-success" style="position: relative; margin-bottom: 16px;"><?= $msg ?></div>
<?php endif; ?>
<?php if (!empty($error)): ?>
    <div class="toast toast-error" style="position: relative; margin-bottom: 16px;"><?= $error ?></div>
<?php endif; ?>

<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
    <!-- 发布新版本 -->
    <div class="card">
        <div class="card-title" style="margin-bottom: 16px;">🚀 发布新版本</div>
        <form method="POST" enctype="multipart/form-data">
            <input type="hidden" name="action" value="publish">
            <div class="form-group">
                <label class="form-label">版本号 (versionCode) *</label>
                <input type="number" name="version_code" class="form-control" placeholder="例: 101" required>
                <small style="color: #999;">纯数字，每次递增，用于判断是否需要更新</small>
            </div>
            <div class="form-group">
                <label class="form-label">版本名 (versionName) *</label>
                <input type="text" name="version_name" class="form-control" placeholder="例: 1.0.1" required>
            </div>
            <div class="form-group">
                <label class="form-label">更新日志</label>
                <textarea name="changelog" class="form-control" rows="5" placeholder="请输入本次更新内容，每行一条&#10;例:&#10;- 修复了扫码闪退的问题&#10;- 新增离线模式支持&#10;- 优化了同步速度"></textarea>
            </div>
            <div class="form-group">
                <label class="form-label">APK文件</label>
                <input type="file" name="apk_file" class="form-control" accept=".apk">
                <small style="color: #999;">最大100MB，如仅更新版本信息可不上传</small>
            </div>
            <div class="form-group">
                <label class="form-label">强制更新</label>
                <label class="switch">
                    <input type="checkbox" name="is_force" value="1">
                    <span class="switch-slider"></span>
                </label>
                <span style="font-size: 12px; color: #999; margin-left: 8px;">开启后用户必须更新才能使用APP</span>
            </div>
            <button type="submit" class="btn btn-primary btn-lg" style="width: 100%;">🚀 发布版本</button>
        </form>
    </div>

    <!-- API接口说明 -->
    <div class="card">
        <div class="card-title" style="margin-bottom: 16px;">📡 接口说明</div>
        <div style="background: #f8f9fa; padding: 16px; border-radius: 8px; font-size: 13px; line-height: 1.8;">
            <p><strong>APP检查更新接口：</strong></p>
            <code style="display: block; background: #fff; padding: 8px; border-radius: 4px; margin: 8px 0;">GET /backend/api/version.php?action=check&version_code={当前版本号}</code>
            <p><strong>返回字段：</strong></p>
            <ul style="padding-left: 20px; color: #636E72;">
                <li>has_update - 是否有更新</li>
                <li>latest.version_code - 最新版本号</li>
                <li>latest.version_name - 最新版本名</li>
                <li>latest.changelog - 更新日志</li>
                <li>latest.is_force - 是否强制更新</li>
                <li>latest.apk_url - APK下载地址</li>
            </ul>
            <hr style="margin: 12px 0; border: none; border-top: 1px solid #ddd;">
            <p><strong>使用流程：</strong></p>
            <ol style="padding-left: 20px; color: #636E72;">
                <li>在此页面上传新版本APK并发布</li>
                <li>APP启动时调用检查更新接口</li>
                <li>如有新版本，弹窗提示用户下载更新</li>
                <li>强制更新时弹窗不可取消</li>
            </ol>
        </div>
    </div>
</div>

<!-- 版本列表 -->
<div class="card" style="margin-top: 20px;">
    <div class="card-header">
        <div class="card-title">📋 版本历史</div>
    </div>
    <?php if (empty($versions)): ?>
        <div class="empty-state">
            <div class="empty-icon">📱</div>
            <div class="empty-text">暂无版本，请发布第一个版本</div>
        </div>
    <?php else: ?>
        <div class="table-wrapper">
            <table>
                <thead><tr><th>版本号</th><th>版本名</th><th>更新日志</th><th>强制</th><th>APK大小</th><th>下载次数</th><th>状态</th><th>发布时间</th><th>操作</th></tr></thead>
                <tbody>
                    <?php foreach ($versions as $v): ?>
                    <tr>
                        <td><strong>v<?= $v['version_code'] ?></strong></td>
                        <td><?= htmlspecialchars($v['version_name']) ?></td>
                        <td style="max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: pre-line;"><?= htmlspecialchars($v['changelog']) ?></td>
                        <td><?= $v['is_force'] ? '<span class="badge badge-danger">强制</span>' : '<span class="badge badge-info">可选</span>' ?></td>
                        <td><?= $v['apk_size'] ? formatSize($v['apk_size']) : '-' ?></td>
                        <td><?= $v['download_count'] ?></td>
                        <td><?= $v['status'] ? '<span class="badge badge-success">已发布</span>' : '<span class="badge badge-info">已下架</span>' ?></td>
                        <td><?= date('Y-m-d H:i', $v['published_at']) ?></td>
                        <td>
                            <?php if ($v['apk_path']): ?>
                                <a href="../backend/uploads/<?= $v['apk_path'] ?>" class="btn btn-sm btn-outline" download>📥 下载</a>
                            <?php endif; ?>
                            <form method="POST" style="display: inline;">
                                <input type="hidden" name="action" value="toggle">
                                <input type="hidden" name="id" value="<?= $v['id'] ?>">
                                <input type="hidden" name="status" value="<?= $v['status'] ? 0 : 1 ?>">
                                <button type="submit" class="btn btn-sm btn-outline"><?= $v['status'] ? '下架' : '上架' ?></button>
                            </form>
                        </td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    <?php endif; ?>
</div>
