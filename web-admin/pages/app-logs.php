<?php
$db = getDB();

// 处理清空
if (isset($_GET['action']) && $_GET['action'] === 'clear') {
    $db->exec('DELETE FROM app_log');
    $msg = '日志已清空';
}

$page = max(1, intval($_GET['pg'] ?? 1));
$pageSize = 50;
$logType = $_GET['type'] ?? '';
$keyword = $_GET['keyword'] ?? '';

$where = [];
$params = [];
if ($logType) { $where[] = 'log_type = ?'; $params[] = $logType; }
if ($keyword) { $where[] = '(message LIKE ? OR tag LIKE ?)'; $params[] = "%$keyword%"; $params[] = "%$keyword%"; }

$whereStr = $where ? 'WHERE ' . implode(' AND ', $where) : '';

$countStmt = $db->prepare("SELECT COUNT(*) as cnt FROM app_log $whereStr");
$countStmt->execute($params);
$total = $countStmt->fetch()['cnt'];

$offset = ($page - 1) * $pageSize;
$stmt = $db->prepare("SELECT * FROM app_log $whereStr ORDER BY id DESC LIMIT $pageSize OFFSET $offset");
$stmt->execute($params);
$logs = $stmt->fetchAll();

// 统计
$totalAll = $db->query('SELECT COUNT(*) as cnt FROM app_log')->fetch()['cnt'];
$totalCrash = $db->query("SELECT COUNT(*) as cnt FROM app_log WHERE log_type = 'crash'")->fetch()['cnt'];
$totalError = $db->query("SELECT COUNT(*) as cnt FROM app_log WHERE log_type = 'error'")->fetch()['cnt'];
$totalToday = $db->query('SELECT COUNT(*) as cnt FROM app_log WHERE created_at > ' . (time() - 86400))->fetch()['cnt'];
?>

<style>
.log-stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px;margin-bottom:16px}
.stat-card{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:16px;text-align:center}
.stat-num{font-size:24px;font-weight:700;color:#FF8C42}
.stat-label{font-size:12px;color:#718096;margin-top:4px}
.log-table{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);overflow:hidden}
.log-item{padding:12px 16px;border-bottom:1px solid #EDF2F7;cursor:pointer;transition:background .15s}
.log-item:hover{background:#FFF7F0}
.log-item:last-child{border-bottom:none}
.log-header{display:flex;align-items:center;gap:8px;margin-bottom:4px}
.log-type{font-size:10px;font-weight:700;padding:2px 8px;border-radius:10px;text-transform:uppercase}
.log-type.crash{background:#FED7D7;color:#C53030}
.log-type.error{background:#FED7D7;color:#9B2C2C}
.log-type.api{background:#FEFCBF;color:#975A16}
.log-type.warn{background:#FEFCBF;color:#744210}
.log-type.info{background:#C6F6D5;color:#22543D}
.log-tag{font-size:11px;color:#718096}
.log-time{font-size:11px;color:#A0AEC0;margin-left:auto}
.log-msg{font-size:13px;color:#2D3748;line-height:1.4}
.log-device{font-size:11px;color:#A0AEC0;margin-top:4px}
.log-detail{display:none;padding:12px 16px;background:#F7FAFC;border-top:1px solid #EDF2F7;font-size:12px;font-family:monospace;color:#4A5568;white-space:pre-wrap;word-break:break-all;max-height:300px;overflow-y:auto}
.filter-bar{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:16px 20px;margin-bottom:16px}
</style>

<div class="page-header">
    <div>
        <div class="page-title">📱 APP端日志</div>
        <div class="page-desc">收集APP端的错误、崩溃和API异常日志</div>
    </div>
    <div style="display:flex;gap:8px">
        <a href="?p=app-logs&action=clear" class="btn btn-danger btn-sm" onclick="return confirm('确定清空所有日志？')">🗑 清空</a>
        <a href="?p=app-logs" class="btn btn-outline btn-sm">🔄 刷新</a>
    </div>
</div>

<div class="log-stats">
    <div class="stat-card"><div class="stat-num"><?= $totalAll ?></div><div class="stat-label">总日志</div></div>
    <div class="stat-card"><div class="stat-num" style="color:#F56565"><?= $totalCrash ?></div><div class="stat-label">崩溃</div></div>
    <div class="stat-card"><div class="stat-num" style="color:#ED8936"><?= $totalError ?></div><div class="stat-label">错误</div></div>
    <div class="stat-card"><div class="stat-num" style="color:#48BB78"><?= $totalToday ?></div><div class="stat-label">今日</div></div>
</div>

<div class="filter-bar">
    <form method="GET" style="display:flex;gap:12px;align-items:flex-end;flex-wrap:wrap">
        <input type="hidden" name="p" value="app-logs">
        <div class="form-group" style="margin:0">
            <label class="form-label">类型</label>
            <select name="type" class="form-control" style="min-width:120px">
                <option value="">全部</option>
                <option value="crash" <?= $logType === 'crash' ? 'selected' : '' ?>>💥 崩溃</option>
                <option value="error" <?= $logType === 'error' ? 'selected' : '' ?>>❌ 错误</option>
                <option value="api" <?= $logType === 'api' ? 'selected' : '' ?>>🌐 API</option>
                <option value="warn" <?= $logType === 'warn' ? 'selected' : '' ?>>⚠️ 警告</option>
                <option value="info" <?= $logType === 'info' ? 'selected' : '' ?>>ℹ️ 信息</option>
            </select>
        </div>
        <div class="form-group" style="margin:0">
            <label class="form-label">关键词</label>
            <input name="keyword" class="form-control" placeholder="搜索消息/标签..." value="<?= htmlspecialchars($keyword) ?>">
        </div>
        <button type="submit" class="btn btn-primary btn-sm">🔍 搜索</button>
        <a href="?p=app-logs" class="btn btn-outline btn-sm">↻ 重置</a>
    </form>
</div>

<div class="log-table">
    <?php if (empty($logs)): ?>
    <div style="padding:40px;text-align:center;color:#A0AEC0">
        <div style="font-size:40px;margin-bottom:12px">📋</div>
        <div>暂无日志</div>
    </div>
    <?php else: foreach ($logs as $log): ?>
    <div class="log-item" onclick="toggleDetail('log-<?= $log['id'] ?>')">
        <div class="log-header">
            <span class="log-type <?= htmlspecialchars($log['log_type']) ?>"><?= htmlspecialchars($log['log_type']) ?></span>
            <span class="log-tag"><?= htmlspecialchars($log['tag']) ?></span>
            <span class="log-time"><?= date('m-d H:i:s', $log['created_at']) ?></span>
        </div>
        <div class="log-msg"><?= htmlspecialchars(mb_substr($log['message'], 0, 200)) ?></div>
        <div class="log-device">👤 <?= $log['user_id'] ?> · 📱 <?= htmlspecialchars($log['device_info']) ?> · 📦 v<?= htmlspecialchars($log['app_version']) ?></div>
    </div>
    <div id="log-<?= $log['id'] ?>" class="log-detail"><?= htmlspecialchars($log['stack_trace'] ?: $log['message']) ?></div>
    <?php endforeach; endif; ?>
</div>

<?php if ($total > $pageSize): ?>
<div class="pagination" style="margin-top:16px">
    <div class="page-info">共 <?= $total ?> 条 · 第 <?= $page ?> / <?= ceil($total / $pageSize) ?> 页</div>
    <div class="page-controls">
        <?php if ($page > 1): ?>
        <a href="?p=app-logs&pg=<?= $page-1 ?>&type=<?= urlencode($logType) ?>&keyword=<?= urlencode($keyword) ?>" class="page-btn">‹</a>
        <?php endif; ?>
        <?php if ($page < ceil($total / $pageSize)): ?>
        <a href="?p=app-logs&pg=<?= $page+1 ?>&type=<?= urlencode($logType) ?>&keyword=<?= urlencode($keyword) ?>" class="page-btn">›</a>
        <?php endif; ?>
    </div>
</div>
<?php endif; ?>

<script>
function toggleDetail(id) {
    var el = document.getElementById(id);
    el.style.display = el.style.display === 'block' ? 'none' : 'block';
}
</script>
