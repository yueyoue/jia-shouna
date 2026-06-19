<?php
$db = getDB();

// 获取房屋列表
$stmt = $db->query("SELECT * FROM house WHERE status = 1 ORDER BY created_at DESC");
$houses = $stmt->fetchAll();

$selectedHouse = intval($_GET['house_id'] ?? 0);
if (!$selectedHouse && !empty($houses)) $selectedHouse = $houses[0]['id'];

// 获取空间树
$spaces = [];
if ($selectedHouse) {
    $stmt = $db->prepare("SELECT * FROM storage_space WHERE house_id = ? ORDER BY level ASC, sort_order ASC");
    $stmt->execute([$selectedHouse]);
    $spaces = $stmt->fetchAll();
}

// 构建树
function buildTree($items, $parentId = 0) {
    $tree = [];
    foreach ($items as $item) {
        if ($item['parent_id'] == $parentId) {
            $children = buildTree($items, $item['id']);
            $item['children'] = $children;
            $tree[] = $item;
        }
    }
    return $tree;
}

function renderTree($tree, $level = 0) {
    foreach ($tree as $node) {
        $indent = str_repeat('&nbsp;&nbsp;', $level);
        $expandIcon = !empty($node['children']) ? '📂' : '📄';
        echo "<div class='tree-node' data-id='{$node['id']}' onclick='selectSpace({$node['id']})'>";
        echo "<span class='node-icon'>{$node['icon']}</span>";
        echo "<span class='node-name'>{$node['name']}</span>";
        echo "<span class='node-count'>📦 {$node['item_count']}</span>";
        echo "</div>";
        if (!empty($node['children'])) {
            echo "<div class='tree-children'>";
            renderTree($node['children'], $level + 1);
            echo "</div>";
        }
    }
}

$tree = buildTree($spaces);
?>

<div class="card-header" style="margin-bottom: 16px;">
    <div>
        <h2 style="font-size: 18px;">收纳空间管理</h2>
        <p style="color: #999; font-size: 12px;">管理端：查看和维护家庭收纳空间层级，空间创建入口在 APP 端</p>
    </div>
    <div style="display: flex; gap: 10px;">
        <select class="form-control" style="width: auto;" onchange="location.href='?p=spaces&house_id='+this.value">
            <?php foreach ($houses as $h): ?>
                <option value="<?= $h['id'] ?>" <?= $h['id'] == $selectedHouse ? 'selected' : '' ?>><?= htmlspecialchars($h['name']) ?></option>
            <?php endforeach; ?>
        </select>
    </div>
</div>

<div style="display: grid; grid-template-columns: 300px 1fr; gap: 20px;">
    <!-- 左侧树 -->
    <div class="card">
        <div class="card-title" style="margin-bottom: 12px;">📂 空间层级</div>
        <?php if (empty($tree)): ?>
            <div class="empty-state">
                <div class="empty-icon">🏠</div>
                <div class="empty-text">暂无空间，请在APP端创建</div>
            </div>
        <?php else: ?>
            <div class="tree"><?= renderTree($tree) ?></div>
        <?php endif; ?>
    </div>

    <!-- 右侧详情 -->
    <div class="card" id="space-detail">
        <div class="empty-state">
            <div class="empty-icon">👈</div>
            <div class="empty-text">点击左侧空间查看详情</div>
        </div>
    </div>
</div>

<script>
async function selectSpace(id) {
    document.querySelectorAll('.tree-node').forEach(n => n.classList.remove('active'));
    document.querySelector(`.tree-node[data-id="${id}"]`).classList.add('active');
    
    const data = await api(`../backend/api/space.php?action=detail&id=${id}`);
    if (!data) return;
    const s = data.space;
    
    let html = `
        <div class="card-header">
            <div class="card-title">${s.icon} ${s.name}</div>
            <div>
                <button class="btn btn-sm btn-outline" onclick="editSpace(${s.id})">✏️ 编辑</button>
                <button class="btn btn-sm btn-danger" onclick="deleteSpace(${s.id})">🗑 删除</button>
            </div>
        </div>
        <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; margin-bottom: 20px;">
            <div><div style="font-size: 12px; color: #999;">层级</div><div style="font-size: 16px; font-weight: 600;">${['房间', '容器', '区域'][s.level - 1]}</div></div>
            <div><div style="font-size: 12px; color: #999;">物品数量</div><div style="font-size: 16px; font-weight: 600;">${s.item_count}</div></div>
            <div><div style="font-size: 12px; color: #999;">共享状态</div><div style="font-size: 16px; font-weight: 600;">${s.shared == 1 ? '✅ 全家共享' : '🔒 仅自己'}</div></div>
        </div>`;
    
    if (s.children && s.children.length > 0) {
        html += `<div style="margin-bottom: 16px;"><div style="font-size: 13px; font-weight: 600; margin-bottom: 8px;">📁 子空间 (${s.children.length})</div>`;
        s.children.forEach(c => {
            html += `<div style="padding: 8px 12px; background: #f8f9fa; border-radius: 8px; margin-bottom: 6px; display: flex; justify-content: space-between;">
                <span>${c.icon} ${c.name} <span style="color: #999; font-size: 12px;">📦 ${c.item_count}件</span></span>
                <span class="badge badge-info">${['房间', '容器', '区域'][c.level - 1]}</span>
            </div>`;
        });
        html += `</div>`;
    }
    
    document.getElementById('space-detail').innerHTML = html;
}

async function editSpace(id) {
    const name = prompt('请输入新的空间名称:');
    if (!name) return;
    const data = await api('../backend/api/space.php?action=update', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({id: id, name: name})
    });
    if (data !== null) { showToast('更新成功', 'success'); location.reload(); }
}

async function deleteSpace(id) {
    if (!confirm('确定要删除此空间吗？空间内有物品时需要先处理物品。')) return;
    const data = await api('../backend/api/space.php?action=delete', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({id: id, delete_goods: 0})
    });
    if (data !== null) { showToast('删除成功', 'success'); location.reload(); }
}
</script>
