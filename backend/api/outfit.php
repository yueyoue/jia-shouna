<?php
// 临时调试
error_reporting(E_ALL);
ini_set('display_errors', '1');
ini_set('log_errors', '0');

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
corsHeaders();

// 捕获致命错误
register_shutdown_function(function() {
    $error = error_get_last();
    if ($error && in_array($error['type'], [E_ERROR, E_PARSE, E_CORE_ERROR, E_COMPILE_ERROR])) {
        while (ob_get_level()) ob_end_clean();
        header('Content-Type: application/json; charset=utf-8');
        echo json_encode(['code' => 500, 'msg' => 'Fatal: ' . $error['message'] . ' in ' . $error['file'] . ':' . $error['line']], JSON_UNESCAPED_UNICODE);
    }
});

$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    // ==========================================
    //  创建套装
    // ==========================================
    case 'create':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $name = trim($input['name'] ?? '');
        if (!$houseId || empty($name)) error('请填写套装名称');

        $now = time();
        $stmt = $db->prepare("INSERT INTO outfit (house_id, creator_id, name, season, occasion, note, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)");
        $stmt->execute([
            $houseId, $user['id'], $name,
            $input['season'] ?? '',
            $input['occasion'] ?? '',
            $input['note'] ?? '',
            $now, $now
        ]);
        $outfitId = $db->lastInsertId();

        // 关联物品
        if (!empty($input['items']) && is_array($input['items'])) {
            $stmtItem = $db->prepare("INSERT INTO outfit_item (outfit_id, goods_id, slot, sort_order) VALUES (?, ?, ?, ?)");
            foreach ($input['items'] as $idx => $item) {
                $goodsId = intval($item['goods_id'] ?? 0);
                if ($goodsId > 0) {
                    $slot = $item['slot'] ?? '';
                    $stmtItem->execute([$outfitId, $goodsId, $slot, $idx]);
                }
            }
        }

        success(['id' => $outfitId, 'msg' => '创建成功']);
        break;

    // ==========================================
    //  套装列表
    // ==========================================
    case 'list':
        $houseId = intval($_GET['house_id'] ?? 0);
        if (!$houseId) error('缺少house_id');

        $season = $_GET['season'] ?? '';
        $occasion = $_GET['occasion'] ?? '';
        $keyword = $_GET['keyword'] ?? '';
        $page = max(1, intval($_GET['page'] ?? 1));
        $pageSize = min(50, max(1, intval($_GET['page_size'] ?? 20)));

        $where = ["o.house_id = ?", "o.status = 1"];
        $params = [$houseId];

        if (!empty($season)) {
            $where[] = "(o.season = ? OR o.season = '四季' OR o.season = ?)";
            $params[] = $season;
            // 春秋兼容
            if ($season === '春' || $season === '秋') {
                $where[] = "1=1"; // 已经用 OR 处理了
            }
            $params[] = $season === '春' ? '春秋' : ($season === '秋' ? '春秋' : $season);
        }
        if (!empty($occasion)) {
            $where[] = "o.occasion = ?";
            $params[] = $occasion;
        }
        if (!empty($keyword)) {
            $where[] = "(o.name LIKE ? OR o.note LIKE ?)";
            $kw = "%$keyword%";
            $params[] = $kw;
            $params[] = $kw;
        }

        $whereStr = implode(' AND ', $where);

        // 总数
        $countStmt = $db->prepare("SELECT COUNT(*) as cnt FROM outfit o WHERE $whereStr");
        $countStmt->execute($params);
        $total = $countStmt->fetch()['cnt'];

        // 列表
        $offset = ($page - 1) * $pageSize;
        $sql = "SELECT o.* FROM outfit o WHERE $whereStr ORDER BY o.updated_at DESC LIMIT $pageSize OFFSET $offset";
        $stmt = $db->prepare($sql);
        $stmt->execute($params);
        $outfits = $stmt->fetchAll();

        // 为每个套装加载关联物品
        $stmtItems = $db->prepare("
            SELECT oi.*, g.name as goods_name, g.category, g.color, g.brand,
                   (SELECT image_path FROM goods_image WHERE goods_id = g.id ORDER BY sort_order ASC LIMIT 1) as cover_image
            FROM outfit_item oi
            LEFT JOIN goods g ON oi.goods_id = g.id
            WHERE oi.outfit_id = ? AND g.status = 1
            ORDER BY oi.sort_order ASC
        ");

        foreach ($outfits as &$outfit) {
            $stmtItems->execute([$outfit['id']]);
            $items = $stmtItems->fetchAll();
            foreach ($items as &$item) {
                $item['cover_image'] = !empty($item['cover_image']) ? IMAGE_URL_PREFIX . $item['cover_image'] : '';
            }
            $outfit['items'] = $items;

            // 如果没有自定义封面，用第一件物品的图片
            if (empty($outfit['cover_image']) && !empty($items)) {
                foreach ($items as $it) {
                    if (!empty($it['cover_image'])) {
                        $outfit['cover_image'] = $it['cover_image'];
                        break;
                    }
                }
            }
        }

        success(['list' => $outfits, 'total' => $total, 'page' => $page, 'page_size' => $pageSize]);
        break;

    // ==========================================
    //  套装详情
    // ==========================================
    case 'detail':
        $id = intval($_GET['id'] ?? 0);
        if (!$id) error('缺少id');

        $stmt = $db->prepare("SELECT * FROM outfit WHERE id = ? AND status = 1");
        $stmt->execute([$id]);
        $outfit = $stmt->fetch();
        if (!$outfit) error('套装不存在');

        $stmtItems = $db->prepare("
            SELECT oi.*, g.name as goods_name, g.category, g.color, g.brand, g.season as goods_season, g.size, g.material,
                   s.name as space_name,
                   (SELECT image_path FROM goods_image WHERE goods_id = g.id ORDER BY sort_order ASC LIMIT 1) as cover_image
            FROM outfit_item oi
            LEFT JOIN goods g ON oi.goods_id = g.id
            LEFT JOIN storage_space s ON g.space_id = s.id
            WHERE oi.outfit_id = ? AND g.status = 1
            ORDER BY oi.sort_order ASC
        ");
        $stmtItems->execute([$id]);
        $items = $stmtItems->fetchAll();
        foreach ($items as &$item) {
            $item['cover_image'] = !empty($item['cover_image']) ? IMAGE_URL_PREFIX . $item['cover_image'] : '';
        }
        $outfit['items'] = $items;

        if (!empty($outfit['cover_image'])) {
            $outfit['cover_image'] = IMAGE_URL_PREFIX . $outfit['cover_image'];
        }

        success(['outfit' => $outfit]);
        break;

    // ==========================================
    //  更新套装
    // ==========================================
    case 'update':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        if (!$id) error('缺少id');

        // 检查是否存在
        $stmt = $db->prepare("SELECT * FROM outfit WHERE id = ? AND status = 1");
        $stmt->execute([$id]);
        $outfit = $stmt->fetch();
        if (!$outfit) error('套装不存在');

        $now = time();
        $fields = [];
        $params = [];

        if (isset($input['name'])) { $fields[] = "name = ?"; $params[] = trim($input['name']); }
        if (isset($input['season'])) { $fields[] = "season = ?"; $params[] = $input['season']; }
        if (isset($input['occasion'])) { $fields[] = "occasion = ?"; $params[] = $input['occasion']; }
        if (isset($input['note'])) { $fields[] = "note = ?"; $params[] = $input['note']; }
        if (isset($input['cover_image'])) { $fields[] = "cover_image = ?"; $params[] = $input['cover_image']; }

        $fields[] = "updated_at = ?";
        $params[] = $now;
        $params[] = $id;

        $db->prepare("UPDATE outfit SET " . implode(', ', $fields) . " WHERE id = ?")->execute($params);

        // 更新关联物品
        if (isset($input['items']) && is_array($input['items'])) {
            $db->prepare("DELETE FROM outfit_item WHERE outfit_id = ?")->execute([$id]);
            $stmtItem = $db->prepare("INSERT INTO outfit_item (outfit_id, goods_id, slot, sort_order) VALUES (?, ?, ?, ?)");
            foreach ($input['items'] as $idx => $item) {
                $goodsId = intval($item['goods_id'] ?? 0);
                if ($goodsId > 0) {
                    $stmtItem->execute([$id, $goodsId, $item['slot'] ?? '', $idx]);
                }
            }
        }

        success(['msg' => '更新成功']);
        break;

    // ==========================================
    //  删除套装
    // ==========================================
    case 'delete':
        $input = getJsonInput();
        $id = intval($input['id'] ?? $_GET['id'] ?? 0);
        if (!$id) error('缺少id');

        $db->prepare("UPDATE outfit SET status = 0, updated_at = ? WHERE id = ?")->execute([time(), $id]);
        success(['msg' => '已删除']);
        break;

    default:
        error('未知操作');
}
