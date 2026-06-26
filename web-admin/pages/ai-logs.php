<?php
/**
 * AI 调用日志页面
 */
$db = getDB();

// 筛选参数
$filterDate = $_GET['date'] ?? '';
$filterStatus = $_GET['status'] ?? '';
$filterUser = $_GET['user'] ?? '';
$page = max(1, intval($_GET['page'] ?? 1));
$pageSize = 20;

$where = [];
$params = [];
if ($filterDate) {
    $startTs = strtotime($filterDate);
    $endTs = $startTs + 86400;
    $where[] = "l.created_at >= ? AND l.created_at < ?";
    $params[] = $startTs;
    $params[] = $endTs;
}
if ($filterStatus !== '') {
    $where[] = "l.status = ?";
    $params[] = intval($filterStatus);
}
if ($filterUser) {
    $where[] = "u.username LIKE ?";
    $params[] = "%$filterUser%";
}

$whereStr = $where ? 'WHERE ' . implode(' AND ', $where) : '';

// 总数
$countSql = "SELECT COUNT(*) as cnt FROM ai_call_log l LEFT JOIN sys_user u ON l.user_id = u.id $whereStr";
$countStmt = $db->prepare($countSql);
$countStmt->execute($params);
$total = $countStmt->fetch()['cnt'];
$totalPages = max(1, ceil($total / $pageSize));

// 列表
$offset = ($page - 1) * $pageSize;
$sql = "SELECT l.*, u.username, u.nickname
        FROM ai_call_log l
        LEFT JOIN sys_user u ON l.user_id = u.id
        $whereStr
        ORDER BY l.created_at DESC
        LIMIT $pageSize OFFSET $offset";
$stmt = $db->prepare($sql);
$stmt->execute($params);
$logs = $stmt->fetchAll();

// 统计
$stats = $db->query("SELECT
    COUNT(*) as total_calls,
    SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as success_calls,
    SUM(total_tokens) as total_tokens,
    SUM(CASE WHEN DATE(FROM_UNIXTIME(created_at)) = CURDATE() THEN 1 ELSE 0 END) as today_calls
    FROM ai_call_log")->fetch();
?>

<style>
.ai-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:24px}
.stat-card{background:#fff;border-radius:12px;padding:20px;border:1px solid #E2E8F0;box-shadow:0 1px 3px rgba(0,0,0,.04)}
.stat-card .label{font-size:12px;color:#718096;margin-bottom:8px}
.stat-card .value{font-size:24px;font-weight:700;color:#2D3748}
.stat-card .sub{font-size:11px;color:#A0AEC0;margin-top:4px}
.stat-card.success .value{color:#48BB78}
.stat-card.warning .value{color:#ED8936}
.stat-card.info .value{color:#5B9FED}
.log-card{background:#fff;border-radius:12px;border:1px solid #E2E8F0;box-shadow:0 1px 3px rgba(0,0,0,.04);overflow:hidden}
.log-table{width:100%;border-collapse:collapse}
.log-table th{background:#F7FAFC;padding:12px 16px;text-align:left;font-size:12px;color:#718096;font-weight:600;text-transform:uppercase;letter-spacing:.3px;border-bottom:1px solid #E2E8F0}
.log-table td{padding:12px 16px;font-size:13px;border-bottom:1px solid #F0F4F8;vertical-align:middle}
.log-table tr:hover td{background:#FAFBFC}
.badge{display:inline-flex;align-items:center;padding:3px 8px;border-radius:6px;font-size:11px;font-weight:600;gap:4px}
.badge-success{background:rgba(72,187,120,.12);color:#22543D}
.badge-fail{background:rgba(245,101,101,.12);color:#9B2C2C}
.badge-provider{background:rgba(91,159,237,.12);color:#2C5282}
.token-info{font-family:monospace;font-size:11px;color:#718096}
.filter-bar{display:flex;gap:12px;margin-bottom:16px;flex-wrap:wrap;align-items:center}
.filter-bar input,.filter-bar select{padding:8px 12px;border:1px solid #E2E8F0;border-radius:8px;font-size:13px}
.filter-bar input:focus,.filter-bar select:focus{outline:none;border-color:#FF8C42;box-shadow:0 0 0 3px rgba(255,140,66,.1)}
.pagination{display:flex;justify-content:center;gap:8px;padding:16px}
.pagination a,.pagination span{padding:6px 12px;border-radius:6px;font-size:13px;text-decoration:none}
.pagination a{background:#F7FAFC;color:#4A5568;border:1px solid #E2E8F0}
.pagination a:hover{background:#FFF1E0;color:#FF8C42;border-color:#FF8C42}
.pagination .current{background:#FF8C42;color:#fff}
.detail-row{display:none;background:#F7FAFC}
.detail-row.open{display:table-row}
.detail-content{padding:16px;font-size:12px;font-family:monospace;color:#4A5568;white-space:pre-wrap;max-height:300px;overflow-y:auto}
.toggle-btn{background:none;border:none;color:#5B9FED;cursor:pointer;font-size:12px;padding:4px 8px;border-radius:4px}
.toggle-btn:hover{background:rgba(91,159,237,.1)}
</style>

<!-- 统计卡片 -->
<div class="ai-stats">
    <div class="stat-card info">
        <div class="label">📊 总调用次数</div>
        <div class="value"><?= number_format($stats['total_calls'] ?? 0) ?></div>
        <div class="sub">全部识别请求</div>
    </div>
    <div class="stat-card">
        <div class="label">📅 今日调用</div>
        <div class="value"><?= number_format($stats['today_calls'] ?? 0) ?></div>
        <div class="sub"><?= date('Y-m-d') ?></div>
    </div>
    <div class="stat-card success">
        <div class="label">✅ 成功率</div>
        <div class="value"><?= ($stats['total_calls'] ?? 0) > 0 ? round(($stats['success_calls'] ?? 0) / $stats['total_calls'] * 100, 1) : 0 ?>%</div>
        <div class="sub"><?= number_format($stats['success_calls'] ?? 0) ?> / <?= number_format($stats['total_calls'] ?? 0) ?></div>
    </div>
    <div class="stat-card warning">
        <div class="label">🪙 Token 消耗</div>
        <div class="value"><?= number_format($stats['total_tokens'] ?? 0) ?></div>
        <div class="sub">累计 Token</div>
    </div>
</div>

<!-- 筛选栏 -->
<form method="GET" class="filter-bar">
    <input type="hidden" name="p" value="ai-logs">
    <input type="date" name="date" value="<?= htmlspecialchars($filterDate) ?>" placeholder="按日期筛选">
    <select name="status">
        <option value="">全部状态</option>
        <option value="1" <?= $filterStatus === '1' ? 'selected' : '' ?>>成功</option>
        <option value="0" <?= $filterStatus === '0' ? 'selected' : '' ?>>失败</option>
    </select>
    <input type="text" name="user" value="<?= htmlspecialchars($filterUser) ?>" placeholder="用户名筛选">
    <button type="submit" class="btn btn-primary btn-sm">🔍 筛选</button>
    <a href="?p=ai-logs" class="btn btn-outline btn-sm">重置</a>
    <span style="margin-left:auto;font-size:12px;color:#718096">共 <?= $total ?> 条记录</span>
</form>

<!-- 日志列表 -->
<div class="log-card">
    <table class="log-table">
        <thead>
            <tr>
                <th style="width:40px"></th>
                <th>时间</th>
                <th>用户</th>
                <th>类型</th>
                <th>AI 服务商</th>
                <th>Token</th>
                <th>耗时</th>
                <th>状态</th>
            </tr>
        </thead>
        <tbody>
            <?php if (empty($logs)): ?>
            <tr><td colspan="8" style="text-align:center;padding:40px;color:#A0AEC0">暂无 AI 调用日志</td></tr>
            <?php else: foreach ($logs as $log): ?>
            <tr>
                <td><button class="toggle-btn" onclick="toggleDetail('detail-<?= $log['id'] ?>')">📋</button></td>
                <td style="font-family:monospace;font-size:12px;"><?= date('Y-m-d H:i:s', $log['created_at']) ?></td>
                <td><?= htmlspecialchars($log['nickname'] ?: $log['username'] ?? '-') ?></td>
                <td><span class="badge <?= $log['type'] === 'confirm' ? 'badge-success' : 'badge-provider' ?>"><?= $log['type'] === 'confirm' ? '✅ 入库' : '🔍 识别' ?></span></td>
                <td><span class="badge badge-provider"><?= htmlspecialchars($log['ai_provider'] ?: '-') ?> / <?= htmlspecialchars($log['ai_model'] ?: '-') ?></span></td>
                <td class="token-info"><?= number_format($log['total_tokens'] ?? 0) ?></td>
                <td style="font-family:monospace;"><?= $log['duration'] ?>ms</td>
                <td>
                    <?php if ($log['status']): ?>
                        <span class="badge badge-success">✓ 成功</span>
                    <?php else: ?>
                        <span class="badge badge-fail">✗ 失败</span>
                    <?php endif; ?>
                </td>
            </tr>
            <tr class="detail-row" id="detail-<?= $log['id'] ?>">
                <td colspan="8">
                    <div class="detail-content">
                        <strong>图片:</strong> <?= htmlspecialchars($log['image_url'] ?? '-') ?>\n
                        <strong>输入Token:</strong> <?= $log['prompt_tokens'] ?> | <strong>输出Token:</strong> <?= $log['completion_tokens'] ?> | <strong>总计:</strong> <?= $log['total_tokens'] ?>\n
                        <?php if ($log['error_msg']): ?>
                        <strong>错误信息:</strong> <?= htmlspecialchars($log['error_msg']) ?>\n
                        <?php endif; ?>
                        <?php
                        // 获取工具调用记录
                        $toolStmt = $db->prepare("SELECT * FROM ai_tool_call_log WHERE call_id = ? ORDER BY created_at ASC");
                        $toolStmt->execute([$log['id']]);
                        $toolLogs = $toolStmt->fetchAll();
                        if ($toolLogs):
                        ?>
                        <strong>工具调用记录 (<?= count($toolLogs) ?> 次):</strong>\n
                        <?php foreach ($toolLogs as $tl): ?>
                        ┌ <?= htmlspecialchars($tl['tool_name']) ?> (<?= $tl['execute_time'] ?>ms) <?= $tl['status'] ? '✅' : '❌' ?>\n
                        │ 参数: <?= htmlspecialchars(mb_substr($tl['tool_params'] ?? '', 0, 200)) ?>\n
                        └ 结果: <?= htmlspecialchars(mb_substr($tl['tool_result'] ?? '', 0, 300)) ?>\n
                        <?php endforeach; ?>
                        <?php endif; ?>
                    </div>
                </td>
            </tr>
            <?php endforeach; endif; ?>
        </tbody>
    </table>
</div>

<!-- 分页 -->
<?php if ($totalPages > 1): ?>
<div class="pagination">
    <?php if ($page > 1): ?>
    <a href="?p=ai-logs&page=<?= $page - 1 ?>&date=<?= urlencode($filterDate) ?>&status=<?= urlencode($filterStatus) ?>&user=<?= urlencode($filterUser) ?>">← 上一页</a>
    <?php endif; ?>
    <?php for ($i = max(1, $page - 3); $i <= min($totalPages, $page + 3); $i++): ?>
    <?php if ($i === $page): ?>
    <span class="current"><?= $i ?></span>
    <?php else: ?>
    <a href="?p=ai-logs&page=<?= $i ?>&date=<?= urlencode($filterDate) ?>&status=<?= urlencode($filterStatus) ?>&user=<?= urlencode($filterUser) ?>"><?= $i ?></a>
    <?php endif; ?>
    <?php endfor; ?>
    <?php if ($page < $totalPages): ?>
    <a href="?p=ai-logs&page=<?= $page + 1 ?>&date=<?= urlencode($filterDate) ?>&status=<?= urlencode($filterStatus) ?>&user=<?= urlencode($filterUser) ?>">下一页 →</a>
    <?php endif; ?>
</div>
<?php endif; ?>

<script>
function toggleDetail(id) {
    var el = document.getElementById(id);
    if (el) el.classList.toggle('open');
}
</script>
