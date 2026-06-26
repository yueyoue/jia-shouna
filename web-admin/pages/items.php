<?php
require_once __DIR__ . '/../../backend/config/helpers.php';
$db = getDB();

// 处理编辑物品
$editItem = null;
$editItemImages = [];
if (isset($_GET['action']) && $_GET['action'] === 'edit' && isset($_GET['id'])) {
    $editId = intval($_GET['id']);
    $stmt = $db->prepare('SELECT g.*, s.name as space_name FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id WHERE g.id = ? AND g.status = 1');
    $stmt->execute([$editId]);
    $editItem = $stmt->fetch();
    // 加载物品图片
    if ($editItem) {
        $imgStmt = $db->prepare('SELECT * FROM goods_image WHERE goods_id = ? ORDER BY sort_order ASC');
        $imgStmt->execute([$editId]);
        $editItemImages = $imgStmt->fetchAll();
    }
}

// 处理保存编辑
if ($_SERVER['REQUEST_METHOD'] === 'POST' && ($_POST['post_action'] ?? '') === 'edit_item') {
    $editId = intval($_POST['edit_id'] ?? 0);
    if ($editId) {
        $now = time();
        $spaceId = intval($_POST['space_id'] ?? 0);
        $stmt = $db->prepare('UPDATE goods SET name=?, barcode=?, category=?, brand=?, spec=?, quantity=?, unit=?, purchase_date=?, expiry_date=?, purchase_price=?, note=?, space_id=?, updated_at=? WHERE id=?');
        $stmt->execute([
            trim($_POST['name'] ?? ''), $_POST['barcode'] ?? '', $_POST['category'] ?? '',
            $_POST['brand'] ?? '', $_POST['spec'] ?? '', floatval($_POST['quantity'] ?? 1),
            $_POST['unit'] ?? '个', $_POST['purchase_date'] ?: null, $_POST['expiry_date'] ?: null,
            $_POST['purchase_price'] ?: null, $_POST['note'] ?? '', $spaceId ?: null, $now, $editId
        ]);

        // 更新标签
        $db->prepare('DELETE FROM goods_tag WHERE goods_id = ?')->execute([$editId]);
        $tagIds = $_POST['tag_ids'] ?? [];
        foreach ($tagIds as $tagId) {
            $tagId = intval($tagId);
            if ($tagId > 0) {
                $db->prepare('INSERT INTO goods_tag (goods_id, tag_id) VALUES (?, ?)')->execute([$editId, $tagId]);
            }
        }

        // 处理新上传的图片
        if (!empty($_FILES['images']['name'][0])) {
            $uploadDir = __DIR__ . '/../backend/uploads/images/' . date('Ym') . '/';
            if (!is_dir($uploadDir)) mkdir($uploadDir, 0755, true);
            $thumbDir = $uploadDir . 'thumb/';
            if (!is_dir($thumbDir)) mkdir($thumbDir, 0755, true);

            // 获取当前最大排序号
            $maxStmt = $db->prepare('SELECT COALESCE(MAX(sort_order), -1) as max_order FROM goods_image WHERE goods_id = ?');
            $maxStmt->execute([$editId]);
            $maxOrder = intval($maxStmt->fetch()['max_order']);

            foreach ($_FILES['images']['name'] as $idx => $name) {
                if ($_FILES['images']['error'][$idx] !== 0) continue;
                $ext = strtolower(pathinfo($name, PATHINFO_EXTENSION));
                if (!in_array($ext, ['jpg', 'jpeg', 'png', 'gif', 'webp'])) continue;

                $filename = date('YmdHis') . '_' . substr(md5(uniqid(mt_rand(), true)), 0, 8) . '.' . $ext;
                $destPath = $uploadDir . $filename;
                $relativePath = 'images/' . date('Ym') . '/' . $filename;

                if (move_uploaded_file($_FILES['images']['tmp_name'][$idx], $destPath)) {
                    // 创建缩略图
                    $thumbPath = $thumbDir . $filename;
                    createThumbnail($destPath, $thumbPath, 200);
                    $thumbRelative = 'images/' . date('Ym') . '/thumb/' . $filename;

                    $insStmt = $db->prepare('INSERT INTO goods_image (goods_id, image_path, thumb_path, sort_order, created_at) VALUES (?, ?, ?, ?, ?)');
                    $insStmt->execute([$editId, $relativePath, $thumbRelative, $maxOrder + 1 + $idx, $now]);
                }
            }
        }

        // 处理删除图片
        if (!empty($_POST['delete_images'])) {
            $deleteIds = $_POST['delete_images'];
            foreach ($deleteIds as $delId) {
                $delStmt = $db->prepare('SELECT image_path, thumb_path FROM goods_image WHERE id = ? AND goods_id = ?');
                $delStmt->execute([intval($delId), $editId]);
                $delImg = $delStmt->fetch();
                if ($delImg) {
                    // 删除文件
                    $fullPath = __DIR__ . '/../backend/uploads/' . $delImg['image_path'];
                    if (file_exists($fullPath)) unlink($fullPath);
                    if (!empty($delImg['thumb_path'])) {
                        $thumbFull = __DIR__ . '/../backend/uploads/' . $delImg['thumb_path'];
                        if (file_exists($thumbFull)) unlink($thumbFull);
                    }
                    $db->prepare('DELETE FROM goods_image WHERE id = ?')->execute([intval($delId)]);
                }
            }
        }

        $msg = '物品信息已更新';
        $editItem = null;
    }
}

// 处理添加物品
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $postAction = $_POST['post_action'] ?? '';
    if ($postAction === 'add_item') {
        $houseId = intval($_POST['house_id'] ?? 0);
        $spaceId = intval($_POST['space_id'] ?? 0);
        $name = trim($_POST['name'] ?? '');
        if ($houseId && $spaceId && $name) {
            $now = time();
            $stmt = $db->prepare('INSERT INTO goods (house_id, space_id, creator_id, name, barcode, category, brand, spec, quantity, unit, purchase_date, expiry_date, purchase_price, note, is_private, status, created_at, updated_at) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?)');
            $stmt->execute([
                $houseId, $spaceId, $name,
                $_POST['barcode'] ?? '', $_POST['category'] ?? '', $_POST['brand'] ?? '',
                $_POST['spec'] ?? '', floatval($_POST['quantity'] ?? 1), $_POST['unit'] ?? '个',
                $_POST['purchase_date'] ?: null, $_POST['expiry_date'] ?: null,
                $_POST['purchase_price'] ?: null, $_POST['note'] ?? '',
                $now, $now
            ]);
            $goodsId = $db->lastInsertId();
            $db->prepare('UPDATE storage_space SET item_count = item_count + 1, updated_at = ? WHERE id = ?')->execute([$now, $spaceId]);
            $db->prepare('UPDATE house SET item_count = item_count + 1, updated_at = ? WHERE id = ?')->execute([$now, $houseId]);
            $msg = '物品添加成功';
        } else {
            $error = '请填写物品名称并选择存放位置';
        }
    } elseif ($postAction === 'batch_import') {
        $houseId = intval($_POST['house_id'] ?? 0);
        $csvText = trim($_POST['csv_data'] ?? '');
        if ($houseId && $csvText) {
            $lines = array_filter(explode("\n", $csvText));
            $imported = 0;
            $importErrors = [];
            $now = time();
            foreach ($lines as $idx => $line) {
                $line = trim($line);
                if (empty($line) || $idx === 0) continue; // skip header
                $cols = str_getcsv($line);
                if (count($cols) < 1 || empty($cols[0])) continue;
                $itemName = trim($cols[0]);
                $barcode = isset($cols[1]) ? trim($cols[1]) : '';
                $category = isset($cols[2]) ? trim($cols[2]) : '';
                $brand = isset($cols[3]) ? trim($cols[3]) : '';
                $spec = isset($cols[4]) ? trim($cols[4]) : '';
                $qty = isset($cols[5]) && is_numeric($cols[5]) ? floatval($cols[5]) : 1;
                $unit = isset($cols[6]) ? trim($cols[6]) : '个';
                $purchaseDate = isset($cols[7]) ? trim($cols[7]) : '';
                $expiryDate = isset($cols[8]) ? trim($cols[8]) : '';
                $price = isset($cols[9]) && is_numeric($cols[9]) ? floatval($cols[9]) : null;
                $note = isset($cols[10]) ? trim($cols[10]) : '';
                $spaceName = isset($cols[11]) ? trim($cols[11]) : '';

                // 查找空间
                $spaceId = 0;
                if ($spaceName) {
                    $spStmt = $db->prepare('SELECT id FROM storage_space WHERE house_id = ? AND name = ? LIMIT 1');
                    $spStmt->execute([$houseId, $spaceName]);
                    $sp = $spStmt->fetch();
                    if ($sp) $spaceId = $sp['id'];
                }
                if (!$spaceId) {
                    $importErrors[] = "第" . ($idx+1) . "行: 未找到空间 '{$spaceName}'";
                    continue;
                }

                $stmt = $db->prepare('INSERT INTO goods (house_id, space_id, creator_id, name, barcode, category, brand, spec, quantity, unit, purchase_date, expiry_date, purchase_price, note, is_private, status, created_at, updated_at) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?)');
                $stmt->execute([$houseId, $spaceId, $itemName, $barcode, $category, $brand, $spec, $qty, $unit, $purchaseDate ?: null, $expiryDate ?: null, $price, $note, $now, $now]);
                $gid = $db->lastInsertId();
                $db->prepare('UPDATE storage_space SET item_count = item_count + 1, updated_at = ? WHERE id = ?')->execute([$now, $spaceId]);
                $db->prepare('UPDATE house SET item_count = item_count + 1, updated_at = ? WHERE id = ?')->execute([$now, $houseId]);
                $imported++;
            }
            $msg = "批量导入完成: 成功 {$imported} 件";
            if (!empty($importErrors)) {
                $msg .= '，失败 ' . count($importErrors) . ' 件';
            }
        } else {
            $error = '请选择家庭并粘贴CSV数据';
        }
    }
}

// 获取所有家庭
$allHouses = $db->query('SELECT h.*, u.username as creator_name FROM house h LEFT JOIN sys_user u ON h.creator_id = u.id WHERE h.status = 1 ORDER BY h.created_at DESC')->fetchAll();

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
$stmt = $db->prepare("SELECT g.*, s.name as space_name, h.name as house_name,
        (SELECT image_path FROM goods_image WHERE goods_id = g.id ORDER BY sort_order ASC LIMIT 1) as cover_image
        FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id LEFT JOIN house h ON g.house_id = h.id WHERE $whereStr ORDER BY g.updated_at DESC LIMIT $pageSize OFFSET $offset");
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
            <div class="form-group">
                <label class="form-label">标签搜索</label>
                <select name="tag" class="form-control" onchange="if(this.value)document.querySelector('input[name=keyword]').value=this.value;else document.querySelector('input[name=keyword]').value=''">
                    <option value="">全部标签</option>
                    <?php
                    $allTags = $db->query('SELECT DISTINCT name FROM tag ORDER BY name ASC')->fetchAll();
                    foreach ($allTags as $t): ?>
                    <option value="<?= htmlspecialchars($t['name']) ?>" <?= ($keyword === $t['name']) ? 'selected' : '' ?>><?= htmlspecialchars($t['name']) ?></option>
                    <?php endforeach; ?>
                </select>
            </div>
        </div>
        <div class="filter-actions">
            <button type="submit" class="btn btn-primary btn-sm">🔍 搜索</button>
            <a href="?p=items" class="btn btn-outline btn-sm">↻ 重置</a>
        </div>
    </form>
</div>

<script>
// Load tags for search
var allTags = [];
fetch('../backend/api/tag.php?action=list&house_id=0').then(r=>r.json()).then(d=>{
    if(d.code===0 && d.data && d.data.list) allTags=d.data.list;
}).catch(()=>{});
function filterByTag(tagName) {
    document.querySelector('input[name=keyword]').value = tagName;
    document.querySelector('form').submit();
}
</script>

<!-- Items table - 照抄 UI -->
<div class="items-table">
    <div class="table-toolbar">
        <div class="left">
            <strong style="font-size:14px">物品列表</strong>
            <span style="font-size:12px;color:#A0AEC0">· 显示 <?= $offset + 1 ?>-<?= min($offset + $pageSize, $total) ?> / <?= number_format($total) ?></span>
        </div>
        <div class="right">
            <button class="btn btn-primary btn-sm" onclick="showAddItem()">➕ 添加物品</button>
            <button class="btn btn-outline btn-sm" onclick="showBatchImport()">📥 批量导入</button>
            <a href="../backend/api/goods.php?action=export-template" class="btn btn-ghost btn-sm">📋 下载模板</a>
            <button class="btn btn-ghost btn-sm" onclick="location.reload()">🔄 刷新</button>
        </div>
    </div>

    <table>
        <thead>
            <tr>
                <th>物品信息</th>
                <th>分类</th>
                <th>家庭</th>
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
                        <?php if ($item['cover_image']): ?>
                        <div class="item-img" style="background:none;position:relative;cursor:pointer" onmouseenter="showPreview(this, '<?= htmlspecialchars(IMAGE_URL_PREFIX . $item['cover_image']) ?>')" onmouseleave="hidePreview()">
                            <img src="<?= htmlspecialchars(IMAGE_URL_PREFIX . $item['cover_image']) ?>" style="width:40px;height:40px;border-radius:8px;object-fit:cover" onerror="this.parentNode.innerHTML='📦'">
                        </div>
                        <?php else: ?>
                        <div class="item-img">📦</div>
                        <?php endif; ?>
                        <div>
                            <div class="item-name"><?= htmlspecialchars($item['name']) ?></div>
                            <div class="item-meta"><?= $item['barcode'] ? '条码: '.$item['barcode'] : '' ?></div>
                        </div>
                    </div>
                </td>
                <td><span class="tag tag-orange"><?= $item['category'] ?: '-' ?></span></td>
                <td><div class="path-cell"><?= htmlspecialchars($item['house_name'] ?? '-') ?></div></td>
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
// Image preview
var previewDiv = null;
function showPreview(el, src) {
    if (!src || src === '') return;
    if (!previewDiv) {
        previewDiv = document.createElement('div');
        previewDiv.style.cssText = 'position:fixed;z-index:9999;background:#fff;border-radius:8px;box-shadow:0 4px 20px rgba(0,0,0,.25);padding:6px;display:none;pointer-events:none;transition:opacity .15s';
        previewDiv.innerHTML = '<img style="max-width:320px;max-height:320px;border-radius:6px;display:block;min-width:80px;min-height:80px;background:#f7fafc" onerror="this.parentNode.style.display=\'none\'">';
        document.body.appendChild(previewDiv);
    }
    var img = previewDiv.querySelector('img');
    img.src = src;
    var rect = el.getBoundingClientRect();
    var left = rect.right + 12;
    var top = rect.top - 10;
    // 防止超出右边界
    if (left + 330 > window.innerWidth) {
        left = rect.left - 330;
    }
    // 防止超出底部
    if (top + 330 > window.innerHeight) {
        top = window.innerHeight - 340;
    }
    if (top < 5) top = 5;
    previewDiv.style.left = left + 'px';
    previewDiv.style.top = top + 'px';
    previewDiv.style.display = 'block';
    previewDiv.style.opacity = '1';
}
function hidePreview() {
    if (previewDiv) {
        previewDiv.style.opacity = '0';
        setTimeout(function() { if (previewDiv) previewDiv.style.display = 'none'; }, 150);
    }
}
// 获取空间列表
var spaceCache = {};
async function loadSpaces(houseId) {
    if (spaceCache[houseId]) return spaceCache[houseId];
    try {
        var resp = await fetch('../backend/api/space.php?action=list&house_id=' + houseId);
        var data = await resp.json();
        if (data.code === 0 && data.data && data.data.list) {
            spaceCache[houseId] = data.data.list;
            return data.data.list;
        }
    } catch(e) {}
    return [];
}

async function showAddItem() {
    var houses = <?= json_encode($allHouses ?? []) ?>;
    if (houses.length === 0) { alert('请先创建家庭'); return; }

    var html = '<form method="POST" style="text-align:left">';
    html += '<input type="hidden" name="post_action" value="add_item">';
    html += '<div class="form-group"><label class="form-label">所属家庭</label>';
    html += '<select name="house_id" class="form-control" id="item_house_select" onchange="updateSpaceSelect()">';
    houses.forEach(function(h) {
        html += '<option value="' + h.id + '">' + h.name + (h.creator_name ? ' (' + h.creator_name + ')' : '') + '</option>';
    });
    html += '</select></div>';
    html += '<div class="form-group"><label class="form-label">存放空间</label>';
    html += '<select name="space_id" class="form-control" id="item_space_select"><option>加载中...</option></select></div>';
    html += '<div class="form-group"><label class="form-label">物品名称 *</label><input name="name" class="form-control" required></div>';
    html += '<div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">';
    html += '<div class="form-group"><label class="form-label">条形码</label><input name="barcode" class="form-control"></div>';
    html += '<div class="form-group"><label class="form-label">分类</label><input name="category" class="form-control" placeholder="食品/日用品等"></div>';
    html += '<div class="form-group"><label class="form-label">品牌</label><input name="brand" class="form-control"></div>';
    html += '<div class="form-group"><label class="form-label">规格</label><input name="spec" class="form-control"></div>';
    html += '<div class="form-group"><label class="form-label">数量</label><input name="quantity" class="form-control" type="number" value="1" step="0.01"></div>';
    html += '<div class="form-group"><label class="form-label">单位</label><input name="unit" class="form-control" value="个"></div>';
    html += '<div class="form-group"><label class="form-label">购买日期</label><input name="purchase_date" class="form-control" type="date"></div>';
    html += '<div class="form-group"><label class="form-label">保质期</label><input name="expiry_date" class="form-control" type="date"></div>';
    html += '<div class="form-group"><label class="form-label">价格</label><input name="purchase_price" class="form-control" type="number" step="0.01"></div>';
    html += '</div>';
    html += '<div class="form-group"><label class="form-label">备注</label><textarea name="note" class="form-control" rows="2"></textarea></div>';
    html += '<button type="submit" class="btn btn-primary btn-lg" style="width:100%;margin-top:12px">确认添加</button>';
    html += '</form>';

    document.getElementById('modal-add-item-body').innerHTML = html;
    document.getElementById('modal-add-item').style.display = 'flex';
    updateSpaceSelect();
}

async function updateSpaceSelect() {
    var houseId = document.getElementById('item_house_select').value;
    var sel = document.getElementById('item_space_select');
    sel.innerHTML = '<option>加载中...</option>';
    var spaces = await loadSpaces(houseId);
    sel.innerHTML = '';
    if (spaces.length === 0) {
        sel.innerHTML = '<option value="">暂无空间，请先创建</option>';
    } else {
        spaces.forEach(function(s) {
            sel.innerHTML += '<option value="' + s.id + '">' + s.name + '</option>';
        });
    }
}

function showBatchImport() {
    var houses = <?= json_encode($allHouses ?? []) ?>;
    if (houses.length === 0) { alert('请先创建家庭'); return; }

    var html = '<form method="POST" style="text-align:left">';
    html += '<input type="hidden" name="post_action" value="batch_import">';
    html += '<div class="form-group"><label class="form-label">导入到家庭</label>';
    html += '<select name="house_id" class="form-control">';
    houses.forEach(function(h) {
        html += '<option value="' + h.id + '">' + h.name + '</option>';
    });
    html += '</select></div>';
    html += '<div class="form-group"><label class="form-label">CSV数据</label>';
    html += '<textarea name="csv_data" class="form-control" rows="12" placeholder="粘贴CSV数据，每行一条\n格式: 物品名称,条形码,分类,品牌,规格,数量,单位,购买日期,保质期,价格,备注,空间名称\n\n示例:\n趣多多饼干,6901234567890,食品,趣多多,100g,2,盒,2025-01-15,2025-07-15,12.5,好吃,厨房"></textarea></div>';
    html += '<p style="font-size:12px;color:#999;margin:8px 0">提示: 先 <a href="../backend/api/goods.php?action=export-template" download>下载模板</a> 填写后粘贴</p>';
    html += '<button type="submit" class="btn btn-primary btn-lg" style="width:100%;margin-top:8px">开始导入</button>';
    html += '</form>';

    document.getElementById('modal-add-item-body').innerHTML = html;
    document.getElementById('modal-add-item').style.display = 'flex';
}

async function deleteItem(id) {
    if (!confirm('确定要删除此物品吗？')) return;
    const resp = await postJSON('../backend/api/goods.php?action=delete', {id: id});
    if (resp !== null) { showToast('删除成功', 'success'); location.reload(); }
}
</script>

<!-- 添加物品/导入弹窗 -->
<div id="modal-add-item" class="modal-mask" style="display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.4);z-index:999;align-items:center;justify-content:center">
    <div style="background:#fff;border-radius:12px;max-width:600px;width:90%;max-height:85vh;overflow-y:auto;padding:24px;position:relative">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
            <h3 style="font-size:16px;font-weight:600">添加物品</h3>
            <span onclick="document.getElementById('modal-add-item').style.display='none'" style="cursor:pointer;font-size:20px;color:#999">&times;</span>
        </div>
        <div id="modal-add-item-body"></div>
    </div>
</div>

<!-- 编辑物品弹窗 -->
<?php if ($editItem): ?>
<div style="position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.4);z-index:999;display:flex;align-items:center;justify-content:center">
    <div style="background:#fff;border-radius:12px;max-width:600px;width:90%;max-height:85vh;overflow-y:auto;padding:24px;position:relative">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
            <h3 style="font-size:16px;font-weight:600">编辑物品 - <?= htmlspecialchars($editItem['name']) ?></h3>
            <a href="?p=items" style="cursor:pointer;font-size:20px;color:#999;text-decoration:none">&times;</a>
        </div>
        <form method="POST" enctype="multipart/form-data" style="text-align:left">
            <input type="hidden" name="post_action" value="edit_item">
            <input type="hidden" name="edit_id" value="<?= $editItem['id'] ?>">

            <!-- 图片管理 -->
            <div class="form-group">
                <label class="form-label">📷 物品图片</label>
                <div id="edit-images-grid" style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px">
                    <?php if (!empty($editItemImages)): foreach ($editItemImages as $img): ?>
                    <div class="edit-img-item" data-id="<?= $img['id'] ?>" style="position:relative;width:80px;height:80px;border-radius:8px;overflow:hidden;border:1px solid #EDF2F7">
                        <img src="<?= IMAGE_URL_PREFIX . $img['image_path'] ?>" style="width:100%;height:100%;object-fit:cover" onerror="this.parentNode.style.display='none'">
                        <label style="position:absolute;top:2px;right:2px;background:rgba(255,255,255,.9);border-radius:4px;padding:1px 4px;cursor:pointer;font-size:11px;color:#F56565">
                            <input type="checkbox" name="delete_images[]" value="<?= $img['id'] ?>" style="display:none" onchange="this.parentNode.style.opacity=this.checked?'1':'0.6';if(this.checked)this.parentNode.parentNode.style.opacity='0.4'">
                            ✕
                        </label>
                    </div>
                    <?php endforeach; endif; ?>
                </div>
                <input type="file" name="images[]" accept="image/*" multiple class="form-control" style="font-size:12px">
                <p style="font-size:11px;color:#A0AEC0;margin:4px 0 0">选择新图片追加上传，勾选 ✕ 删除已有图片</p>
            </div>

            <div class="form-group"><label class="form-label">物品名称 *</label><input name="name" class="form-control" required value="<?= htmlspecialchars($editItem['name']) ?>"></div>
            <div class="form-group"><label class="form-label">存放位置</label>
                <select name="space_id" class="form-control" id="edit_space_select">
                    <option value="">请选择空间</option>
                    <?php
                    $editHouseId = $editItem['house_id'] ?? 0;
                    if ($editHouseId) {
                        $editSpaces = $db->prepare('SELECT * FROM storage_space WHERE house_id = ? ORDER BY level ASC, sort_order ASC');
                        $editSpaces->execute([$editHouseId]);
                        foreach ($editSpaces->fetchAll() as $sp) {
                            $selected = $sp['id'] == $editItem['space_id'] ? 'selected' : '';
                            $prefix = $sp['level'] > 1 ? str_repeat('&nbsp;&nbsp;', $sp['level'] - 1) . '└ ' : '';
                            echo '<option value="' . $sp['id'] . '" ' . $selected . '>' . $prefix . htmlspecialchars($sp['name']) . '</option>';
                        }
                    }
                    ?>
                </select>
            </div>
            <div class="form-group"><label class="form-label">标签</label>
                <div id="edit-tags-container" style="display:flex;flex-wrap:wrap;gap:6px;margin-bottom:6px">
                    <?php
                    $editTags = $db->prepare('SELECT t.id, t.name FROM tag t INNER JOIN goods_tag gt ON t.id = gt.tag_id WHERE gt.goods_id = ?');
                    $editTags->execute([$editItem['id']]);
                    $editTagIds = [];
                    foreach ($editTags->fetchAll() as $et) {
                        $editTagIds[] = $et['id'];
                        echo '<span class="tag tag-blue" data-tag-id="' . $et['id'] . '">' . htmlspecialchars($et['name']) . ' <span onclick="removeEditTag(' . $et['id'] . ', this)" style="cursor:pointer;margin-left:4px">✕</span></span>';
                    }
                    ?>
                </div>
                <select id="edit_tag_select" class="form-control" onchange="addEditTag(this)">
                    <option value="">+ 添加标签</option>
                    <?php
                    $allTagsForEdit = $db->query('SELECT id, name FROM tag ORDER BY name ASC')->fetchAll();
                    foreach ($allTagsForEdit as $at) {
                        if (!in_array($at['id'], $editTagIds)) {
                            echo '<option value="' . $at['id'] . '">' . htmlspecialchars($at['name']) . '</option>';
                        }
                    }
                    ?>
                </select>
                <div id="edit-tag-ids">
                    <?php foreach ($editTagIds as $tid): ?>
                    <input type="hidden" name="tag_ids[]" value="<?= $tid ?>" data-tag-id="<?= $tid ?>">
                    <?php endforeach; ?>
                </div>
            </div>
            <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
                <div class="form-group"><label class="form-label">条形码</label><input name="barcode" class="form-control" value="<?= htmlspecialchars($editItem['barcode'] ?? '') ?>"></div>
                <div class="form-group"><label class="form-label">分类</label><input name="category" class="form-control" value="<?= htmlspecialchars($editItem['category'] ?? '') ?>"></div>
                <div class="form-group"><label class="form-label">品牌</label><input name="brand" class="form-control" value="<?= htmlspecialchars($editItem['brand'] ?? '') ?>"></div>
                <div class="form-group"><label class="form-label">规格</label><input name="spec" class="form-control" value="<?= htmlspecialchars($editItem['spec'] ?? '') ?>"></div>
                <div class="form-group"><label class="form-label">数量</label><input name="quantity" class="form-control" type="number" step="0.01" value="<?= $editItem['quantity'] ?>"></div>
                <div class="form-group"><label class="form-label">单位</label><input name="unit" class="form-control" value="<?= htmlspecialchars($editItem['unit'] ?? '个') ?>"></div>
                <div class="form-group"><label class="form-label">生产日期</label><input name="purchase_date" class="form-control" type="date" value="<?= $editItem['purchase_date'] ?? '' ?>"></div>
                <div class="form-group"><label class="form-label">保质期</label><input name="expiry_date" class="form-control" type="date" value="<?= $editItem['expiry_date'] ?? '' ?>"></div>
                <div class="form-group"><label class="form-label">价格</label><input name="purchase_price" class="form-control" type="number" step="0.01" value="<?= $editItem['purchase_price'] ?? '' ?>"></div>
            </div>
            <div class="form-group"><label class="form-label">备注</label><textarea name="note" class="form-control" rows="2"><?= htmlspecialchars($editItem['note'] ?? '') ?></textarea></div>
            <script>
            function removeEditTag(tagId, el) {
                el.parentNode.remove();
                var inputs = document.querySelectorAll('#edit-tag-ids input[data-tag-id="' + tagId + '"]');
                inputs.forEach(function(i) { i.remove(); });
                // 回到下拉选项
                var sel = document.getElementById('edit_tag_select');
                var opt = document.createElement('option');
                opt.value = tagId;
                opt.text = el.parentNode.textContent.replace('✕','').trim();
                sel.appendChild(opt);
            }
            function addEditTag(sel) {
                var tagId = sel.value;
                if (!tagId) return;
                var text = sel.options[sel.selectedIndex].text;
                var container = document.getElementById('edit-tags-container');
                var span = document.createElement('span');
                span.className = 'tag tag-blue';
                span.setAttribute('data-tag-id', tagId);
                span.innerHTML = text + ' <span onclick="removeEditTag(' + tagId + ', this)" style="cursor:pointer;margin-left:4px">✕</span>';
                container.appendChild(span);
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'tag_ids[]';
                input.value = tagId;
                input.setAttribute('data-tag-id', tagId);
                document.getElementById('edit-tag-ids').appendChild(input);
                sel.remove(sel.selectedIndex);
                sel.selectedIndex = 0;
            }
            </script>
            <button type="submit" class="btn btn-primary btn-lg" style="width:100%;margin-top:12px">保存修改</button>
        </form>
    </div>
</div>
<?php endif; ?>
