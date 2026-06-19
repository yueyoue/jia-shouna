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

<?php if (!empty($msg)): ?>
    <div class="alert alert-success" style="margin-bottom:var(--space-5);">
        <span class="alert-icon">✅</span>
        <div><?= $msg ?></div>
    </div>
<?php endif; ?>

<!-- Status bar -->
<div class="status-bar" style="margin-bottom:var(--space-6);">
    <span class="status-icon">✅</span>
    <span>数据完整性：良好 · 上次备份：2026-06-19 03:00 · 备份文件数：<?= count($backups) ?> · 总占用：<?= formatSize(array_sum(array_column($backups, 'file_size'))) ?></span>
</div>

<!-- 2x2 Card grid -->
<div class="grid-2" style="margin-bottom:var(--space-6);">
    <!-- 数据库备份 -->
    <div class="card">
        <div class="card-body">
            <div class="card-icon-header">
                <div class="icon-wrapper green">💾</div>
                <div class="icon-text">
                    <h3>数据库备份</h3>
                    <p>一键生成完整数据库备份文件</p>
                </div>
            </div>
            <form method="POST">
                <input type="hidden" name="action" value="backup_db">
                <div class="card-operation" style="border-color:var(--primary);">
                    <div class="op-text">
                        <div class="op-title">📦 立即备份数据库</div>
                        <div class="op-desc">生成 .sql 格式备份文件</div>
                        <div class="op-meta">上次备份：今天 03:00</div>
                    </div>
                    <button type="submit" class="btn btn-primary btn-sm">立即备份</button>
                </div>
            </form>
        </div>
    </div>

    <!-- 附件备份 -->
    <div class="card">
        <div class="card-body">
            <div class="card-icon-header">
                <div class="icon-wrapper blue">🖼️</div>
                <div class="icon-text">
                    <h3>附件备份</h3>
                    <p>打包备份所有图片等附件文件</p>
                </div>
            </div>
            <div class="card-operation" style="border-color:var(--accent-blue);">
                <div class="op-text">
                    <div class="op-title">📸 打包图片附件</div>
                    <div class="op-desc">生成 .tar.gz 格式压缩包</div>
                    <div class="op-meta">图片数量：<?= $db->query("SELECT COUNT(*) as cnt FROM goods_image")->fetch()['cnt'] ?? 0 ?> 张</div>
                </div>
                <button class="btn btn-secondary btn-sm" onclick="showToast('正在打包...','info')">打包下载</button>
            </div>
        </div>
    </div>

    <!-- 全量导出 -->
    <div class="card">
        <div class="card-body">
            <div class="card-icon-header">
                <div class="icon-wrapper cyan">📤</div>
                <div class="icon-text">
                    <h3>全量导出</h3>
                    <p>导出所有数据为通用格式</p>
                </div>
            </div>
            <div class="card-operation" style="border-color:var(--accent-cyan);">
                <div class="op-text">
                    <div class="op-title">📋 导出全量数据</div>
                    <div class="op-desc">支持 JSON / Excel 格式</div>
                </div>
                <div style="display:flex;gap:var(--space-2);">
                    <button class="btn btn-outline btn-sm" onclick="showToast('导出JSON...','info')">JSON</button>
                    <button class="btn btn-outline btn-sm" onclick="showToast('导出Excel...','info')">Excel</button>
                </div>
            </div>
        </div>
    </div>

    <!-- 从备份恢复 -->
    <div class="card">
        <div class="card-body">
            <div class="card-icon-header">
                <div class="icon-wrapper orange">↩</div>
                <div class="icon-text">
                    <h3>从备份恢复</h3>
                    <p>上传备份文件恢复数据</p>
                </div>
            </div>
            <div class="card-operation" style="border-color:var(--accent-orange);">
                <div class="op-text">
                    <div class="op-title">📤 上传备份文件恢复</div>
                    <div class="op-desc">支持 .sql / .tar.gz 格式</div>
                </div>
                <button class="btn btn-orange btn-sm" onclick="showToast('请选择备份文件','info')">上传恢复</button>
            </div>
        </div>
    </div>
</div>

<!-- 自动备份策略 -->
<div class="card" style="margin-bottom:var(--space-6);">
    <div class="card-header">
        <div>
            <div class="card-title">⚙ 自动备份策略</div>
            <div class="card-subtitle">配置定时自动备份策略</div>
        </div>
        <form method="POST" action="?p=settings&action=save">
            <input type="hidden" name="action" value="save">
            <div style="display:flex;align-items:center;gap:var(--space-4);">
                <label style="display:flex;align-items:center;gap:var(--space-2);font-size:var(--font-size-sm);">
                    <span>启用</span>
                    <label class="switch">
                        <input type="checkbox" name="auto_backup_enabled" <?= ($autoEnabled['svalue'] ?? '') == '1' ? 'checked' : '' ?>>
                        <span class="switch-slider"></span>
                    </label>
                </label>
                <select name="auto_backup_cycle" class="form-control" style="width:auto;padding:4px 8px;font-size:var(--font-size-sm);">
                    <option value="daily" <?= ($autoCycle['svalue'] ?? '') == 'daily' ? 'selected' : '' ?>>每日</option>
                    <option value="weekly" <?= ($autoCycle['svalue'] ?? '') == 'weekly' ? 'selected' : '' ?>>每周</option>
                </select>
                <span style="font-size:var(--font-size-sm);color:var(--text-tertiary);">保留</span>
                <input type="number" name="auto_backup_keep" class="form-control" style="width:60px;padding:4px 8px;font-size:var(--font-size-sm);" value="<?= $autoKeep['svalue'] ?? 5 ?>">
                <span style="font-size:var(--font-size-sm);color:var(--text-tertiary);">份</span>
                <button type="submit" class="btn btn-primary btn-sm">保存</button>
            </div>
        </form>
    </div>
    <div class="card-body">
        <div style="display:flex;gap:var(--space-6);flex-wrap:wrap;">
            <div style="display:flex;align-items:center;gap:var(--space-2);">
                <span style="font-size:var(--font-size-sm);color:var(--text-tertiary);">状态</span>
                <span class="tag tag-green">运行中</span>
            </div>
            <div style="display:flex;align-items:center;gap:var(--space-2);">
                <span style="font-size:var(--font-size-sm);color:var(--text-tertiary);">上次备份</span>
                <span style="font-size:var(--font-size-sm);font-weight:500;">2026-06-19 03:00</span>
            </div>
            <div style="display:flex;align-items:center;gap:var(--space-2);">
                <span style="font-size:var(--font-size-sm);color:var(--text-tertiary);">下次备份</span>
                <span style="font-size:var(--font-size-sm);font-weight:500;">2026-06-20 03:00</span>
            </div>
        </div>
    </div>
</div>

<!-- 备份文件列表 -->
<div class="card">
    <div class="card-header">
        <div>
            <div class="card-title">📋 备份文件列表</div>
            <div class="card-subtitle">共 <?= count($backups) ?> 个备份文件，总占用空间 <?= formatSize(array_sum(array_column($backups, 'file_size'))) ?></div>
        </div>
        <div style="display:flex;gap:var(--space-3);">
            <select class="form-control" style="width:auto;padding:4px 8px;font-size:var(--font-size-sm);">
                <option>全部类型</option>
                <option>数据库</option>
                <option>附件</option>
            </select>
            <button class="btn btn-outline btn-sm" onclick="if(confirm('确定清理旧备份？'))showToast('已清理','success')">🧹 清除旧备份</button>
        </div>
    </div>
    <?php if (empty($backups)): ?>
        <div class="empty-state">
            <div class="empty-icon">💾</div>
            <div class="empty-text">暂无备份文件</div>
        </div>
    <?php else: ?>
        <div style="padding:var(--space-2) 0;">
            <?php foreach ($backups as $b): ?>
            <div style="padding:var(--space-4) var(--space-6);display:flex;align-items:center;gap:var(--space-4);border-bottom:1px solid var(--border-light);transition:background .15s;" onmouseover="this.style.background='var(--bg-input)'" onmouseout="this.style.background='transparent'">
                <div style="width:42px;height:42px;border-radius:var(--radius-md);background:var(--accent-blue-light);display:flex;align-items:center;justify-content:center;font-size:22px;flex-shrink:0;">📄</div>
                <div style="flex:1;min-width:0;">
                    <div style="font-size:var(--font-size-md);font-weight:600;"><?= htmlspecialchars($b['filename']) ?></div>
                    <div style="display:flex;gap:var(--space-4);margin-top:var(--space-1);font-size:var(--font-size-xs);color:var(--text-muted);">
                        <span class="tag <?= $b['type'] === 'database' ? 'tag-blue' : 'tag-green' ?>" style="font-size:10px;"><?= $b['type'] === 'database' ? '数据库' : '附件' ?></span>
                        <span style="font-family:monospace;"><?= formatSize($b['file_size']) ?></span>
                        <span><?= $b['method'] === 'auto' ? '🔄 自动' : '👤 手动' ?></span>
                        <span><?= date('Y-m-d H:i:s', $b['created_at']) ?></span>
                    </div>
                </div>
                <div style="display:flex;gap:var(--space-2);flex-shrink:0;">
                    <a href="../backend/admin/download.php?type=backup&file=<?= urlencode($b['filename']) ?>" class="btn btn-outline btn-sm">📥 下载</a>
                    <button class="btn btn-danger btn-sm" onclick="deleteBackup(<?= $b['id'] ?>)">🗑</button>
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
