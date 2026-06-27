# 家收纳 - 家庭物品收纳管理APP

## 项目简介

「家收纳」是一款面向家庭用户的物品全生命周期管理工具，解决家庭物品找不到、记不住、易过期、重复买的痛点。

## 技术栈

| 端 | 技术 |
|---|---|
| APP端 | Android 原生 (Java, API 26+) |
| 后端 | PHP 7.4+ / 8.0+ |
| 数据库 | MySQL 5.7+ / 8.0+ |
| 管理后台 | PHP + HTML + CSS + JS |
| 构建 | GitHub Actions 自动编译 |

## 项目结构

```
jia-shouna/
├── backend/              # PHP后端
│   ├── api/              # APP端API接口
│   ├── admin/            # 管理后台后端
│   ├── config/           # 配置文件
│   └── uploads/          # 上传文件
├── web-admin/            # Web管理后台
│   ├── assets/           # CSS/JS
│   └── pages/            # 页面
├── android-app/          # Android APP
│   └── src/main/
│       ├── java/         # Java源码
│       └── res/          # 资源文件
└── database/             # 数据库脚本
```

## 核心功能

### APP端
- 多级收纳空间管理（家→房间→容器 三级层级）
- 物品录入（条码扫描 / 拍照 / AI智能识别 / 手动）
- 物品查找（搜索 / 扫码 / 浏览）
- **品牌管理**：AI识别自动填入品牌字段
- 领用归还管理
- 智能提醒（临期 / 库存不足）
- 家庭共享协作（邀请码、角色权限）
- 物品级隐私控制
- **离线模式**：无网络时可查看已缓存的物品数据
- **日志收集**：APP端错误/崩溃自动上报
- 多房屋支持
- 自动版本更新（GitHub Actions）

### Web管理后台
- 数据总览看板
- 收纳空间管理（树形层级、自适应高度）
- 物品信息管理（含品牌/标签/位置、批量导入导出）
- 接口配置（条码查询 / AI识别）
- AI调用日志
- **APP端日志**（错误/崩溃监控）
- 数据备份与恢复
- 用户与家庭组管理
- APP版本更新管理
- 系统设置

## 部署说明

### 1. 数据库
```bash
mysql -u root -p < database/schema.sql
```

### 2. 后端配置
编辑 `backend/config/database.php`，修改数据库连接信息。

### 3. Web管理后台
将 `web-admin/` 目录部署到Web服务器，访问 `http://your-domain/web-admin/`。

默认管理员账号：admin / admin123

### 4. Android APP
用 Android Studio 打开 `android-app/` 目录，修改 `App.java` 中的 `BASE_URL` 为你的服务器地址。

## API接口

所有APP端API通过 `backend/api/` 目录下的PHP文件提供，使用JWT Token鉴权。

主要接口：
- `POST /auth.php?action=login` - 登录
- `GET /goods.php?action=list` - 物品列表
- `POST /goods.php?action=create` - 创建物品（支持brand、tags、images）
- `GET /goods.php?action=detail&id=X` - 物品详情
- `GET /space.php?action=tree` - 空间树形结构
- `POST /ai/recognize.php?action=recognize` - AI识别
- `POST /log.php?action=upload` - 日志上报
- `GET /version.php?action=check` - 检查更新

## 版本更新功能

### Web管理后台
在「版本更新」页面：
1. 填写版本号（versionCode）和版本名（versionName）
2. 填写更新日志
3. 上传APK文件
4. 选择是否强制更新
5. 点击发布

### APP端
- 启动时自动检查更新
- 有新版本弹窗提示下载
- 强制更新时不可取消
- 「我的」页面显示当前版本号和检查更新按钮

## License

MIT License
