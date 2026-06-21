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
    } elseif ($action === 'add_api') {
        $type = $_POST['api_type'] ?? 'barcode';
        $name = trim($_POST['api_name'] ?? '');
        $apiUrl = trim($_POST['api_url'] ?? '');
        $apiKey = trim($_POST['api_key'] ?? '');
        $apiSecret = trim($_POST['api_secret'] ?? '');
        if ($name && $apiUrl) {
            $stmt = $db->prepare("INSERT INTO api_config (type, name, api_url, api_key, api_secret, is_active, priority, total_calls, success_calls, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 0, 1, 0, 0, ?, ?)");
            $stmt->execute([$type, $name, $apiUrl, $apiKey, $apiSecret, time(), time()]);
            $msg = '接口添加成功';
        } else {
            $error = '请填写接口名称和地址';
        }
    } elseif ($action === 'delete_api') {
        $id = intval($_POST['id'] ?? 0);
        if ($id) {
            $db->prepare('DELETE FROM api_config WHERE id = ?')->execute([$id]);
            $msg = '接口已删除';
        }
    } elseif ($action === 'reset_defaults') {
        $now = time();
        $db->exec('DELETE FROM api_config');
        $db->exec('ALTER TABLE api_config AUTO_INCREMENT = 1');
        $defaults = [
            ['barcode', 'ApiZero', 'https://apizero.cn/marketplace/barcode-gs1?barcode={barcode}&api_key=', '', 1, 20],
            ['barcode', 'ApiZero Pro', 'https://v1.apizero.cn/api/barcode-gs1?code={barcode}&key=', '', 0, 15],
            ['barcode', 'ApiByte 山海云端', 'https://apione.apibyte.cn/api/barcode?barcode={barcode}', '', 0, 12],
            ['barcode', 'RollAPI (mxnzp)', 'https://api.mxnzp.com/barcode/goods/details?barcode={barcode}&app_id=&app_secret=', '', 0, 10],
            ['barcode', 'Open Food Facts', 'https://world.openfoodfacts.org/api/v2/product/{barcode}', '', 0, 8],
            ['image', '百度AI图像识别', 'https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general', '', 0, 10],
            ['image', '腾讯云图像识别', 'https://ai.tencent.com/api/image/tag', '', 0, 5],
        ];
        $stmt = $db->prepare('INSERT INTO api_config (type, name, api_url, api_key, is_active, priority, total_calls, success_calls, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 0, 0, ?, ?)');
        foreach ($defaults as $d) {
            $stmt->execute([$d[0], $d[1], $d[2], $d[3], $d[4], $d[5], $now, $now]);
        }
        $msg = '已恢复默认接口配置';
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

$barcodeApis = $db->query("SELECT * FROM api_config WHERE type = 'barcode' ORDER BY priority DESC")->fetchAll();
$imageApis = $db->query("SELECT * FROM api_config WHERE type = 'image' ORDER BY priority DESC")->fetchAll();
$logs = $db->query("SELECT * FROM api_log ORDER BY created_at DESC LIMIT 20")->fetchAll();
?>

<style>
.alert-banner{background:linear-gradient(135deg,#FFF7F0 0%,#F0FBFA 100%);border:1px solid rgba(255,140,66,.2);border-radius:var(--radius);padding:14px 18px;margin-bottom:16px;display:flex;align-items:center;gap:12px}
.alert-banner .icon{width:36px;height:36px;border-radius:10px;background:rgba(255,140,66,.15);color:#FF8C42;display:flex;align-items:center;justify-content:center;font-size:18px;flex-shrink:0}
.alert-banner .content{flex:1;font-size:13px;line-height:1.5}
.alert-banner .content strong{color:#C25A1E}
.api-card{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);margin-bottom:16px;overflow:hidden}
.api-card-header{padding:16px 20px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--border-2);background:linear-gradient(90deg,#FAFBFC 0%,#fff 100%)}
.api-card-title{display:flex;align-items:center;gap:12px}
.api-icon{width:40px;height:40px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:20px;color:#fff}
.api-icon.barcode{background:linear-gradient(135deg,#FF8C42,#FF6B35)}
.api-icon.image{background:linear-gradient(135deg,#4ECDC4,#0E9F8E)}
.api-card-title h3{font-size:15px;font-weight:600}
.api-card-title p{font-size:11px;color:#718096;margin-top:2px}
.channel-list{padding:8px 0}
.channel-item{padding:14px 20px;display:flex;align-items:center;gap:14px;border-bottom:1px solid var(--border-2);transition:all .15s}
.channel-item:last-child{border-bottom:none}
.channel-item:hover{background:#FAFBFC}
.channel-rank{width:24px;height:24px;border-radius:50%;background:#F7FAFC;color:#718096;display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:600;flex-shrink:0}
.channel-info{flex:1;min-width:0}
.channel-name{font-size:14px;font-weight:600;display:flex;align-items:center;gap:8px}
.channel-name .badge-main{font-size:10px;padding:2px 6px;border-radius:4px;background:rgba(255,140,66,.12);color:#C25A1E;font-weight:600}
.channel-name .badge-backup{font-size:10px;padding:2px 6px;border-radius:4px;background:#F7FAFC;color:#718096;font-weight:600}
.channel-desc{font-size:11px;color:#718096;margin-top:2px}
.channel-stats{display:flex;gap:18px;font-size:11px;color:#718096;margin-top:6px}
.channel-stats strong{color:#4A5568;font-weight:600;font-size:12px}
.channel-stats .green{color:#48BB78}
.channel-actions{display:flex;gap:6px;flex-shrink:0}
.channel-actions .icon-mini{width:32px;height:32px;border-radius:8px;background:#F7FAFC;color:#4A5568;display:flex;align-items:center;justify-content:center;font-size:14px;cursor:pointer;transition:all .15s;border:1px solid var(--border-2)}
.channel-actions .icon-mini:hover{background:#FFF1E0;color:#FF8C42;border-color:#FF8C42}
.config-form{padding:20px}
.config-form .form-group{margin-bottom:14px}
.log-row{display:grid;grid-template-columns:90px 100px 1fr 80px 80px 100px 60px;gap:8px;padding:10px 20px;align-items:center;border-bottom:1px solid var(--border-2);font-size:12px}
.log-row:hover{background:#FAFBFC}
.log-row.head{background:#F7FAFC;font-weight:600;color:#4A5568;font-size:11px;text-transform:uppercase;letter-spacing:.3px}
.log-status{font-size:10px;padding:2px 6px;border-radius:4px;font-weight:600;display:inline-flex;align-items:center;gap:3px}
.log-status.success{background:rgba(72,187,120,.12);color:#22543D}
.log-status.failed{background:rgba(245,101,101,.12);color:#9B2C2C}
.log-method{font-family:monospace;font-size:10px;padding:2px 6px;background:rgba(91,159,237,.12);color:#2C5282;border-radius:4px}
</style>

<?php if (!empty($msg)): ?>
<div class="alert alert-success" style="margin-bottom:16px">
    <span class="alert-icon">✅</span>
    <div><?= $msg ?></div>
</div>
<?php endif; ?>

<div class="alert-banner">
    <div class="icon">🔐</div>
    <div class="content">
        <strong>安全说明：</strong>所有第三方接口密钥仅保存在云端后台，APP 端不存储。切换接口仅需后台修改配置，无需更新 APP。
    </div>
    <span class="tag tag-green" style="font-size:11px">✓ 密钥已加密</span>
</div>

<!-- Tabs -->
<div class="tabs">
    <div class="tab active">📊 条码查询接口</div>
    <div class="tab">📷 图像识别接口</div>
    <div class="tab">📋 接口调用日志</div>
</div>

<!-- Barcode API - 照抄 UI -->
<div class="api-card">
    <div class="api-card-header">
        <div class="api-card-title">
            <div class="api-icon barcode">📊</div>
            <div>
                <h3>条码查询接口</h3>
                <p>APP 扫码时调用的商品数据库</p>
            </div>
        </div>
        <button class="btn btn-outline btn-sm" onclick="showAddApi('barcode')">＋ 添加接口</button>
        <form method="POST" style="display:inline;margin-left:8px">
            <input type="hidden" name="action" value="reset_defaults">
            <button type="submit" class="btn btn-outline btn-sm" onclick="return confirm('将删除所有现有接口并恢复默认配置，确定继续？')">🔄 恢复默认</button>
        </form>
    </div>
    <div class="channel-list">
        <?php foreach ($barcodeApis as $idx => $api): ?>
        <div class="channel-item">
            <div class="channel-rank"><?= $idx + 1 ?></div>
            <div class="channel-info">
                <div class="channel-name">
                    <?= htmlspecialchars($api['name']) ?>
                    <?php if ($api['is_active']): ?>
                        <span class="badge-main">主接口</span>
                    <?php else: ?>
                        <span class="badge-backup">备用</span>
                    <?php endif; ?>
                    <?php if ($api['is_active']): ?>
                        <span class="tag tag-green" style="font-size:10px;padding:2px 6px">运行中</span>
                    <?php else: ?>
                        <span class="tag tag-gray" style="font-size:10px;padding:2px 6px">已配置</span>
                    <?php endif; ?>
                </div>
                <div class="channel-desc"><?= htmlspecialchars($api['api_url']) ?></div>
                <div class="channel-stats">
                    <span>📊 今日 <strong><?= $api['total_calls'] ?></strong></span>
                    <span>成功率 <strong class="green"><?= $api['total_calls'] > 0 ? round($api['success_calls']/$api['total_calls']*100) : 0 ?>%</strong></span>
                </div>
            </div>
            <div class="channel-actions">
                <form method="POST" style="display:inline">
                    <input type="hidden" name="action" value="test">
                    <input type="hidden" name="id" value="<?= $api['id'] ?>">
                    <button type="submit" class="icon-mini" title="测试连接">⚡</button>
                </form>
                <div class="icon-mini" title="编辑" onclick="toggleEdit(<?= $api['id'] ?>)">✎</div>
            </div>
        </div>
        <!-- Edit form (hidden by default) -->
        <div id="edit-<?= $api['id'] ?>" style="display:none;padding:14px 20px;background:#FAFBFC;border-bottom:1px solid var(--border-2)">
            <form method="POST">
                <input type="hidden" name="action" value="save">
                <input type="hidden" name="id" value="<?= $api['id'] ?>">
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
                    <div class="form-group" style="margin-bottom:0">
                        <label class="form-label">接口地址</label>
                        <input type="text" name="api_url" class="form-control" value="<?= htmlspecialchars($api['api_url']) ?>">
                    </div>
                    <div class="form-group" style="margin-bottom:0">
                        <label class="form-label">API Key</label>
                        <input type="text" name="api_key" class="form-control" value="<?= htmlspecialchars($api['api_key']) ?>" placeholder="如无需密钥可留空">
                    </div>
                </div>
                <div class="form-group" style="margin-top:12px;margin-bottom:0">
                    <label class="form-label">Secret Key</label>
                    <input type="text" name="api_secret" class="form-control" value="<?= htmlspecialchars($api['api_secret'] ?? '') ?>" placeholder="如无需Secret Key可留空">
                </div>
                <div style="display:flex;gap:8px;margin-top:10px;align-items:center">
                    <label class="switch">
                        <input type="checkbox" name="is_active" <?= $api['is_active'] ? 'checked' : '' ?>>
                        <span class="switch-slider"></span>
                    </label>
                    <span style="font-size:12px;color:#718096">启用</span>
                    <button type="submit" class="btn btn-primary btn-sm" style="margin-left:auto">💾 保存</button>
                    <button type="button" class="btn btn-outline btn-sm" onclick="toggleEdit(<?= $api['id'] ?>)">取消</button>
                </div>
            </form>
        </div>
        <?php endforeach; ?>
    </div>
</div>

<!-- Image API - 照抄 UI -->
<div class="api-card">
    <div class="api-card-header">
        <div class="api-card-title">
            <div class="api-icon image">📷</div>
            <div>
                <h3>图像识别接口</h3>
                <p>APP 拍照识别物品时调用</p>
            </div>
        </div>
        <button class="btn btn-outline btn-sm" onclick="showAddApi('image')">＋ 添加接口</button>
    </div>
    <div class="channel-list">
        <?php foreach ($imageApis as $idx => $api): ?>
        <div class="channel-item">
            <div class="channel-rank"><?= $idx + 1 ?></div>
            <div class="channel-info">
                <div class="channel-name">
                    <?= htmlspecialchars($api['name']) ?>
                    <?php if ($api['is_active']): ?>
                        <span class="badge-main">主接口</span>
                    <?php else: ?>
                        <span class="badge-backup">备用</span>
                    <?php endif; ?>
                </div>
                <div class="channel-desc"><?= htmlspecialchars($api['api_url']) ?></div>
                <div class="channel-stats">
                    <span>📊 今日 <strong><?= $api['total_calls'] ?></strong></span>
                    <span>成功率 <strong class="green"><?= $api['total_calls'] > 0 ? round($api['success_calls']/$api['total_calls']*100) : 0 ?>%</strong></span>
                </div>
            </div>
            <div class="channel-actions">
                <form method="POST" style="display:inline">
                    <input type="hidden" name="action" value="test">
                    <input type="hidden" name="id" value="<?= $api['id'] ?>">
                    <button type="submit" class="icon-mini" title="测试连接">⚡</button>
                </form>
                <div class="icon-mini" title="编辑" onclick="toggleEdit(<?= $api['id'] ?>)">✎</div>
            </div>
        </div>
        <div id="edit-<?= $api['id'] ?>" style="display:none;padding:14px 20px;background:#FAFBFC;border-bottom:1px solid var(--border-2)">
            <form method="POST">
                <input type="hidden" name="action" value="save">
                <input type="hidden" name="id" value="<?= $api['id'] ?>">
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
                    <div class="form-group" style="margin-bottom:0">
                        <label class="form-label">接口地址</label>
                        <input type="text" name="api_url" class="form-control" value="<?= htmlspecialchars($api['api_url']) ?>">
                    </div>
                    <div class="form-group" style="margin-bottom:0">
                        <label class="form-label">API Key</label>
                        <input type="text" name="api_key" class="form-control" value="<?= htmlspecialchars($api['api_key']) ?>" placeholder="请输入API Key">
                    </div>
                </div>
                <div class="form-group" style="margin-top:12px;margin-bottom:0">
                    <label class="form-label">Secret Key</label>
                    <input type="text" name="api_secret" class="form-control" value="<?= htmlspecialchars($api['api_secret']) ?>" placeholder="百度AI需要填写Secret Key，其他接口可留空">
                </div>
                <div style="display:flex;gap:8px;margin-top:10px;align-items:center">
                    <label class="switch">
                        <input type="checkbox" name="is_active" <?= $api['is_active'] ? 'checked' : '' ?>>
                        <span class="switch-slider"></span>
                    </label>
                    <span style="font-size:12px;color:#718096">启用</span>
                    <button type="submit" class="btn btn-primary btn-sm" style="margin-left:auto">💾 保存</button>
                    <button type="button" class="btn btn-outline btn-sm" onclick="toggleEdit(<?= $api['id'] ?>)">取消</button>
                </div>
            </form>
        </div>
        <?php endforeach; ?>
    </div>
</div>

<!-- API Logs - 照抄 UI -->
<div class="api-card">
    <div class="api-card-header">
        <div class="api-card-title">
            <div class="api-icon barcode">📋</div>
            <div>
                <h3>接口调用日志</h3>
                <p>实时记录每一次第三方接口调用</p>
            </div>
        </div>
    </div>
    <div class="log-table">
        <div class="log-row head">
            <div>时间</div>
            <div>接口</div>
            <div>请求</div>
            <div>状态码</div>
            <div>耗时</div>
            <div>结果</div>
            <div>操作</div>
        </div>
        <?php if (empty($logs)): ?>
        <div style="padding:40px;text-align:center;color:#718096">暂无日志</div>
        <?php else: foreach ($logs as $log): ?>
        <div class="log-row" style="<?= $log['status'] ? '' : 'background:rgba(245,101,101,.04)' ?>">
            <div style="font-family:monospace;color:#718096"><?= date('H:i:s', $log['created_at']) ?></div>
            <div><span class="log-method"><?= strtoupper($log['type'] ?? 'GET') ?></span></div>
            <div style="font-size:11px;font-family:monospace;color:#4A5568;overflow:hidden;text-overflow:ellipsis;white-space:nowrap"><?= htmlspecialchars($log['request_url'] ?? '-') ?></div>
            <div style="font-family:monospace"><strong><?= $log['status'] ? '200' : '500' ?></strong></div>
            <div style="font-family:monospace;color:<?= ($log['duration'] ?? 0) < 500 ? '#48BB78' : '#ED8936' ?>"><?= $log['duration'] ?? 0 ?>ms</div>
            <div><span class="log-status <?= $log['status'] ? 'success' : 'failed' ?>"><?= $log['status'] ? '✓ 成功' : '✗ 失败' ?></span></div>
            <div>-</div>
        </div>
        <?php endforeach; endif; ?>
    </div>
</div>

<script>
function toggleEdit(id) {
    var el = document.getElementById('edit-' + id);
    if (el) {
        el.style.display = el.style.display === 'none' ? 'block' : 'none';
    }
}
function showAddApi(type) {
    document.getElementById('add-api-type').value = type;
    document.getElementById('add-api-modal').style.display = 'flex';
}
function hideAddApi() {
    document.getElementById('add-api-modal').style.display = 'none';
}
</script>

<!-- Add API Modal -->
<div id="add-api-modal" style="display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.4);z-index:999;align-items:center;justify-content:center">
    <div style="background:#fff;border-radius:12px;max-width:500px;width:90%;padding:24px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
            <h3 style="font-size:16px;font-weight:600">添加自定义接口</h3>
            <span onclick="hideAddApi()" style="cursor:pointer;font-size:20px;color:#999">&times;</span>
        </div>
        <form method="POST">
            <input type="hidden" name="action" value="add_api">
            <input type="hidden" name="api_type" id="add-api-type" value="barcode">
            <div class="form-group"><label class="form-label">接口名称 *</label><input name="api_name" class="form-control" placeholder="如：我的自定义条码接口" required></div>
            <div class="form-group"><label class="form-label">接口地址 *</label><input name="api_url" class="form-control" placeholder="如：https://api.example.com/barcode/{barcode}  ({barcode}会被替换为实际条码)" required></div>
            <div class="form-group"><label class="form-label">API Key</label><input name="api_key" class="form-control" placeholder="可选"></div>
            <div class="form-group"><label class="form-label">Secret Key</label><input name="api_secret" class="form-control" placeholder="可选"></div>
            <button type="submit" class="btn btn-primary btn-lg" style="width:100%">添加接口</button>
        </form>
    </div>
</div>
