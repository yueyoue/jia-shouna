<?php
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';
corsHeaders();

try {
$action = $_GET['action'] ?? '';
$db = getDB();
$user = requireLogin();

switch ($action) {
    case 'list':
        $houseId = intval($_GET['house_id'] ?? 0);
        $category = $_GET['category'] ?? '';
        $keyword = $_GET['keyword'] ?? '';
        $page = max(1, intval($_GET['page'] ?? 1));
        $pageSize = min(50, max(1, intval($_GET['page_size'] ?? 20)));
        $expiring = intval($_GET['expiring'] ?? 0); // 即将过期

        $where = ["d.status = 1"];
        $params = [];

        if (!$houseId) {
            $hs = $db->prepare("SELECT house_id FROM house_member WHERE user_id = ? ORDER BY joined_at ASC LIMIT 1");
            $hs->execute([$user['id']]);
            $hr = $hs->fetch();
            if ($hr) $houseId = intval($hr['house_id']);
            else {
                $hs2 = $db->prepare("SELECT id FROM house WHERE creator_id = ? AND status = 1 LIMIT 1");
                $hs2->execute([$user['id']]);
                $hr2 = $hs2->fetch();
                if ($hr2) $houseId = intval($hr2['id']);
            }
        }
        if ($houseId) { $where[] = "d.house_id = ?"; $params[] = $houseId; }
        if ($category) { $where[] = "d.category = ?"; $params[] = $category; }
        if ($keyword) {
            $where[] = "(d.name LIKE ? OR d.doc_no LIKE ? OR d.issuer LIKE ?)";
            $kw = "%$keyword%";
            $params = array_merge($params, [$kw, $kw, $kw]);
        }
        if ($expiring) {
            $where[] = "d.expiry_date IS NOT NULL AND d.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 90 DAY) AND d.expiry_date >= CURDATE()";
        }
        $where[] = "(d.is_private = 0 OR d.creator_id = ?)";
        $params[] = $user['id'];

        $whereStr = implode(' AND ', $where);
        $offset = ($page - 1) * $pageSize;

        $countStmt = $db->prepare("SELECT COUNT(*) as cnt FROM document d WHERE $whereStr");
        $countStmt->execute($params);
        $total = $countStmt->fetch()['cnt'];

        $stmt = $db->prepare("SELECT d.*, s.name as space_name, u.nickname as creator_name
            FROM document d
            LEFT JOIN storage_space s ON d.space_id = s.id
            LEFT JOIN sys_user u ON d.creator_id = u.id
            WHERE $whereStr ORDER BY d.updated_at DESC LIMIT $pageSize OFFSET $offset");
        $stmt->execute($params);
        $list = $stmt->fetchAll();

        // 为每个文档加载首张图片
        foreach ($list as &$doc) {
            $imgStmt = $db->prepare("SELECT image_path FROM document_image WHERE document_id = ? ORDER BY sort_order ASC LIMIT 1");
            $imgStmt->execute([$doc['id']]);
            $img = $imgStmt->fetch();
            $doc['cover_image'] = $img ? $img['image_path'] : '';

            // 计算过期状态
            $doc['expiry_status'] = '';
            if (!empty($doc['expiry_date'])) {
                $diff = (strtotime($doc['expiry_date']) - time()) / 86400;
                if ($diff < 0) $doc['expiry_status'] = 'expired';
                elseif ($diff <= 30) $doc['expiry_status'] = 'expiring';
                elseif ($diff <= 90) $doc['expiry_status'] = 'warning';
                else $doc['expiry_status'] = 'valid';
                $doc['days_left'] = intval($diff);
            }

            // 图片数量
            $cntStmt = $db->prepare("SELECT COUNT(*) as c FROM document_image WHERE document_id = ?");
            $cntStmt->execute([$doc['id']]);
            $doc['image_count'] = $cntStmt->fetch()['c'];
        }
        unset($doc);

        success(['list' => $list, 'total' => $total, 'page' => $page]);
        break;

    case 'detail':
        $id = intval($_GET['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $stmt = $db->prepare("SELECT d.*, s.name as space_name, u.nickname as creator_name
            FROM document d
            LEFT JOIN storage_space s ON d.space_id = s.id
            LEFT JOIN sys_user u ON d.creator_id = u.id
            WHERE d.id = ? AND d.status = 1");
        $stmt->execute([$id]);
        $doc = $stmt->fetch();
        if (!$doc) error('文件不存在');

        // 加载图片
        $imgStmt = $db->prepare("SELECT * FROM document_image WHERE document_id = ? ORDER BY sort_order ASC");
        $imgStmt->execute([$id]);
        $doc['images'] = $imgStmt->fetchAll();

        // 过期状态
        if (!empty($doc['expiry_date'])) {
            $diff = (strtotime($doc['expiry_date']) - time()) / 86400;
            $doc['days_left'] = intval($diff);
        }

        success(['document' => $doc]);
        break;

    case 'create':
        $input = getJsonInput();
        $houseId = intval($input['house_id'] ?? 0);
        $name = trim($input['name'] ?? '');
        $category = trim($input['category'] ?? '其他');

        if (empty($name)) error('请输入文件名称');

        if (!$houseId) {
            $hs = $db->prepare("SELECT house_id FROM house_member WHERE user_id = ? LIMIT 1");
            $hs->execute([$user['id']]);
            $hr = $hs->fetch();
            if ($hr) $houseId = intval($hr['house_id']);
        }
        if (!$houseId) error('请先加入一个家庭');

        $now = time();
        $issueDate = !empty($input['issue_date']) ? $input['issue_date'] : null;
        $expiryDate = !empty($input['expiry_date']) ? $input['expiry_date'] : null;

        $stmt = $db->prepare("INSERT INTO document (house_id, creator_id, name, category, doc_no, issuer, issue_date, expiry_date, storage_location, space_id, note, is_private, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)");
        $stmt->execute([
            $houseId, $user['id'], $name, $category,
            $input['doc_no'] ?? '', $input['issuer'] ?? '',
            $issueDate, $expiryDate,
            $input['storage_location'] ?? '',
            intval($input['space_id'] ?? 0) ?: null,
            $input['note'] ?? '',
            intval($input['is_private'] ?? 1),
            $now, $now
        ]);
        $docId = $db->lastInsertId();

        // 处理图片
        if (!empty($input['images'])) {
            foreach ($input['images'] as $idx => $imgPath) {
                $db->prepare("INSERT INTO document_image (document_id, image_path, sort_order, created_at) VALUES (?, ?, ?, ?)")
                    ->execute([$docId, $imgPath, $idx, $now]);
            }
        }

        // 创建到期提醒
        if ($expiryDate) {
            $expiryTs = strtotime($expiryDate);
            $remindTs = $expiryTs - 30 * 86400; // 提前30天提醒
            if ($remindTs > $now) {
                $db->prepare("INSERT INTO reminder (user_id, house_id, type, title, content, remind_time, created_at) VALUES (?, ?, 'custom', ?, ?, ?, ?)")
                    ->execute([$user['id'], $houseId, "文件即将到期: $name", "到期日: $expiryDate", $remindTs, $now]);
            }
        }

        success(['id' => $docId], '创建成功');
        break;

    case 'update':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $stmt = $db->prepare("SELECT * FROM document WHERE id = ? AND status = 1");
        $stmt->execute([$id]);
        $doc = $stmt->fetch();
        if (!$doc) error('文件不存在');

        $now = time();
        $issueDate = !empty($input['issue_date']) ? $input['issue_date'] : null;
        $expiryDate = !empty($input['expiry_date']) ? $input['expiry_date'] : null;

        $db->prepare("UPDATE document SET name=?, category=?, doc_no=?, issuer=?, issue_date=?, expiry_date=?, storage_location=?, space_id=?, note=?, is_private=?, updated_at=? WHERE id=?")
            ->execute([
                $input['name'] ?? $doc['name'],
                $input['category'] ?? $doc['category'],
                $input['doc_no'] ?? $doc['doc_no'],
                $input['issuer'] ?? $doc['issuer'],
                $issueDate ?: $doc['issue_date'],
                $expiryDate ?: $doc['expiry_date'],
                $input['storage_location'] ?? $doc['storage_location'],
                isset($input['space_id']) ? (intval($input['space_id']) ?: null) : $doc['space_id'],
                $input['note'] ?? $doc['note'],
                isset($input['is_private']) ? intval($input['is_private']) : $doc['is_private'],
                $now, $id
            ]);

        // 更新图片（如果有新图片）
        if (!empty($input['images'])) {
            $db->prepare("DELETE FROM document_image WHERE document_id = ?")->execute([$id]);
            foreach ($input['images'] as $idx => $imgPath) {
                $db->prepare("INSERT INTO document_image (document_id, image_path, sort_order, created_at) VALUES (?, ?, ?, ?)")
                    ->execute([$id, $imgPath, $idx, $now]);
            }
        }

        success(null, '更新成功');
        break;

    case 'delete':
        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        if (!$id) error('缺少参数id');

        $db->prepare("UPDATE document SET status = 0, updated_at = ? WHERE id = ?")
            ->execute([time(), $id]);
        success(null, '已删除');
        break;

    case 'stats':
        $houseId = intval($_GET['house_id'] ?? 0);
        if (!$houseId) {
            $hs = $db->prepare("SELECT house_id FROM house_member WHERE user_id = ? LIMIT 1");
            $hs->execute([$user['id']]);
            $hr = $hs->fetch();
            if ($hr) $houseId = intval($hr['house_id']);
        }

        $stats = [];
        $where = ["d.status = 1", "d.house_id = ?"];
        $params = [$houseId];
        $where[] = "(d.is_private = 0 OR d.creator_id = ?)";
        $params[] = $user['id'];
        $whereStr = implode(' AND ', $where);

        // 总数
        $stmt = $db->prepare("SELECT COUNT(*) as c FROM document d WHERE $whereStr");
        $stmt->execute($params);
        $stats['total'] = $stmt->fetch()['c'];

        // 各分类数量
        $stmt = $db->prepare("SELECT category, COUNT(*) as c FROM document d WHERE $whereStr GROUP BY category");
        $stmt->execute($params);
        $stats['categories'] = [];
        while ($row = $stmt->fetch()) {
            $stats['categories'][$row['category']] = intval($row['c']);
        }

        // 即将过期(90天内)
        $expWhere = array_merge($where, ["d.expiry_date IS NOT NULL", "d.expiry_date <= DATE_ADD(CURDATE(), INTERVAL 90 DAY)", "d.expiry_date >= CURDATE()"]);
        $expWhereStr = implode(' AND ', $expWhere);
        $stmt = $db->prepare("SELECT COUNT(*) as c FROM document d WHERE $expWhereStr");
        $stmt->execute($params);
        $stats['expiring'] = $stmt->fetch()['c'];

        // 已过期
        $expWhere2 = array_merge($where, ["d.expiry_date IS NOT NULL", "d.expiry_date < CURDATE()"]);
        $expWhereStr2 = implode(' AND ', $expWhere2);
        $stmt = $db->prepare("SELECT COUNT(*) as c FROM document d WHERE $expWhereStr2");
        $stmt->execute($params);
        $stats['expired'] = $stmt->fetch()['c'];

        success($stats);
        break;

    default:
        error('未知操作');
}
} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(['code' => 500, 'msg' => 'PHP错误: ' . $e->getMessage(), 'file' => $e->getFile(), 'line' => $e->getLine()], JSON_UNESCAPED_UNICODE);
    exit;
}
