<?php
$db = getDB();

// 安全查询辅助函数
function safeQuery($db, $sql, $field) {
    try {
        $row = $db->query($sql)->fetch();
        return ($row && isset($row[$field])) ? $row[$field] : 0;
    } catch (Exception $e) { return 0; }
}

// 统计信息
$stats = [];
$stats['total_goods'] = safeQuery($db, "SELECT COUNT(*) as c FROM goods WHERE status = 1", 'c');
$stats['total_images'] = safeQuery($db, "SELECT COUNT(*) as c FROM goods_image", 'c');
$stats['total_spaces'] = safeQuery($db, "SELECT COUNT(*) as c FROM storage_space", 'c');
$stats['total_users'] = safeQuery($db, "SELECT COUNT(*) as c FROM sys_user WHERE status = 1", 'c');
$stats['total_houses'] = safeQuery($db, "SELECT COUNT(*) as c FROM house WHERE status = 1", 'c');

// 图片目录大小
$imageDirSize = 0;
$imageDir = UPLOAD_PATH . 'images/';
if (is_dir($imageDir)) {
    try {
        $iter = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($imageDir));
        foreach ($iter as $file) {
            if ($file->isFile()) $imageDirSize += $file->getSize();
        }
    } catch (Exception $e) {}
}
$stats['image_size'] = $imageDirSize;

// 备份目录大小
$backupDirSize = 0;
$backupDir = UPLOAD_PATH . 'backups/';
if (is_dir($backupDir)) {
    try {
        $iter = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($backupDir));
        foreach ($iter as $file) {
            if ($file->isFile()) $backupDirSize += $file->getSize();
        }
    } catch (Exception $e) {}
}
$stats['backup_size'] = $backupDirSize;

$stats['db_size'] = safeQuery($db, "SELECT SUM(data_length + index_length) as s FROM information_schema.tables WHERE table_schema = DATABASE()", 's');

// 最后备份时间
$lastBackupTs = safeQuery($db, "SELECT created_at FROM backup_record ORDER BY created_at DESC LIMIT 1", 'created_at');
$stats['last_backup'] = $lastBackupTs;
?>

<style>
.backup-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:20px}
@media(max-width:1024px){.backup-grid{grid-template-columns:1fr}}
.action-card{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:24px;position:relative;overflow:hidden}
.action-card::before{content:'';position:absolute;right:-30px;top:-30px;width:120px;height:120px;border-radius:50%;opacity:.06}
.ac1::before{background:#FF8C42}
.ac2::before{background:#4ECDC4}
.ac3::before{background:#5B9FED}
.ac4::before{background:#ED8936}
.ac-head{display:flex;align-items:center;gap:12px;margin-bottom:14px}
.ac-icon{width:48px;height:48px;border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:22px;color:#fff}
.ac1 .ac-icon{background:linear-gradient(135deg,#FF8C42,#FF6B6B)}
.ac2 .ac-icon{background:linear-gradient(135deg,#4ECDC4,#0E9F8E)}
.ac3 .ac-icon{background:linear-gradient(135deg,#5B9FED,#2C5282)}
.ac4 .ac-icon{background:linear-gradient(135deg,#ED8936,#9C4221)}
.ac-title{font-size:15px;font-weight:600}
.ac-desc{font-size:11px;color:#718096;margin-top:2px;line-height:1.5}
.ac-body{padding:14px;border-radius:10px;border:1px dashed;margin-top:4px}
.ac1 .ac-body{background:#FFF7F0;border-color:#FFD3B0}
.ac2 .ac-body{background:#E0F7F4;border-color:#7EE0D8}
.ac3 .ac-body{background:#E0EFFF;border-color:#A8C5FA}
.ac4 .ac-body{background:#FEEBC8;border-color:#F6C987}
.ac-label{font-size:13px;font-weight:600}
.ac-sub{font-size:11px;color:#718096;margin-top:2px}
.ac-meta{font-size:10px;color:#A0AEC0;margin-top:4px;font-family:monospace}
.ac-actions{display:flex;align-items:center;justify-content:space-between;margin-top:12px}
.ac-btn{padding:8px 20px;border-radius:8px;border:none;font-size:13px;font-weight:600;cursor:pointer;transition:all .2s}
.ac-btn-primary{background:linear-gradient(135deg,#FF8C42,#FF6B6B);color:#fff}
.ac-btn-primary:hover{transform:translateY(-1px);box-shadow:0 4px 12px rgba(255,140,66,.3)}
.ac-btn-secondary{background:linear-gradient(135deg,#4ECDC4,#0E9F8E);color:#fff}
.ac-btn-secondary:hover{transform:translateY(-1px);box-shadow:0 4px 12px rgba(78,205,196,.3)}
.ac-btn-outline{background:transparent;border:1px solid #CBD5E0;color:#4A5568}
.ac-btn-outline:hover{border-color:#FF8C42;color:#FF8C42}
.ac-btn:disabled{opacity:.5;cursor:not-allowed;transform:none!important}

.stats-bar{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:12px;margin-bottom:16px}
.s-item{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:14px;text-align:center}
.s-icon{font-size:22px;margin-bottom:6px}
.s-val{font-size:18px;font-weight:700;color:#2D3748}
.s-label{font-size:11px;color:#718096;margin-top:2px}

.files-card{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);overflow:hidden}
.files-header{padding:14px 20px;border-bottom:1px solid var(--border-2);display:flex;align-items:center;justify-content:space-between;background:#FAFBFC}
.files-title{font-size:14px;font-weight:600}
.file-row{display:flex;align-items:center;gap:14px;padding:12px 20px;border-bottom:1px solid #F7FAFC;transition:background .15s}
.file-row:hover{background:#FAFBFC}
.file-row:last-child{border-bottom:none}
.file-icon{width:42px;height:42px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0}
.file-icon.sql{background:linear-gradient(135deg,#D6E4FF,#A8C5FA)}
.file-icon.zip{background:linear-gradient(135deg,#C7F0EC,#7EE0D8)}
.file-icon.json{background:linear-gradient(135deg,#E9D8FD,#B794F4)}
.file-info{flex:1;min-width:0}
.file-name{font-size:13px;font-weight:600;color:#2D3748}
.file-meta{display:flex;gap:14px;margin-top:3px;font-size:11px;color:#718096}
.file-tag{font-size:10px;padding:2px 6px;border-radius:4px;font-weight:600}
.tag-auto{background:rgba(78,205,196,.12);color:#0E9F8E}
.tag-manual{background:rgba(255,140,66,.12);color:#C25A1E}
.tag-safety{background:rgba(245,101,101,.12);color:#F56565}
.file-btns{display:flex;gap:4px;flex-shrink:0}
.fbtn{width:30px;height:30px;border-radius:6px;display:flex;align-items:center;justify-content:center;font-size:12px;cursor:pointer;border:1px solid var(--border-2);background:#F7FAFC;color:#4A5568;transition:all .15s;text-decoration:none}
.fbtn:hover{background:#FFF1E0;color:#FF8C42;border-color:#FF8C42}
.fbtn.danger:hover{background:#FED7D7;color:#F56565;border-color:#F56565}

.recovery-card{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:24px;margin-top:16px}
.recovery-steps{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin:16px 0;position:relative}
.recovery-steps::before{content:'';position:absolute;top:16px;left:10%;right:10%;height:2px;background:#EDF2F7;z-index:0}
.r-step{text-align:center;position:relative;z-index:1}
.r-step-num{width:32px;height:32px;border-radius:50%;background:#fff;border:2px solid #CBD5E0;display:flex;align-items:center;justify-content:center;margin:0 auto 8px;font-size:12px;font-weight:700;color:#A0AEC0}
.r-step.done .r-step-num{background:#48BB78;border-color:#48BB78;color:#fff}
.r-step-name{font-size:12px;font-weight:600;color:#4A5568}
.r-step-desc{font-size:10px;color:#A0AEC0;margin-top:2px}

.guide-box{background:#FFFAF0;border:1px solid #FED7AA;border-radius:8px;padding:16px;margin-top:16px}
.guide-title{font-size:13px;font-weight:600;color:#C25A1E;margin-bottom:10px}
.guide-step{display:flex;gap:10px;padding:6px 0;font-size:12px;color:#4A5568;line-height:1.6}
.guide-step .num{width:20px;height:20px;border-radius:50%;background:#FF8C42;color:#fff;display:flex;align-items:center;justify-content:center;font-size:10px;font-weight:700;flex-shrink:0;margin-top:2px}
.guide-step code{background:#FEF3C7;padding:1px 6px;border-radius:4px;font-size:11px;color:#92400E}

/* Modal */
.modal-mask{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.45);z-index:999;display:none;align-items:center;justify-content:center}
.modal-box{background:#fff;border-radius:12px;max-width:600px;width:92%;max-height:85vh;overflow-y:auto;padding:24px;position:relative}
.modal-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}
.modal-title{font-size:16px;font-weight:600}
.modal-close{cursor:pointer;font-size:22px;color:#999;background:none;border:none;line-height:1}

.preview-box{background:#1a1a2e;border-radius:8px;padding:16px;max-height:300px;overflow:auto;font-family:'Fira Code',monospace;font-size:11px;color:#e2e8f0;line-height:1.6;white-space:pre-wrap;word-break:break-all}

.form-row{margin-bottom:14px}
.form-label{display:block;font-size:12px;font-weight:600;color:#4A5568;margin-bottom:6px}
.form-input{width:100%;padding:10px 12px;border:1px solid #E2E8F0;border-radius:8px;font-size:13px;transition:border .2s}
.form-input:focus{outline:none;border-color:#FF8C42;box-shadow:0 0 0 3px rgba(255,140,66,.12)}
.form-hint{font-size:11px;color:#A0AEC0;margin-top:4px}

.btn-row{display:flex;gap:8px;margin-top:16px}
.btn-lg{padding:12px 24px;border-radius:8px;font-size:14px;font-weight:600;cursor:pointer;border:none;transition:all .2s}
.btn-orange{background:linear-gradient(135deg,#FF8C42,#FF6B6B);color:#fff}
.btn-orange:hover{box-shadow:0 4px 12px rgba(255,140,66,.3)}
.btn-green{background:linear-gradient(135deg,#4ECDC4,#0E9F8E);color:#fff}
.btn-gray{background:#F7FAFC;color:#4A5568;border:1px solid #E2E8F0}
</style>

<div class="page-header">
    <div>
        <div class="page-title">💾 数据备份与恢复</div>
        <div class="page-desc">完整备份所有数据 · 一键恢复 · 多格式导出</div>
    </div>
</div>

<!-- 数据概览 -->
<div class="stats-bar">
    <div class="s-item"><div class="s-icon">📦</div><div class="s-val"><?= number_format($stats['total_goods']) ?></div><div class="s-label">物品总数</div></div>
    <div class="s-item"><div class="s-icon">🖼</div><div class="s-val"><?= number_format($stats['total_images']) ?></div><div class="s-label">图片数量</div></div>
    <div class="s-item"><div class="s-icon">🏠</div><div class="s-val"><?= number_format($stats['total_spaces']) ?></div><div class="s-label">收纳空间</div></div>
    <div class="s-item"><div class="s-icon">👥</div><div class="s-val"><?= number_format($stats['total_users']) ?></div><div class="s-label">用户数量</div></div>
    <div class="s-item"><div class="s-icon">🗄</div><div class="s-val"><?= formatSize($stats['db_size']) ?></div><div class="s-label">数据库大小</div></div>
    <div class="s-item"><div class="s-icon">🖼</div><div class="s-val"><?= formatSize($stats['image_size']) ?></div><div class="s-label">图片占用</div></div>
    <div class="s-item"><div class="s-icon">💾</div><div class="s-val"><?= formatSize($stats['backup_size']) ?></div><div class="s-label">备份占用</div></div>
    <div class="s-item"><div class="s-icon">⏰</div><div class="s-val"><?= $stats['last_backup'] ? date('m-d H:i', $stats['last_backup']) : '无' ?></div><div class="s-label">最后备份</div></div>
</div>

<!-- 操作卡片 -->
<div class="backup-grid">
    <!-- 1. 数据库备份 -->
    <div class="action-card ac1">
        <div class="ac-head">
            <div class="ac-icon">🗄</div>
            <div>
                <div class="ac-title">数据库备份</div>
                <div class="ac-desc">完整备份所有数据表：物品、空间、用户、标签、提醒、设置等</div>
            </div>
        </div>
        <div class="ac-body">
            <div class="ac-label">立即备份数据库</div>
            <div class="ac-sub">生成完整 .sql 文件，包含建表语句和全部数据</div>
            <div class="ac-meta">数据库: <?= DB_NAME ?> · <?= $stats['total_goods'] ?> 件物品 · <?= $stats['total_users'] ?> 个用户</div>
            <div class="ac-actions">
                <button class="ac-btn ac-btn-primary" id="btn-backup-db" onclick="doBackupDb()">⚡ 立即备份</button>
                <span id="backup-db-status" style="font-size:12px;color:#718096"></span>
            </div>
        </div>
    </div>

    <!-- 2. 图片打包 -->
    <div class="action-card ac2">
        <div class="ac-head">
            <div class="ac-icon">🖼</div>
            <div>
                <div class="ac-title">图片附件打包</div>
                <div class="ac-desc">打包所有物品图片为 ZIP，下载到本地保存</div>
            </div>
        </div>
        <div class="ac-body">
            <div class="ac-label">打包下载图片</div>
            <div class="ac-sub">包含原图和缩略图，ZIP 格式</div>
            <div class="ac-meta">图片: <?= $stats['total_images'] ?> 张 · 占用: <?= formatSize($stats['image_size']) ?></div>
            <div class="ac-actions">
                <button class="ac-btn ac-btn-secondary" id="btn-backup-img" onclick="doBackupImages()">📦 打包下载</button>
                <span id="backup-img-status" style="font-size:12px;color:#718096"></span>
            </div>
        </div>
    </div>

    <!-- 3. 全量导出 -->
    <div class="action-card ac3">
        <div class="ac-head">
            <div class="ac-icon">📊</div>
            <div>
                <div class="ac-title">全量数据导出</div>
                <div class="ac-desc">导出全部业务数据为 JSON 或 CSV，方便迁移到其他系统</div>
            </div>
        </div>
        <div class="ac-body">
            <div class="ac-label">选择导出格式</div>
            <div class="ac-sub">JSON 包含所有数据和关联关系 · CSV 仅物品列表</div>
            <div class="ac-meta">包含: 物品、空间、标签、用户、设置、领用记录</div>
            <div class="ac-actions">
                <button class="ac-btn ac-btn-outline" onclick="doExportJson()">{ } JSON</button>
                <button class="ac-btn ac-btn-outline" onclick="doExportCsv()">📊 CSV</button>
            </div>
        </div>
    </div>

    <!-- 4. 数据恢复 -->
    <div class="action-card ac4">
        <div class="ac-head">
            <div class="ac-icon">📤</div>
            <div>
                <div class="ac-title">数据恢复</div>
                <div class="ac-desc">从备份文件恢复数据，恢复前会自动备份当前数据</div>
            </div>
        </div>
        <div class="ac-body">
            <div class="ac-label">选择恢复方式</div>
            <div class="ac-sub">支持从已有备份恢复，或上传 .sql 文件恢复</div>
            <div class="ac-meta">⚠ 恢复操作会覆盖当前数据（自动保留恢复前备份）</div>
            <div class="ac-actions">
                <button class="ac-btn" style="background:linear-gradient(135deg,#ED8936,#C05621);color:#fff" onclick="showRestoreModal()">🔄 数据恢复</button>
            </div>
        </div>
    </div>
</div>

<!-- 备份文件列表 -->
<div class="files-card">
    <div class="files-header">
        <div>
            <div class="files-title">📁 备份文件列表</div>
            <div style="font-size:11px;color:#718096;margin-top:2px" id="files-count">加载中...</div>
        </div>
        <button class="ac-btn ac-btn-outline" style="padding:6px 14px;font-size:12px" onclick="loadBackupFiles()">🔄 刷新</button>
    </div>
    <div id="file-list">
        <div style="padding:40px;text-align:center;color:#A0AEC0">
            <div style="font-size:28px;margin-bottom:8px">⏳</div>
            <div>加载中...</div>
        </div>
    </div>
</div>

<!-- 恢复流程说明 -->
<div class="recovery-card">
    <div style="margin-bottom:4px">
        <div style="font-size:15px;font-weight:600">🔄 数据恢复流程</div>
        <div style="font-size:12px;color:#718096;margin-top:2px">恢复操作会自动保护你的数据安全</div>
    </div>
    <div class="recovery-steps">
        <div class="r-step done"><div class="r-step-num">1</div><div class="r-step-name">选择备份</div><div class="r-step-desc">从列表或上传文件</div></div>
        <div class="r-step done"><div class="r-step-num">2</div><div class="r-step-name">预览内容</div><div class="r-step-desc">确认备份内容正确</div></div>
        <div class="r-step done"><div class="r-step-num">3</div><div class="r-step-name">输入确认码</div><div class="r-step-desc">防止误操作</div></div>
        <div class="r-step done"><div class="r-step-num">4</div><div class="r-step-name">自动备份</div><div class="r-step-desc">恢复前先备份当前数据</div></div>
        <div class="r-step done"><div class="r-step-num">5</div><div class="r-step-name">执行恢复</div><div class="r-step-desc">导入数据完成</div></div>
    </div>
</div>

<!-- 完整备份指南 -->
<div class="guide-box">
    <div class="guide-title">📋 完整备份指南（删除站点重建前必读）</div>
    <div class="guide-step"><span class="num">1</span><div><strong>备份数据库</strong>：点击上方「⚡ 立即备份数据库」，下载 <code>.sql</code> 文件到本地电脑</div></div>
    <div class="guide-step"><span class="num">2</span><div><strong>备份图片</strong>：点击「📦 打包下载」，下载图片 <code>.zip</code> 文件到本地电脑</div></div>
    <div class="guide-step"><span class="num">3</span><div><strong>确认文件</strong>：检查本地是否有这两个文件，缺一不可</div></div>
    <div class="guide-step"><span class="num">4</span><div><strong>重建站点</strong>：删除旧站点，重新部署新站点</div></div>
    <div class="guide-step"><span class="num">5</span><div><strong>恢复数据库</strong>：在新站点的备份页面，点「🔄 数据恢复」→ 上传 <code>.sql</code> 文件 → 输入确认码 <code>RESTORE_CONFIRM</code> → 点击恢复</div></div>
    <div class="guide-step"><span class="num">6</span><div><strong>恢复图片</strong>：将下载的 <code>.zip</code> 解压，把 <code>images/</code> 文件夹上传到新站点的 <code>backend/uploads/</code> 目录下</div></div>
    <div class="guide-step"><span class="num">7</span><div><strong>验证</strong>：登录新站点，检查物品、图片是否完整</div></div>
    <div style="margin-top:12px;padding:10px;background:#FEF3C7;border-radius:6px;font-size:12px;color:#92400E">
        ⚠ <strong>重要提醒</strong>：数据库备份不包含图片文件！图片需要单独备份。两样都要下载才能完整恢复。
    </div>
</div>

<!-- ========== 恢复弹窗 ========== -->
<div id="modal-restore" class="modal-mask">
    <div class="modal-box">
        <div class="modal-head">
            <div class="modal-title">🔄 数据恢复</div>
            <button class="modal-close" onclick="hideRestoreModal()">&times;</button>
        </div>

        <!-- Tab 切换 -->
        <div style="display:flex;gap:8px;margin-bottom:16px">
            <button class="tab-btn active" data-tab="from-list" onclick="switchRestoreTab(this)">从已有备份恢复</button>
            <button class="tab-btn" data-tab="from-upload" onclick="switchRestoreTab(this)">上传文件恢复</button>
        </div>

        <!-- Tab 1: 从列表恢复 -->
        <div id="tab-from-list" class="tab-content">
            <div class="form-row">
                <label class="form-label">选择备份文件</label>
                <select id="restore-select" class="form-input">
                    <option value="">-- 请选择 --</option>
                </select>
                <div class="form-hint">仅显示 .sql 数据库备份文件</div>
            </div>
            <div id="restore-preview" style="display:none">
                <label class="form-label">备份文件预览</label>
                <div id="restore-preview-content" class="preview-box"></div>
            </div>
        </div>

        <!-- Tab 2: 上传文件恢复 -->
        <div id="tab-from-upload" class="tab-content" style="display:none">
            <div class="form-row">
                <label class="form-label">上传 .sql 备份文件</label>
                <input type="file" id="restore-file" accept=".sql" class="form-input">
                <div class="form-hint">支持从本系统导出的 .sql 备份文件</div>
            </div>
        </div>

        <!-- 确认码 -->
        <div class="form-row" style="margin-top:16px;padding-top:16px;border-top:1px dashed #EDF2F7">
            <label class="form-label">安全确认</label>
            <input type="text" id="restore-confirm" class="form-input" placeholder="请输入确认码: RESTORE_CONFIRM" autocomplete="off">
            <div class="form-hint">输入 <code style="background:#FEF3C7;padding:1px 6px;border-radius:4px;color:#92400E">RESTORE_CONFIRM</code> 确认执行恢复操作</div>
        </div>

        <div style="padding:12px;background:#FEF3C7;border-radius:8px;margin-top:12px;font-size:12px;color:#92400E;line-height:1.6">
            ⚠ <strong>注意</strong>：恢复操作将覆盖当前数据库中的所有数据。系统会在恢复前自动创建一份当前数据的安全备份，如果恢复出问题可以用安全备份回滚。
        </div>

        <div class="btn-row">
            <button class="btn-lg btn-gray" onclick="hideRestoreModal()">取消</button>
            <button class="btn-lg btn-orange" id="btn-restore" onclick="doRestore()">🔄 执行恢复</button>
        </div>
        <div id="restore-status" style="margin-top:12px;font-size:12px"></div>
    </div>
</div>

<!-- ========== 预览弹窗 ========== -->
<div id="modal-preview" class="modal-mask">
    <div class="modal-box" style="max-width:700px">
        <div class="modal-head">
            <div class="modal-title">👁 备份文件预览</div>
            <button class="modal-close" onclick="document.getElementById('modal-preview').style.display='none'">&times;</button>
        </div>
        <div id="preview-body"></div>
    </div>
</div>

<style>
.tab-btn{padding:8px 16px;border-radius:8px;border:1px solid #E2E8F0;background:#F7FAFC;font-size:13px;cursor:pointer;color:#718096;transition:all .2s}
.tab-btn.active{background:#FF8C42;color:#fff;border-color:#FF8C42}
</style>

<script>
var BACKUP_API = '../backend/admin/backup.php';

// ====== 备份数据库 ======
async function doBackupDb() {
    var btn = document.getElementById('btn-backup-db');
    var status = document.getElementById('backup-db-status');
    btn.disabled = true; btn.textContent = '⏳ 备份中...';
    status.textContent = '';

    try {
        var resp = await fetch(BACKUP_API + '?action=backup_db', {method:'POST'});
        var data = await resp.json();
        if (data.code === 0) {
            status.innerHTML = '<span style="color:#48BB78">✅ ' + data.msg + '</span>';
            showToast('数据库备份成功', 'success');
            loadBackupFiles();
        } else {
            status.innerHTML = '<span style="color:#F56565">❌ ' + data.msg + '</span>';
            showToast(data.msg, 'error');
        }
    } catch(e) {
        status.innerHTML = '<span style="color:#F56565">❌ 请求失败</span>';
        showToast('备份请求失败', 'error');
    }
    btn.disabled = false; btn.textContent = '⚡ 立即备份';
}

// ====== 打包图片 ======
function doBackupImages() {
    var btn = document.getElementById('btn-backup-img');
    var status = document.getElementById('backup-img-status');
    btn.disabled = true; btn.textContent = '⏳ 打包中...';
    status.textContent = '';

    fetch(BACKUP_API + '?action=backup_images', {method:'POST'})
        .then(function(r) { return r.json(); })
        .then(function(data) {
            btn.disabled = false; btn.textContent = '📦 打包下载';
            if (data.code === 0 && data.data && data.data.download_url) {
                status.innerHTML = '<span style="color:#48BB78">✅ ' + data.msg + ' (' + data.data.file_count + ' 张图片, ' + formatSize(data.data.file_size) + ')</span>';
                // 触发下载
                window.location.href = BACKUP_API + '?action=download&file=' + encodeURIComponent(data.data.filename);
                loadBackupFiles();
            } else {
                status.innerHTML = '<span style="color:#F56565">❌ ' + (data.msg || '打包失败') + '</span>';
            }
        })
        .catch(function() {
            btn.disabled = false; btn.textContent = '📦 打包下载';
            status.innerHTML = '<span style="color:#F56565">❌ 请求失败</span>';
        });
}

// ====== JSON 导出 ======
function doExportJson() {
    window.location.href = BACKUP_API + '?action=export_json';
}

// ====== CSV 导出 ======
function doExportCsv() {
    window.location.href = BACKUP_API + '?action=export_csv';
}

// ====== 加载备份文件列表 ======
async function loadBackupFiles() {
    try {
        var resp = await fetch(BACKUP_API + '?action=list');
        var data = await resp.json();
        if (data.code !== 0) return;

        var list = data.data.list || [];
        var container = document.getElementById('file-list');
        var countEl = document.getElementById('files-count');

        if (list.length === 0) {
            container.innerHTML = '<div style="padding:40px;text-align:center;color:#A0AEC0"><div style="font-size:28px;margin-bottom:8px">📭</div><div>暂无备份文件</div></div>';
            countEl.textContent = '共 0 个备份';
            return;
        }

        var totalSize = 0;
        list.forEach(function(f) { totalSize += (f.file_size || 0); });
        countEl.textContent = '共 ' + list.length + ' 个备份 · 总占用 ' + formatSize(totalSize);

        var html = '';
        list.forEach(function(f) {
            var ext = (f.filename || '').split('.').pop().toLowerCase();
            var iconClass = ext === 'sql' ? 'sql' : (ext === 'zip' ? 'zip' : 'json');
            var icon = ext === 'sql' ? '🗄' : (ext === 'zip' ? '🖼' : '📊');
            var tagClass = f.method === 'auto' ? 'tag-auto' : (f.method === 'restore_safety' ? 'tag-safety' : 'tag-manual');
            var tagText = f.method === 'auto' ? '自动' : (f.method === 'restore_safety' ? '恢复前备份' : '手动');

            html += '<div class="file-row">';
            html += '  <div class="file-icon ' + iconClass + '">' + icon + '</div>';
            html += '  <div class="file-info">';
            html += '    <div class="file-name">' + escHtml(f.filename) + ' <span class="file-tag ' + tagClass + '">' + tagText + '</span></div>';
            html += '    <div class="file-meta">';
            html += '      <span>📅 ' + formatDate(f.created_at) + '</span>';
            html += '      <span>💾 ' + formatSize(f.file_size || 0) + '</span>';
            html += '    </div>';
            html += '  </div>';
            html += '  <div class="file-btns">';
            if (ext === 'sql') {
                html += '    <a class="fbtn" title="预览" onclick="previewBackup(\'' + escHtml(f.filename) + '\')">👁</a>';
            }
            html += '    <a class="fbtn" title="下载" href="' + BACKUP_API + '?action=download&file=' + encodeURIComponent(f.filename) + '">⬇</a>';
            html += '    <a class="fbtn danger" title="删除" onclick="deleteBackup(\'' + escHtml(f.filename) + '\', ' + (f.id || 0) + ')">🗑</a>';
            html += '  </div>';
            html += '</div>';
        });

        container.innerHTML = html;
    } catch(e) {
        document.getElementById('file-list').innerHTML = '<div style="padding:20px;text-align:center;color:#F56565">加载失败</div>';
    }
}

// ====== 预览备份 ======
async function previewBackup(filename) {
    try {
        var resp = await fetch(BACKUP_API + '?action=preview&file=' + encodeURIComponent(filename));
        var data = await resp.json();
        if (data.code !== 0) { showToast(data.msg, 'error'); return; }

        var d = data.data;
        var html = '<div style="margin-bottom:12px">';
        html += '<div style="font-size:13px;font-weight:600;margin-bottom:8px">📄 ' + escHtml(filename) + '</div>';
        html += '<div style="display:flex;gap:16px;font-size:12px;color:#718096;margin-bottom:12px">';
        html += '<span>💾 ' + formatSize(d.file_size) + '</span>';
        html += '<span>📋 ' + d.table_count + ' 张表</span>';
        html += '<span>📝 ' + d.insert_count + ' 条 INSERT</span>';
        html += '</div>';
        if (d.tables && d.tables.length > 0) {
            html += '<div style="margin-bottom:12px"><span style="font-size:12px;font-weight:600">包含的表：</span>';
            html += '<div style="display:flex;flex-wrap:wrap;gap:4px;margin-top:6px">';
            d.tables.forEach(function(t) {
                html += '<span style="background:#EDF2F7;padding:2px 8px;border-radius:4px;font-size:11px;color:#4A5568">' + escHtml(t) + '</span>';
            });
            html += '</div></div>';
        }
        html += '</div>';
        html += '<div class="preview-box">' + escHtml(d.preview) + '</div>';

        document.getElementById('preview-body').innerHTML = html;
        document.getElementById('modal-preview').style.display = 'flex';
    } catch(e) {
        showToast('预览失败', 'error');
    }
}

// ====== 删除备份 ======
async function deleteBackup(filename, id) {
    if (!confirm('确定要删除备份文件 ' + filename + ' 吗？')) return;
    try {
        var body = id > 0 ? {id: id} : {filename: filename};
        var resp = await fetch(BACKUP_API + '?action=delete', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(body)
        });
        var data = await resp.json();
        if (data.code === 0) {
            showToast('删除成功', 'success');
            loadBackupFiles();
        } else {
            showToast(data.msg, 'error');
        }
    } catch(e) {
        showToast('删除失败', 'error');
    }
}

// ====== 恢复弹窗 ======
function showRestoreModal() {
    document.getElementById('modal-restore').style.display = 'flex';
    loadRestoreSelect();
    document.getElementById('restore-confirm').value = '';
    document.getElementById('restore-status').innerHTML = '';
}

function hideRestoreModal() {
    document.getElementById('modal-restore').style.display = 'none';
}

function switchRestoreTab(el) {
    document.querySelectorAll('.tab-btn').forEach(function(b) { b.classList.remove('active'); });
    el.classList.add('active');
    document.querySelectorAll('.tab-content').forEach(function(c) { c.style.display = 'none'; });
    document.getElementById('tab-' + el.dataset.tab).style.display = 'block';
}

async function loadRestoreSelect() {
    var sel = document.getElementById('restore-select');
    sel.innerHTML = '<option value="">加载中...</option>';
    try {
        var resp = await fetch(BACKUP_API + '?action=list');
        var data = await resp.json();
        sel.innerHTML = '<option value="">-- 请选择 --</option>';
        if (data.code === 0 && data.data.list) {
            data.data.list.forEach(function(f) {
                var ext = (f.filename || '').split('.').pop().toLowerCase();
                if (ext === 'sql') {
                    var opt = document.createElement('option');
                    opt.value = f.filename;
                    opt.textContent = f.filename + ' (' + formatSize(f.file_size || 0) + ', ' + formatDate(f.created_at) + ')';
                    sel.appendChild(opt);
                }
            });
        }
    } catch(e) {
        sel.innerHTML = '<option value="">加载失败</option>';
    }
}

// 选择备份文件时自动预览
document.getElementById('restore-select').addEventListener('change', function() {
    var file = this.value;
    var previewDiv = document.getElementById('restore-preview');
    if (file) {
        previewDiv.style.display = 'block';
        document.getElementById('restore-preview-content').textContent = '加载预览...';
        fetch(BACKUP_API + '?action=preview&file=' + encodeURIComponent(file))
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.code === 0) {
                    var d = data.data;
                    var text = '表: ' + d.table_count + ' 张, 数据: ' + d.insert_count + ' 条\n';
                    text += '包含: ' + (d.tables || []).join(', ') + '\n\n';
                    text += d.preview || '';
                    document.getElementById('restore-preview-content').textContent = text;
                }
            })
            .catch(function() {
                document.getElementById('restore-preview-content').textContent = '预览加载失败';
            });
    } else {
        previewDiv.style.display = 'none';
    }
});

// ====== 执行恢复 ======
async function doRestore() {
    var confirmCode = document.getElementById('restore-confirm').value.trim();
    if (confirmCode !== 'RESTORE_CONFIRM') {
        showToast('请输入正确的确认码: RESTORE_CONFIRM', 'error');
        return;
    }

    var btn = document.getElementById('btn-restore');
    var status = document.getElementById('restore-status');
    btn.disabled = true; btn.textContent = '⏳ 恢复中...';
    status.innerHTML = '<div style="color:#ED8936">⏳ 正在恢复，请勿关闭页面...</div>';

    var formData = new FormData();
    formData.append('action', 'restore');
    formData.append('confirm_code', confirmCode);

    // 判断是选择文件还是上传文件
    var activeTab = document.querySelector('.tab-btn.active').dataset.tab;
    if (activeTab === 'from-list') {
        var selectedFile = document.getElementById('restore-select').value;
        if (!selectedFile) {
            status.innerHTML = '<div style="color:#F56565">请选择备份文件</div>';
            btn.disabled = false; btn.textContent = '🔄 执行恢复';
            return;
        }
        formData.append('file', selectedFile);
    } else {
        var fileInput = document.getElementById('restore-file');
        if (!fileInput.files || !fileInput.files[0]) {
            status.innerHTML = '<div style="color:#F56565">请选择要上传的 .sql 文件</div>';
            btn.disabled = false; btn.textContent = '🔄 执行恢复';
            return;
        }
        formData.append('backup_file', fileInput.files[0]);
    }

    try {
        var resp = await fetch(BACKUP_API + '?action=restore', {
            method: 'POST',
            body: formData
        });
        var data = await resp.json();
        if (data.code === 0) {
            status.innerHTML = '<div style="color:#48BB78">✅ ' + data.msg + '</div>';
            showToast('数据恢复成功！', 'success');
            loadBackupFiles();
        } else {
            status.innerHTML = '<div style="color:#F56565">❌ ' + data.msg + '</div>';
            showToast(data.msg, 'error');
        }
    } catch(e) {
        status.innerHTML = '<div style="color:#F56565">❌ 请求失败: ' + e.message + '</div>';
        showToast('恢复请求失败', 'error');
    }
    btn.disabled = false; btn.textContent = '🔄 执行恢复';
}

// ====== 工具函数 ======
function formatSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
    return (bytes / 1073741824).toFixed(2) + ' GB';
}

function formatDate(ts) {
    if (!ts) return '-';
    var d = new Date(ts * 1000);
    var pad = function(n) { return n < 10 ? '0' + n : n; };
    return d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}

function escHtml(s) {
    if (!s) return '';
    var div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
}

// 初始化
loadBackupFiles();
</script>
