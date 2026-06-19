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
- 多级收纳空间管理（3级层级）
- 物品录入（扫码/拍照/手动）
- 物品查找（搜索/扫码/浏览）
- 领用归还管理
- 智能提醒（临期/库存/自定义）
- 家庭共享协作
- 物品级隐私控制
- 离线模式（断网录入，联网同步）
- 多房屋支持
- 自动版本更新

### Web管理后台
- 数据总览看板
- 收纳空间管理
- 物品信息管理（批量操作/Excel导入导出）
- 第三方接口配置（条码查询/图像识别）
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

所有APP端API通过 `backend/api/index.php` 路由，使用JWT Token鉴权。

主要接口：
- `POST /auth/login` - 登录
- `POST /auth/register` - 注册
- `GET /space/list` - 空间列表
- `GET /goods/list` - 物品列表
- `POST /goods/create` - 创建物品
- `GET /version/check` - 检查更新
- `POST /sync/push` - 离线同步
- `GET /sync/pull` - 拉取数据

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
