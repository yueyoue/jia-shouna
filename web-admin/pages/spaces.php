<?php
$db = getDB();

// 处理POST操作
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $postAction = $_POST['post_action'] ?? '';
    if ($postAction === 'create_space') {
        $houseId = intval($_POST['house_id'] ?? 0);
        $name = trim($_POST['name'] ?? '');
        $level = intval($_POST['level'] ?? 1);
        $icon = $_POST['icon'] ?? '📦';
        $parentId = intval($_POST['parent_id'] ?? 0);
        if ($houseId && $name) {
            $now = time();
            $stmt = $db->prepare('INSERT INTO storage_space (house_id, parent_id, name, level, icon, color, shared, creator_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, 1, ?, ?)');
            $stmt->execute([$houseId, $parentId, $name, $level, $icon, '#FF8C42', $now, $now]);
            $db->prepare('UPDATE house SET space_count = space_count + 1, updated_at = ? WHERE id = ?')->execute([$now, $houseId]);
            $msg = '空间创建成功';
        } else {
            $error = '请填写空间名称';
        }
    } elseif ($postAction === 'create_house') {
        $name = trim($_POST['name'] ?? '');
        if ($name) {
            $now = time();
            $code = strtoupper(substr(md5(uniqid(mt_rand(), true)), 0, 8));
            $stmt = $db->prepare('INSERT INTO house (name, invite_code, creator_id, member_count, created_at, updated_at) VALUES (?, ?, 1, 1, ?, ?)');
            $stmt->execute([$name, $code, $now, $now]);
            $houseId = $db->lastInsertId();
            $db->prepare('INSERT INTO house_member (house_id, user_id, role, is_current, joined_at) VALUES (?, 1, 1, 1, ?)')->execute([$houseId, $now]);
            $msg = '家庭创建成功';
        } else {
            $error = '请输入家庭名称';
        }
    }
}

// 获取房屋列表(显示所有家，每个家绑定到创建者)
$houses = [];
$stmt = $db->query("SELECT h.*, u.username as creator_name FROM house h LEFT JOIN sys_user u ON h.creator_id = u.id WHERE h.status = 1 ORDER BY h.created_at DESC");
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
    $levelClasses = ['ti-room','ti-container','ti-area'];
    foreach ($tree as $node) {
        $levelClass = $levelClasses[($node['level'] - 1) % 3];
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

<style>
.layout{display:grid;grid-template-columns:340px 1fr;gap:16px}
@media(max-width:1024px){.layout{grid-template-columns:1fr}}
.tree-panel{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);display:flex;flex-direction:column;max-height:calc(100vh - 130px);position:sticky;top:84px}
.tree-header{padding:16px 18px;border-bottom:1px solid var(--border-2);display:flex;align-items:center;justify-content:space-between}
.tree-header h3{font-size:15px;font-weight:600}
.tree-actions{display:flex;gap:6px}
.tree-actions .icon-btn-sm{width:28px;height:28px;border-radius:6px;display:flex;align-items:center;justify-content:center;background:#F7FAFC;color:#4A5568;font-size:13px;cursor:pointer;transition:all .2s}
.tree-actions .icon-btn-sm:hover{background:#EDF2F7;color:#FF8C42}
.tree-body{padding:12px;flex:1;overflow-y:auto}
.tree-search{position:relative;margin-bottom:12px}
.tree-search input{width:100%;padding:7px 10px 7px 30px;background:#F7FAFC;border-radius:8px;font-size:12px;border:1px solid transparent}
.tree-search input:focus{background:#fff;border-color:#FF8C42}
.tree-search::before{content:'🔍';position:absolute;left:10px;top:50%;transform:translateY(-50%);font-size:12px}
.tree-item{position:relative}
.tree-node{display:flex;align-items:center;gap:8px;padding:3px 10px;border-radius:8px;cursor:pointer;font-size:13px;transition:all .15s;position:relative;height:28px}
.tree-node:hover{background:#FFF7F0}
.tree-node.active{background:linear-gradient(90deg,#FFF1E0 0%,transparent 100%);color:#FF8C42;font-weight:600}
.tree-node.active::before{content:'';position:absolute;left:0;top:6px;bottom:6px;width:3px;background:#FF8C42;border-radius:0 3px 3px 0}
.tree-toggle{width:16px;height:16px;display:flex;align-items:center;justify-content:center;font-size:10px;color:#A0AEC0;cursor:pointer;transition:transform .2s;flex-shrink:0}
.tree-toggle.open{transform:rotate(90deg)}
.tree-toggle.empty{visibility:hidden}
.tree-icon{width:20px;height:20px;border-radius:5px;display:flex;align-items:center;justify-content:center;font-size:12px;flex-shrink:0}
.ti-room{background:linear-gradient(135deg,#FFE8D6,#FFD3B0)}
.ti-container{background:linear-gradient(135deg,#C7F0EC,#7EE0D8)}
.ti-area{background:linear-gradient(135deg,#D6E4FF,#A8C5FA)}
.tree-name{flex:1;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tree-count{font-size:11px;color:#A0AEC0;background:#F7FAFC;padding:1px 6px;border-radius:8px}
.tree-node.active .tree-count{background:rgba(255,140,66,.12);color:#FF8C42}
.tree-children{margin-left:18px;border-left:1px dashed #E2E8F0;padding-left:8px}
.detail-panel{background:#fff;border-radius:var(--radius);border:1px solid var(--border-2);box-shadow:var(--shadow);overflow:hidden}
.detail-header{padding:20px 24px;background:linear-gradient(135deg,#FFF7F0 0%,#F0FBFA 100%);border-bottom:1px solid var(--border-2);display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px}
.detail-path{display:flex;align-items:center;gap:6px;font-size:12px;color:#718096;margin-bottom:8px}
.detail-path .sep{color:#CBD5E0}
.detail-title{display:flex;align-items:center;gap:12px}
.detail-icon{width:48px;height:48px;border-radius:12px;background:linear-gradient(135deg,#FF8C42,#FF6B6B);display:flex;align-items:center;justify-content:center;font-size:24px;color:#fff;box-shadow:0 4px 12px rgba(255,140,66,.3)}
.detail-name{font-size:20px;font-weight:700}
.detail-desc{font-size:12px;color:#718096;margin-top:2px}
.detail-stats{display:flex;gap:20px;margin-top:12px}
.detail-stat{display:flex;align-items:center;gap:6px;font-size:12px;color:#4A5568}
.detail-stat strong{color:#FF8C42;font-size:14px}
.space-cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px;padding:20px 24px}
.space-card{background:#fff;border:1.5px solid var(--border-2);border-radius:12px;padding:16px;cursor:pointer;transition:all .2s;position:relative;overflow:hidden}
.space-card:hover{border-color:#FF8C42;box-shadow:0 4px 12px rgba(255,140,66,.1);transform:translateY(-2px)}
.space-card::before{content:'';position:absolute;left:0;top:0;bottom:0;width:4px;background:#FF8C42}
.space-card.c2::before{background:#4ECDC4}
.space-card.c3::before{background:#5B9FED}
.space-card-head{display:flex;align-items:center;gap:10px;margin-bottom:10px}
.space-card-icon{width:38px;height:38px;border-radius:10px;background:linear-gradient(135deg,#FFE8D6,#FFD3B0);display:flex;align-items:center;justify-content:center;font-size:20px}
.space-card.c2 .space-card-icon{background:linear-gradient(135deg,#C7F0EC,#7EE0D8)}
.space-card.c3 .space-card-icon{background:linear-gradient(135deg,#D6E4FF,#A8C5FA)}
.space-card-name{font-size:14px;font-weight:600;flex:1}
.space-card-tag{font-size:10px;padding:2px 6px;border-radius:4px;background:#F7FAFC;color:#718096}
.space-card-meta{display:flex;justify-content:space-between;font-size:12px;color:#718096;padding-top:10px;border-top:1px dashed #EDF2F7}
.space-card-meta strong{color:#4A5568;font-size:13px}
</style>

<div class="page-header">
    <div>
        <div class="page-title">收纳空间管理</div>
        <div class="page-desc">管理端 · 家庭成员也可在 APP 端创建空间并共享给家人</div>
    </div>
</div>

<div class="layout">
    <!-- Tree - 照抄 UI -->
    <div class="tree-panel">
        <div class="tree-header">
            <h3>📂 空间层级</h3>
            <div class="tree-actions">
                <div class="icon-btn-sm" title="新建家庭" onclick="showCreateHouse()">🏠+</div>
                <div class="icon-btn-sm" title="新建空间" onclick="showCreateSpace()">📁+</div>
                <div class="icon-btn-sm" title="刷新" onclick="location.reload()">↻</div>
            </div>
        </div>
        <div class="tree-body">
            <!-- 家庭选择器 -->
            <?php if (!empty($houses)): ?>
            <div style="margin-bottom:12px">
                <select class="form-control" style="font-size:12px;padding:6px 10px" onchange="switchHouse(this.value)">
                    <?php foreach ($houses as $h): ?>
                    <option value="<?= $h['id'] ?>" <?= $h['id'] == $selectedHouse ? 'selected' : '' ?>>
                        🏠 <?= htmlspecialchars($h['name']) ?> (创建者: <?= htmlspecialchars($h['creator_name'] ?? '未知') ?>)
                    </option>
                    <?php endforeach; ?>
                </select>
            </div>
            <?php endif; ?>

            <div class="tree-search">
                <input type="text" placeholder="搜索空间名称...">
            </div>
            <?php if (empty($tree)): ?>
            <div class="empty">
                <div class="empty-icon">🏠</div>
                <div>暂无空间，点击上方按钮创建</div>
            </div>
            <?php else: ?>
            <div class="tree-item">
                <div class="tree-node">
                    <span class="tree-toggle open">▶</span>
                    <span class="tree-icon ti-room">🏠</span>
                    <span class="tree-name"><?= htmlspecialchars($houses[array_search($selectedHouse, array_column($houses, 'id'))]['name'] ?? '我的家') ?></span>
                    <span class="tree-count"><?= count($spaces) ?></span>
                </div>
                <div class="tree-children">
                    <?= renderTree($tree) ?>
                </div>
            </div>
            <?php endif; ?>
        </div>
    </div>

    <!-- Right detail - 照抄 UI -->
    <div class="detail-panel" id="space-detail">
        <div class="empty" style="padding:60px 20px">
            <div class="empty-icon">👈</div>
            <div>点击左侧空间查看详情</div>
        </div>
    </div>
</div>

<script>
function switchHouse(houseId) {
    window.location.href = '?p=spaces&house_id=' + houseId;
}

function showCreateHouse() {
    var name = prompt('请输入家庭名称（如：奶奶家、爷爷家）');
    if (!name) return;
    var form = document.createElement('form');
    form.method = 'POST';
    form.innerHTML = '<input type="hidden" name="post_action" value="create_house">' +
        '<input type="hidden" name="name" value="' + name + '">';
    document.body.appendChild(form);
    form.submit();
}

function showCreateSpace() {
    var houseId = <?= $selectedHouse ?>;
    if (!houseId) { alert('请先选择一个家庭'); return; }
    var name = prompt('请输入空间名称（如：卧室、衣柜、抽屉）');
    if (!name) return;
    var level = prompt('空间层级：1=房间 2=容器 3=区域', '1');
    if (!level) return;
    var icons = {'1':'🛋','2':'📦','3':'📂'};
    var form = document.createElement('form');
    form.method = 'POST';
    form.innerHTML = '<input type="hidden" name="post_action" value="create_space">' +
        '<input type="hidden" name="house_id" value="' + houseId + '">' +
        '<input type="hidden" name="name" value="' + name + '">' +
        '<input type="hidden" name="level" value="' + level + '">' +
        '<input type="hidden" name="icon" value="' + (icons[level]||'📦') + '">' +
        '<input type="hidden" name="parent_id" value="0">';
    document.body.appendChild(form);
    form.submit();
}

async function selectSpace(id) {
    document.querySelectorAll('.tree-node').forEach(n => n.classList.remove('active'));
    const node = document.querySelector(`.tree-node[data-id="${id}"]`);
    if (node) node.classList.add('active');
    
    const data = await api(`../backend/api/space.php?action=detail&id=${id}`);
    if (!data) return;
    const s = data.space;
    
    const levelNames = ['房间', '容器', '区域'];
    
    let html = `
        <div class="detail-header">
            <div>
                <div class="detail-path">
                    <a href="#">我的家</a>
                    <span class="sep">/</span>
                    <span style="color:#2D3748;font-weight:500">${s.name}</span>
                </div>
                <div class="detail-title">
                    <div class="detail-icon">${s.icon}</div>
                    <div>
                        <div class="detail-name">${s.name}</div>
                        <div class="detail-desc">${levelNames[s.level - 1] || '空间'}</div>
                    </div>
                </div>
                <div class="detail-stats">
                    <div class="detail-stat">📦 物品 <strong>${s.item_count}</strong></div>
                    <div class="detail-stat">${s.shared == 1 ? '👁 已共享给全家人' : '🔒 仅自己可见'}</div>
                </div>
            </div>
            <div style="display:flex;gap:8px;flex-wrap:wrap">
                <button class="btn btn-outline btn-sm" onclick="editSpace(${s.id})">✎ 编辑</button>
                <button class="btn btn-danger btn-sm" onclick="deleteSpace(${s.id})">🗑 删除</button>
            </div>
        </div>`;
    
    if (s.children && s.children.length > 0) {
        html += `<div class="space-cards">`;
        const colors = ['','c2','c3','c4','c5','c6'];
        s.children.forEach((c, i) => {
            html += `<div class="space-card ${colors[i % 6]}">
                <div class="space-card-head">
                    <div class="space-card-icon">${c.icon}</div>
                    <div class="space-card-name">${c.name}</div>
                    <span class="space-card-tag">${levelNames[c.level - 1] || '空间'}</span>
                </div>
                <div class="space-card-meta">
                    <span>📦 <strong>${c.item_count}</strong> 件物品</span>
                </div>
            </div>`;
        });
        html += `</div>`;
    }
    
    // 加载物品列表
    html += `<div id="space-items" style="padding:20px 24px"><div style="color:#A0AEC0;font-size:13px">加载物品中...</div></div>`;
    
    document.getElementById('space-detail').innerHTML = html;
    
    // 异步加载物品
    loadSpaceItems(id);
}

async function loadSpaceItems(spaceId) {
    const container = document.getElementById('space-items');
    if (!container) return;
    
    const data = await api(`../backend/api/goods.php?action=list&space_id=${spaceId}&include_children=1&page_size=50`);
    if (!data || !data.list) {
        container.innerHTML = '<div style="color:#A0AEC0;font-size:13px">暂无物品</div>';
        return;
    }
    
    if (data.list.length === 0) {
        container.innerHTML = '<div style="color:#A0AEC0;font-size:13px">该空间暂无物品</div>';
        return;
    }
    
    let html = `<div style="font-size:13px;font-weight:600;color:#2D3748;margin-bottom:12px">📦 物品列表 (${data.list.length})</div>`;
    html += `<div style="display:grid;gap:8px">`;
    data.list.forEach(item => {
        const img = item.cover_image ? `<img src="${item.cover_image}" style="width:36px;height:36px;border-radius:8px;object-fit:cover" onerror="this.outerHTML='📦'">` : '📦';
        html += `<div style="display:flex;align-items:center;gap:10px;padding:10px 12px;background:#F7FAFC;border-radius:8px;cursor:pointer" onclick="window.location.href='?p=items&keyword=${encodeURIComponent(item.name)}'">
            <div style="width:36px;height:36px;border-radius:8px;background:linear-gradient(135deg,#FFE8D6,#FFD3B0);display:flex;align-items:center;justify-content:center;font-size:16px;overflow:hidden">${img}</div>
            <div style="flex:1">
                <div style="font-weight:600;font-size:13px">${item.name}</div>
                <div style="font-size:11px;color:#718096">× ${item.quantity}${item.unit || '件'}</div>
            </div>
            <div style="font-size:16px;color:#CBD5E0">›</div>
        </div>`;
    });
    html += `</div>`;
    container.innerHTML = html;
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
    if (!confirm('确定要删除此空间吗？')) return;
    const data = await api('../backend/api/space.php?action=delete', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({id: id, delete_goods: 0})
    });
    if (data !== null) { showToast('删除成功', 'success'); location.reload(); }
}
</script>
