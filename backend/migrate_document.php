<?php
/**
 * 文件档案模块迁移脚本
 * 访问: https://域名/backend/migrate_document.php
 */
require_once __DIR__ . '/config/database.php';
header('Content-Type: application/json; charset=utf-8');

try {
    $db = getDB();
    $results = [];

    // 1. 创建 document 表
    $db->exec("CREATE TABLE IF NOT EXISTS `document` (
        `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
        `house_id` INT UNSIGNED NOT NULL COMMENT '所属家庭',
        `creator_id` INT UNSIGNED NOT NULL COMMENT '创建者',
        `name` VARCHAR(200) NOT NULL COMMENT '文件名称',
        `category` VARCHAR(30) NOT NULL DEFAULT '其他' COMMENT '分类',
        `doc_no` VARCHAR(100) DEFAULT '' COMMENT '证件号码/合同编号',
        `issuer` VARCHAR(100) DEFAULT '' COMMENT '签发机构',
        `issue_date` DATE DEFAULT NULL COMMENT '签发日期',
        `expiry_date` DATE DEFAULT NULL COMMENT '到期日期',
        `storage_location` VARCHAR(200) DEFAULT '' COMMENT '存放位置',
        `space_id` INT UNSIGNED DEFAULT NULL COMMENT '关联收纳空间',
        `note` TEXT DEFAULT NULL COMMENT '备注',
        `is_private` TINYINT NOT NULL DEFAULT 1 COMMENT '是否私密',
        `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
        `created_at` INT UNSIGNED NOT NULL,
        `updated_at` INT UNSIGNED NOT NULL,
        PRIMARY KEY (`id`),
        KEY `idx_house` (`house_id`),
        KEY `idx_category` (`category`),
        KEY `idx_creator` (`creator_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件档案表'");
    $results[] = 'document 表已就绪';

    // 2. 创建 document_image 表
    $db->exec("CREATE TABLE IF NOT EXISTS `document_image` (
        `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
        `document_id` INT UNSIGNED NOT NULL,
        `image_path` VARCHAR(500) NOT NULL,
        `sort_order` INT NOT NULL DEFAULT 0,
        `created_at` INT UNSIGNED NOT NULL,
        PRIMARY KEY (`id`),
        KEY `idx_doc` (`document_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件档案图片表'");
    $results[] = 'document_image 表已就绪';

    echo json_encode(['code' => 0, 'msg' => '迁移完成', 'data' => $results], JSON_UNESCAPED_UNICODE);
} catch (Exception $e) {
    echo json_encode(['code' => 500, 'msg' => '迁移失败: ' . $e->getMessage()], JSON_UNESCAPED_UNICODE);
}
