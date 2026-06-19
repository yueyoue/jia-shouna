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

// 自动备份设置
$autoEnabled = $db->query("SELECT svalue FROM sys_setting WHERE skey='auto_backup_enabled'")->fetch();
$autoCycle = $db->query("SELECT svalue FROM sys_setting WHERE skey='auto_backup_cycle'")->fetch();
$autoKeep = $db->query("SELECT svalue FROM sys_setting WHERE skey='auto_backup_keep'")->fetch();
?>

<div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;flex-wrap:wrap;gap:12px;">
    <div>
        <div class="page-title" style="font-size:22px;font-weight:700">数据备份与恢复</div>
        <div class="page-desc" style="color:var(--text-3);font-size:13px;margin-top:4px">保障家庭数据安全，支持手动/自动备份</div>
    </div>
</div>

<?php if (!empty($msg)): ?>
    <div class="alert alert-success" style="margin-bottom:16px;">
        <span class="alert-icon">✅</span>
        <div><?= $msg ?></div>
    </div>
<?php endif; ?>

<!-- Backup action cards -->
<div style="display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:20px;">
    <!-- 手动备份 -->
    <div class="card" style="padding:24px;position:relative;overflow:hidden;">
        <div style="position:absolute;right:-30px;top:-30px;width:120px;height:120px;border-radius:50%;background:var(--primary);opacity:.08"></div>
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
            <div style="width:48px;height:48px;border-radius:12px;background:linear-gradient(135deg,#FF8C42,#FF6B6B);display:flex;align-items:center;justify-content:center;font-size:22px;color:#fff">💾</div>
            <div>
                <div style="font-size:16px;font-weight:600">手动备份</div>
                <div style="font-size:12px;color:var(--text-3);margin-top:2px;line-height:1.5">一键生成完整数据库备份文件</div>
            </div>
        </div>
        <form method="POST">
            <input type="hidden" name="action" value="backup_db">
            <div style="display:flex;align-items:center;justify-content:space-between;padding:14px;border-radius:10px;background:linear-gradient(135deg,#FFF7F0 0%,#F0FBFA 100%);border:1px dashed var(--primary);">
                <div>
                    <div style="font-size:12px;font-weight:600">📦 立即备份数据库</div>
                    <div style="font-size:11px;color:var(--text-3);margin-top:2px">生成 .sql 格式备份文件</div>
                </div>
                <button type="submit" class="btn btn-primary btn-sm">开始备份</button>
            </div>
        </form>
    </div>

    <!-- 自动备份设置 -->
    <div class="card" style="padding:24px;position:relative;overflow:hidden;">
        <div style="position:absolute;right:-30px;top:-30px;width:120px;height:120px;border-radius:50%;background:var(--secondary);opacity:.08"></div>
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
            <div style="width:48px;height:48px;border-radius:12px;background:linear-gradient(135deg,#4ECDC4,#0E9F8E);display:flex;align-items:center;justify-content:center;font-size:22px;color:#fff">⚙</div>
            <div>
                <div style="font-size:16px;font-weight:600">自动备份设置</div>
                <div style="font-size:12px;color:var(--text-3);margin-top:2px;line-height:1.5">配置定时自动备份策略</div>
            </div>
        </div>
        <form method="POST" action="?p=settings&action=save">
            <input type="hidden" name="action" value="save">
            <div style="display:flex;flex-direction:column;gap:14px;">
                <div style="display:flex;align-items:center;justify-content:space-between;">
                    <div>
                        <div style="font-size:13px;font-weight:600">启用自动备份</div>
                        <div style="font-size:11px;color:var(--text-3)">开启后按周期自动备份</div>
                    </div>
                    <label class="switch">
                        <input type="checkbox" name="auto_backup_enabled" <?= ($autoEnabled['svalue'] ?? '') == '1' ? 'checked' : '' ?>>
                        <span class="switch-slider"></span>
                    </label>
                </div>
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;">
                    <div class="form-group" style="margin-bottom:0">
                        <label class="form-label">备份周期</label>
                        <select name="auto_backup_cycle" class="form-control">
                            <option value="daily" <?= ($autoCycle['svalue'] ?? '') == 'daily' ? 'selected' : '' ?>>每天</option>
                            <option value="weekly" <?= ($autoCycle['svalue'] ?? '') == 'weekly' ? 'selected' : '' ?>>每周</option>
                        </select>
                    </div>
                    <div class="form-group" style="margin-bottom:0">
                        <label class="form-label">保留份数</label>
                        <input type="number" name="auto_backup_keep" class="form-control" value="<?= $autoKeep['svalue'] ?? 5 ?>">
                    </div>
                </div>
                <button type="submit" class="btn btn-primary btn-sm" style="align-self:flex-start">💾 保存设置</button>
            </div>
        </form>
    </div>
</div>

<!-- 备份文件列表 -->
<div class="card" style="overflow:hidden;">
    <div class="card-header">
        <div class="card-title">📋 备份文件列表</div>
    </div>
    <?php if (empty($backups)): ?>
        <div class="empty-state">
            <div class="empty-icon">💾</div>
            <div class="empty-text">暂无备份文件</div>
        </div>
    <?php else: ?>
        <div style="padding:8px 0;">
            <?php foreach ($backups as $b): ?>
            <div style="padding:14px 20px;display:flex;align-items:center;gap:14px;border-bottom:1px solid var(--border-2);transition:background .15s;" onmouseover="this.style.background='#FAFBFC'" onmouseout="this.style.background='transparent'">
                <div style="width:42px;height:42px;border-radius:10px;background:linear-gradient(135deg,#D6E4FF,#A8C5FA);display:flex;align-items:center;justify-content:center;font-size:22px;flex-shrink:0">📄</div>
                <div style="flex:1;min-width:0">
                    <div style="font-size:14px;font-weight:600"><?= htmlspecialchars($b['filename']) ?></div>
                    <div style="display:flex;gap:14px;margin-top:4px;font-size:11px;color:var(--text-3)">
                        <span class="tag <?= $b['type'] === 'database' ? 'tag-blue' : 'tag-green' ?>" style="font-size:10px"><?= $b['type'] ?></span>
                        <span style="font-family:monospace"><?= formatSize($b['file_size']) ?></span>
                        <span><?= $b['method'] === 'auto' ? '🔄 自动' : '👤 手动' ?></span>
                        <span><?= date('Y-m-d H:i:s', $b['created_at']) ?></span>
                    </div>
                </div>
                <div style="display:flex;gap:6px;flex-shrink:0">
                    <a href="../backend/admin/download.php?type=backup&file=<?= urlencode($b['filename']) ?>" class="btn btn-outline btn-sm" style="padding:4px 8px;font-size:12px">📥 下载</a>
                    <button class="btn btn-danger btn-sm" style="padding:4px 8px;font-size:12px" onclick="deleteBackup(<?= $b['id'] ?>)">🗑</button>
                </div>
            </div>
            <?php endforeach; ?>
        </div>
    <?php endif; ?>
</div>

<script>
async function deleteBackup(id) {
    if (!confirm('确定要删除此备份文件吗？')) return;
    const resp = await postJSON('../backend/admin/backup.php?action=delete', {id: id});
    if (resp !== null) { showToast('删除成功', 'success'); location.reload(); }
}
</script>
