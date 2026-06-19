# 家收纳 部署说明

## 1. 环境要求
- PHP 7.4+ / 8.0+
- MySQL 5.7+ / 8.0+
- Nginx / Apache

## 2. 部署步骤

### 2.1 导入数据库
```bash
mysql -u root -p -e "CREATE DATABASE jia_shouna DEFAULT CHARSET utf8mb4;"
mysql -u root -p jia_shouna < schema.sql
```

### 2.2 修改数据库配置
编辑 `backend/config/database.php`：
```php
define('DB_HOST', 'localhost');
define('DB_NAME', 'jia_shouna');
define('DB_USER', 'your_username');
define('DB_PASS', 'your_password');
```

### 2.3 上传文件
将 `backend/` 和 `web-admin/` 目录上传到 Web 服务器根目录。

### 2.4 Nginx 配置示例
```nginx
server {
    listen 80;
    server_name j.tthsdd.top;
    root /var/www/html;
    index index.php index.html;

    location /backend/api/ {
        try_files $uri $uri/ /backend/api/index.php?$query_string;
    }

    location ~ \.php$ {
        fastcgi_pass 127.0.0.1:9000;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        include fastcgi_params;
    }
}
```

### 2.5 访问管理后台
浏览器打开：http://j.tthsdd.top/web-admin/
默认账号：admin / admin123

### 2.6 设置目录权限
```bash
chmod -R 755 backend/uploads/
chmod -R 777 backend/uploads/images/
chmod -R 777 backend/uploads/apks/
chmod -R 777 backend/uploads/backups/
```
