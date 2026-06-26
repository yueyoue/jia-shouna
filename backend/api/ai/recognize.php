<?php
/**
 * AI 智能识别接口（预览，不入库）
 * POST /api/ai/recognize
 * Content-Type: multipart/form-data
 */
require_once __DIR__ . '/../../config/database.php';
require_once __DIR__ . '/../../config/helpers.php';
require_once __DIR__ . '/../../config/ai.php';
require_once __DIR__ . '/../../library/Agent/Agent.php';
require_once __DIR__ . '/../../library/Agent/Tools/BarcodeTool.php';
require_once __DIR__ . '/../../library/Agent/Tools/MatchGoodsTool.php';
require_once __DIR__ . '/../../library/Agent/Tools/SpacesTool.php';

corsHeaders();
$db = getDB();
$user = requireLogin();

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    error('请求方式错误', 405);
}

// 接收上传的图片
if (empty($_FILES['image'])) {
    error('请上传物品照片');
}

$file = $_FILES['image'];
$allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
if (!in_array($file['type'], $allowedTypes)) {
    error('仅支持 JPG/PNG/GIF/WebP 格式');
}
if ($file['size'] > 5 * 1024 * 1024) {
    error('图片大小不能超过 5MB');
}

// 保存图片
$ext = pathinfo($file['name'], PATHINFO_EXTENSION);
$dir = UPLOAD_PATH . 'images/' . date('Ym') . '/';
if (!is_dir($dir)) mkdir($dir, 0755, true);
$filename = generateFileName($ext);
$filepath = $dir . $filename;
$relativePath = 'images/' . date('Ym') . '/' . $filename;
move_uploaded_file($file['tmp_name'], $filepath);

// 构建图片URL
$imageUrl = IMAGE_URL_PREFIX . $relativePath;
$spaceId = intval($_POST['space_id'] ?? 0);
$houseId = intval($_POST['house_id'] ?? 0);

// 如果没传 house_id，自动获取
if (!$houseId) {
    $houseStmt = $db->prepare("SELECT house_id FROM house_member WHERE user_id = ? ORDER BY joined_at ASC LIMIT 1");
    $houseStmt->execute([$user['id']]);
    $houseRow = $houseStmt->fetch();
    if ($houseRow) {
        $houseId = intval($houseRow['house_id']);
    } else {
        $houseStmt2 = $db->prepare("SELECT id FROM house WHERE creator_id = ? AND status = 1 LIMIT 1");
        $houseStmt2->execute([$user['id']]);
        $houseRow2 = $houseStmt2->fetch();
        if ($houseRow2) $houseId = intval($houseRow2['id']);
    }
}

try {
    // 创建 Agent 实例
    $agent = new Agent($user['id']);

    // 注册工具
    register_barcode_tool($agent);
    register_match_goods_tool($agent);
    register_spaces_tool($agent);

    // 执行识别
    $result = $agent->recognize($imageUrl);

    // 补充返回字段
    $result['image_path'] = $relativePath;
    $result['image_url'] = $imageUrl;
    $result['house_id'] = $houseId;

    // 如果识别到分类，补充建议空间
    if (!empty($result['category']) && $houseId) {
        $spaceSuggestion = suggestSpace($db, $houseId, $result['category']);
        if ($spaceSuggestion) {
            $result['suggested_space_id'] = $spaceSuggestion['id'];
            $result['suggested_space_name'] = $spaceSuggestion['name'];
        }
    }

    success($result);

} catch (Exception $e) {
    error('AI 识别失败: ' . $e->getMessage());
}

/**
 * 根据分类推荐存放空间
 */
function suggestSpace($db, $houseId, $category) {
    // 分类 -> 常见空间名映射
    $map = [
        '食品'   => ['厨房', '冰箱', '储物柜', '食品柜'],
        '药品'   => ['药箱', '客厅', '卧室', '卫生间'],
        '日用品' => ['卫生间', '浴室', '储物间', '阳台'],
        '电子配件' => ['书房', '电脑桌', '客厅', '抽屉'],
        '衣物'   => ['卧室', '衣柜', '衣帽间'],
        '厨具'   => ['厨房', '橱柜'],
        '文具'   => ['书房', '书桌', '客厅'],
    ];

    $keywords = $map[$category] ?? [];
    if (empty($keywords)) return null;

    foreach ($keywords as $kw) {
        $stmt = $db->prepare("SELECT id, name FROM storage_space WHERE house_id = ? AND name LIKE ? AND level = 1 LIMIT 1");
        $stmt->execute([$houseId, "%$kw%"]);
        $space = $stmt->fetch();
        if ($space) return $space;
    }

    // 没匹配到，返回第一个一级空间
    $stmt = $db->prepare("SELECT id, name FROM storage_space WHERE house_id = ? AND level = 1 ORDER BY sort_order ASC LIMIT 1");
    $stmt->execute([$houseId]);
    return $stmt->fetch();
}
