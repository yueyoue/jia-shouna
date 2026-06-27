-- ============================================
-- 家收纳 数据库结构
-- Database: MySQL 5.7+ / 8.0+
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------
-- 用户表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码(bcrypt)',
    `nickname` VARCHAR(50) DEFAULT '' COMMENT '昵称',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '手机号',
    `avatar` VARCHAR(255) DEFAULT '' COMMENT '头像路径',
    `role` TINYINT NOT NULL DEFAULT 2 COMMENT '角色: 1=管理员 2=普通用户',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用 1=正常',
    `token` VARCHAR(255) DEFAULT '' COMMENT 'JWT Token',
    `token_expire` INT UNSIGNED DEFAULT 0 COMMENT 'Token过期时间戳',
    `last_login_time` INT UNSIGNED DEFAULT 0 COMMENT '最后登录时间',
    `last_login_ip` VARCHAR(45) DEFAULT '' COMMENT '最后登录IP',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    `updated_at` INT UNSIGNED NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -------------------------------------------
-- 房屋/家庭组表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `house` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL COMMENT '房屋名称(如: 我家、爷爷家)',
    `invite_code` VARCHAR(20) NOT NULL COMMENT '邀请码',
    `creator_id` INT UNSIGNED NOT NULL COMMENT '创建者用户ID',
    `member_count` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '成员数量',
    `space_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '空间数量',
    `item_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '物品数量',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0=已解散 1=正常',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    `updated_at` INT UNSIGNED NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invite_code` (`invite_code`),
    KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房屋/家庭组表';

-- -------------------------------------------
-- 房屋成员关联表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `house_member` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `house_id` INT UNSIGNED NOT NULL COMMENT '房屋ID',
    `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
    `role` TINYINT NOT NULL DEFAULT 2 COMMENT '角色: 1=管理员 2=编辑 3=只读',
    `is_current` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为当前选中房屋',
    `joined_at` INT UNSIGNED NOT NULL COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_house_user` (`house_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房屋成员关联表';

-- -------------------------------------------
-- 收纳空间表(支持3级层级)
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `storage_space` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `house_id` INT UNSIGNED NOT NULL COMMENT '所属房屋ID',
    `parent_id` INT UNSIGNED DEFAULT 0 COMMENT '父级空间ID, 0=一级空间',
    `name` VARCHAR(100) NOT NULL COMMENT '空间名称',
    `level` TINYINT NOT NULL DEFAULT 1 COMMENT '层级: 1=房间 2=容器 3=区域',
    `icon` VARCHAR(50) DEFAULT '🏠' COMMENT '图标',
    `color` VARCHAR(20) DEFAULT '#FF8C42' COMMENT '主题色',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `item_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '物品数量',
    `expiring_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '临期物品数',
    `shared` TINYINT NOT NULL DEFAULT 1 COMMENT '是否共享: 0=不共享 1=全家共享',
    `creator_id` INT UNSIGNED NOT NULL COMMENT '创建者用户ID',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    `updated_at` INT UNSIGNED NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_house` (`house_id`),
    KEY `idx_parent` (`parent_id`),
    KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收纳空间表';

-- -------------------------------------------
-- 空间成员访问控制表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `space_member` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `space_id` INT UNSIGNED NOT NULL COMMENT '空间ID',
    `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
    `can_edit` TINYINT NOT NULL DEFAULT 1 COMMENT '是否可编辑',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_user` (`space_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空间成员访问控制表';

-- -------------------------------------------
-- 物品表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `goods` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `house_id` INT UNSIGNED NOT NULL COMMENT '所属房屋ID',
    `space_id` INT UNSIGNED NOT NULL COMMENT '所在空间ID',
    `creator_id` INT UNSIGNED NOT NULL COMMENT '录入者用户ID',
    `name` VARCHAR(200) NOT NULL COMMENT '物品名称',
    `barcode` VARCHAR(100) DEFAULT '' COMMENT '条形码',
    `category` VARCHAR(50) DEFAULT '' COMMENT '分类',
    `brand` VARCHAR(100) DEFAULT '' COMMENT '品牌',
    `spec` VARCHAR(200) DEFAULT '' COMMENT '规格',
    `quantity` DECIMAL(10,2) NOT NULL DEFAULT 1 COMMENT '数量',
    `unit` VARCHAR(20) DEFAULT '个' COMMENT '单位',
    `purchase_date` DATE DEFAULT NULL COMMENT '购买日期',
    `expiry_date` DATE DEFAULT NULL COMMENT '保质期/有效期',
    `purchase_price` DECIMAL(10,2) DEFAULT NULL COMMENT '购买价格',
    `stock_threshold` DECIMAL(10,2) DEFAULT NULL COMMENT '库存阈值',
    `note` TEXT COMMENT '备注',
    `is_private` TINYINT NOT NULL DEFAULT 0 COMMENT '隐私: 0=共享 1=仅自己可见',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0=已删除 1=正常 2=已领用',
    `is_offline` TINYINT NOT NULL DEFAULT 0 COMMENT '离线创建标记: 0=已同步 1=待同步',
    `offline_id` VARCHAR(50) DEFAULT '' COMMENT '离线客户端生成的临时ID',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    `updated_at` INT UNSIGNED NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_house` (`house_id`),
    KEY `idx_space` (`space_id`),
    KEY `idx_creator` (`creator_id`),
    KEY `idx_barcode` (`barcode`),
    KEY `idx_category` (`category`),
    KEY `idx_expiry` (`expiry_date`),
    KEY `idx_offline` (`is_offline`, `creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品表';

-- -------------------------------------------
-- 物品图片表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `goods_image` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `goods_id` INT UNSIGNED NOT NULL COMMENT '物品ID',
    `image_path` VARCHAR(500) NOT NULL COMMENT '图片路径',
    `thumb_path` VARCHAR(500) DEFAULT '' COMMENT '缩略图路径',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_goods` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品图片表';

-- -------------------------------------------
-- 标签表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `tag` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `house_id` INT UNSIGNED NOT NULL COMMENT '所属房屋ID',
    `name` VARCHAR(50) NOT NULL COMMENT '标签名',
    `color` VARCHAR(20) DEFAULT '#5B9FED' COMMENT '标签颜色',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_house` (`house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- -------------------------------------------
-- 物品-标签关联表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `goods_tag` (
    `goods_id` INT UNSIGNED NOT NULL COMMENT '物品ID',
    `tag_id` INT UNSIGNED NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`goods_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物品标签关联表';

-- -------------------------------------------
-- 领用记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `goods_borrow` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `goods_id` INT UNSIGNED NOT NULL COMMENT '物品ID',
    `user_id` INT UNSIGNED NOT NULL COMMENT '领用人ID',
    `quantity` DECIMAL(10,2) NOT NULL DEFAULT 1 COMMENT '领用数量',
    `borrow_time` INT UNSIGNED NOT NULL COMMENT '领用时间',
    `return_time` INT UNSIGNED DEFAULT NULL COMMENT '归还时间',
    `return_reminder` INT UNSIGNED DEFAULT NULL COMMENT '归还提醒时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=已领用 2=已归还',
    `note` VARCHAR(500) DEFAULT '' COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_goods` (`goods_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领用记录表';

-- -------------------------------------------
-- 提醒表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `reminder` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
    `house_id` INT UNSIGNED NOT NULL COMMENT '房屋ID',
    `goods_id` INT UNSIGNED DEFAULT NULL COMMENT '关联物品ID',
    `space_id` INT UNSIGNED DEFAULT NULL COMMENT '关联空间ID',
    `type` VARCHAR(20) NOT NULL COMMENT '类型: expiry/stock/custom/tidy',
    `title` VARCHAR(200) NOT NULL COMMENT '提醒标题',
    `content` TEXT COMMENT '提醒内容',
    `remind_time` INT UNSIGNED NOT NULL COMMENT '提醒时间',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读',
    `is_handled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已处理',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_house` (`house_id`),
    KEY `idx_remind_time` (`remind_time`),
    KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提醒表';

-- -------------------------------------------
-- 第三方接口配置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `api_config` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `type` VARCHAR(30) NOT NULL COMMENT '接口类型: barcode/image',
    `name` VARCHAR(100) NOT NULL COMMENT '服务商名称',
    `api_url` VARCHAR(500) NOT NULL COMMENT '接口地址',
    `api_key` VARCHAR(255) DEFAULT '' COMMENT 'API Key',
    `api_secret` VARCHAR(255) DEFAULT '' COMMENT 'API Secret',
    `extra_params` TEXT COMMENT '额外参数(JSON)',
    `is_active` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用',
    `priority` INT NOT NULL DEFAULT 0 COMMENT '优先级(越大越优先)',
    `total_calls` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总调用次数',
    `success_calls` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '成功次数',
    `last_call_time` INT UNSIGNED DEFAULT 0 COMMENT '最后调用时间',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    `updated_at` INT UNSIGNED NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_type_active` (`type`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方接口配置表';

-- -------------------------------------------
-- 接口调用日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `api_log` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `api_config_id` INT UNSIGNED DEFAULT NULL COMMENT '接口配置ID',
    `type` VARCHAR(30) NOT NULL COMMENT '接口类型: barcode/image',
    `request_url` VARCHAR(500) DEFAULT '' COMMENT '请求地址',
    `request_params` TEXT COMMENT '请求参数',
    `response_body` TEXT COMMENT '响应内容',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0=失败 1=成功',
    `error_msg` VARCHAR(500) DEFAULT '' COMMENT '错误信息',
    `duration` INT UNSIGNED DEFAULT 0 COMMENT '耗时(ms)',
    `user_id` INT UNSIGNED DEFAULT 0 COMMENT '调用用户ID',
    `created_at` INT UNSIGNED NOT NULL COMMENT '调用时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_created` (`created_at`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口调用日志表';

-- -------------------------------------------
-- APP端日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `app_log` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT UNSIGNED DEFAULT 0 COMMENT '用户ID',
    `device_info` VARCHAR(500) DEFAULT '' COMMENT '设备信息',
    `log_type` VARCHAR(20) DEFAULT 'error' COMMENT '日志类型: crash/error/api/warn/info',
    `tag` VARCHAR(100) DEFAULT '' COMMENT '标签',
    `message` TEXT COMMENT '错误信息',
    `stack_trace` TEXT COMMENT '堆栈跟踪',
    `app_version` VARCHAR(50) DEFAULT '' COMMENT 'APP版本',
    `created_at` INT UNSIGNED DEFAULT 0 COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='APP端日志';

-- -------------------------------------------
-- 操作日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `operate_log` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` INT UNSIGNED NOT NULL COMMENT '操作人ID',
    `username` VARCHAR(50) DEFAULT '' COMMENT '操作人用户名',
    `module` VARCHAR(50) NOT NULL COMMENT '模块: space/goods/house/user/backup/system',
    `action` VARCHAR(50) NOT NULL COMMENT '操作: create/update/delete/import/export/restore',
    `target_id` INT UNSIGNED DEFAULT 0 COMMENT '操作目标ID',
    `content` TEXT COMMENT '操作内容描述',
    `ip` VARCHAR(45) DEFAULT '' COMMENT '操作IP',
    `created_at` INT UNSIGNED NOT NULL COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_module` (`module`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- -------------------------------------------
-- 备份记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `backup_record` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `filename` VARCHAR(255) NOT NULL COMMENT '备份文件名',
    `file_size` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件大小(bytes)',
    `type` VARCHAR(20) NOT NULL COMMENT '类型: database/images/full',
    `method` VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '方式: manual/auto',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0=失败 1=成功',
    `operator_id` INT UNSIGNED DEFAULT 0 COMMENT '操作人ID',
    `created_at` INT UNSIGNED NOT NULL COMMENT '备份时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份记录表';

-- -------------------------------------------
-- 系统设置表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_setting` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `skey` VARCHAR(100) NOT NULL COMMENT '设置键',
    `svalue` TEXT COMMENT '设置值',
    `description` VARCHAR(255) DEFAULT '' COMMENT '描述',
    `updated_at` INT UNSIGNED NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skey` (`skey`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置表';

-- -------------------------------------------
-- APP版本管理表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `app_version` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `version_code` INT UNSIGNED NOT NULL COMMENT '版本号(versionCode)',
    `version_name` VARCHAR(50) NOT NULL COMMENT '版本名(versionName)',
    `changelog` TEXT COMMENT '更新日志',
    `is_force` TINYINT NOT NULL DEFAULT 0 COMMENT '是否强制更新: 0=否 1=是',
    `apk_path` VARCHAR(500) NOT NULL COMMENT 'APK文件路径',
    `apk_size` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'APK文件大小(bytes)',
    `download_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '下载次数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0=已下架 1=已发布',
    `published_at` INT UNSIGNED NOT NULL COMMENT '发布时间',
    `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_version_code` (`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='APP版本管理表';

-- -------------------------------------------
-- 数据同步记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `sync_log` (
    `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
    `house_id` INT UNSIGNED NOT NULL COMMENT '房屋ID',
    `type` VARCHAR(20) NOT NULL COMMENT '同步类型: full/incremental',
    `direction` VARCHAR(10) NOT NULL COMMENT '方向: upload/download',
    `item_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '同步条数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0=失败 1=成功',
    `last_sync_time` INT UNSIGNED NOT NULL COMMENT '同步时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_house` (`user_id`, `house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据同步记录表';

-- ============================================
-- 初始数据
-- ============================================

-- 默认管理员账号 (密码: admin123)
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `role`, `status`, `created_at`, `updated_at`)
VALUES ('admin', '$2y$10$placeholder_will_be_generated', '管理员', 1, 1, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());

-- 默认系统设置
INSERT INTO `sys_setting` (`skey`, `svalue`, `description`, `updated_at`) VALUES
('site_name', '家收纳', '站点名称', UNIX_TIMESTAMP()),
('open_register', '1', '是否开放注册: 0=关闭 1=开放', UNIX_TIMESTAMP()),
('default_remind_days', '7', '默认提前提醒天数', UNIX_TIMESTAMP()),
('rule_days_365', '45', '保质期≥1年：到期前几天提醒', UNIX_TIMESTAMP()),
('rule_days_180', '20', '6个月≤保质期<1年：到期前几天提醒', UNIX_TIMESTAMP()),
('rule_days_90', '15', '90天≤保质期<6个月：到期前几天提醒', UNIX_TIMESTAMP()),
('rule_days_30', '10', '30天≤保质期<90天：到期前几天提醒', UNIX_TIMESTAMP()),
('rule_days_16', '5', '16天≤保质期<30天：到期前几天提醒', UNIX_TIMESTAMP()),
('rule_days_short', '2', '保质期<15天：到期前几天提醒', UNIX_TIMESTAMP()),
('ip_whitelist', '', 'IP白名单(逗号分隔)', UNIX_TIMESTAMP()),
('ip_whitelist_enabled', '0', '是否启用IP白名单', UNIX_TIMESTAMP()),
('auto_backup_enabled', '0', '是否启用自动备份', UNIX_TIMESTAMP()),
('auto_backup_cycle', 'weekly', '自动备份周期: daily/weekly', UNIX_TIMESTAMP()),
('auto_backup_keep', '5', '自动备份保留份数', UNIX_TIMESTAMP());

-- 默认第三方接口配置
INSERT INTO `api_config` (`type`, `name`, `api_url`, `api_key`, `is_active`, `priority`, `created_at`, `updated_at`) VALUES
('barcode', 'ApiZero', 'https://apizero.cn/marketplace/barcode-gs1?barcode={barcode}&api_key=', '', 1, 20, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('barcode', 'ApiZero Pro', 'https://v1.apizero.cn/api/barcode-gs1?code={barcode}&key=', '', 0, 15, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('barcode', 'ApiByte 山海云端', 'https://apione.apibyte.cn/api/barcode?barcode={barcode}', '', 0, 12, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('barcode', 'RollAPI (mxnzp)', 'https://api.mxnzp.com/barcode/goods/details?barcode={barcode}&app_id=&app_secret=', '', 0, 10, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('barcode', 'Open Food Facts', 'https://world.openfoodfacts.org/api/v2/product/{barcode}', '', 0, 8, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('image', '百度AI图像识别', 'https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general', '', 0, 10, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('image', '腾讯云图像识别', 'https://ai.tencent.com/api/image/tag', '', 0, 5, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());

-- -------------------------------------------
-- AI 调用总日志表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` INT UNSIGNED NOT NULL COMMENT '用户ID',
  `type` VARCHAR(20) DEFAULT 'recognize' COMMENT '调用类型: recognize识别/confirm入库',
  `image_url` VARCHAR(500) DEFAULT NULL COMMENT '图片地址',
  `ai_provider` VARCHAR(30) DEFAULT '' COMMENT 'AI服务商',
  `ai_model` VARCHAR(50) DEFAULT '' COMMENT '模型名称',
  `prompt_tokens` INT UNSIGNED DEFAULT 0 COMMENT '输入token数',
  `completion_tokens` INT UNSIGNED DEFAULT 0 COMMENT '输出token数',
  `total_tokens` INT UNSIGNED DEFAULT 0 COMMENT '总消耗',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1成功 0失败',
  `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
  `duration` INT UNSIGNED DEFAULT 0 COMMENT '总耗时(ms)',
  `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志表';

-- -------------------------------------------
-- AI Agent 工具调用记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_tool_call_log` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `call_id` INT UNSIGNED NOT NULL COMMENT '关联 ai_call_log.id',
  `tool_name` VARCHAR(50) NOT NULL COMMENT '工具名称',
  `tool_params` TEXT COMMENT '工具入参(JSON)',
  `tool_result` TEXT COMMENT '工具返回结果(JSON)',
  `execute_time` INT UNSIGNED DEFAULT 0 COMMENT '执行耗时(ms)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1成功 0失败',
  `created_at` INT UNSIGNED NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_call` (`call_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Agent工具调用记录表';

-- -------------------------------------------
-- AI 服务商默认配置
-- -------------------------------------------
INSERT INTO `api_config` (`type`, `name`, `api_url`, `api_key`, `api_secret`, `extra_params`, `is_active`, `priority`, `total_calls`, `success_calls`, `created_at`, `updated_at`) VALUES
('ai', '智谱 GLM-4V-Flash', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', '', '', '{"model":"glm-4v-flash","provider":"zhipu"}', 1, 100, 0, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('ai', '火山引擎 豆包 Vision', 'https://ark.cn-beijing.volces.com/api/v3/chat/completions', '', '', '{"model":"doubao-vision-pro-32k","provider":"doubao"}', 0, 80, 0, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('ai', '百度文心 ERNIE-Speed', 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-speed-128k', '', '', '{"model":"ernie-speed-128k","provider":"ernie"}', 0, 60, 0, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());

SET FOREIGN_KEY_CHECKS = 1;
