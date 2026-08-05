<?php
/**
 * 物品流转日志 + 借出功能 迁移脚本
 * 访问: https://域名/backend/migrate_goods_log.php
 */
require_once __DIR__ . '/config/database.php';
header('Content-Type: application/json; charset=utf-8');

try {
    $db = getDB();
    $results = [];

    // 1. 创建 goods_log 表
    $db->exec("CREATE TABLE IF NOT EXISTS `goods_log` (
        `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
        `goods_id` INT UNSIGNED NOT NULL COMMENT '物品ID',
        `user_id` INT UNSIGNED NOT NULL COMMENT '操作人ID',
        `action` VARCHAR(30) NOT NULL COMMENT '操作类型: create/edit/borrow/lend/return/import',
        `detail` VARCHAR(500) DEFAULT '' COMMENT '操作详情',
        `extra` TEXT DEFAULT NULL COMMENT '扩展JSON',
        `created_at` INT UNSIGNED NOT NULL COMMENT '操作时间',
        PRIMARY KEY (`id`),
        KEY `idx_goods` (`goods_id`),
        KEY `idx_user` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品流转日志表'");
    $results[] = 'goods_log 表已就绪';

    // 2. goods_borrow 增加 lend_to 字段
    $cols = [];
    $stmt = $db->query("SHOW COLUMNS FROM goods_borrow");
    while ($row = $stmt->fetch()) { $cols[] = $row['Field']; }

    if (!in_array('lend_to', $cols)) {
        $db->exec("ALTER TABLE goods_borrow ADD COLUMN lend_to VARCHAR(100) DEFAULT '' COMMENT '借出对象' AFTER note");
        $results[] = '已添加 lend_to 字段';
    } else {
        $results[] = 'lend_to 字段已存在';
    }

    if (!in_array('remind_at', $cols)) {
        $db->exec("ALTER TABLE goods_borrow ADD COLUMN remind_at INT UNSIGNED DEFAULT NULL COMMENT '归还提醒时间戳' AFTER lend_to");
        $results[] = '已添加 remind_at 字段';
    } else {
        $results[] = 'remind_at 字段已存在';
    }

    // 3. 为已有数据补录 goods_log
    $exists = $db->query("SELECT COUNT(*) as cnt FROM goods_log")->fetch()['cnt'];
    if ($exists == 0) {
        // 补录创建记录
        $db->exec("INSERT INTO goods_log (goods_id, user_id, action, detail, created_at)
            SELECT id, creator_id, 'create', '物品录入', created_at FROM goods WHERE status = 1");
        $created = $db->query("SELECT ROW_COUNT()")->fetchColumn();
        $results[] = "补录创建日志 {$created} 条";

        // 补录领用/借出记录
        $db->exec("INSERT INTO goods_log (goods_id, user_id, action, detail, created_at)
            SELECT goods_id, user_id, 'borrow', CONCAT('领取 ', quantity, ' 件'), borrow_time FROM goods_borrow");
        $borrowed = $db->query("SELECT ROW_COUNT()")->fetchColumn();
        $results[] = "补录领用日志 {$borrowed} 条";

        // 补录归还记录
        $db->exec("INSERT INTO goods_log (goods_id, user_id, action, detail, created_at)
            SELECT goods_id, user_id, 'return', CONCAT('归还 ', quantity, ' 件'), return_time FROM goods_borrow WHERE status = 2 AND return_time IS NOT NULL");
        $returned = $db->query("SELECT ROW_COUNT()")->fetchColumn();
        $results[] = "补录归还日志 {$returned} 条";
    } else {
        $results[] = '流转日志已有数据，跳过补录';
    }

    echo json_encode(['code' => 0, 'msg' => '迁移完成', 'data' => $results], JSON_UNESCAPED_UNICODE);
} catch (Exception $e) {
    echo json_encode(['code' => 500, 'msg' => '迁移失败: ' . $e->getMessage()], JSON_UNESCAPED_UNICODE);
}
