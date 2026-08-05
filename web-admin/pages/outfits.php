<?php
require_once __DIR__ . '/../../backend/config/helpers.php';

// 临时调试：捕获所有错误并输出
set_error_handler(function($severity, $message, $file, $line) {
    echo '<div style="background:#FED7D7;color:#9B2C2C;padding:12px;margin:8px;border-radius:8px;font-size:13px">';
    echo '<b>PHP Error:</b> ' . htmlspecialchars($message) . ' in <b>' . htmlspecialchars($file) . '</b> line ' . $line;
    echo '</div>';
});
set_exception_handler(function($e) {
    echo '<div style="background:#FED7D7;color:#9B2C2C;padding:12px;margin:8px;border-radius:8px;font-size:13px">';
    echo '<b>Exception:</b> ' . htmlspecialchars($e->getMessage()) . ' in <b>' . htmlspecialchars($e->getFile()) . '</b> line ' . $e->getLine();
    echo '<pre>' . htmlspecialchars($e->getTraceAsString()) . '</pre>';
    echo '</div>';
});
register_shutdown_function(function() {
    $error = error_get_last();
    if ($error && in_array($error['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR])) {
        while (ob_get_level()) ob_end_clean();
        echo '<div style="background:#FED7D7;color:#9B2C2C;padding:12px;margin:8px;border-radius:8px;font-size:13px">';
        echo '<b>Fatal Error:</b> ' . htmlspecialchars($error['message']) . ' in <b>' . htmlspecialchars($error['file']) . '</b> line ' . $error['line'];
        echo '</div>';
    }
});

$db = getDB();

// 获取当前house_id
$houseId = intval($_GET['house_id'] ?? 0);
if (!$houseId) {
    $h = $db->query("SELECT id FROM house WHERE status = 1 ORDER BY id ASC LIMIT 1")->fetch();
    if ($h) $houseId = intval($h['id']);
}

// 获取所有家庭
$houses = $db->query("SELECT id, name FROM house WHERE status = 1 ORDER BY id ASC")->fetchAll();

// 获取服装类物品（用于创建套装时选择）
$clothingCategories = ['服装', '鞋帽'];
$catPlaceholders = implode(',', array_fill(0, count($clothingCategories), '?'));
$goodsStmt = $db->prepare("SELECT g.id, g.name, g.category, g.color, g.brand, g.season,
    (SELECT image_path FROM goods_image WHERE goods_id = g.id ORDER BY sort_order ASC LIMIT 1) as cover_image
    FROM goods g WHERE g.house_id = ? AND g.status = 1 AND g.category IN ($catPlaceholders) ORDER BY g.updated_at DESC");
$goodsStmt->execute(array_merge([$houseId], $clothingCategories));
$allClothing = $goodsStmt->fetchAll();

// 获取所有套装
$season = $_GET['season'] ?? '';
$occasion = $_GET['occasion'] ?? '';
$keyword = $_GET['keyword'] ?? '';

$where = ["o.house_id = ?", "o.status = 1"];
$params = [$houseId];
if (!empty($season)) { $where[] = "(o.season = ? OR o.season = '四季')"; $params[] = $season; }
if (!empty($occasion)) { $where[] = "o.occasion = ?"; $params[] = $occasion; }
if (!empty($keyword)) { $where[] = "(o.name LIKE ? OR o.note LIKE ?)"; $kw = "%$keyword%"; $params[] = $kw; $params[] = $kw; }
$whereStr = implode(' AND ', $where);

$outfitStmt = $db->prepare("SELECT o.* FROM outfit o WHERE $whereStr ORDER BY o.updated_at DESC");
$outfitStmt->execute($params);
$outfits = $outfitStmt->fetchAll();

// 加载每个套装的物品
$itemStmt = $db->prepare("SELECT oi.*, g.name as goods_name, g.category, g.color, g.brand,
    (SELECT image_path FROM goods_image WHERE goods_id = g.id ORDER BY sort_order ASC LIMIT 1) as cover_image
    FROM outfit_item oi LEFT JOIN goods g ON oi.goods_id = g.id
    WHERE oi.outfit_id = ? AND g.status = 1 ORDER BY oi.sort_order ASC");

$IMAGE_BASE = 'https://sn.tthsdd.top/backend/uploads/';
?>

<style>
.page-header{margin-bottom:20px}
.filter-bar{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-bottom:16px}
.filter-bar select,.filter-bar input{padding:8px 12px;border:1px solid #E2E8F0;border-radius:8px;font-size:13px}
.filter-bar select:focus,.filter-bar input:focus{outline:none;border-color:#FF8C42}

.outfit-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));gap:16px}
.outfit-card{background:#fff;border-radius:12px;border:1px solid #E2E8F0;overflow:hidden;transition:all .2s;cursor:pointer}
.outfit-card:hover{transform:translateY(-2px);box-shadow:0 8px 24px rgba(0,0,0,.08)}

.outfit-items-grid{display:grid;grid-template-columns:1fr 1fr;gap:2px;padding:2px;background:#F7FAFC;min-height:160px}
.outfit-items-grid .item-thumb{aspect-ratio:1;background:#EDF2F7;display:flex;align-items:center;justify-content:center;overflow:hidden}
.outfit-items-grid .item-thumb img{width:100%;height:100%;object-fit:cover}
.outfit-items-grid .item-thumb .placeholder{font-size:28px;color:#CBD5E0}

.outfit-info{padding:14px}
.outfit-name{font-size:15px;font-weight:600;color:#2D3748;margin-bottom:6px}
.outfit-tags{display:flex;gap:6px;flex-wrap:wrap}
.tag{padding:3px 10px;border-radius:12px;font-size:11px;font-weight:600}
.tag-season{background:#E0F7F4;color:#0E9F8E}
.tag-occasion{background:#E9D8FD;color:#6B46C1}
.tag-count{background:#EDF2F7;color:#718096}

.empty-state{text-align:center;padding:60px 20px;color:#A0AEC0}
.empty-state .icon{font-size:48px;margin-bottom:12px}
.empty-state .title{font-size:16px;font-weight:600;color:#4A5568;margin-bottom:6px}
.empty-state .desc{font-size:13px}

/* Modal */
.modal-mask{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.45);z-index:999;display:none;align-items:center;justify-content:center}
.modal-box{background:#fff;border-radius:12px;max-width:700px;width:92%;max-height:85vh;overflow-y:auto;padding:24px}
.modal-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}
.modal-title{font-size:16px;font-weight:600}
.modal-close{cursor:pointer;font-size:22px;color:#999;background:none;border:none}

.form-row{margin-bottom:14px}
.form-label{display:block;font-size:12px;font-weight:600;color:#4A5568;margin-bottom:6px}
.form-input{width:100%;padding:10px 12px;border:1px solid #E2E8F0;border-radius:8px;font-size:13px;box-sizing:border-box}
.form-input:focus{outline:none;border-color:#FF8C42}
.form-hint{font-size:11px;color:#A0AEC0;margin-top:4px}

.goods-picker{max-height:300px;overflow-y:auto;border:1px solid #E2E8F0;border-radius:8px;margin-top:8px}
.goods-picker-item{display:flex;align-items:center;gap:10px;padding:10px 12px;border-bottom:1px solid #F7FAFC;cursor:pointer;transition:background .15s}
.goods-picker-item:hover{background:#FFFAF0}
.goods-picker-item.selected{background:#FFF7F0;border-left:3px solid #FF8C42}
.goods-picker-item .thumb{width:40px;height:40px;border-radius:8px;background:#EDF2F7;display:flex;align-items:center;justify-content:center;overflow:hidden;flex-shrink:0}
.goods-picker-item .thumb img{width:100%;height:100%;object-fit:cover}
.goods-picker-item .info{flex:1;min-width:0}
.goods-picker-item .info .name{font-size:13px;font-weight:600;color:#2D3748}
.goods-picker-item .info .meta{font-size:11px;color:#718096}

.selected-items{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}
.selected-chip{display:flex;align-items:center;gap:6px;padding:6px 10px;background:#FFFAF0;border:1px solid #FFD3B0;border-radius:8px;font-size:12px}
.selected-chip .remove{cursor:pointer;color:#F56565;font-size:14px}

.slot-select{padding:4px 8px;border:1px solid #E2E8F0;border-radius:6px;font-size:11px;background:#fff}

.btn-row{display:flex;gap:8px;margin-top:16px}
.btn-lg{padding:12px 24px;border-radius:8px;font-size:14px;font-weight:600;cursor:pointer;border:none;transition:all .2s}
.btn-orange{background:linear-gradient(135deg,#FF8C42,#FF6B6B);color:#fff}
.btn-gray{background:#F7FAFC;color:#4A5568;border:1px solid #E2E8F0}

.outfit-detail-items{display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:12px;margin:12px 0}
.detail-item{background:#F7FAFC;border-radius:10px;padding:10px;text-align:center}
.detail-item .thumb{width:80px;height:80px;border-radius:8px;margin:0 auto 8px;background:#EDF2F7;display:flex;align-items:center;justify-content:center;overflow:hidden}
.detail-item .thumb img{width:100%;height:100%;object-fit:cover}
.detail-item .name{font-size:12px;font-weight:600;color:#2D3748}
.detail-item .slot{font-size:10px;color:#718096;margin-top:2px}

.picker-filter{display:flex;gap:8px;margin-bottom:8px;flex-wrap:wrap}
.picker-filter input{padding:6px 10px;border:1px solid #E2E8F0;border-radius:6px;font-size:12px;flex:1;min-width:120px}
.picker-filter select{padding:6px 8px;border:1px solid #E2E8F0;border-radius:6px;font-size:12px}
</style>

<div class="page-header">
    <div style="display:flex;justify-content:space-between;align-items:center">
        <div>
            <div class="page-title">👔 衣帽间</div>
            <div class="page-desc">管理服装搭配 · 创建套装组合 · 按季节/场合筛选</div>
        </div>
        <div style="display:flex;gap:8px">
            <select id="house-selector" onchange="switchHouse(this.value)" style="padding:8px 12px;border:1px solid #E2E8F0;border-radius:8px;font-size:13px">
                <?php foreach ($houses as $h): ?>
                <option value="<?= $h['id'] ?>" <?= $h['id'] == $houseId ? 'selected' : '' ?>><?= htmlspecialchars($h['name']) ?></option>
                <?php endforeach; ?>
            </select>
            <button class="btn-lg btn-orange" onclick="showCreateModal()">+ 新建套装</button>
        </div>
    </div>
</div>

<!-- 筛选栏 -->
<div class="filter-bar">
    <select id="filter-season" onchange="applyFilter()">
        <option value="">全部季节</option>
        <option value="春" <?= $season === '春' ? 'selected' : '' ?>>🌸 春季</option>
        <option value="夏" <?= $season === '夏' ? 'selected' : '' ?>>☀️ 夏季</option>
        <option value="秋" <?= $season === '秋' ? 'selected' : '' ?>>🍂 秋季</option>
        <option value="冬" <?= $season === '冬' ? 'selected' : '' ?>>❄️ 冬季</option>
        <option value="四季" <?= $season === '四季' ? 'selected' : '' ?>>🔄 四季通用</option>
    </select>
    <select id="filter-occasion" onchange="applyFilter()">
        <option value="">全部场合</option>
        <option value="通勤" <?= $occasion === '通勤' ? 'selected' : '' ?>>💼 通勤</option>
        <option value="运动" <?= $occasion === '运动' ? 'selected' : '' ?>>🏃 运动</option>
        <option value="约会" <?= $occasion === '约会' ? 'selected' : '' ?>>💕 约会</option>
        <option value="居家" <?= $occasion === '居家' ? 'selected' : '' ?>>🏠 居家</option>
        <option value="正装" <?= $occasion === '正装' ? 'selected' : '' ?>>👔 正装</option>
        <option value="休闲" <?= $occasion === '休闲' ? 'selected' : '' ?>>😎 休闲</option>
    </select>
    <input type="text" id="filter-keyword" placeholder="🔍 搜索套装名称..." value="<?= htmlspecialchars($keyword) ?>" onkeyup="if(event.key==='Enter')applyFilter()" style="width:200px">
    <button class="btn-lg btn-gray" onclick="applyFilter()" style="padding:8px 16px;font-size:12px">筛选</button>
</div>

<!-- 套装列表 -->
<?php if (empty($outfits)): ?>
<div class="empty-state">
    <div class="icon">👔</div>
    <div class="title">还没有套装</div>
    <div class="desc">点击右上角「+ 新建套装」创建你的第一个穿搭组合</div>
</div>
<?php else: ?>
<div class="outfit-grid" id="outfit-grid">
    <?php foreach ($outfits as $outfit):
        $itemStmt->execute([$outfit['id']]);
        $items = $itemStmt->fetchAll();
    ?>
    <div class="outfit-card" onclick="showDetail(<?= $outfit['id'] ?>)">
        <div class="outfit-items-grid">
            <?php
            $slots = ['top', 'bottom', 'hat', 'shoes', 'outer', 'accessory'];
            $slotIcons = ['top'=>'👕', 'bottom'=>'👖', 'hat'=>'🧢', 'shoes'=>'👟', 'outer'=>'🧥', 'accessory'=>'👜'];
            $shown = 0;
            foreach ($items as $it):
                if ($shown >= 4) break;
                $img = !empty($it['cover_image']) ? $IMAGE_BASE . $it['cover_image'] : '';
            ?>
            <div class="item-thumb">
                <?php if ($img): ?>
                    <img src="<?= $img ?>" alt="">
                <?php else: ?>
                    <span class="placeholder"><?= $slotIcons[$it['slot']] ?? '📦' ?></span>
                <?php endif; ?>
            </div>
            <?php $shown++; endforeach; ?>
            <?php for ($i = $shown; $i < 4; $i++): ?>
            <div class="item-thumb"><span class="placeholder">+</span></div>
            <?php endfor; ?>
        </div>
        <div class="outfit-info">
            <div class="outfit-name"><?= htmlspecialchars($outfit['name']) ?></div>
            <div class="outfit-tags">
                <?php if (!empty($outfit['season'])): ?>
                    <span class="tag tag-season"><?= htmlspecialchars($outfit['season']) ?></span>
                <?php endif; ?>
                <?php if (!empty($outfit['occasion'])): ?>
                    <span class="tag tag-occasion"><?= htmlspecialchars($outfit['occasion']) ?></span>
                <?php endif; ?>
                <span class="tag tag-count"><?= count($items) ?> 件</span>
            </div>
        </div>
    </div>
    <?php endforeach; ?>
</div>
<?php endif; ?>

<!-- ========== 创建/编辑套装弹窗 ========== -->
<div id="modal-create" class="modal-mask">
    <div class="modal-box">
        <div class="modal-head">
            <div class="modal-title" id="modal-create-title">+ 新建套装</div>
            <button class="modal-close" onclick="hideCreateModal()">&times;</button>
        </div>

        <input type="hidden" id="edit-outfit-id" value="">

        <div class="form-row">
            <label class="form-label">套装名称 *</label>
            <input type="text" id="outfit-name" class="form-input" placeholder="如：通勤穿搭、运动套装、约会look">
        </div>
        <div style="display:flex;gap:12px">
            <div class="form-row" style="flex:1">
                <label class="form-label">适用季节</label>
                <select id="outfit-season" class="form-input">
                    <option value="">不指定</option>
                    <option value="春">🌸 春季</option>
                    <option value="夏">☀️ 夏季</option>
                    <option value="秋">🍂 秋季</option>
                    <option value="冬">❄️ 冬季</option>
                    <option value="四季">🔄 四季通用</option>
                    <option value="春秋">🌿 春秋</option>
                </select>
            </div>
            <div class="form-row" style="flex:1">
                <label class="form-label">场合</label>
                <select id="outfit-occasion" class="form-input">
                    <option value="">不指定</option>
                    <option value="通勤">💼 通勤</option>
                    <option value="运动">🏃 运动</option>
                    <option value="约会">💕 约会</option>
                    <option value="居家">🏠 居家</option>
                    <option value="正装">👔 正装</option>
                    <option value="休闲">😎 休闲</option>
                </select>
            </div>
        </div>
        <div class="form-row">
            <label class="form-label">备注</label>
            <input type="text" id="outfit-note" class="form-input" placeholder="可选备注">
        </div>

        <div class="form-row">
            <label class="form-label">选择物品（服装/鞋帽类）</label>

            <!-- 物品筛选器 -->
            <div class="picker-filter">
                <input type="text" id="picker-search" placeholder="搜索物品名称..." oninput="filterPicker()">
                <select id="picker-category" onchange="filterPicker()">
                    <option value="">全部分类</option>
                    <option value="服装">👔 服装</option>
                    <option value="鞋帽">👟 鞋帽</option>
                </select>
                <select id="picker-color" onchange="filterPicker()">
                    <option value="">全部颜色</option>
                </select>
                <select id="picker-season" onchange="filterPicker()">
                    <option value="">全部季节</option>
                    <option value="春">春</option>
                    <option value="夏">夏</option>
                    <option value="秋">秋</option>
                    <option value="冬">冬</option>
                    <option value="四季">四季</option>
                </select>
            </div>

            <div class="goods-picker" id="goods-picker">
                <?php foreach ($allClothing as $g):
                    $img = !empty($g['cover_image']) ? $IMAGE_BASE . $g['cover_image'] : '';
                ?>
                <div class="goods-picker-item" data-id="<?= $g['id'] ?>" data-name="<?= htmlspecialchars($g['name']) ?>" data-cat="<?= htmlspecialchars($g['category']) ?>" data-color="<?= htmlspecialchars($g['color']) ?>" data-season="<?= htmlspecialchars($g['season']) ?>" onclick="toggleGoods(this, <?= $g['id'] ?>)">
                    <div class="thumb">
                        <?php if ($img): ?><img src="<?= $img ?>" alt=""><?php else: ?><span style="font-size:18px">📦</span><?php endif; ?>
                    </div>
                    <div class="info">
                        <div class="name"><?= htmlspecialchars($g['name']) ?></div>
                        <div class="meta"><?= htmlspecialchars($g['category']) ?> · <?= htmlspecialchars($g['color'] ?: '未标色') ?> · <?= htmlspecialchars($g['season'] ?: '未标季') ?></div>
                    </div>
                </div>
                <?php endforeach; ?>
            </div>
        </div>

        <!-- 已选物品 -->
        <div class="form-row">
            <label class="form-label">已选物品 (<span id="selected-count">0</span> 件)</label>
            <div class="selected-items" id="selected-items"></div>
        </div>

        <div class="btn-row">
            <button class="btn-lg btn-gray" onclick="hideCreateModal()">取消</button>
            <button class="btn-lg btn-orange" onclick="saveOutfit()">💾 保存套装</button>
        </div>
        <div id="save-status" style="margin-top:10px;font-size:12px"></div>
    </div>
</div>

<!-- ========== 套装详情弹窗 ========== -->
<div id="modal-detail" class="modal-mask">
    <div class="modal-box">
        <div class="modal-head">
            <div class="modal-title" id="detail-title">套装详情</div>
            <button class="modal-close" onclick="document.getElementById('modal-detail').style.display='none'">&times;</button>
        </div>
        <div id="detail-body"></div>
    </div>
</div>

<script>
var IMAGE_BASE = '<?= $IMAGE_BASE ?>';
var HOUSE_ID = <?= $houseId ?>;
var selectedGoods = []; // [{id, name, slot, color, category}]
var SLOT_NAMES = {top:'上装', bottom:'下装', hat:'帽子', shoes:'鞋子', outer:'外套', accessory:'配饰'};
var SLOT_OPTIONS = ['top','bottom','hat','shoes','outer','accessory'];

function switchHouse(hid) {
    window.location.href = '?p=outfits&house_id=' + hid;
}

function applyFilter() {
    var season = document.getElementById('filter-season').value;
    var occasion = document.getElementById('filter-occasion').value;
    var keyword = document.getElementById('filter-keyword').value;
    var url = '?p=outfits&house_id=' + HOUSE_ID;
    if (season) url += '&season=' + encodeURIComponent(season);
    if (occasion) url += '&occasion=' + encodeURIComponent(occasion);
    if (keyword) url += '&keyword=' + encodeURIComponent(keyword);
    window.location.href = url;
}

// ====== 创建套装 ======
function showCreateModal(outfitId) {
    document.getElementById('edit-outfit-id').value = outfitId || '';
    document.getElementById('modal-create-title').textContent = outfitId ? '编辑套装' : '+ 新建套装';
    document.getElementById('outfit-name').value = '';
    document.getElementById('outfit-season').value = '';
    document.getElementById('outfit-occasion').value = '';
    document.getElementById('outfit-note').value = '';
    selectedGoods = [];
    renderSelected();
    // 清除picker选中状态
    document.querySelectorAll('.goods-picker-item').forEach(function(el) { el.classList.remove('selected'); });
    document.getElementById('modal-create').style.display = 'flex';

    // 如果是编辑模式，加载数据
    if (outfitId) {
        fetch('../backend/api/outfit.php?action=detail&id=' + outfitId)
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.code === 0) {
                    var o = data.data.outfit;
                    document.getElementById('outfit-name').value = o.name || '';
                    document.getElementById('outfit-season').value = o.season || '';
                    document.getElementById('outfit-occasion').value = o.occasion || '';
                    document.getElementById('outfit-note').value = o.note || '';
                    if (o.items) {
                        o.items.forEach(function(it) {
                            selectedGoods.push({id: parseInt(it.goods_id), name: it.goods_name, slot: it.slot || 'top', color: it.color || '', category: it.category || ''});
                            var el = document.querySelector('.goods-picker-item[data-id="' + it.goods_id + '"]');
                            if (el) el.classList.add('selected');
                        });
                        renderSelected();
                    }
                }
            });
    }
}

function hideCreateModal() {
    document.getElementById('modal-create').style.display = 'none';
}

function filterPicker() {
    var search = document.getElementById('picker-search').value.toLowerCase();
    var cat = document.getElementById('picker-category').value;
    var color = document.getElementById('picker-color').value;
    var season = document.getElementById('picker-season').value;

    document.querySelectorAll('.goods-picker-item').forEach(function(el) {
        var name = (el.dataset.name || '').toLowerCase();
        var elCat = el.dataset.cat || '';
        var elColor = el.dataset.color || '';
        var elSeason = el.dataset.season || '';

        var show = true;
        if (search && name.indexOf(search) < 0) show = false;
        if (cat && elCat !== cat) show = false;
        if (color && elColor !== color) show = false;
        if (season && elSeason !== season && elSeason !== '四季') show = false;

        el.style.display = show ? '' : 'none';
    });
}

function toggleGoods(el, goodsId) {
    goodsId = parseInt(goodsId);
    var idx = selectedGoods.findIndex(function(g) { return g.id === goodsId; });
    if (idx >= 0) {
        selectedGoods.splice(idx, 1);
        el.classList.remove('selected');
    } else {
        var name = el.dataset.name || '';
        var cat = el.dataset.cat || '';
        var color = el.dataset.color || '';
        // 自动推测slot
        var slot = 'top';
        if (cat === '鞋帽') slot = 'shoes';
        else if (cat === '服装' && (name.indexOf('裤') >= 0 || name.indexOf('裙') >= 0)) slot = 'bottom';
        else if (name.indexOf('帽') >= 0) slot = 'hat';
        else if (name.indexOf('外套') >= 0 || name.indexOf('夹克') >= 0 || name.indexOf('大衣') >= 0 || name.indexOf('羽绒') >= 0) slot = 'outer';

        selectedGoods.push({id: goodsId, name: name, slot: slot, color: color, category: cat});
        el.classList.add('selected');
    }
    renderSelected();
}

function renderSelected() {
    var container = document.getElementById('selected-items');
    document.getElementById('selected-count').textContent = selectedGoods.length;

    if (selectedGoods.length === 0) {
        container.innerHTML = '<span style="font-size:12px;color:#A0AEC0">点击上方物品列表选择</span>';
        return;
    }

    var html = '';
    selectedGoods.forEach(function(g, idx) {
        html += '<div class="selected-chip">';
        html += '<span>' + g.name + '</span>';
        html += '<select class="slot-select" onchange="selectedGoods[' + idx + '].slot=this.value">';
        SLOT_OPTIONS.forEach(function(s) {
            html += '<option value="' + s + '"' + (g.slot === s ? ' selected' : '') + '>' + SLOT_NAMES[s] + '</option>';
        });
        html += '</select>';
        html += '<span class="remove" onclick="removeGoods(' + idx + ')">✕</span>';
        html += '</div>';
    });
    container.innerHTML = html;
}

function removeGoods(idx) {
    var g = selectedGoods[idx];
    selectedGoods.splice(idx, 1);
    var el = document.querySelector('.goods-picker-item[data-id="' + g.id + '"]');
    if (el) el.classList.remove('selected');
    renderSelected();
}

async function saveOutfit() {
    var name = document.getElementById('outfit-name').value.trim();
    if (!name) { showToast('请输入套装名称', 'error'); return; }

    var editId = document.getElementById('edit-outfit-id').value;
    var body = {
        house_id: HOUSE_ID,
        name: name,
        season: document.getElementById('outfit-season').value,
        occasion: document.getElementById('outfit-occasion').value,
        note: document.getElementById('outfit-note').value,
        items: selectedGoods.map(function(g) { return {goods_id: g.id, slot: g.slot}; })
    };

    var url = editId ? '../backend/api/outfit.php?action=update' : '../backend/api/outfit.php?action=create';
    if (editId) body.id = parseInt(editId);

    var status = document.getElementById('save-status');
    status.innerHTML = '<span style="color:#ED8936">⏳ 保存中...</span>';

    var resp = await postJSON(url, body);
    if (resp) {
        status.innerHTML = '<span style="color:#48BB78">✅ 保存成功</span>';
        showToast('套装已保存', 'success');
        setTimeout(function() { location.reload(); }, 800);
    } else {
        status.innerHTML = '<span style="color:#F56565">❌ 保存失败</span>';
    }
}

// ====== 套装详情 ======
async function showDetail(outfitId) {
    var resp = await api('../backend/api/outfit.php?action=detail&id=' + outfitId);
    if (!resp) return;

    var o = resp.outfit;
    document.getElementById('detail-title').textContent = o.name;

    var html = '<div style="margin-bottom:12px">';
    if (o.season) html += '<span class="tag tag-season" style="margin-right:6px">' + o.season + '</span>';
    if (o.occasion) html += '<span class="tag tag-occasion" style="margin-right:6px">' + o.occasion + '</span>';
    if (o.note) html += '<div style="font-size:12px;color:#718096;margin-top:8px">' + escHtml(o.note) + '</div>';
    html += '</div>';

    html += '<div class="outfit-detail-items">';
    if (o.items && o.items.length > 0) {
        o.items.forEach(function(it) {
            html += '<div class="detail-item">';
            html += '<div class="thumb">';
            if (it.cover_image) html += '<img src="' + it.cover_image + '" alt="">';
            else html += '<span style="font-size:24px">📦</span>';
            html += '</div>';
            html += '<div class="name">' + escHtml(it.goods_name) + '</div>';
            html += '<div class="slot">' + (SLOT_NAMES[it.slot] || it.slot) + '</div>';
            if (it.color) html += '<div class="slot">' + it.color + '</div>';
            html += '</div>';
        });
    }
    html += '</div>';

    html += '<div style="display:flex;gap:8px;margin-top:16px">';
    html += '<button class="btn-lg btn-orange" onclick="document.getElementById(\'modal-detail\').style.display=\'none\';showCreateModal(' + o.id + ')">✏️ 编辑</button>';
    html += '<button class="btn-lg" style="background:#FED7D7;color:#F56565;border:none" onclick="deleteOutfit(' + o.id + ')">🗑 删除</button>';
    html += '<button class="btn-lg btn-gray" onclick="document.getElementById(\'modal-detail\').style.display=\'none\'">关闭</button>';
    html += '</div>';

    document.getElementById('detail-body').innerHTML = html;
    document.getElementById('modal-detail').style.display = 'flex';
}

async function deleteOutfit(id) {
    if (!confirm('确定要删除这个套装吗？（物品不会被删除）')) return;
    var resp = await postJSON('../backend/api/outfit.php?action=delete', {id: id});
    if (resp) {
        showToast('已删除', 'success');
        document.getElementById('modal-detail').style.display = 'none';
        setTimeout(function() { location.reload(); }, 500);
    }
}

function escHtml(s) {
    if (!s) return '';
    var div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
}

// 初始化颜色筛选选项
(function() {
    var colors = new Set();
    document.querySelectorAll('.goods-picker-item').forEach(function(el) {
        var c = el.dataset.color;
        if (c) colors.add(c);
    });
    var sel = document.getElementById('picker-color');
    colors.forEach(function(c) {
        var opt = document.createElement('option');
        opt.value = c;
        opt.textContent = c;
        sel.appendChild(opt);
    });
})();
</script>
