# 家收纳 APP AI Agent 智能录入功能开发文档 v2

> 本文档记录已实现的功能方案，与代码保持一致。

## 1. 功能概述

### 1.1 功能目标

在现有「家收纳」PHP+MySQL+Android 原生项目基础上，**最小侵入式新增 AI 智能录入能力**。用户仅需拍摄一张物品照片，AI Agent 自动完成条码解析、包装文字识别、商品信息提取、收纳分类推荐、保质期计算，最终一键完成物品入库，无需手动填写表单。

### 1.2 核心能力（已实现）

- 单图自动识别：多模态大模型同时支持条形码解析 + 包装文字 OCR + 物品视觉识别
- 结构化信息提取：自动输出商品名、品牌、规格、保质期、建议分类、建议存储位置
- 工具自主调度：AI 自主决定调用条码查询、商品匹配、空间推荐等工具
- 无缝对接现有系统：识别结果直接填充物品录入表单，用户确认后一键入库

### 1.3 适用范围

- 端侧：Android APP 物品录入页（新增 AI Tab）
- 后端：兼容现有 PHP 7.4+ / MySQL 5.7+ 技术栈
- 网络：在线使用，依赖云端大模型 API，国内网络可直连

## 2. 技术选型

### 2.1 AI 大模型选型（国内直连，无需翻墙）

| 选型方案 | 核心能力 | 成本说明 | 状态 |
|---------|---------|---------|------|
| 智谱 GLM-4V-Flash | 多模态识图 + 函数调用 (Function Calling) 原生支持 | 永久免费额度充足 | ✅ 已配置（首选） |
| 火山引擎 豆包 Doubao-Vision | 商品包装识别精度高，兼容 OpenAI 接口格式 | 新用户赠送大额免费 token | ✅ 已配置（备用） |
| 百度文心一言 ERNIE-Speed | 专项优化日用品/药品包装文字识别 | 新用户免费额度 | ✅ 已配置（备用） |

> 统一采用 **大模型函数调用（Function Calling）** 实现轻量 AI Agent，无需引入重型 Agent 框架。

### 2.2 依赖组件

- 后端：PHP cURL 扩展（默认已开启）、JSON 扩展
- 条码解析：由多模态大模型直接识别 + 第三方条码 API 查询
- 鉴权：复用现有 JWT Token 鉴权体系，无新增依赖

## 3. 整体架构设计

### 3.1 现有架构复用

完全保留原有项目结构与业务逻辑，仅新增 AI 模块，不修改原有物品、空间、用户等核心表与接口。

```
原有链路：Android APP → backend/api/index.php → 业务逻辑 → MySQL
新增链路：Android APP → AI录入接口 → Agent调度层 → 工具层 → 大模型API → 复用原有入库逻辑 → MySQL
```

### 3.2 新增模块说明

1. **AI 配置模块**：复用 `api_config` 表（type='ai'），管理后台可配置 API 密钥、模型参数
2. **Agent 调度核心**：`backend/library/Agent/Agent.php`，负责和大模型交互、注册工具、调度执行、汇总结果
3. **工具集**：封装条码查询、商品匹配、空间推荐 3 个可被 AI 调用的工具
4. **日志模块**：`ai_call_log` + `ai_tool_call_log` 表，记录每次 AI 调用、工具执行、token 消耗

### 3.3 核心业务流程

1. 用户在 APP 物品录入页点击「AI」Tab，拍摄物品照片上传至后端
2. 后端接收图片，调用 AI Agent，下发指令：识别物品信息，优先解析条码，无条码则识别包装文字
3. AI Agent 自主调度工具：
   - 第一步：多模态大模型从图片中识别条码和包装文字
   - 第二步：有条码则调用 `lookup_barcode` 工具匹配本地商品库
   - 第三步：调用 `get_storage_spaces` 工具获取用户空间列表，推荐存放位置
   - 第四步：汇总结果，返回结构化 JSON
4. 后端返回结构化识别结果给 APP，表单自动填充
5. 用户确认/修改信息后，点击入库，复用原有物品创建接口完成数据写入

## 4. 数据库设计

### 4.1 变更说明

- 不修改任何原有业务表（goods、space、user 等）
- 复用 `api_config` 表，新增 type='ai' 类型记录
- 新增 2 张表：AI 调用日志表、工具调用记录表

### 4.2 新增表定义

#### 表 1：`ai_call_log` AI 调用总日志表

```sql
CREATE TABLE `ai_call_log` (
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
```

#### 表 2：`ai_tool_call_log` Agent 工具调用记录表

```sql
CREATE TABLE `ai_tool_call_log` (
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
```

#### 复用表：`api_config` 新增 AI 服务商配置

```sql
INSERT INTO `api_config` (`type`, `name`, `api_url`, `api_key`, `api_secret`, `extra_params`, `is_active`, `priority`) VALUES
('ai', '智谱 GLM-4V-Flash', 'https://open.bigmodel.cn/api/paas/v4/chat/completions', '', '', '{"model":"glm-4v-flash","provider":"zhipu"}', 1, 100),
('ai', '火山引擎 豆包 Vision', 'https://ark.cn-beijing.volces.com/api/v3/chat/completions', '', '', '{"model":"doubao-vision-pro-32k","provider":"doubao"}', 0, 80),
('ai', '百度文心 ERNIE-Speed', 'https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/ernie-speed-128k', '', '', '{"model":"ernie-speed-128k","provider":"ernie"}', 0, 60);
```

## 5. 后端开发（PHP）

### 5.1 目录结构

```
backend/
├── config/
│   └── ai.php                  # AI配置读取
├── library/
│   └── Agent/
│       ├── Agent.php           # Agent核心调度类
│       └── Tools/
│           ├── BarcodeTool.php # 条码查询工具
│           ├── MatchGoodsTool.php # 商品匹配工具
│           └── SpacesTool.php # 空间推荐工具
├── api/
│   └── ai/
│       ├── recognize.php       # AI识别预览接口
│       └── confirm.php         # AI确认入库接口
└── uploads/
    └── images/                 # AI识别图片存储（复用原有路径）
```

### 5.2 配置文件

`backend/config/ai.php`：从 `api_config` 表（type='ai'）读取当前启用的 AI 服务商配置，提供全局配置获取方法和系统提示词。

### 5.3 工具层

每个工具统一格式：包含工具描述、入参定义、执行方法，供 Agent 注册调用。

| 工具名 | 功能 | 输入 | 输出 |
|-------|------|------|------|
| `lookup_barcode` | 根据条码查询本地商品库 + 第三方API | barcode | 商品信息/未找到 |
| `match_goods` | 根据名称搜索本地相似物品 | goods_name, house_id | 匹配列表 |
| `get_storage_spaces` | 获取用户收纳空间树 | house_id | 空间树形结构 |

### 5.4 Agent 核心逻辑

`Agent.php` 核心能力：

1. 注册所有可用工具，生成符合大模型要求的 tools 参数
2. 调用大模型 API（支持智谱/豆包/文心，统一接口）
3. 处理工具调用循环（最多 3 轮，防止死循环）
4. 解析大模型返回的 JSON 结果，标准化输出格式
5. 记录调用日志与工具执行日志

### 5.5 API 接口

#### 接口 1：AI 物品识别（预览，不入库）

- **接口地址**：`POST /backend/api/ai/recognize.php?action=recognize`
- **请求方式**：multipart/form-data
- **请求参数**：

| 参数名 | 类型 | 必填 | 说明 |
|-------|------|------|------|
| image | file | 是 | 物品照片文件（≤5MB） |
| space_id | int | 否 | 当前选中的收纳空间 ID |
| house_id | int | 否 | 房屋 ID（不传自动获取） |

- **返回参数**：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "barcode": "6901234567890",
    "goods_name": "布洛芬缓释胶囊",
    "brand": "芬必得",
    "spec": "0.4g*24粒",
    "category": "药品",
    "expire_date": "2027-05-20",
    "storage_tip": "建议存放于客厅药箱",
    "confidence": 0.92,
    "image_path": "images/202606/xxx.jpg",
    "image_url": "https://sn.tthsdd.top/backend/uploads/images/202606/xxx.jpg",
    "house_id": 1,
    "suggested_space_id": 3,
    "suggested_space_name": "客厅"
  }
}
```

#### 接口 2：确认 AI 识别结果并入库

- **接口地址**：`POST /backend/api/ai/confirm.php?action=confirm`
- **请求参数**：识别结果字段 + space_id + 数量 + 备注（完全兼容原有物品创建字段）
- **逻辑**：复用原有 `goods/create` 业务逻辑，写入数据库，返回物品 ID

## 6. Android APP 端改造

### 6.1 入口新增

在「物品录入」页面，原有「扫码」「拍照」「手动」三个 Tab 旁，新增第 4 个 Tab：**AI**

- Tab 文字：`AI`
- 位置：页面顶部 Tab 栏，与其他录入方式并列

### 6.2 交互流程

1. 点击「AI」Tab → 调起系统相机拍照
2. 图片压缩后上传（JPEG 85%），显示 Toast「AI 正在识别...」
3. 识别成功：弹窗显示识别结果（名称/品牌/条码/保质期/置信度），表单自动填充
4. 识别失败：Toast 提示错误信息
5. 用户核对信息后，点击「确认入库」完成操作，逻辑与现有入库完全一致

### 6.3 接口对接

- 复用现有 OkHttp 网络请求框架与 Token 鉴权
- 图片压缩：JPEG 85% 质量
- 接口地址：`POST ai/recognize.php?action=recognize`（multipart/form-data）

### 6.4 版本信息

- versionCode: 7
- versionName: 1.4.0

## 7. Web 管理后台改造

### 7.1 AI 系统配置

- **路径**：系统设置 → 接口配置 → AI 智能识别
- **功能**：修改 AI 服务商、API 密钥，切换启用的服务商
- **支持**：连接测试（点击⚡按钮验证 API Key 是否有效）
- **实现**：复用现有 `api-config.php` 页面，新增 AI 类型展示

### 7.2 AI 调用日志

- **路径**：系统设置 → AI 调用日志
- **展示**：调用用户、时间、类型、AI 服务商/模型、token 消耗、耗时、状态
- **筛选**：按日期、状态、用户筛选
- **详情**：点击展开查看工具调用记录（工具名、参数、结果、耗时）
- **统计**：总调用次数、今日调用、成功率、累计 Token 消耗

## 8. 部署说明

### 8.1 数据库初始化

在现有数据库中执行 `database/ai_schema.sql`：

```bash
mysql -u root -p jia_shouna < database/ai_schema.sql
```

### 8.2 后端部署

新增文件直接部署到 `backend/` 目录，无需修改原有文件的 nginx 配置。

### 8.3 AI 服务商配置

1. 登录管理后台 → 接口配置 → AI 智能识别
2. 点击编辑，填入 AI 服务商的 API Key
3. 点击⚡测试连接，确认有效后保存

### 8.4 APP 更新

下载新版本 APK 安装即可（v1.4.0, versionCode 7）。

## 9. 注意事项

1. **安全约束**：AI 仅可调用注册的工具，禁止直连数据库；所有写入操作必须用户确认
2. **隐私合规**：用户物品照片仅用于识别，上传后存储在 `uploads/images/` 目录
3. **错误降级**：大模型调用失败、网络异常时，Toast 提示错误，用户可手动录入
4. **成本控制**：通过日志监控 token 消耗，智谱 GLM-4V-Flash 免费额度充足
5. **识别精度**：AI 识别结果仅作填充参考，最终数据以用户确认为准
6. **签名一致**：所有版本使用 `release.keystore.p12` 签名，避免更新失败

## 10. 后续扩展方向

1. 新增 AI 自然语言查询：支持「帮我找 3 个月内过期的药品」等语音/文字指令
2. 批量 AI 识别：批量上传照片，一次性识别多个物品入库
3. 智能收纳建议：根据用户现有空间情况，AI 推荐最优存储位置
4. 过期物品自动盘点：AI Agent 自动定期扫描并生成过期物品清单
