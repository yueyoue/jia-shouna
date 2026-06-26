-- ============================================
-- AI Agent 智能录入模块 - 数据库增量脚本
-- 执行前请确保 jia_shouna 数据库已存在
-- ============================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
-- 在 api_config 表中新增 AI 服务商默认配置
-- -------------------------------------------
INSERT IGNORE INTO `api_config` (`type`, `name`, `api_url`, `api_key`, `api_secret`, `extra_params`, `is_active`, `priority`, `total_calls`, `success_calls`, `created_at`, `updated_at`) VALUES
('ai', '智谱 GLM-4V-Flash', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', '', '', '{"model":"glm-4v-flash","provider":"zhipu"}', 1, 100, 0, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('ai', '火山引擎 豆包 Vision', 'https://ark.cn-beijing.volces.com/api/v3/chat/completions', '', '', '{"model":"doubao-vision-pro-32k","provider":"doubao"}', 0, 80, 0, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP()),
('ai', '百度文心 ERNIE-Speed', 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-speed-128k', '', '', '{"model":"ernie-speed-128k","provider":"ernie"}', 0, 60, 0, 0, UNIX_TIMESTAMP(), UNIX_TIMESTAMP());

SET FOREIGN_KEY_CHECKS = 1;
