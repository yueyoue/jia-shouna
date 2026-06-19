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

<div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;flex-wrap:wrap;gap:12px;">
    <div>
        <div class="page-title" style="font-size:22px;font-weight:700">物品信息管理</div>
        <div class="page-desc" style="color:var(--text-3);font-size:13px;margin-top:4px">共 <?= number_format($total) ?> 件物品 · 多条件筛选</div>
    </div>
    <div style="display:flex;gap:10px;">
        <button class="btn btn-outline btn-sm" onclick="exportItems()">📤 导出</button>
        <button class="btn btn-outline btn-sm" onclick="importItems()">📥 Excel导入</button>
    </div>
</div>

<!-- Filter bar -->
<div class="card" style="margin-bottom:16px;">
    <div style="padding:16px 20px;">
        <form style="display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;align-items:flex-end;" method="GET">
            <input type="hidden" name="p" value="items">
            <div class="form-group" style="margin-bottom:0">
                <label class="form-label">物品名称</label>
                <input type="text" name="keyword" class="form-control" placeholder="搜索名称/条码..." value="<?= htmlspecialchars($keyword) ?>">
            </div>
            <div class="form-group" style="margin-bottom:0">
                <label class="form-label">物品分类</label>
                <select name="category" class="form-control">
                    <option value="">全部分类</option>
                    <?php foreach ($categories as $cat): ?>
                        <option value="<?= $cat ?>" <?= $category === $cat ? 'selected' : '' ?>><?= $cat ?></option>
                    <?php endforeach; ?>
                </select>
            </div>
            <div class="form-group" style="margin-bottom:0">
                <label class="form-label">可见性</label>
                <select name="privacy" class="form-control">
                    <option value="">全部</option>
                    <option value="shared" <?= $privacy === 'shared' ? 'selected' : '' ?>>👁 正常可见</option>
                    <option value="private" <?= $privacy === 'private' ? 'selected' : '' ?>>🔒 隐藏物品</option>
                </select>
            </div>
            <div style="display:flex;gap:8px;padding-top:4px;">
                <button type="submit" class="btn btn-primary btn-sm">🔍 搜索</button>
                <a href="?p=items" class="btn btn-outline btn-sm">↻ 重置</a>
            </div>
        </form>
    </div>
</div>

<!-- Items table -->
<div class="card" style="overflow:hidden;">
    <div style="padding:12px 16px;display:flex;align-items:center;gap:8px;border-bottom:1px solid var(--border-2);background:#FAFBFC;">
        <strong style="font-size:14px">物品列表</strong>
        <span style="font-size:12px;color:var(--text-4)">· 显示 <?= $offset + 1 ?>-<?= min($offset + $pageSize, $total) ?> / <?= number_format($total) ?></span>
    </div>

    <div class="table-wrapper">
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
                    <tr><td colspan="7" class="empty-state"><div class="empty-icon">📦</div><div class="empty-text">暂无物品数据</div></td></tr>
                <?php else: foreach ($items as $item): ?>
                    <tr>
                        <td>
                            <div style="display:flex;align-items:center;gap:10px">
                                <div style="width:40px;height:40px;border-radius:8px;background:linear-gradient(135deg,#FFE8D6,#FFD3B0);display:flex;align-items:center;justify-content:center;font-size:22px;flex-shrink:0">📦</div>
                                <div>
                                    <div style="font-size:13px;font-weight:600;line-height:1.3"><?= htmlspecialchars($item['name']) ?></div>
                                    <div style="font-size:11px;color:var(--text-4);margin-top:2px"><?= $item['barcode'] ? '条码: '.$item['barcode'] : '' ?></div>
                                </div>
                            </div>
                        </td>
                        <td><span class="tag tag-orange"><?= $item['category'] ?: '-' ?></span></td>
                        <td><div style="font-size:11px;color:var(--text-3);line-height:1.5"><?= htmlspecialchars($item['space_name'] ?? '-') ?></div></td>
                        <td><strong><?= $item['quantity'] ?></strong> <?= $item['unit'] ?></td>
                        <td>
                            <?php if ($item['expiry_date']): 
                                $daysLeft = floor((strtotime($item['expiry_date']) - time()) / 86400);
                            ?>
                                <span style="font-size:12px;font-weight:500;<?= $daysLeft <= 0 ? 'color:var(--danger)' : ($daysLeft <= 7 ? 'color:var(--warning)' : 'color:var(--success)') ?>">
                                    <?= $daysLeft <= 0 ? '⚠ 已过期' : ($daysLeft <= 7 ? '⏰ '.$daysLeft.'天后' : $item['expiry_date']) ?>
                                </span>
                            <?php else: ?>
                                <span style="font-size:12px;color:var(--text-4)">— 无</span>
                            <?php endif; ?>
                        </td>
                        <td>
                            <?php if ($item['is_private']): ?>
                                <span style="display:inline-flex;align-items:center;gap:3px;padding:2px 8px;border-radius:4px;background:rgba(159,122,234,.12);color:#553C9A;font-size:11px;font-weight:600">🔒 隐藏</span>
                            <?php else: ?>
                                <span style="font-size:11px;color:var(--text-4)">👁 可见</span>
                            <?php endif; ?>
                        </td>
                        <td>
                            <div style="display:flex;gap:4px;">
                                <a href="?p=items&action=edit&id=<?= $item['id'] ?>" class="btn btn-sm btn-outline" style="padding:4px 8px;font-size:11px">✎</a>
                                <button class="btn btn-sm btn-danger" style="padding:4px 8px;font-size:11px" onclick="deleteItem(<?= $item['id'] ?>)">🗑</button>
                            </div>
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
    window.open('../backend/admin/export.php?type=items', '_blank');
}

function importItems() {
    showToast('请使用物品管理页面的导入功能', 'info');
}
</script>
