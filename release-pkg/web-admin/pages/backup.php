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
        
        // 使用mysqldump或PHP导出
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
?>

<div class="card-header" style="margin-bottom: 16px;">
    <div>
        <h2 style="font-size: 18px;">数据备份与恢复</h2>
        <p style="color: #999; font-size: 12px;">保障家庭数据安全，支持手动/自动备份</p>
    </div>
</div>

<?php if (!empty($msg)): ?>
    <div class="toast toast-success" style="position: relative; margin-bottom: 16px;"><?= $msg ?></div>
<?php endif; ?>

<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
    <!-- 手动备份 -->
    <div class="card">
        <div class="card-title" style="margin-bottom: 16px;">💾 手动备份</div>
        <form method="POST">
            <input type="hidden" name="action" value="backup_db">
            <p style="font-size: 13px; color: #636E72; margin-bottom: 16px;">一键生成完整数据库备份文件</p>
            <button type="submit" class="btn btn-primary">📦 立即备份数据库</button>
        </form>
    </div>

    <!-- 自动备份设置 -->
    <div class="card">
        <div class="card-title" style="margin-bottom: 16px;">⚙ 自动备份设置</div>
        <?php
        $autoEnabled = $db->query("SELECT svalue FROM sys_setting WHERE skey='auto_backup_enabled'")->fetch();
        $autoCycle = $db->query("SELECT svalue FROM sys_setting WHERE skey='auto_backup_cycle'")->fetch();
        $autoKeep = $db->query("SELECT svalue FROM sys_setting WHERE skey='auto_backup_keep'")->fetch();
        ?>
        <form method="POST" action="?p=settings&action=save">
            <input type="hidden" name="action" value="save">
            <div class="form-group">
                <label class="form-label">启用自动备份</label>
                <label class="switch">
                    <input type="checkbox" name="auto_backup_enabled" <?= ($autoEnabled['svalue'] ?? '') == '1' ? 'checked' : '' ?>>
                    <span class="switch-slider"></span>
                </label>
            </div>
            <div class="form-group">
                <label class="form-label">备份周期</label>
                <select name="auto_backup_cycle" class="form-control">
                    <option value="daily" <?= ($autoCycle['svalue'] ?? '') == 'daily' ? 'selected' : '' ?>>每天</option>
                    <option value="weekly" <?= ($autoCycle['svalue'] ?? '') == 'weekly' ? 'selected' : '' ?>>每周</option>
                </select>
            </div>
            <div class="form-group">
                <label class="form-label">保留份数</label>
                <input type="number" name="auto_backup_keep" class="form-control" value="<?= $autoKeep['svalue'] ?? 5 ?>">
            </div>
            <button type="submit" class="btn btn-primary btn-sm">💾 保存设置</button>
        </form>
    </div>
</div>

<!-- 备份文件列表 -->
<div class="card">
    <div class="card-header">
        <div class="card-title">📋 备份文件列表</div>
    </div>
    <?php if (empty($backups)): ?>
        <div class="empty-state">
            <div class="empty-icon">💾</div>
            <div class="empty-text">暂无备份文件</div>
        </div>
    <?php else: ?>
        <div class="table-wrapper">
            <table>
                <thead><tr><th>文件名</th><th>类型</th><th>大小</th><th>备份方式</th><th>备份时间</th><th>操作</th></tr></thead>
                <tbody>
                    <?php foreach ($backups as $b): ?>
                    <tr>
                        <td><strong><?= htmlspecialchars($b['filename']) ?></strong></td>
                        <td><span class="badge badge-info"><?= $b['type'] ?></span></td>
                        <td><?= formatSize($b['file_size']) ?></td>
                        <td><?= $b['method'] === 'auto' ? '🔄 自动' : '👤 手动' ?></td>
                        <td><?= date('Y-m-d H:i:s', $b['created_at']) ?></td>
                        <td>
                            <a href="../backend/admin/download.php?type=backup&file=<?= urlencode($b['filename']) ?>" class="btn btn-sm btn-outline">📥 下载</a>
                            <button class="btn btn-sm btn-danger" onclick="deleteBackup(<?= $b['id'] ?>)">🗑</button>
                        </td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
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
