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
        $levelClass = ['ti-room','ti-container','ti-area'][($node['level'] - 1) % 3];
        $hasChildren = !empty($node['children']);
        echo "<div class='tree-item'>";
        echo "<div class='tree-node' data-id='{$node['id']}' onclick='selectSpace({$node['id']})'>";
        echo "<span class='tree-toggle ".($hasChildren ? 'open' : 'empty')."'>▶</span>";
        echo "<span class='tree-icon {$levelClass}'>{$node['icon']}</span>";
        echo "<span class='tree-name'>{$node['name']}</span>";
        echo "<span class='tree-count'>{$node['item_count']}</span>";
        echo "</div>";
        if ($hasChildren) {
            echo "<div class='tree-children'>";
            renderTree($node['children'], $level + 1);
            echo "</div>";
        }
        echo "</div>";
    }
}

$tree = buildTree($spaces);
?>

<div class="page-header" style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;flex-wrap:wrap;gap:12px;">
    <div>
        <div class="page-title" style="font-size:22px;font-weight:700">收纳空间管理</div>
        <div class="page-desc" style="color:var(--text-3);font-size:13px;margin-top:4px">管理端 · 家庭成员也可在 APP 端创建空间并共享给家人</div>
    </div>
    <div style="display:flex;gap:10px;align-items:center;">
        <select class="form-control" style="width:auto;" onchange="location.href='?p=spaces&house_id='+this.value">
            <?php foreach ($houses as $h): ?>
                <option value="<?= $h['id'] ?>" <?= $h['id'] == $selectedHouse ? 'selected' : '' ?>><?= htmlspecialchars($h['name']) ?></option>
            <?php endforeach; ?>
        </select>
    </div>
</div>

<div style="display:grid;grid-template-columns:340px 1fr;gap:16px;">
    <!-- Tree panel -->
    <div class="card" style="display:flex;flex-direction:column;max-height:calc(100vh - 180px);position:sticky;top:84px;">
        <div class="card-header">
            <div class="card-title">📂 空间层级</div>
            <div style="display:flex;gap:6px">
                <button class="btn btn-ghost btn-sm" title="刷新" onclick="location.reload()">↻</button>
            </div>
        </div>
        <div style="padding:12px;flex:1;overflow-y:auto;">
            <div style="position:relative;margin-bottom:12px;">
                <input type="text" placeholder="搜索空间名称..." style="width:100%;padding:7px 10px 7px 30px;background:var(--bg);border-radius:8px;font-size:12px;border:1px solid transparent;" onfocus="this.style.background='#fff';this.style.borderColor='var(--primary)'" onblur="this.style.background='var(--bg)';this.style.borderColor='transparent'">
                <span style="position:absolute;left:10px;top:50%;transform:translateY(-50%);font-size:12px;">🔍</span>
            </div>
            <?php if (empty($tree)): ?>
                <div class="empty-state">
                    <div class="empty-icon">🏠</div>
                    <div class="empty-text">暂无空间，请在APP端创建</div>
                </div>
            <?php else: ?>
                <div class="tree"><?= renderTree($tree) ?></div>
            <?php endif; ?>
        </div>
    </div>

    <!-- Right detail -->
    <div class="card" id="space-detail" style="overflow:hidden;">
        <div class="empty-state" style="padding:60px 20px;">
            <div class="empty-icon">👈</div>
            <div class="empty-text">点击左侧空间查看详情</div>
        </div>
    </div>
</div>

<script>
async function selectSpace(id) {
    document.querySelectorAll('.tree-node').forEach(n => n.classList.remove('active'));
    const node = document.querySelector(`.tree-node[data-id="${id}"]`);
    if (node) node.classList.add('active');
    
    const data = await api(`../backend/api/space.php?action=detail&id=${id}`);
    if (!data) return;
    const s = data.space;
    
    const levelNames = ['房间', '容器', '区域'];
    const levelColors = ['#FF8C42','#4ECDC4','#5B9FED'];
    
    let html = `
        <div style="padding:20px 24px;background:linear-gradient(135deg,#FFF7F0 0%,#F0FBFA 100%);border-bottom:1px solid var(--border-2);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;">
            <div>
                <div style="display:flex;align-items:center;gap:6px;font-size:12px;color:var(--text-3);margin-bottom:8px;">
                    <span>空间路径</span>
                </div>
                <div style="display:flex;align-items:center;gap:12px">
                    <div style="width:48px;height:48px;border-radius:12px;background:linear-gradient(135deg,#FF8C42,#FF6B6B);display:flex;align-items:center;justify-content:center;font-size:24px;color:#fff;box-shadow:0 4px 12px rgba(255,140,66,.3)">${s.icon}</div>
                    <div>
                        <div style="font-size:20px;font-weight:700">${s.name}</div>
                        <div style="font-size:12px;color:var(--text-3);margin-top:2px">${levelNames[s.level - 1] || '空间'}</div>
                    </div>
                </div>
                <div style="display:flex;gap:20px;margin-top:12px;">
                    <div style="display:flex;align-items:center;gap:6px;font-size:12px;color:var(--text-2)">📦 物品 <strong style="color:var(--primary);font-size:14px">${s.item_count}</strong></div>
                    <div style="display:flex;align-items:center;gap:6px;font-size:12px;color:var(--text-2)">${s.shared == 1 ? '👁 已共享给全家人' : '🔒 仅自己可见'}</div>
                </div>
            </div>
            <div style="display:flex;gap:8px;flex-wrap:wrap">
                <button class="btn btn-outline btn-sm" onclick="editSpace(${s.id})">✎ 编辑</button>
                <button class="btn btn-danger btn-sm" onclick="deleteSpace(${s.id})">🗑 删除</button>
            </div>
        </div>`;
    
    if (s.children && s.children.length > 0) {
        html += `<div style="padding:20px 24px;">
            <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;">
                <div style="font-size:15px;font-weight:600">子空间 (${s.children.length})</div>
            </div>
            <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px;">`;
        s.children.forEach((c, i) => {
            const colors = ['#FF8C42','#4ECDC4','#5B9FED','#ED8936','#9F7AEA','#48BB78'];
            const color = colors[i % colors.length];
            html += `<div style="background:#fff;border:1.5px solid var(--border-2);border-radius:12px;padding:16px;cursor:pointer;transition:all .2s;position:relative;overflow:hidden;" onmouseover="this.style.borderColor='${color}';this.style.transform='translateY(-2px)';this.style.boxShadow='0 4px 12px rgba(0,0,0,.1)'" onmouseout="this.style.borderColor='var(--border-2)';this.style.transform='none';this.style.boxShadow='none'">
                <div style="position:absolute;left:0;top:0;bottom:0;width:4px;background:${color}"></div>
                <div style="display:flex;align-items:center;gap:10px;margin-bottom:10px">
                    <div style="width:38px;height:38px;border-radius:10px;background:linear-gradient(135deg,#FFE8D6,#FFD3B0);display:flex;align-items:center;justify-content:center;font-size:20px">${c.icon}</div>
                    <div style="font-size:14px;font-weight:600;flex:1">${c.name}</div>
                    <span style="font-size:10px;padding:2px 6px;border-radius:4px;background:var(--bg);color:var(--text-3)">${levelNames[c.level - 1] || '空间'}</span>
                </div>
                <div style="display:flex;justify-content:space-between;font-size:12px;color:var(--text-3);padding-top:10px;border-top:1px dashed var(--border-2);">
                    <span>📦 <strong style="color:var(--text)">${c.item_count}</strong> 件物品</span>
                </div>
            </div>`;
        });
        html += `</div></div>`;
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
