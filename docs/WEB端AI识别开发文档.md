# 家收纳 Web 端 AI 识别功能开发文档

> 最后更新：2026-08-01

---

## 一、架构概览

```
APP (Android)
  │
  ├─ 拍照 → POST /backend/api/image-recognize.php?action=recognize
  │           │
  │           ├─ 1. 上传图片 → 保存到 /backend/uploads/images/YYYYMM/
  │           ├─ 2. 加载 AI 配置 → get_ai_config() (type='ai', is_active=1)
  │           ├─ 3. Agent 类调用大模型 API（图片转 base64 发送）
  │           └─ 4. 返回 JSON 结果给 APP
  │
  └─ 结果解析 → handleAiResult() 填入表单
```

---

## 二、关键文件清单

| 文件 | 作用 | 部署位置 |
|------|------|---------|
| `backend/api/image-recognize.php` | 图片识别入口，调度 Agent | 服务器 |
| `backend/config/ai.php` | AI 配置读取（`get_ai_config()`） | 服务器 |
| `backend/library/Agent/Agent.php` | 大模型调用核心类 | 服务器 |
| `backend/config/database.php` | 数据库连接 + 常量定义 | 服务器 |
| `backend/config/helpers.php` | 通用工具函数（认证、响应） | 服务器 |
| `web-admin/pages/api-config.php` | 后台 AI 配置管理页面 | 服务器 |
| `database/schema.sql` | 数据库建表 + 默认数据 | 首次部署 |
| `android-app/.../AddItemActivity.java` | APP 端识别逻辑 + 结果解析 | APP 编译 |

---

## 三、后端调用流程（完整）

### 3.1 APP 发起请求

```java
// AddItemActivity.java → callAiRecognize()
POST {BASE_URL}image-recognize.php?action=recognize
Header: Authorization: Bearer {token}
Body: multipart/form-data, field name="image"
```

### 3.2 image-recognize.php 处理流程

```php
1. requireLogin()          // JWT 鉴权
2. 验证图片文件（类型、大小）
3. get_ai_config()         // 从 api_config 表查 type='ai' AND is_active=1
4. if ($aiConfig) {
     保存图片到服务器
     new Agent(userId)      // 不注册 tools（免费模型不支持 function calling）
     $agent->recognize($imageUrl)
   } else {
     降级到传统图像识别（type='image'，百度AI/腾讯云）
   }
5. success([...])           // 返回识别结果
```

### 3.3 Agent.php recognize() 流程

```
1. 图片 URL → 读取本地文件 → base64 编码
2. 检测 MIME 类型（不依赖 finfo 扩展，用文件头魔数）
3. 构建请求 payload:
   {
     "model": "xxx",
     "messages": [
       {"role": "system", "content": "你是物品识别助手..."},
       {"role": "user", "content": [
         {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}},
         {"type": "text", "text": "请识别图片中的物品信息，优先解析条码。"}
       ]}
     ],
     "temperature": 0.2,
     "max_tokens": 500
   }
4. 调用大模型 API（curl POST）
5. 解析返回的 JSON（5种策略：直接解析、markdown提取、正则匹配、首尾{}、key-value重建）
6. 移除 <think>...</think> 标签（思考模型专用）
7. 返回标准化结果
```

### 3.4 返回给 APP 的 JSON

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "recognized": true,
    "suggested_name": "蓝芩口服液",
    "suggested_category": "药品",
    "suggested_brand": "龙凤堂",
    "suggested_tags": [],
    "barcode": "8441682 034805288453",
    "confidence": 0.9,
    "image_path": "images/202608/xxx.jpg",
    "image_url": "https://sn.tthsdd.top/backend/uploads/images/202608/xxx.jpg"
  }
}
```

---

## 四、Web 管理后台

### 4.1 后台地址
- URL: http://sn.tthsdd.top/web-admin
- 默认账号: admin / admin123

### 4.2 AI 配置页面路径
- 菜单: 接口配置 → 🤖 AI 智能识别

### 4.3 支持的模型（仅限支持识图的视觉模型）

| 模型 | 平台 | 免费 | API地址 |
|------|------|------|---------|
| GLM-4V-Flash | 智谱AI | ✅永久免费 | open.bigmodel.cn |
| GLM-4.1V-Thinking-Flash | 智谱AI | ✅ | open.bigmodel.cn |
| GLM-4.6V-Flash | 智谱AI | ✅ | open.bigmodel.cn |
| MiniCPM-V-4.6-1B | 面壁智能 | ✅公开Key | api.modelbest.cn |
| MiniCPM-O-4.5-9B | 面壁智能 | ✅公开Key | api.modelbest.cn |
| Qwen3-VL-235B | 魔搭社区 | ✅每天2000次 | api-inference.modelscope.cn |
| Qwen3-VL-8B | 魔搭社区 | ✅ | api-inference.modelscope.cn |
| InternVL3.5-241B | 魔搭社区 | ✅ | api-inference.modelscope.cn |
| GPT-4o | AIHubMix | ✅每天500次 | aihubmix.com |
| Gemini-3-Flash | AIHubMix | ✅ | aihubmix.com |
| Gemma-4-31B | OpenRouter | ✅ | openrouter.ai |

### 4.4 判断模型是否支持识图

**模型名带 VL / V / Vision 的才是视觉模型：**
- `Qwen3-**VL**` ✅
- `GLM-4**V**-Flash` ✅
- `Intern**VL**` ✅
- `MiniCPM-**V**` ✅
- `GPT-4o` ✅
- `Qwen3.5-35B` ❌（纯文本）
- `DeepSeek-V4` ❌（纯文本）

---

## 五、踩坑记录（重要！）

### 5.1 finfo 扩展未安装
- **症状**: `Class "finfo" not found`，HTTP 500
- **原因**: 部分宝塔 PHP 环境未安装 `fileinfo` 扩展
- **修复**: Agent.php 改用文件头魔数检测 MIME 类型（`detectMime()` 方法）

### 5.2 免费模型不支持 function calling
- **症状**: API 返回错误或空结果
- **原因**: Agent 类发送了 `tools` 和 `tool_choice` 参数
- **修复**: image-recognize.php 识别时**不注册 tools**

### 5.3 图片 URL 无法被 AI 服务器访问
- **症状**: 模型返回空结果或超时
- **原因**: Agent 把图片当 URL 发送，AI 服务器无法访问内网/受限服务器
- **修复**: Agent.php 自动将图片 URL 转为 base64 data URL

### 5.4 思考模型返回 <think> 标签
- **症状**: 解析失败，content 为 null
- **原因**: Qwen3.5-Thinking 等模型把推理过程放在 <think> 标签里
- **修复**: Agent.php 解析前自动移除 <think>...</think> 标签

### 5.5 面壁智能公开 Key 失效
- **症状**: `invalid_api_key`
- **原因**: 公开 Key 调用次数过多被封
- **解决**: 注册自己的 Key 或换其他平台

### 5.6 魔搭需要绑定阿里云
- **症状**: `Please bind your Alibaba Cloud account`
- **原因**: 魔搭免费 API 需要绑定阿里云实名认证
- **解决**: 去 modelscope.cn 绑定阿里云账号

### 5.7 ai.php 文件缺失
- **症状**: 返回"图像识别API未配置"，数据库有配置但 get_ai_config() 返回 null
- **原因**: 从 GitHub 下载的压缩包可能不完整
- **解决**: 单独下载 `backend/config/ai.php`

### 5.8 Agent.php 日志 SQL 报错
- **症状**: `SQLSTATE[HY093]: Invalid parameter number`
- **原因**: ai_call_log 表结构或 INSERT 语句参数不匹配
- **修复**: createCallLog / updateCallLog / updateApiStats 加 try-catch，不让日志错误阻塞识别

---

## 六、部署检查清单

部署到新服务器时，按顺序检查：

```
□ 1. 确认 PHP 版本 ≥ 7.4
□ 2. 确认 PHP 扩展: pdo_mysql, json, gd (fileinfo 可选)
□ 3. 确认以下文件完整:
     - backend/config/database.php
     - backend/config/helpers.php
     - backend/config/ai.php          ← 经常漏掉！
     - backend/config/jwt.php
     - backend/library/Agent/Agent.php
     - backend/api/image-recognize.php
□ 4. 导入 database/schema.sql
□ 5. 配置 database.php 的数据库连接信息
□ 6. 访问 web-admin 完成安装向导
□ 7. 进入"接口配置 → AI 智能识别"，启用一个视觉模型并填入 API Key
□ 8. 宝塔终端测试:
     php -r "require 'backend/config/database.php'; require 'backend/config/ai.php'; var_dump(get_ai_config());"
     → 应返回模型配置数组
□ 9. 用 APP 拍照测试
□ 10. 如果失败，检查:
      tail -5 /www/wwwlogs/你的域名.error.log
      tail -10 /www/wwwroot/你的站点/backend/api/debug_ai.log
```

---

## 七、调试方法

### 7.1 查看 PHP 错误日志
```bash
tail -5 /www/wwwlogs/sn.tthsdd.top.error.log
```

### 7.2 查看 AI 调试日志
```bash
tail -10 /www/wwwroot/sn.tthsdd.top/backend/api/debug_ai.log
```

### 7.3 测试 AI 配置是否正常
```bash
cd /www/wwwroot/sn.tthsdd.top && php -r "
require 'backend/config/database.php';
require 'backend/config/ai.php';
\$c = get_ai_config();
echo \$c ? 'OK: ' . \$c['name'] : 'FAIL: 无配置';
"
```

### 7.4 测试数据库 AI 模型
```bash
cd /www/wwwroot/sn.tthsdd.top && php -r "
require 'backend/config/database.php';
\$db = getDB();
\$rows = \$db->query(\"SELECT id, name, is_active FROM api_config WHERE type='ai'\")->fetchAll();
foreach (\$rows as \$r) echo \$r['id'] . '|' . \$r['name'] . '|active=' . \$r['is_active'] . PHP_EOL;
"
```

### 7.5 从 GitHub 更新文件（国内镜像）
```bash
# jsdelivr CDN（推荐）
curl -L -o /path/to/file https://cdn.jsdelivr.net/gh/yueyoue/jia-shouna@main/path/to/file

# gitmirror
curl -L -o /path/to/file https://raw.gitmirror.com/yueyoue/jia-shouna/main/path/to/file

# ghproxy
curl -L -o /path/to/file https://ghp.ci/https://raw.githubusercontent.com/yueyoue/jia-shouna/main/path/to/file
```

---

## 八、当前已知未解决问题

### ~~🔴 严重：APP 端识别结果解析失败（后端已成功，APP 解析崩）~~ ✅ 已修复

**状态**：已修复
**修复内容**：
1. `handleAiResult()` 和 `handleRecognizeResult()` 均增加了防御性解析：
   - `data` 对象增加 null 检查
   - `code`/`msg` 字段增加 JsonNull 检查
   - `recognized` 字段增加 null 安全判断
2. 新增 `safeGetString()` 和 `safeGetDouble()` 工具方法，避免 `getAsString()`/`getAsDouble()` 在 null 值上抛 NPE
3. `confidence` 字段兼容 int/double/string 三种类型
4. `suggested_space_id` 解析增加 try-catch 防护

**根因分析**：
- `data.get("confidence").getAsDouble()` 在后端返回整数 0 时可能抛异常
- `data.get("suggested_name").getAsString()` 在字段值为 null 时抛 NPE（Gson 的 `has()` 对 null 值也返回 true）
- `json.get("code").getAsInt()` 缺少 null 检查

**重新编译 APP 后即可生效**

---

### ~~🟡 中等：Agent.php 的 SQL 日志操作报错~~ ✅ 已修复

**状态**：已修复
**修复内容**：
1. `logToolCall()` 方法增加 try-catch 兜底（之前只有 `createCallLog`/`updateCallLog`/`updateApiStats` 有）
2. 日志写入失败时仅记录 error_log，不阻塞识别主流程

**根因**：`logToolCall()` 是唯一没有 try-catch 的日志方法，当 `ai_tool_call_log` 表写入失败时异常会向上冒泡，导致整个识别请求失败

---

### ~~🟡 中等：面壁智能公开 Key 失效~~ ✅ 已修复

**状态**：已修复
**修复内容**：
1. `database/schema.sql` 中移除了硬编码的面壁智能公开 API Key
2. 新部署时面壁智能配置默认为空 Key，需在管理后台手动填写自己的 Key
3. 已有部署不受影响（数据库中已有配置）

**建议**：注册面壁智能开放平台获取自己的 Key，或使用其他免费平台（智谱/魔搭/AIHubMix）

---

## 九、API 参考

### 认证
```
POST /backend/api/auth.php?action=login
Body: {"username":"admin","password":"xxx"}
返回: {"code":0, "data":{"token":"eyJ..."}}
```

### AI 识别
```
POST /backend/api/image-recognize.php?action=recognize
Header: Authorization: Bearer {token}
Body: multipart/form-data, field "image"
返回: {"code":0, "data":{"recognized":true, "suggested_name":"...", ...}}
```

### Web 管理后台
```
POST /web-admin/index.php?p=login
Body: action=login&username=admin&password=xxx
Session: PHPSESSID cookie
```
