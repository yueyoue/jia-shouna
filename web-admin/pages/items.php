<?php
$db = getDB();
$page = max(1, intval($_GET['pg'] ?? 1));
$pageSize = 20;
$keyword = $_GET['keyword'] ?? '';
$category = $_GET['category'] ?? '';
$privacy = $_GET['privacy'] ?? '';

$where = ["g.status = 1"];
$params = [];
if ($keyword) { $where[] = "(g.name LIKE ? OR g.barcode LIKE ?)"; $kw = "%$keyword%"; $params[] = $kw; $params[] = $kw; }
if ($category) { $where[] = "g.category = ?"; $params[] = $category; }
if ($privacy === 'private') { $where[] = "g.is_private = 1"; }
elseif ($privacy === 'shared') { $where[] = "g.is_private = 0"; }

$whereStr = implode(' AND ', $where);
$countStmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods g WHERE $whereStr");
$countStmt->execute($params);
$total = $countStmt->fetch()['cnt'];

$offset = ($page - 1) * $pageSize;
$stmt = $db->prepare("SELECT g.*, s.name as space_name FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id WHERE $whereStr ORDER BY g.updated_at DESC LIMIT $pageSize OFFSET $offset");
$stmt->execute($params);
$items = $stmt->fetchAll();

$categories = ['食品', '衣物', '药品', '日用品', '数码', '证件', '厨具', '其他'];
?>

<style>
.filter-bar{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);padding:16px 20px;margin-bottom:16px}
.filter-row{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;align-items:flex-end}
.filter-row .form-group{margin-bottom:0}
.filter-actions{display:flex;gap:8px;margin-top:12px;padding-top:12px;border-top:1px dashed #EDF2F7}
.items-table{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);overflow:hidden}
.table-toolbar{padding:12px 16px;display:flex;align-items:center;gap:8px;border-bottom:1px solid var(--border-2);background:#FAFBFC}
.table-toolbar .left{display:flex;align-items:center;gap:10px}
.table-toolbar .right{margin-left:auto;display:flex;gap:6px}
.item-img{width:40px;height:40px;border-radius:8px;background:linear-gradient(135deg,#FFE8D6,#FFD3B0);display:flex;align-items:center;justify-content:center;font-size:22px;flex-shrink:0}
.item-info{display:flex;align-items:center;gap:10px}
.item-name{font-size:13px;font-weight:600;line-height:1.3}
.item-meta{font-size:11px;color:#A0AEC0;margin-top:2px}
.path-cell{font-size:11px;color:#718096;line-height:1.5}
.path-cell .arrow{color:#CBD5E0;margin:0 2px}
.expire-cell{font-size:12px;font-weight:500}
.expire-cell.danger{color:#F56565}
.expire-cell.warning{color:#ED8936}
.expire-cell.success{color:#48BB78}
.expire-cell.muted{color:#A0AEC0}
td .row-actions{display:flex;gap:4px;opacity:0;transition:opacity .15s}
tr:hover td .row-actions{opacity:1}
.row-actions .icon-mini{width:24px;height:24px;border-radius:4px;display:flex;align-items:center;justify-content:center;font-size:12px;color:#718096;cursor:pointer}
.row-actions .icon-mini:hover{background:#FFF1E0;color:#FF8C42}
.pagination{padding:12px 16px;display:flex;align-items:center;justify-content:space-between;border-top:1px solid var(--border-2);background:#FAFBFC}
.page-info{font-size:12px;color:#718096}
.page-controls{display:flex;align-items:center;gap:4px}
.page-btn{min-width:32px;height:32px;padding:0 8px;border-radius:6px;background:#fff;border:1px solid var(--border);display:flex;align-items:center;justify-content:center;font-size:12px;color:#4A5568;cursor:pointer;transition:all .15s}
.page-btn:hover{border-color:#FF8C42;color:#FF8C42}
.page-btn.active{background:#FF8C42;color:#fff;border-color:#FF8C42}
.page-btn:disabled{opacity:.4;cursor:not-allowed}
</style>

<div class="page-header">
    <div>
        <div class="page-title">物品信息管理</div>
        <div class="page-desc">共 <?= number_format($total) ?> 件物品 · 多条件筛选</div>
    </div>
</div>

<!-- Filter bar - 照抄 UI -->
<div class="filter-bar">
    <form method="GET">
        <input type="hidden" name="p" value="items">
        <div class="filter-row">
            <div class="form-group">
                <label class="form-label">物品名称</label>
                <input class="form-control" name="keyword" placeholder="搜索名称/条码..." value="<?= htmlspecialchars($keyword) ?>">
            </div>
            <div class="form-group">
                <label class="form-label">物品分类</label>
                <select name="category" class="form-control">
                    <option value="">全部分类</option>
                    <?php foreach ($categories as $cat): ?>
                    <option value="<?= $cat ?>" <?= $category === $cat ? 'selected' : '' ?>><?= $cat ?></option>
                    <?php endforeach; ?>
                </select>
            </div>
            <div class="form-group">
                <label class="form-label">可见性</label>
                <select name="privacy" class="form-control">
                    <option value="">全部</option>
                    <option value="shared" <?= $privacy === 'shared' ? 'selected' : '' ?>>👁 正常可见</option>
                    <option value="private" <?= $privacy === 'private' ? 'selected' : '' ?>>🔒 隐藏物品</option>
                </select>
            </div>
        </div>
        <div class="filter-actions">
            <button type="submit" class="btn btn-primary btn-sm">🔍 搜索</button>
            <a href="?p=items" class="btn btn-outline btn-sm">↻ 重置</a>
        </div>
    </form>
</div>

<!-- Items table - 照抄 UI -->
<div class="items-table">
    <div class="table-toolbar">
        <div class="left">
            <strong style="font-size:14px">物品列表</strong>
            <span style="font-size:12px;color:#A0AEC0">· 显示 <?= $offset + 1 ?>-<?= min($offset + $pageSize, $total) ?> / <?= number_format($total) ?></span>
        </div>
        <div class="right">
            <button class="btn btn-ghost btn-sm" onclick="location.reload()">🔄 刷新</button>
        </div>
    </div>

    <table>
        <thead>
            <tr>
                <th>物品信息</th>
                <th>分类</th>
                <th>存放位置</th>
                <th>数量</th>
                <th>保质期</th>
                <th>可见性</th>
                <th style="width:80px">操作</th>
            </tr>
        </thead>
        <tbody>
            <?php if (empty($items)): ?>
            <tr><td colspan="7" class="empty"><div class="empty-icon">📦</div><div>暂无物品数据</div></td></tr>
            <?php else: foreach ($items as $item): ?>
            <tr>
                <td>
                    <div class="item-info">
                        <div class="item-img">📦</div>
                        <div>
                            <div class="item-name"><?= htmlspecialchars($item['name']) ?></div>
                            <div class="item-meta"><?= $item['barcode'] ? '条码: '.$item['barcode'] : '' ?></div>
                        </div>
                    </div>
                </td>
                <td><span class="tag tag-orange"><?= $item['category'] ?: '-' ?></span></td>
                <td><div class="path-cell"><?= htmlspecialchars($item['space_name'] ?? '-') ?></div></td>
                <td><strong><?= $item['quantity'] ?></strong> <?= $item['unit'] ?></td>
                <td>
                    <?php if ($item['expiry_date']):
                        $daysLeft = floor((strtotime($item['expiry_date']) - time()) / 86400);
                    ?>
                        <span class="expire-cell <?= $daysLeft <= 0 ? 'danger' : ($daysLeft <= 7 ? 'warning' : 'success') ?>">
                            <?= $daysLeft <= 0 ? '⚠ 已过期' : ($daysLeft <= 7 ? '⏰ '.$daysLeft.'天后' : $item['expiry_date']) ?>
                        </span>
                    <?php else: ?>
                        <span class="expire-cell muted">— 无</span>
                    <?php endif; ?>
                </td>
                <td>
                    <?php if ($item['is_private']): ?>
                        <span style="display:inline-flex;align-items:center;gap:3px;padding:2px 8px;border-radius:4px;background:rgba(159,122,234,.12);color:#553C9A;font-size:11px;font-weight:600">🔒 隐藏</span>
                    <?php else: ?>
                        <span style="font-size:11px;color:#A0AEC0">👁 可见</span>
                    <?php endif; ?>
                </td>
                <td>
                    <div class="row-actions">
                        <a href="?p=items&action=edit&id=<?= $item['id'] ?>" class="icon-mini" title="编辑">✎</a>
                        <div class="icon-mini" title="删除" onclick="deleteItem(<?= $item['id'] ?>)">🗑</div>
                    </div>
                </td>
            </tr>
            <?php endforeach; endif; ?>
        </tbody>
    </table>

    <!-- Pagination -->
    <div class="pagination">
        <div class="page-info">共 <strong><?= number_format($total) ?></strong> 条 · 第 <strong><?= $page ?></strong> / <?= ceil($total / $pageSize) ?> 页</div>
        <div class="page-controls">
            <?php if ($page > 1): ?>
            <a href="?p=items&pg=<?= $page-1 ?>&keyword=<?= urlencode($keyword) ?>&category=<?= urlencode($category) ?>&privacy=<?= urlencode($privacy) ?>" class="page-btn">‹</a>
            <?php else: ?>
            <button class="page-btn" disabled>‹</button>
            <?php endif; ?>
            
            <?php
            $totalPages = ceil($total / $pageSize);
            $start = max(1, $page - 2);
            $end = min($totalPages, $start + 4);
            if ($end - $start < 4) $start = max(1, $end - 4);
            for ($i = $start; $i <= $end; $i++):
            ?>
            <a href="?p=items&pg=<?= $i ?>&keyword=<?= urlencode($keyword) ?>&category=<?= urlencode($category) ?>&privacy=<?= urlencode($privacy) ?>" class="page-btn <?= $i === $page ? 'active' : '' ?>"><?= $i ?></a>
            <?php endfor; ?>
            
            <?php if ($page < $totalPages): ?>
            <a href="?p=items&pg=<?= $page+1 ?>&keyword=<?= urlencode($keyword) ?>&category=<?= urlencode($category) ?>&privacy=<?= urlencode($privacy) ?>" class="page-btn">›</a>
            <?php else: ?>
            <button class="page-btn" disabled>›</button>
            <?php endif; ?>
        </div>
    </div>
</div>

<script>
async function deleteItem(id) {
    if (!confirm('确定要删除此物品吗？')) return;
    const resp = await postJSON('../backend/api/goods.php?action=delete', {id: id});
    if (resp !== null) { showToast('删除成功', 'success'); location.reload(); }
}
</script>
