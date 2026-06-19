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

<div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;flex-wrap:wrap;gap:12px;">
    <div>
        <div class="page-title" style="font-size:22px;font-weight:700">第三方接口配置中心</div>
        <div class="page-desc" style="color:var(--text-3);font-size:13px;margin-top:4px">管理条码查询、图像识别等第三方服务接口</div>
    </div>
</div>

<?php if (!empty($msg)): ?>
    <div class="alert alert-success" style="margin-bottom:16px;">
        <span class="alert-icon">✅</span>
        <div><?= $msg ?></div>
    </div>
<?php endif; ?>

<!-- 条码查询接口 -->
<div class="card" style="margin-bottom:16px;">
    <div class="card-header">
        <div style="display:flex;align-items:center;gap:12px">
            <div style="width:40px;height:40px;border-radius:10px;background:linear-gradient(135deg,#FF8C42,#FF6B35);display:flex;align-items:center;justify-content:center;font-size:20px;color:#fff">📊</div>
            <div>
                <div class="card-title">条码查询接口</div>
                <div style="font-size:11px;color:var(--text-3);margin-top:2px">管理条码查询服务的接口配置</div>
            </div>
        </div>
    </div>
    <div style="padding:8px 0;">
        <?php foreach ($barcodeApis as $api): ?>
        <form method="POST" style="padding:14px 20px;border-bottom:1px solid var(--border-2);transition:all .15s;<?= $api['is_active'] ? 'background:linear-gradient(90deg,#FFF7F0 0%,#fff 50%);position:relative;padding-left:23px;' : '' ?>">
            <?php if ($api['is_active']): ?><div style="position:absolute;left:0;top:0;bottom:0;width:3px;background:linear-gradient(180deg,#FF8C42,#FF6B6B);border-radius:3px;"></div><?php endif; ?>
            <input type="hidden" name="action" value="save">
            <input type="hidden" name="id" value="<?= $api['id'] ?>">
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">
                <div style="display:flex;align-items:center;gap:8px;">
                    <div style="width:24px;height:24px;border-radius:50%;background:<?= $api['is_active'] ? '#FF8C42' : 'var(--bg)' ?>;color:<?= $api['is_active'] ? '#fff' : 'var(--text-3)' ?>;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:600">1</div>
                    <strong style="font-size:14px;"><?= htmlspecialchars($api['name']) ?></strong>
                    <?php if ($api['is_active']): ?>
                        <span style="font-size:10px;padding:2px 6px;border-radius:4px;background:rgba(255,140,66,.12);color:#C25A1E;font-weight:600">主用</span>
                    <?php else: ?>
                        <span style="font-size:10px;padding:2px 6px;border-radius:4px;background:var(--bg);color:var(--text-3);font-weight:600">备用</span>
                    <?php endif; ?>
                </div>
                <div style="display:flex;gap:8px;align-items:center;">
                    <span style="font-size:11px;color:var(--text-3)">调用 <strong style="color:var(--text)"><?= $api['total_calls'] ?></strong> 次 · 成功率 <strong style="color:var(--success)"><?= $api['total_calls'] > 0 ? round($api['success_calls']/$api['total_calls']*100) : 0 ?>%</strong></span>
                    <label class="switch">
                        <input type="checkbox" name="is_active" <?= $api['is_active'] ? 'checked' : '' ?>>
                        <span class="switch-slider"></span>
                    </label>
                </div>
            </div>
            <div style="display:grid;grid-template-columns:1.4fr 1fr;gap:12px;">
                <div class="form-group" style="margin-bottom:0">
                    <label class="form-label">接口地址</label>
                    <input type="text" name="api_url" class="form-control" value="<?= htmlspecialchars($api['api_url']) ?>">
                </div>
                <div class="form-group" style="margin-bottom:0">
                    <label class="form-label">API Key</label>
                    <input type="text" name="api_key" class="form-control" value="<?= htmlspecialchars($api['api_key']) ?>" placeholder="如无需密钥可留空">
                </div>
            </div>
            <input type="hidden" name="api_secret" value="<?= htmlspecialchars($api['api_secret']) ?>">
            <div style="display:flex;gap:8px;margin-top:12px;">
                <button type="submit" class="btn btn-primary btn-sm">💾 保存</button>
                <button type="submit" formaction="" onclick="this.form.querySelector('[name=action]').value='test'" class="btn btn-outline btn-sm">🔌 测试连通性</button>
            </div>
        </form>
        <?php endforeach; ?>
    </div>
</div>

<!-- 图像识别接口 -->
<div class="card" style="margin-bottom:16px;">
    <div class="card-header">
        <div style="display:flex;align-items:center;gap:12px">
            <div style="width:40px;height:40px;border-radius:10px;background:linear-gradient(135deg,#4ECDC4,#0E9F8E);display:flex;align-items:center;justify-content:center;font-size:20px;color:#fff">🖼️</div>
            <div>
                <div class="card-title">图像识别接口</div>
                <div style="font-size:11px;color:var(--text-3);margin-top:2px">管理图像识别服务的接口配置</div>
            </div>
        </div>
    </div>
    <div style="padding:8px 0;">
        <?php foreach ($imageApis as $api): ?>
        <form method="POST" style="padding:14px 20px;border-bottom:1px solid var(--border-2);transition:all .15s;<?= $api['is_active'] ? 'background:linear-gradient(90deg,#FFF7F0 0%,#fff 50%);position:relative;padding-left:23px;' : '' ?>">
            <?php if ($api['is_active']): ?><div style="position:absolute;left:0;top:0;bottom:0;width:3px;background:linear-gradient(180deg,#4ECDC4,#0E9F8E);border-radius:3px;"></div><?php endif; ?>
            <input type="hidden" name="action" value="save">
            <input type="hidden" name="id" value="<?= $api['id'] ?>">
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">
                <div style="display:flex;align-items:center;gap:8px;">
                    <div style="width:24px;height:24px;border-radius:50%;background:<?= $api['is_active'] ? '#4ECDC4' : 'var(--bg)' ?>;color:<?= $api['is_active'] ? '#fff' : 'var(--text-3)' ?>;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:600">1</div>
                    <strong style="font-size:14px;"><?= htmlspecialchars($api['name']) ?></strong>
                    <?php if ($api['is_active']): ?>
                        <span style="font-size:10px;padding:2px 6px;border-radius:4px;background:rgba(78,205,196,.12);color:#0E9F8E;font-weight:600">主用</span>
                    <?php else: ?>
                        <span style="font-size:10px;padding:2px 6px;border-radius:4px;background:var(--bg);color:var(--text-3);font-weight:600">备用</span>
                    <?php endif; ?>
                </div>
                <label class="switch">
                    <input type="checkbox" name="is_active" <?= $api['is_active'] ? 'checked' : '' ?>>
                    <span class="switch-slider"></span>
                </label>
            </div>
            <div style="display:grid;grid-template-columns:1.4fr 1fr;gap:12px;">
                <div class="form-group" style="margin-bottom:0">
                    <label class="form-label">接口地址</label>
                    <input type="text" name="api_url" class="form-control" value="<?= htmlspecialchars($api['api_url']) ?>">
                </div>
                <div class="form-group" style="margin-bottom:0">
                    <label class="form-label">API Key</label>
                    <input type="text" name="api_key" class="form-control" value="<?= htmlspecialchars($api['api_key']) ?>" placeholder="请输入API Key">
                </div>
            </div>
            <input type="hidden" name="api_secret" value="<?= htmlspecialchars($api['api_secret']) ?>">
            <div style="display:flex;gap:8px;margin-top:12px;">
                <button type="submit" class="btn btn-primary btn-sm">💾 保存</button>
                <button type="submit" formaction="" onclick="this.form.querySelector('[name=action]').value='test'" class="btn btn-outline btn-sm">🔌 测试连通性</button>
            </div>
        </form>
        <?php endforeach; ?>
    </div>
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
                    <td style="font-size:12px;color:var(--text-3)"><?= date('m-d H:i:s', $log['created_at']) ?></td>
                    <td><span class="tag tag-blue"><?= $log['type'] ?></span></td>
                    <td style="max-width:300px;overflow:hidden;text-overflow:ellipsis;font-size:12px;"><?= htmlspecialchars($log['request_url']) ?></td>
                    <td><?= $log['status'] ? '<span class="tag tag-green">✓ 成功</span>' : '<span class="tag tag-red">✗ 失败</span>' ?></td>
                    <td style="font-size:12px;font-family:monospace;"><?= $log['duration'] ?>ms</td>
                </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>
</div>
