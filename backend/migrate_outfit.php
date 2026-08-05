<?php
/**
 * 一次性迁移脚本 - 创建衣帽间套装系统所需的数据表和字段
 * 运行后自动删除自身
 */
require_once __DIR__ . '/config/database.php';

header('Content-Type: application/json; charset=utf-8');
$db = getDB();

$errors = [];
$success = [];

// 1. goods 表新增字段
$newColumns = [
    'color' => "VARCHAR(30) DEFAULT '' COMMENT '颜色'",
    'season' => "VARCHAR(30) DEFAULT '' COMMENT '适用季节'",
    'size' => "VARCHAR(20) DEFAULT '' COMMENT '尺码'",
    'material' => "VARCHAR(50) DEFAULT '' COMMENT '材质'",
    'shoe_size' => "VARCHAR(10) DEFAULT '' COMMENT '鞋码'",
];

foreach ($newColumns as $col => $type) {
    try {
        $db->exec("ALTER TABLE goods ADD COLUMN `$col` $type AFTER " .
            ($col === 'color' ? 'category' :
             ($col === 'season' ? 'color' :
             ($col === 'size' ? 'spec' :
             ($col === 'material' ? 'size' : 'material')))));
        $success[] = "goods.$col 已添加";
    } catch (Exception $e) {
        if (strpos($e->getMessage(), 'Duplicate column') !== false) {
            $success[] = "goods.$col 已存在(跳过)";
        } else {
            $errors[] = "goods.$col: " . $e->getMessage();
        }
    }
}

// 2. 创建 outfit 表
try {
    $db->exec("CREATE TABLE IF NOT EXISTS `outfit` (
        `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
        `house_id` INT UNSIGNED NOT NULL COMMENT '所属家庭',
        `creator_id` INT UNSIGNED NOT NULL COMMENT '创建者',
        `name` VARCHAR(100) NOT NULL COMMENT '套装名称',
        `season` VARCHAR(30) DEFAULT '' COMMENT '适用季节',
        `occasion` VARCHAR(50) DEFAULT '' COMMENT '场合',
        `cover_image` VARCHAR(500) DEFAULT '' COMMENT '封面图',
        `note` TEXT COMMENT '备注',
        `status` TINYINT NOT NULL DEFAULT 1,
        `created_at` INT UNSIGNED NOT NULL,
        `updated_at` INT UNSIGNED NOT NULL,
        PRIMARY KEY (`id`),
        KEY `idx_house` (`house_id`),
        KEY `idx_season` (`season`),
        KEY `idx_creator` (`creator_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套装表'");
    $success[] = "outfit 表已创建";
} catch (Exception $e) {
    $errors[] = "outfit: " . $e->getMessage();
}

// 3. 创建 outfit_item 表
try {
    $db->exec("CREATE TABLE IF NOT EXISTS `outfit_item` (
        `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
        `outfit_id` INT UNSIGNED NOT NULL COMMENT '套装ID',
        `goods_id` INT UNSIGNED NOT NULL COMMENT '物品ID',
        `slot` VARCHAR(20) DEFAULT '' COMMENT '位置',
        `sort_order` INT NOT NULL DEFAULT 0,
        PRIMARY KEY (`id`),
        UNIQUE KEY `uk_outfit_goods` (`outfit_id`, `goods_id`),
        KEY `idx_outfit` (`outfit_id`),
        KEY `idx_goods` (`goods_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套装物品关联表'");
    $success[] = "outfit_item 表已创建";
} catch (Exception $e) {
    $errors[] = "outfit_item: " . $e->getMessage();
}

echo json_encode([
    'code' => empty($errors) ? 0 : 1,
    'msg' => empty($errors) ? '迁移完成' : '部分迁移失败',
    'success' => $success,
    'errors' => $errors
], JSON_UNESCAPED_UNICODE);
