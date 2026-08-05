<?php
$db = getDB();

// 获取当前house_id
$houseId = intval($_GET['house_id'] ?? 0);
if (!$houseId) {
    $h = $db->query("SELECT id FROM house WHERE status = 1 ORDER BY id ASC LIMIT 1")->fetch();
    if ($h) $houseId = intval($h['id']);
}

// 获取所有家庭
$houses = $db->query("SELECT id, name FROM house WHERE status = 1 ORDER BY id ASC")->fetchAll();

// 获取标签列表
$tags = [];
if ($houseId) {
    $stmt = $db->prepare("SELECT t.*, (SELECT COUNT(*) FROM goods_tag WHERE tag_id = t.id) as usage_count FROM tag t WHERE t.house_id = ? ORDER BY t.name ASC");
    $stmt->execute([$houseId]);
    $tags = $stmt->fetchAll();
}
?>

<style>
.tag-header{margin-bottom:20px}
.tag-stats{display:flex;gap:16px;margin-bottom:16px;flex-wrap:wrap}
.tag-stat-card{background:#fff;border:1px solid #E2E8F0;border-radius:12px;padding:16px 20px;min-width:140px}
.tag-stat-card .num{font-size:24px;font-weight:700;color:#FF8C42}
.tag-stat-card .label{font-size:12px;color:#718096;margin-top:4px}

.tag-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:12px}
.tag-card{background:#fff;border:1px solid #E2E8F0;border-radius:12px;padding:16px;cursor:pointer;transition:all .2s;position:relative;overflow:hidden}
.tag-card:hover{transform:translateY(-2px);box-shadow:0 8px 24px rgba(0,0,0,.06)}
.tag-card::before{content:'';position:absolute;left:0;top:0;bottom:0;width:4px;border-radius:4px 0 0 4px}
.tag-card-head{display:flex;align-items:center;gap:10px;margin-bottom:10px}
.tag-dot{width:14px;height:14px;border-radius:50%;flex-shrink:0}
.tag-card-name{font-size:15px;font-weight:600;color:#2D3748;flex:1}
.tag-card-meta{display:flex;justify-content:space-between;align-items:center;font-size:12px;color:#718096;padding-top:10px;border-top:1px dashed #EDF2F7}
.tag-card-meta .count{background:#F7FAFC;padding:2px 8px;border-radius:8px;font-weight:600;color:#4A5568}
.tag-card-actions{display:flex;gap:4px}
.tag-card-actions button{background:none;border:none;cursor:pointer;font-size:14px;padding:4px;border-radius:4px;transition:background .15s}
.tag-card-actions button:hover{background:#F7FAFC}

.empty-state{text-align:center;padding:60px 20px;color:#A0AEC0}
.empty-state .icon{font-size:48px;margin-bottom:12px}
.empty-state .title{font-size:16px;font-weight:600;color:#4A5568;margin-bottom:6px}
.empty-state .desc{font-size:13px}

/* 物品列表面板 */
.goods-panel{background:#fff;border:1px solid #E2E8F0;border-radius:12px;margin-top:16px;overflow:hidden}
.goods-panel-header{padding:16px 20px;background:linear-gradient(135deg,#FFF7F0 0%,#F0FBFA 100%);border-bottom:1px solid #E2E8F0;display:flex;align-items:center;justify-content:space-between}
.goods-panel-title{font-size:15px;font-weight:600;color:#2D3748}
.goods-list{max-height:400px;overflow-y:auto}
.goods-item{display:flex;align-items:center;gap:12px;padding:12px 20px;border-bottom:1px solid #F7FAFC;transition:background .15s}
.goods-item:hover{background:#FFFAF0}
.goods-item:last-child{border-bottom:none}
.goods-item-thumb{width:40px;height:40px;border-radius:8px;background:linear-gradient(135deg,#FFE8D6,#FFD3B0);display:flex;align-items:center;justify-content:center;font-size:18px;overflow:hidden;flex-shrink:0}
.goods-item-thumb img{width:100%;height:100%;object-fit:cover}
.goods-item-info{flex:1;min-width:0}
.goods-item-name{font-size:13px;font-weight:600;color:#2D3748}
.goods-item-meta{font-size:11px;color:#718096;margin-top:2px}
</style>

<div class="page-header">
    <div style="display:flex;justify-content:space-between;align-items:center">
        <div>
            <div class="page-title">🏷️ 标签管理</div>
            <div class="page-desc">管理物品标签 · 创建和编辑标签 · 按标签筛选物品</div>
        </div>
        <div style="display:flex;gap:8px">
            <select id="house-selector" onchange="switchHouse(this.value)" style="padding:8px 12px;border:1px solid #E2E8F0;border-radius:8px;font-size:13px">
                <?php foreach ($houses as $h): ?>
                <option value="<?= $h['id'] ?>" <?= $h['id'] == $houseId ? 'selected' : '' ?>><?= htmlspecialchars($h['name']) ?></option>
                <?php endforeach; ?>
            </select>
            <button class="btn btn-outline btn-sm" onclick="showTagModal()">+ 新建标签</button>
        </div>
    </div>
</div>

<!-- 统计卡片 -->
<div class="tag-stats">
    <div class="tag-stat-card">
        <div class="num"><?= count($tags) ?></div>
        <div class="label">标签总数</div>
    </div>
    <div class="tag-stat-card">
        <div class="num"><?= array_sum(array_column($tags, 'usage_count')) ?></div>
        <div class="label">关联物品</div>
    </div>
</div>

<!-- 标签列表 -->
<?php if (empty($tags)): ?>
<div class="empty-state">
    <div class="icon">🏷️</div>
    <div class="title">还没有标签</div>
    <div class="desc">点击右上角「+ 新建标签」创建你的第一个标签</div>
</div>
<?php else: ?>
<div class="tag-grid" id="tag-grid">
    <?php foreach ($tags as $tag): ?>
    <div class="tag-card" style="border-left:4px solid <?= htmlspecialchars($tag['color']) ?>" onclick="showTagGoods(<?= $tag['id'] ?>, '<?= htmlspecialchars($tag['name']) ?>', '<?= htmlspecialchars($tag['color']) ?>')">
        <div class="tag-card-head">
            <div class="tag-dot" style="background:<?= htmlspecialchars($tag['color']) ?>"></div>
            <div class="tag-card-name"><?= htmlspecialchars($tag['name']) ?></div>
            <div class="tag-card-actions" onclick="event.stopPropagation()">
                <button title="编辑" onclick="editTag(<?= $tag['id'] ?>, '<?= htmlspecialchars($tag['name']) ?>', '<?= htmlspecialchars($tag['color']) ?>')">✏️</button>
                <button title="删除" onclick="deleteTag(<?= $tag['id'] ?>, '<?= htmlspecialchars($tag['name']) ?>')">🗑</button>
            </div>
        </div>
        <div class="tag-card-meta">
            <span>创建于 <?= date('Y-m-d', $tag['created_at']) ?></span>
            <span class="count"><?= $tag['usage_count'] ?> 件物品</span>
        </div>
    </div>
    <?php endforeach; ?>
</div>
<?php endif; ?>

<!-- 物品列表面板 -->
<div id="tag-goods-panel" style="display:none"></div>

<!-- 创建/编辑标签弹窗 -->
<div id="modal-tag" style="position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.45);z-index:999;display:none;align-items:center;justify-content:center">
    <div style="background:#fff;border-radius:12px;max-width:420px;width:92%;padding:24px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px">
            <div style="font-size:16px;font-weight:600" id="modal-tag-title">新建标签</div>
            <button style="cursor:pointer;font-size:22px;color:#999;background:none;border:none" onclick="hideTagModal()">&times;</button>
        </div>
        <input type="hidden" id="edit-tag-id" value="">
        <div style="margin-bottom:16px">
            <label style="display:block;font-size:12px;font-weight:600;color:#4A5568;margin-bottom:6px">标签名称 *</label>
            <input type="text" id="tag-name" style="width:100%;padding:10px 12px;border:1px solid #E2E8F0;border-radius:8px;font-size:13px;box-sizing:border-box" placeholder="如：重要、季节性、待处理">
        </div>
        <div style="margin-bottom:20px">
            <label style="display:block;font-size:12px;font-weight:600;color:#4A5568;margin-bottom:6px">标签颜色</label>
            <div id="color-picker" style="display:flex;gap:8px;flex-wrap:wrap">
                <div class="color-opt" data-color="#FF8C42" style="width:32px;height:32px;border-radius:8px;background:#FF8C42;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#F56565" style="width:32px;height:32px;border-radius:8px;background:#F56565;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#ED8936" style="width:32px;height:32px;border-radius:8px;background:#ED8936;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#ECC94B" style="width:32px;height:32px;border-radius:8px;background:#ECC94B;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#48BB78" style="width:32px;height:32px;border-radius:8px;background:#48BB78;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#38B2AC" style="width:32px;height:32px;border-radius:8px;background:#38B2AC;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#5B9FED" style="width:32px;height:32px;border-radius:8px;background:#5B9FED;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#805AD5" style="width:32px;height:32px;border-radius:8px;background:#805AD5;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#D53F8C" style="width:32px;height:32px;border-radius:8px;background:#D53F8C;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#718096" style="width:32px;height:32px;border-radius:8px;background:#718096;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
                <div class="color-opt" data-color="#2D3748" style="width:32px;height:32px;border-radius:8px;background:#2D3748;cursor:pointer;border:3px solid transparent;transition:all .15s"></div>
            </div>
        </div>
        <div style="display:flex;gap:8px;justify-content:flex-end">
            <button class="btn btn-outline btn-sm" onclick="hideTagModal()">取消</button>
            <button class="btn btn-outline btn-sm" onclick="saveTag()" style="background:linear-gradient(135deg,#FF8C42,#FF6B6B);color:#fff;border:none">💾 保存</button>
        </div>
    </div>
</div>

<script>
var HOUSE_ID = <?= $houseId ?>;
var IMAGE_BASE = 'https://sn.tthsdd.top/backend/uploads/';
var selectedColor = '#5B9FED';

// 颜色选择器
document.querySelectorAll('.color-opt').forEach(function(el) {
    el.addEventListener('click', function() {
        document.querySelectorAll('.color-opt').forEach(function(o) { o.style.borderColor = 'transparent'; });
        el.style.borderColor = '#2D3748';
        selectedColor = el.dataset.color;
    });
});
// 默认选中
var defaultColor = document.querySelector('.color-opt[data-color="#5B9FED"]');
if (defaultColor) defaultColor.style.borderColor = '#2D3748';

function switchHouse(hid) {
    window.location.href = '?p=tags&house_id=' + hid;
}

function showTagModal(id, name, color) {
    document.getElementById('edit-tag-id').value = id || '';
    document.getElementById('modal-tag-title').textContent = id ? '编辑标签' : '新建标签';
    document.getElementById('tag-name').value = name || '';
    selectedColor = color || '#5B9FED';
    document.querySelectorAll('.color-opt').forEach(function(o) {
        o.style.borderColor = o.dataset.color === selectedColor ? '#2D3748' : 'transparent';
    });
    document.getElementById('modal-tag').style.display = 'flex';
}

function hideTagModal() {
    document.getElementById('modal-tag').style.display = 'none';
}

function editTag(id, name, color) {
    showTagModal(id, name, color);
}

async function saveTag() {
    var name = document.getElementById('tag-name').value.trim();
    if (!name) { showToast('请输入标签名称', 'error'); return; }
    var editId = document.getElementById('edit-tag-id').value;
    var body = { house_id: HOUSE_ID, name: name, color: selectedColor };
    var url;
    if (editId) {
        body.id = parseInt(editId);
        url = '../backend/api/tag.php?action=update';
    } else {
        url = '../backend/api/tag.php?action=create';
    }
    var resp = await postJSON(url, body);
    if (resp !== null) {
        showToast(editId ? '标签已更新' : '标签已创建', 'success');
        hideTagModal();
        setTimeout(function() { location.reload(); }, 500);
    }
}

async function deleteTag(id, name) {
    if (!confirm('确定要删除标签「' + name + '」吗？\n关联的物品不会被删除，只会解除标签关联。')) return;
    var resp = await api('../backend/api/tag.php?action=delete&id=' + id);
    if (resp !== null) {
        showToast('标签已删除', 'success');
        setTimeout(function() { location.reload(); }, 500);
    }
}

async function showTagGoods(tagId, tagName, tagColor) {
    var panel = document.getElementById('tag-goods-panel');
    panel.style.display = 'block';
    panel.innerHTML = '<div style="padding:20px;text-align:center;color:#A0AEC0">加载中...</div>';
    
    var data = await api('../backend/api/goods.php?action=list&tag_id=' + tagId + '&page_size=50');
    var items = (data && data.list) ? data.list : [];
    
    var html = '<div class="goods-panel">';
    html += '<div class="goods-panel-header">';
    html += '  <div class="goods-panel-title"><span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:' + tagColor + ';margin-right:6px"></span>' + escHtml(tagName) + ' · ' + items.length + ' 件物品</div>';
    html += '  <button style="background:none;border:none;cursor:pointer;font-size:18px;color:#999" onclick="document.getElementById(\'tag-goods-panel\').style.display=\'none\'">&times;</button>';
    html += '</div>';
    
    if (items.length === 0) {
        html += '<div style="padding:30px;text-align:center;color:#A0AEC0;font-size:13px">该标签下暂无物品</div>';
    } else {
        html += '<div class="goods-list">';
        items.forEach(function(item) {
            var img = item.cover_image ? '<img src="' + IMAGE_BASE + item.cover_image + '" alt="">' : '📦';
            html += '<div class="goods-item" onclick="window.location.href=\'?p=items&keyword=' + encodeURIComponent(item.name) + '\'">';
            html += '  <div class="goods-item-thumb">' + img + '</div>';
            html += '  <div class="goods-item-info">';
            html += '    <div class="goods-item-name">' + escHtml(item.name) + '</div>';
            html += '    <div class="goods-item-meta">' + escHtml(item.category || '') + ' · ' + escHtml(item.color || '') + ' · ×' + item.quantity + (item.unit || '件') + '</div>';
            html += '  </div>';
            html += '  <div style="font-size:16px;color:#CBD5E0">›</div>';
            html += '</div>';
        });
        html += '</div>';
    }
    html += '</div>';
    panel.innerHTML = html;
    panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function escHtml(s) {
    if (!s) return '';
    var div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
}
</script>
