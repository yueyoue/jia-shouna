<?php
$db = getDB();
$page = max(1, intval($_GET['pg'] ?? 1));
$pageSize = 20;
$keyword = $_GET['keyword'] ?? '';
$category = $_GET['category'] ?? '';
$privacy = $_GET['privacy'] ?? '';
$houseId = intval($_GET['house_id'] ?? 0);

$where = ["g.status = 1"];
$params = [];
if ($keyword) { $where[] = "(g.name LIKE ? OR g.barcode LIKE ?)"; $kw = "%$keyword%"; $params[] = $kw; $params[] = $kw; }
if ($category) { $where[] = "g.category = ?"; $params[] = $category; }
if ($privacy === 'private') { $where[] = "g.is_private = 1"; }
elseif ($privacy === 'shared') { $where[] = "g.is_private = 0"; }
if ($houseId) { $where[] = "g.house_id = ?"; $params[] = $houseId; }

$whereStr = implode(' AND ', $where);
$countStmt = $db->prepare("SELECT COUNT(*) as cnt FROM goods g WHERE $whereStr");
$countStmt->execute($params);
$total = $countStmt->fetch()['cnt'];

$offset = ($page - 1) * $pageSize;
$stmt = $db->prepare("SELECT g.*, s.name as space_name FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id WHERE $whereStr ORDER BY g.updated_at DESC LIMIT $pageSize OFFSET $offset");
$stmt->execute($params);
$items = $stmt->fetchAll();

// 分类列表
$categories = ['食品', '衣物', '药品', '日用品', '数码', '证件', '厨具', '其他'];
?>

<div class="card-header" style="margin-bottom: 16px;">
    <div>
        <h2 style="font-size: 18px;">物品信息管理</h2>
        <p style="color: #999; font-size: 12px;">共 <?= number_format($total) ?> 件物品</p>
    </div>
    <div style="display: flex; gap: 10px;">
        <button class="btn btn-outline" onclick="exportItems()">📤 导出</button>
        <button class="btn btn-outline" onclick="importItems()">📥 Excel导入</button>
    </div>
</div>

<!-- 筛选栏 -->
<div class="card" style="margin-bottom: 16px;">
    <form class="filter-bar" method="GET">
        <input type="hidden" name="p" value="items">
        <input type="text" name="keyword" class="form-control" placeholder="搜索物品名称/条码" value="<?= htmlspecialchars($keyword) ?>">
        <select name="category" class="form-control">
            <option value="">全部分类</option>
            <?php foreach ($categories as $cat): ?>
                <option value="<?= $cat ?>" <?= $category === $cat ? 'selected' : '' ?>><?= $cat ?></option>
            <?php endforeach; ?>
        </select>
        <select name="privacy" class="form-control">
            <option value="">全部状态</option>
            <option value="shared" <?= $privacy === 'shared' ? 'selected' : '' ?>>共享</option>
            <option value="private" <?= $privacy === 'private' ? 'selected' : '' ?>>隐藏</option>
        </select>
        <button type="submit" class="btn btn-primary btn-sm">🔍 搜索</button>
        <a href="?p=items" class="btn btn-outline btn-sm">重置</a>
    </form>
</div>

<!-- 物品列表 -->
<div class="card">
    <div class="table-wrapper">
        <table>
            <thead>
                <tr>
                    <th>物品名称</th><th>条码</th><th>分类</th><th>存放位置</th><th>数量</th><th>保质期</th><th>隐私</th><th>操作</th>
                </tr>
            </thead>
            <tbody>
                <?php if (empty($items)): ?>
                    <tr><td colspan="8" class="empty-state"><div class="empty-icon">📦</div><div class="empty-text">暂无物品数据</div></td></tr>
                <?php else: foreach ($items as $item): ?>
                    <tr>
                        <td><strong><?= htmlspecialchars($item['name']) ?></strong></td>
                        <td style="font-family: monospace;"><?= $item['barcode'] ?: '-' ?></td>
                        <td><?= $item['category'] ?: '-' ?></td>
                        <td><?= htmlspecialchars($item['space_name'] ?? '-') ?></td>
                        <td><?= $item['quantity'] ?> <?= $item['unit'] ?></td>
                        <td><?= $item['expiry_date'] ?: '-' ?></td>
                        <td>
                            <?php if ($item['is_private']): ?>
                                <span class="badge badge-private">🔒 隐藏</span>
                            <?php else: ?>
                                <span class="badge badge-success">共享</span>
                            <?php endif; ?>
                        </td>
                        <td>
                            <a href="?p=items&action=edit&id=<?= $item['id'] ?>" class="btn btn-sm btn-outline">编辑</a>
                            <button class="btn btn-sm btn-danger" onclick="deleteItem(<?= $item['id'] ?>)">删除</button>
                        </td>
                    </tr>
                <?php endforeach; endif; ?>
            </tbody>
        </table>
    </div>
    <?= renderPagination($total, $page, $pageSize, 'goPage') ?>
</div>

<script>
function goPage(p) { 
    const url = new URL(window.location); 
    url.searchParams.set('pg', p); 
    location.href = url.toString(); 
}

async function deleteItem(id) {
    if (!confirm('确定要删除此物品吗？')) return;
    const resp = await postJSON('../backend/api/goods.php?action=delete', {id: id});
    if (resp !== null) { showToast('删除成功', 'success'); location.reload(); }
}

function exportItems() {
    showToast('正在准备导出...', 'info');
    // 简单实现：跳转到导出API
    window.open('../backend/admin/export.php?type=items', '_blank');
}

function importItems() {
    showToast('请使用物品管理页面的导入功能', 'info');
}
</script>
