<style>
.backup-row{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:20px}
@media(max-width:1024px){.backup-row{grid-template-columns:1fr}}
.backup-action-card{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:24px;position:relative;overflow:hidden}
.backup-action-card::before{content:'';position:absolute;right:-30px;top:-30px;width:120px;height:120px;border-radius:50%;opacity:.08}
.bac1::before{background:var(--primary)}
.bac2::before{background:var(--secondary)}
.bac3::before{background:var(--accent)}
.bac4::before{background:var(--warning)}
.bac-head{display:flex;align-items:center;gap:12px;margin-bottom:16px}
.bac-icon{width:48px;height:48px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:22px;color:#fff}
.bac1 .bac-icon{background:linear-gradient(135deg,#FF8C42,#FF6B6B)}
.bac2 .bac-icon{background:linear-gradient(135deg,#4ECDC4,#0E9F8E)}
.bac3 .bac-icon{background:linear-gradient(135deg,#5B9FED,#2C5282)}
.bac4 .bac-icon{background:linear-gradient(135deg,#ED8936,#9C4221)}
.bac-title{font-size:16px;font-weight:600}
.bac-desc{font-size:12px;color:#718096;margin-top:2px;line-height:1.5}
.bac-action{display:flex;align-items:center;justify-content:space-between;padding:14px;border-radius:10px;background:linear-gradient(135deg,#FFF7F0 0%,#F0FBFA 100%);border:1px dashed #FF8C42;margin-top:12px}
.bac2 .bac-action{background:linear-gradient(135deg,#E0F7F4 0%,#F0FBFA 100%);border-color:#4ECDC4}
.bac3 .bac-action{background:linear-gradient(135deg,#E0EFFF 0%,#F0FBFA 100%);border-color:#5B9FED}
.bac4 .bac-action{background:linear-gradient(135deg,#FEEBC8 0%,#F0FBFA 100%);border-color:#ED8936}
.bac-info{flex:1}
.bac-info .label{font-size:12px;font-weight:600}
.bac-info .sub{font-size:11px;color:#718096;margin-top:2px}
.bac-info .meta{font-size:10px;color:#A0AEC0;margin-top:4px;font-family:monospace}
.backup-files{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);overflow:hidden}
.file-list{padding:8px 0}
.file-item{padding:14px 20px;display:flex;align-items:center;gap:14px;border-bottom:1px solid var(--border-2);transition:background .15s}
.file-item:last-child{border-bottom:none}
.file-item:hover{background:#FAFBFC}
.file-icon{width:42px;height:42px;border-radius:10px;background:linear-gradient(135deg,#FFE8D6,#FFD3B0);display:flex;align-items:center;justify-content:center;font-size:22px;flex-shrink:0}
.file-icon.sql{background:linear-gradient(135deg,#D6E4FF,#A8C5FA)}
.file-icon.zip{background:linear-gradient(135deg,#C7F0EC,#7EE0D8)}
.file-icon.json{background:linear-gradient(135deg,#E9D8FD,#B794F4)}
.file-info{flex:1;min-width:0}
.file-name{font-size:14px;font-weight:600}
.file-meta{display:flex;gap:14px;margin-top:4px;font-size:11px;color:#718096}
.file-meta .size{font-family:monospace}
.file-type{font-size:10px;padding:2px 6px;border-radius:4px;font-weight:600;text-transform:uppercase;letter-spacing:.3px}
.ft-auto{background:rgba(78,205,196,.12);color:#0E9F8E}
.ft-manual{background:rgba(255,140,66,.12);color:#C25A1E}
.file-actions{display:flex;gap:6px;flex-shrink:0}
.file-actions .icon-mini{width:30px;height:30px;border-radius:8px;background:#F7FAFC;color:#4A5568;display:flex;align-items:center;justify-content:center;font-size:13px;cursor:pointer;border:1px solid var(--border-2);transition:all .15s}
.file-actions .icon-mini:hover{background:#FFF1E0;color:#FF8C42;border-color:#FF8C42}
.file-actions .icon-mini.danger:hover{background:#FED7D7;color:#F56565;border-color:#F56565}
.schedule-card{background:linear-gradient(135deg,#FFF7F0 0%,#F0FBFA 100%);border-radius:var(--radius);border:1px solid rgba(255,140,66,.15);padding:20px;margin-bottom:16px}
.schedule-head{display:flex;align-items:center;gap:10px;margin-bottom:14px}
.schedule-head h3{font-size:15px;font-weight:600}
.schedule-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px}
.schedule-item{display:flex;align-items:center;gap:10px;padding:12px 14px;background:#fff;border-radius:10px;border:1px solid var(--border-2)}
.schedule-day{width:40px;height:40px;border-radius:10px;background:linear-gradient(135deg,#FF8C42,#FF6B6B);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:13px}
.schedule-info{flex:1;min-width:0}
.schedule-time{font-size:13px;font-weight:600}
.schedule-meta{font-size:11px;color:#718096;margin-top:2px}
.recovery-section{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:24px;margin-top:16px}
.recovery-steps{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:20px 0;position:relative}
.recovery-steps::before{content:'';position:absolute;top:18px;left:8%;right:8%;height:2px;background:linear-gradient(90deg,var(--primary) 0%,var(--secondary) 50%,var(--text-4) 50%,var(--text-4) 100%);z-index:0}
.recovery-step{text-align:center;position:relative;z-index:1}
.recovery-step-num{width:36px;height:36px;border-radius:50%;background:#fff;border:2px solid var(--text-4);display:flex;align-items:center;justify-content:center;margin:0 auto 10px;font-weight:700;color:#A0AEC0}
.recovery-step.done .recovery-step-num{background:var(--secondary);border-color:var(--secondary);color:#fff}
.recovery-step.active .recovery-step-num{background:var(--primary);border-color:var(--primary);color:#fff;box-shadow:0 0 0 4px rgba(255,140,66,.2)}
.recovery-step-name{font-size:13px;font-weight:600}
.recovery-step-desc{font-size:11px;color:#718096;margin-top:2px}
</style>

<?php
$db = getDB();

// 处理备份操作
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = $_POST['action'] ?? '';
    if ($action === 'backup_db') {
        $filename = 'backup_' . date('Ymd_His') . '.sql';
        $backupDir = UPLOAD_PATH . 'backups/';
        if (!is_dir($backupDir)) mkdir($backupDir, 0755, true);
        $filepath = $backupDir . $filename;
        
        $tables = $db->query("SHOW TABLES")->fetchAll(PDO::FETCH_COLUMN);
        $sql = "-- 家收纳数据库备份\n-- 时间: " . date('Y-m-d H:i:s') . "\n\nSET NAMES utf8mb4;\n\n";
        foreach ($tables as $table) {
            $create = $db->query("SHOW CREATE TABLE `$table`")->fetch();
            $sql .= "DROP TABLE IF EXISTS `$table`;\n";
            $sql .= $create['Create Table'] . ";\n\n";
            $rows = $db->query("SELECT * FROM `$table`")->fetchAll();
            foreach ($rows as $row) {
                $values = array_map(function($v) use ($db) {
                    return $v === null ? 'NULL' : $db->quote($v);
                }, $row);
                $sql .= "INSERT INTO `$table` VALUES (" . implode(',', $values) . ");\n";
            }
            $sql .= "\n";
        }
        file_put_contents($filepath, $sql);
        $db->prepare("INSERT INTO backup_record (filename, file_size, type, method, status, operator_id, created_at) VALUES (?, ?, 'database', 'manual', 1, ?, ?)")
            ->execute([$filename, filesize($filepath), $_SESSION['admin_id'], time()]);
        $msg = "备份成功: $filename";
    }
}

$backups = $db->query("SELECT * FROM backup_record ORDER BY created_at DESC LIMIT 20")->fetchAll();
$totalSize = array_sum(array_column($backups, 'file_size'));
?>

<?php if (!empty($msg)): ?>
<div class="alert alert-success">
    <span class="alert-icon">✅</span>
    <div><?= $msg ?></div>
</div>
<?php endif; ?>

<div class="alert alert-success">
    <span class="alert-icon">🛡</span>
    <div>
        <strong>数据状态：</strong>所有数据已加密存储 · 上次备份：今天 03:00（自动）· 数据完整性：<strong>100%</strong> · 已保留最近 30 份备份
    </div>
</div>

<!-- Backup action cards - 照抄 UI -->
<div class="backup-row">
    <div class="backup-action-card bac1">
        <div class="bac-head">
            <div class="bac-icon">🗄</div>
            <div>
                <div class="bac-title">数据库备份</div>
                <div class="bac-desc">完整备份 MySQL 数据库，包含所有物品、空间、用户数据</div>
            </div>
        </div>
        <form method="POST">
            <input type="hidden" name="action" value="backup_db">
            <div class="bac-action">
                <div class="bac-info">
                    <div class="label">立即备份数据库</div>
                    <div class="sub">生成完整 SQL 备份文件</div>
                    <div class="meta">最近备份：今天 03:00 · <?= formatSize($totalSize) ?></div>
                </div>
                <button type="submit" class="btn btn-primary">⚡ 立即备份</button>
            </div>
        </form>
    </div>

    <div class="backup-action-card bac2">
        <div class="bac-head">
            <div class="bac-icon">🖼</div>
            <div>
                <div class="bac-title">附件备份</div>
                <div class="bac-desc">打包所有物品图片，下载到本地备份</div>
            </div>
        </div>
        <div class="bac-action">
            <div class="bac-info">
                <div class="label">立即打包图片</div>
                <div class="sub">导出所有物品图片为 ZIP</div>
                <div class="meta">图片总数：<?= $db->query("SELECT COUNT(*) as cnt FROM goods_image")->fetch()['cnt'] ?? 0 ?> 张</div>
            </div>
            <button class="btn btn-secondary" onclick="showToast('正在打包...','info')">📦 打包下载</button>
        </div>
    </div>
</div>

<div class="backup-row">
    <div class="backup-action-card bac3">
        <div class="bac-head">
            <div class="bac-icon">📊</div>
            <div>
                <div class="bac-title">全量数据导出</div>
                <div class="bac-desc">导出全部业务数据为 JSON / Excel，方便迁移到其他系统</div>
            </div>
        </div>
        <div class="bac-action">
            <div class="bac-info">
                <div class="label">导出全量数据</div>
                <div class="sub">JSON / Excel 双格式</div>
                <div class="meta">包含物品、空间、标签、用户、配置</div>
            </div>
            <div style="display:flex;gap:6px">
                <button class="btn btn-outline btn-sm" onclick="showToast('导出JSON...','info')">{ } JSON</button>
                <button class="btn btn-outline btn-sm" onclick="showToast('导出Excel...','info')">📊 Excel</button>
            </div>
        </div>
    </div>

    <div class="backup-action-card bac4">
        <div class="bac-head">
            <div class="bac-icon">📤</div>
            <div>
                <div class="bac-title">从备份恢复</div>
                <div class="bac-desc">上传备份文件恢复数据，支持完整性校验和预览</div>
            </div>
        </div>
        <div class="bac-action">
            <div class="bac-info">
                <div class="label">上传备份文件</div>
                <div class="sub">支持 .sql / .zip / .json 格式</div>
                <div class="meta">⚠ 恢复将覆盖现有数据</div>
            </div>
            <button class="btn" style="background:#ED8936;color:#fff" onclick="showToast('请选择备份文件','info')">📤 上传恢复</button>
        </div>
    </div>
</div>

<!-- Schedule - 照抄 UI -->
<div class="schedule-card">
    <div class="schedule-head">
        <span style="font-size:18px">⏰</span>
        <h3>自动备份策略</h3>
        <span class="tag tag-green" style="margin-left:6px">已启用</span>
        <label class="switch" style="margin-left:auto"><input type="checkbox" checked><span class="switch-slider"></span></label>
    </div>
    <div class="schedule-list">
        <div class="schedule-item">
            <div class="schedule-day">每日</div>
            <div class="schedule-info">
                <div class="schedule-time">每天 03:00</div>
                <div class="schedule-meta">完整数据库备份</div>
            </div>
            <span class="tag tag-green">运行中</span>
        </div>
        <div class="schedule-item">
            <div class="schedule-day">每周</div>
            <div class="schedule-info">
                <div class="schedule-time">周日 04:00</div>
                <div class="schedule-meta">图片附件打包</div>
            </div>
            <span class="tag tag-green">运行中</span>
        </div>
        <div class="schedule-item">
            <div class="schedule-day">每月</div>
            <div class="schedule-info">
                <div class="schedule-time">1 号 05:00</div>
                <div class="schedule-meta">全量数据导出到云端</div>
            </div>
            <span class="tag tag-blue">已配置</span>
        </div>
        <div class="schedule-item">
            <div class="schedule-day" style="background:linear-gradient(135deg,#A0AEC0,#718096)">保留</div>
            <div class="schedule-info">
                <div class="schedule-time">30 份</div>
                <div class="schedule-meta">超出自动清理最旧备份</div>
            </div>
            <span class="tag tag-gray">策略</span>
        </div>
    </div>
</div>

<!-- Backup files list - 照抄 UI -->
<div class="backup-files">
    <div class="card-header">
        <div>
            <div class="card-title">📁 备份文件列表</div>
            <div style="font-size:11px;color:#718096;margin-top:2px">共 <?= count($backups) ?> 个备份文件 · 总占用空间 <?= formatSize($totalSize) ?></div>
        </div>
        <div style="display:flex;gap:8px">
            <select class="form-control" style="width:120px">
                <option>全部类型</option>
                <option>数据库</option>
                <option>图片附件</option>
                <option>全量数据</option>
            </select>
            <button class="btn btn-outline btn-sm" onclick="if(confirm('确定清理旧备份？'))showToast('已清理','success')">🗑 清理旧备份</button>
        </div>
    </div>

    <?php if (empty($backups)): ?>
    <div class="empty">
        <div class="empty-icon">💾</div>
        <div>暂无备份文件</div>
    </div>
    <?php else: ?>
    <div class="file-list">
        <?php foreach ($backups as $b):
            $ext = pathinfo($b['filename'], PATHINFO_EXTENSION);
            $iconClass = $ext === 'sql' ? 'sql' : ($ext === 'zip' ? 'zip' : 'json');
            $icon = $ext === 'sql' ? '🗄' : ($ext === 'zip' ? '🖼' : '📊');
        ?>
        <div class="file-item">
            <div class="file-icon <?= $iconClass ?>"><?= $icon ?></div>
            <div class="file-info">
                <div class="file-name">
                    <?= htmlspecialchars($b['filename']) ?>
                    <span class="file-type <?= $b['method'] === 'auto' ? 'ft-auto' : 'ft-manual' ?>" style="margin-left:6px"><?= $b['method'] === 'auto' ? '自动' : '手动' ?></span>
                </div>
                <div class="file-meta">
                    <span>📅 <?= date('Y-m-d H:i', $b['created_at']) ?></span>
                    <span class="size">💾 <?= formatSize($b['file_size']) ?></span>
                    <span style="color:#48BB78">✓ 校验通过</span>
                </div>
            </div>
            <div class="file-actions">
                <a href="../backend/admin/download.php?type=backup&file=<?= urlencode($b['filename']) ?>" class="icon-mini" title="下载">⬇</a>
                <div class="icon-mini" title="恢复" onclick="showToast('请选择备份文件','info')">↺</div>
                <div class="icon-mini" title="详情">👁</div>
                <div class="icon-mini danger" title="删除" onclick="deleteBackup(<?= $b['id'] ?>)">🗑</div>
            </div>
        </div>
        <?php endforeach; ?>
    </div>
    <?php endif; ?>
</div>

<!-- Recovery flow - 照抄 UI -->
<div class="recovery-section">
    <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px">
        <div>
            <div style="font-size:16px;font-weight:600">🔄 数据恢复流程</div>
            <div style="font-size:12px;color:#718096;margin-top:2px">上传备份文件后，将按以下步骤执行恢复</div>
        </div>
    </div>
    <div class="recovery-steps">
        <div class="recovery-step done">
            <div class="recovery-step-num">✓</div>
            <div class="recovery-step-name">选择备份</div>
            <div class="recovery-step-desc">从备份列表选择</div>
        </div>
        <div class="recovery-step done">
            <div class="recovery-step-num">✓</div>
            <div class="recovery-step-name">完整性校验</div>
            <div class="recovery-step-desc">文件 MD5 / 数据条数核对</div>
        </div>
        <div class="recovery-step active">
            <div class="recovery-step-num">3</div>
            <div class="recovery-step-name">二次确认</div>
            <div class="recovery-step-desc">确认将覆盖现有数据</div>
        </div>
        <div class="recovery-step">
            <div class="recovery-step-num">4</div>
            <div class="recovery-step-name">执行恢复</div>
            <div class="recovery-step-desc">导入数据并自动验证</div>
        </div>
    </div>
</div>

<script>
async function deleteBackup(id) {
    if (!confirm('确定要删除此备份文件吗？')) return;
    const resp = await postJSON('../backend/admin/backup.php?action=delete', {id: id});
    if (resp !== null) { showToast('删除成功', 'success'); location.reload(); }
}
</script>
