-- 物品流转日志表
CREATE TABLE IF NOT EXISTS `goods_log` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `goods_id` INT UNSIGNED NOT NULL COMMENT '物品ID',
    `user_id` INT UNSIGNED NOT NULL COMMENT '操作人ID',
    `action` VARCHAR(30) NOT NULL COMMENT '操作类型: create/edit/borrow/lend/return/import',
    `detail` VARCHAR(500) DEFAULT '' COMMENT '操作详情',
    `extra` TEXT DEFAULT NULL COMMENT '扩展JSON(借出对象、数量等)',
    `created_at` INT UNSIGNED NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_goods` (`goods_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品流转日志表';

-- goods_borrow 表增加 lend_to 和 remind_at 字段
ALTER TABLE `goods_borrow` ADD COLUMN `lend_to` VARCHAR(100) DEFAULT '' COMMENT '借出对象(姓名)' AFTER `note`;
ALTER TABLE `goods_borrow` ADD COLUMN `remind_at` INT UNSIGNED DEFAULT NULL COMMENT '归还提醒时间戳' AFTER `lend_to`;
