-- 文件档案表
CREATE TABLE IF NOT EXISTS `document` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `house_id` INT UNSIGNED NOT NULL COMMENT '所属家庭',
    `creator_id` INT UNSIGNED NOT NULL COMMENT '创建者',
    `name` VARCHAR(200) NOT NULL COMMENT '文件名称',
    `category` VARCHAR(30) NOT NULL DEFAULT '其他' COMMENT '分类: 证件/合同/票据/保单/房产/车辆/教育/医疗/其他',
    `doc_no` VARCHAR(100) DEFAULT '' COMMENT '证件号码/合同编号',
    `issuer` VARCHAR(100) DEFAULT '' COMMENT '签发机构/甲方',
    `issue_date` DATE DEFAULT NULL COMMENT '签发日期',
    `expiry_date` DATE DEFAULT NULL COMMENT '到期日期',
    `storage_location` VARCHAR(200) DEFAULT '' COMMENT '存放位置(物理)',
    `space_id` INT UNSIGNED DEFAULT NULL COMMENT '关联收纳空间',
    `note` TEXT DEFAULT NULL COMMENT '备注',
    `is_private` TINYINT NOT NULL DEFAULT 1 COMMENT '是否私密(默认私密)',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=已删除 1=正常',
    `created_at` INT UNSIGNED NOT NULL,
    `updated_at` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_house` (`house_id`),
    KEY `idx_category` (`category`),
    KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件档案表';

-- 文件图片表
CREATE TABLE IF NOT EXISTS `document_image` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `document_id` INT UNSIGNED NOT NULL,
    `image_path` VARCHAR(500) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `created_at` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件档案图片表';
