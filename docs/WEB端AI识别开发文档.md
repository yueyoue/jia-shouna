# 家收纳 Web 端 AI 识别功能开发文档

> 最后更新：2026-08-02

---

## 一、架构概览

```
APP (Android)
  │
  ├─ 拍照 → POST /backend/api/image-recognize.php?action=recognize
  │           │
  │           ├─ 1. 上传图片 → 保存到 /backend/uploads/images/YYYYMM/
  │           ├─ 2. 加载 AI 配置 → get_ai_config() (type='ai', is_active=1)
  │           ├─ 3. 图片压缩（GD库，max 1600px, quality 75%）
  │           ├─ 4. 直接调用大模型 API（curl POST，图片转 base64）
  │           ├─ 5. 解析 JSON 返回结果（兼容 markdown/正则/key-value）
  │           └─ 6. 返回 JSON 结果给 APP
  │
  └─ 结果解析 → handleAiResult() → safeGetString/safeGetDouble 安全取值
```

**架构说明**：为避免 OPcache 兼容性问题，image-recognize.php 直接调用 AI API，不经过 Agent 类。Agent 类仍保留供其他场景使用。

---

## 二、关键文件清单

| 文件 | 作用 | 部署位置 | 必须 |
|------|------|---------|------|
| `backend/api/image-recognize.php` | 图片识别入口，**直接调用 AI API** | 服务器 | ✅ |
| `backend/config/ai.php` | AI 配置读取 + 动态分类 + 系统提示词 | 服务器 | ✅ |
| `backend/config/database.php` | 数据库连接 + 常量定义 | 服务器 | ✅ |
| `backend/config/helpers.php` | 通用工具函数（认证、响应） | 服务器 | ✅ |
| `backend/library/Agent/Agent.php` | 大模型调用核心类（备用） | 服务器 | 可选 |
| `web-admin/pages/api-config.php` | 后台 AI 配置管理页面 | 服务器 | ✅ |
| `database/schema.sql` | 数据库建表 + 默认数据 | 首次部署 | ✅ |
| `android-app/.../AddItemActivity.java` | APP 端识别逻辑 + 结果解析 | APP 编译 | ✅ |

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
1. requireLogin()                    // JWT 鉴权
2. 验证图片文件（类型、大小 ≤ 2MB）
3. get_ai_config()                   // 从 api_config 表查 type='ai' AND is_active=1
4. if ($aiConfig) {
     保存图片到服务器
     GD 库压缩图片（max 1600px, quality 75%）
     构建 prompt（动态注入用户分类列表）
     curl 直接调用大模型 API
     解析 JSON 返回（markdown清理 → 正则提取 → key-value回退）
     记录日志到 ai_call_log（try-catch 隔离）
   } else {
     降级到传统图像识别（type='image'，百度AI/腾讯云）
   }
5. success([...])                    // 返回识别结果
```

### 3.3 Prompt 设计

```
你是家庭收纳物品识别助手。用户会上传家中物品的照片，你需要识别出这是什么物品。
严格只返回一个JSON对象：
{
  "goods_name": "物品名称（简短准确）",
  "brand": "品牌名或null",
  "spec": "规格或null",
  "category": "分类（从用户分类列表选择）",
  "barcode": "条码或null",
  "expire_date": "YYYY-MM-DD或null",
  "storage_tip": "存放建议（10字以内）",
  "confidence": 0.85
}
无信息字段返回 null，不要猜。
```

**分类列表来源**（优先级从高到低）：
1. APP 通过 `categories` POST 参数传入
2. 从数据库 `goods` 表已有分类自动提取
3. 默认：食品、药品、日用品、电子配件、衣物、厨具、文具、其他

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
| MiniCPM-V-4.6-1B | 面壁智能 | ✅需自备Key | api.modelbest.cn |
| MiniCPM-O-4.5-9B | 面壁智能 | ✅需自备Key | api.modelbest.cn |
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
- **修复**: 改用文件头魔数检测 MIME 类型

### 5.2 免费模型不支持 function calling
- **症状**: API 返回错误或空结果
- **原因**: Agent 类发送了 `tools` 和 `tool_choice` 参数
- **修复**: 识别时**不注册 tools**

### 5.3 图片 URL 无法被 AI 服务器访问
- **症状**: 模型返回空结果或超时
- **原因**: 把图片当 URL 发送，AI 服务器无法访问内网/受限服务器
- **修复**: 自动将图片 URL 转为 base64 data URL

### 5.4 思考模型返回 <think> 标签
- **症状**: 解析失败，content 为 null
- **原因**: Qwen3.5-Thinking 等模型把推理过程放在 <think> 标签里
- **修复**: 解析前自动移除 `<think>...</think>` 标签

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

### 5.8 Agent.php SQL 日志报错导致识别失败（严重）
- **症状**: APP 返回"图像识别API未配置"，error.log 显示 `SQLSTATE[HY093]: Invalid parameter number`
- **原因**: Agent 类的 `createCallLog`/`updateCallLog` 在 OPcache 环境下参数绑定异常，抛出的异常被 catch 后降级到传统图像识别（未配置），返回误导性错误信息
- **修复**: image-recognize.php 改为直接调用 AI API，绕过 Agent 类，所有 SQL 操作用 try-catch 隔离
- **教训**: OPcache 可能导致新部署的 PHP 代码不生效，需重启 PHP-FPM 对应版本

### 5.9 OPcache 导致新代码不生效
- **症状**: 文件已更新（grep 确认），但 PHP 执行的仍是旧代码
- **原因**: PHP OPcache 缓存了旧字节码，`opcache_reset()` 通过 web 请求调用可能无效
- **解决**:
  1. 重启对应版本的 PHP-FPM（注意：nginx 用的版本可能和 CLI 不同，看 error.log 里的 `php-cgi-XX.sock`）
  2. 或在宝塔面板找到对应 PHP 版本点重启
  3. 终极方案：修改代码逻辑，不依赖可能被缓存的旧文件

---

## 六、部署检查清单

部署到新服务器时，按顺序检查：

```
□ 1. 确认 PHP 版本 ≥ 7.4，且安装 GD 扩展（图片压缩用）
□ 2. 确认 PHP 扩展: pdo_mysql, json, gd, curl
□ 3. 确认以下文件完整:
     - backend/config/database.php       ← 数据库配置，每个服务器不同！
     - backend/config/helpers.php
     - backend/config/ai.php
     - backend/config/jwt.php
     - backend/api/image-recognize.php   ← AI 识别主入口
□ 4. 导入 database/schema.sql
□ 5. 配置 database.php 的数据库连接信息
□ 6. 访问 web-admin 完成安装向导
□ 7. 进入"接口配置 → AI 智能识别"，启用一个视觉模型并填入 API Key
□ 8. 宝塔终端测试:
     php -r "require 'backend/config/database.php'; require 'backend/config/ai.php'; var_dump(get_ai_config());"
     → 应返回模型配置数组
□ 9. 重启 PHP-FPM（宝塔面板 → 对应 PHP 版本 → 重启）
□ 10. 用 APP 拍照测试
□ 11. 如果失败，检查:
      tail -10 /www/wwwlogs/你的域名.error.log
```

---

## 七、调试方法

### 7.1 查看 PHP 错误日志（最重要！）
```bash
tail -10 /www/wwwlogs/sn.tthsdd.top.error.log
```
所有 AI 识别错误都会记录到这里，包括 AI API 返回的错误、SQL 错误等。

### 7.2 测试 AI 配置是否正常
```bash
cd /www/wwwroot/sn.tthsdd.top && php -r "
require 'backend/config/database.php';
require 'backend/config/ai.php';
\$c = get_ai_config();
echo \$c ? 'OK: ' . \$c['name'] . ' | model: ' . \$c['model'] : 'FAIL: 无配置';
"
```

### 7.3 测试数据库 AI 模型
```bash
cd /www/wwwroot/sn.tthsdd.top && php -r "
require 'backend/config/database.php';
\$db = getDB();
\$rows = \$db->query(\"SELECT id, name, is_active FROM api_config WHERE type='ai'\")->fetchAll();
foreach (\$rows as \$r) echo \$r['id'] . '|' . \$r['name'] . '|active=' . \$r['is_active'] . PHP_EOL;
"
```

### 7.4 直接测试 AI API 调用（绕过 Agent）
创建临时文件 `backend/api/debug_ai_direct.php` 测试 AI 模型是否正常响应：
```bash
# 测试后记得删除！
curl -s 'https://sn.tthsdd.top/backend/api/debug_ai_direct.php'
```

### 7.5 从 GitHub 更新文件
```bash
# raw.githubusercontent.com（推荐，无 CDN 缓存）
curl -L -o /path/to/file https://raw.githubusercontent.com/yueyoue/jia-shouna/main/path/to/file

# jsdelivr CDN（国内快，但有缓存）
curl -L -o /path/to/file https://cdn.jsdelivr.net/gh/yueyoue/jia-shouna@main/path/to/file
```

**重要**：更新文件后必须在宝塔面板重启 PHP-FPM，否则 OPcache 可能继续使用旧代码。

---

## 八、版本历史

### v2.1 (2026-08-02) — AI 识别重大修复

**image-recognize.php 重构**：
- 绕过 Agent 类，直接在入口文件调用 AI API（解决 OPcache 兼容性问题）
- 内置图片压缩（GD 库，max 1600px, quality 75%）
- 内置 JSON 解析（markdown 清理、正则提取、key-value 回退）
- 所有 SQL 操作用 try-catch 隔离，日志错误不影响识别
- 支持 APP 传入自定义分类列表（`categories` POST 参数）

**ai.php 优化**：
- 动态分类列表：优先 APP 传入 → 数据库已有分类 → 默认值
- Prompt 重构：家庭场景定位、null 规则、JSON 格式示例

**Agent.php 修复**：
- `logToolCall()` 增加 try-catch
- `updateCallLog()` 增加列名白名单校验
- 新增 `compressImage()` 方法（GD 库压缩）
- JSON 解析兼容中文 key（名称/品牌/分类等）
- `normalizeResult()` 过滤 "null"/"无"/"暂无" 等无意义值

**APP 端修复**（需重新编译）：
- `handleAiResult()` / `handleRecognizeResult()` 增加防御性解析
- 新增 `safeGetString()` / `safeGetDouble()` 工具方法
- `confidence` 字段兼容 int/double/string 类型

**其他**：
- schema.sql 移除面壁智能硬编码的公开 API Key

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
Body: multipart/form-data
  - field "image" (图片文件，必填)
  - field "categories" (JSON数组字符串，可选，如 '["食品","药品","日用品"]')
返回: {"code":0, "data":{"recognized":true, "suggested_name":"...", ...}}
```

### Web 管理后台
```
POST /web-admin/index.php?p=login
Body: action=login&username=admin&password=xxx
Session: PHPSESSID cookie
```
