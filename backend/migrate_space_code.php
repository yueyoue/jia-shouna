<?php
/**
 * 空间短编码迁移脚本
 * 访问: https://域名/backend/migrate_space_code.php
 */
require_once __DIR__ . '/config/database.php';
header('Content-Type: application/json; charset=utf-8');

try {
    $db = getDB();
    $results = [];

    // 1. 添加 space_code 列
    $cols = $db->query("SHOW COLUMNS FROM storage_space LIKE 'space_code'")->fetchAll();
    if (empty($cols)) {
        $db->exec("ALTER TABLE storage_space ADD COLUMN space_code VARCHAR(10) DEFAULT NULL AFTER name");
        $results[] = 'space_code 列已添加';
    } else {
        $results[] = 'space_code 列已存在';
    }

    // 2. 添加唯一索引
    try {
        $db->exec("CREATE UNIQUE INDEX uk_space_code ON storage_space(space_code)");
        $results[] = '唯一索引已添加';
    } catch (Exception $e) {
        $results[] = '唯一索引已存在';
    }

    // 3. 为已有空间生成编码
    $stmt = $db->query("SELECT id FROM storage_space WHERE space_code IS NULL OR space_code = ''");
    $spaces = $stmt->fetchAll();
    $count = 0;
    foreach ($spaces as $sp) {
        $code = generateSpaceCode($db);
        $db->prepare("UPDATE storage_space SET space_code = ? WHERE id = ?")->execute([$code, $sp['id']]);
        $count++;
    }
    if ($count > 0) $results[] = "已为 {$count} 个空间生成编码";

    echo json_encode(['code' => 0, 'msg' => '迁移完成', 'data' => $results], JSON_UNESCAPED_UNICODE);
} catch (Exception $e) {
    echo json_encode(['code' => 500, 'msg' => '迁移失败: ' . $e->getMessage()], JSON_UNESCAPED_UNICODE);
}

/**
 * 生成6位空间编码 (大写字母+数字, 排除易混淆字符 O/0/I/1/L)
 */
function generateSpaceCode($db) {
    $chars = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
    for ($attempt = 0; $attempt < 100; $attempt++) {
        $code = '';
        for ($i = 0; $i < 6; $i++) {
            $code .= $chars[random_int(0, strlen($chars) - 1)];
        }
        $check = $db->prepare("SELECT id FROM storage_space WHERE space_code = ?");
        $check->execute([$code]);
        if (!$check->fetch()) return $code;
    }
    return strtoupper(substr(md5(uniqid()), 0, 6));
}
