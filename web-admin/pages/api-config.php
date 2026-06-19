<?php
$db = getDB();

// 处理POST操作
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    if ($action === 'save') {
        $id = intval($_POST['id'] ?? 0);
        $stmt = $db->prepare("UPDATE api_config SET api_url = ?, api_key = ?, api_secret = ?, is_active = ?, updated_at = ? WHERE id = ?");
        $stmt->execute([$_POST['api_url'], $_POST['api_key'], $_POST['api_secret'], isset($_POST['is_active']) ? 1 : 0, time(), $id]);
        $msg = '保存成功';
    } elseif ($action === 'test') {
        $id = intval($_POST['id'] ?? 0);
        $stmt = $db->prepare("SELECT * FROM api_config WHERE id = ?");
        $stmt->execute([$id]);
        $api = $stmt->fetch();
        // 简单连通性测试
        $ch = curl_init($api['api_url']);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_TIMEOUT, 5);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_exec($ch);
        $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        $msg = $code > 0 ? "连通性测试成功 (HTTP $code)" : "连通性测试失败";
    }
}

// 获取接口列表
$barcodeApis = $db->query("SELECT * FROM api_config WHERE type = 'barcode' ORDER BY priority DESC")->fetchAll();
$imageApis = $db->query("SELECT * FROM api_config WHERE type = 'image' ORDER BY priority DESC")->fetchAll();

// 接口日志
$logs = $db->query("SELECT * FROM api_log ORDER BY created_at DESC LIMIT 20")->fetchAll();
?>

<div class="card-header" style="margin-bottom: 16px;">
    <div>
        <h2 style="font-size: 18px;">第三方接口配置中心</h2>
        <p style="color: #999; font-size: 12px;">管理条码查询、图像识别等第三方服务接口</p>
    </div>
</div>

<?php if (!empty($msg)): ?>
    <div class="toast toast-success" style="position: relative; margin-bottom: 16px;"><?= $msg ?></div>
<?php endif; ?>

<!-- 条码查询接口 -->
<div class="card">
    <div class="card-header">
        <div class="card-title">📊 条码查询接口</div>
    </div>
    <?php foreach ($barcodeApis as $api): ?>
    <form method="POST" style="border: 1px solid #eee; border-radius: 8px; padding: 16px; margin-bottom: 12px; <?= $api['is_active'] ? 'border-left: 3px solid #48BB78;' : '' ?>">
        <input type="hidden" name="action" value="save">
        <input type="hidden" name="id" value="<?= $api['id'] ?>">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
            <div>
                <strong><?= htmlspecialchars($api['name']) ?></strong>
                <?php if ($api['is_active']): ?>
                    <span class="badge badge-success">当前使用</span>
                <?php else: ?>
                    <span class="badge badge-info">未启用</span>
                <?php endif; ?>
                <span style="font-size: 12px; color: #999; margin-left: 8px;">调用 <?= $api['total_calls'] ?> 次 · 成功率 <?= $api['total_calls'] > 0 ? round($api['success_calls']/$api['total_calls']*100) : 0 ?>%</span>
            </div>
            <div style="display: flex; gap: 8px;">
                <label class="switch">
                    <input type="checkbox" name="is_active" <?= $api['is_active'] ? 'checked' : '' ?>>
                    <span class="switch-slider"></span>
                </label>
            </div>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
            <div class="form-group">
                <label class="form-label">接口地址</label>
                <input type="text" name="api_url" class="form-control" value="<?= htmlspecialchars($api['api_url']) ?>">
            </div>
            <div class="form-group">
                <label class="form-label">API Key</label>
                <input type="text" name="api_key" class="form-control" value="<?= htmlspecialchars($api['api_key']) ?>" placeholder="如无需密钥可留空">
            </div>
        </div>
        <input type="hidden" name="api_secret" value="<?= htmlspecialchars($api['api_secret']) ?>">
        <div style="display: flex; gap: 8px;">
            <button type="submit" class="btn btn-primary btn-sm">💾 保存</button>
            <button type="submit" formaction="" onclick="this.form.querySelector('[name=action]').value='test'" class="btn btn-outline btn-sm">🔌 测试连通性</button>
        </div>
    </form>
    <?php endforeach; ?>
</div>

<!-- 图像识别接口 -->
<div class="card">
    <div class="card-header">
        <div class="card-title">🖼️ 图像识别接口</div>
    </div>
    <?php foreach ($imageApis as $api): ?>
    <form method="POST" style="border: 1px solid #eee; border-radius: 8px; padding: 16px; margin-bottom: 12px; <?= $api['is_active'] ? 'border-left: 3px solid #48BB78;' : '' ?>">
        <input type="hidden" name="action" value="save">
        <input type="hidden" name="id" value="<?= $api['id'] ?>">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
            <div>
                <strong><?= htmlspecialchars($api['name']) ?></strong>
                <?php if ($api['is_active']): ?>
                    <span class="badge badge-success">当前使用</span>
                <?php else: ?>
                    <span class="badge badge-info">未启用</span>
                <?php endif; ?>
            </div>
            <label class="switch">
                <input type="checkbox" name="is_active" <?= $api['is_active'] ? 'checked' : '' ?>>
                <span class="switch-slider"></span>
            </label>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px;">
            <div class="form-group">
                <label class="form-label">接口地址</label>
                <input type="text" name="api_url" class="form-control" value="<?= htmlspecialchars($api['api_url']) ?>">
            </div>
            <div class="form-group">
                <label class="form-label">API Key</label>
                <input type="text" name="api_key" class="form-control" value="<?= htmlspecialchars($api['api_key']) ?>" placeholder="请输入API Key">
            </div>
        </div>
        <input type="hidden" name="api_secret" value="<?= htmlspecialchars($api['api_secret']) ?>">
        <div style="display: flex; gap: 8px;">
            <button type="submit" class="btn btn-primary btn-sm">💾 保存</button>
            <button type="submit" formaction="" onclick="this.form.querySelector('[name=action]').value='test'" class="btn btn-outline btn-sm">🔌 测试连通性</button>
        </div>
    </form>
    <?php endforeach; ?>
</div>

<!-- 接口日志 -->
<div class="card">
    <div class="card-header">
        <div class="card-title">📋 接口调用日志</div>
    </div>
    <div class="table-wrapper">
        <table>
            <thead><tr><th>时间</th><th>类型</th><th>接口</th><th>状态</th><th>耗时</th></tr></thead>
            <tbody>
                <?php foreach ($logs as $log): ?>
                <tr>
                    <td><?= date('m-d H:i:s', $log['created_at']) ?></td>
                    <td><span class="badge badge-info"><?= $log['type'] ?></span></td>
                    <td style="max-width: 300px; overflow: hidden; text-overflow: ellipsis;"><?= htmlspecialchars($log['request_url']) ?></td>
                    <td><?= $log['status'] ? '<span class="badge badge-success">成功</span>' : '<span class="badge badge-danger">失败</span>' ?></td>
                    <td><?= $log['duration'] ?>ms</td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>
