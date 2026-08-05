-- ============================================
-- 衣帽间功能：套装系统 + 物品扩展字段
-- 日期: 2026-08-05
-- ============================================

-- 1. goods 表新增字段
ALTER TABLE `goods` ADD COLUMN `color` VARCHAR(30) DEFAULT '' COMMENT '颜色' AFTER `category`;
ALTER TABLE `goods` ADD COLUMN `season` VARCHAR(30) DEFAULT '' COMMENT '适用季节: 春/夏/秋/冬/四季/春秋' AFTER `color`;
ALTER TABLE `goods` ADD COLUMN `size` VARCHAR(20) DEFAULT '' COMMENT '尺码(S/M/L/XL等)' AFTER `spec`;
ALTER TABLE `goods` ADD COLUMN `material` VARCHAR(50) DEFAULT '' COMMENT '材质' AFTER `size`;
ALTER TABLE `goods` ADD COLUMN `shoe_size` VARCHAR(10) DEFAULT '' COMMENT '鞋码' AFTER `material`;

-- 2. 套装表
CREATE TABLE IF NOT EXISTS `outfit` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `house_id` INT UNSIGNED NOT NULL COMMENT '所属家庭',
    `creator_id` INT UNSIGNED NOT NULL COMMENT '创建者',
    `name` VARCHAR(100) NOT NULL COMMENT '套装名称',
    `season` VARCHAR(30) DEFAULT '' COMMENT '适用季节',
    `occasion` VARCHAR(50) DEFAULT '' COMMENT '场合: 通勤/运动/约会/居家/正装/休闲',
    `cover_image` VARCHAR(500) DEFAULT '' COMMENT '套装封面图',
    `note` TEXT COMMENT '备注',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=已删除 1=正常',
    `created_at` INT UNSIGNED NOT NULL,
    `updated_at` INT UNSIGNED NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_house` (`house_id`),
    KEY `idx_season` (`season`),
    KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套装表';

-- 3. 套装-物品关联表
CREATE TABLE IF NOT EXISTS `outfit_item` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `outfit_id` INT UNSIGNED NOT NULL COMMENT '套装ID',
    `goods_id` INT UNSIGNED NOT NULL COMMENT '物品ID',
    `slot` VARCHAR(20) DEFAULT '' COMMENT '位置: top/bottom/hat/shoes/outer/accessory',
    `sort_order` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outfit_goods` (`outfit_id`, `goods_id`),
    KEY `idx_outfit` (`outfit_id`),
    KEY `idx_goods` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套装物品关联表';
