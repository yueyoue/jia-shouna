<?php
/**
 * API 入口路由
 * 所有APP端请求通过此文件分发
 */

require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';

corsHeaders();

// 解析请求路径
$requestUri = $_SERVER['REQUEST_URI'];
$basePath = '/backend/api';
if (strpos($requestUri, $basePath) === 0) {
    $requestUri = substr($requestUri, strlen($basePath));
}
$requestUri = parse_url($requestUri, PHP_URL_PATH);
$requestUri = rtrim($requestUri, '/');
$method = $_SERVER['REQUEST_METHOD'];

// 路由映射
$routes = [
    // 认证
    'POST /auth/login'          => 'auth.php?action=login',
    'POST /auth/register'       => 'auth.php?action=register',
    'POST /auth/refresh'        => 'auth.php?action=refresh',
    
    // 房屋管理
    'GET  /house/list'          => 'house.php?action=list',
    'GET  /house/invite_code'   => 'house.php?action=invite_code',
    'POST /house/create'        => 'house.php?action=create',
    'POST /house/join'          => 'house.php?action=join',
    'POST /house/switch'        => 'house.php?action=switch',
    'GET  /house/members'       => 'house.php?action=members',
    'POST /house/member/role'   => 'house.php?action=updateRole',
    'POST /house/member/remove' => 'house.php?action=removeMember',
    
    // 收纳空间
    'GET  /space/list'          => 'space.php?action=list',
    'GET  /space/tree'          => 'space.php?action=tree',
    'GET  /space/detail'        => 'space.php?action=detail',
    'POST /space/create'        => 'space.php?action=create',
    'POST /space/update'        => 'space.php?action=update',
    'POST /space/delete'        => 'space.php?action=delete',
    
    // 物品管理
    'GET  /goods/list'          => 'goods.php?action=list',
    'GET  /goods/detail'        => 'goods.php?action=detail',
    'POST /goods/create'        => 'goods.php?action=create',
    'POST /goods/update'        => 'goods.php?action=update',
    'POST /goods/delete'        => 'goods.php?action=delete',
    'POST /goods/move'          => 'goods.php?action=move',
    'POST /goods/copy'          => 'goods.php?action=copy',
    'GET  /goods/search'        => 'goods.php?action=search',
    'GET  /goods/expiring'      => 'goods.php?action=expiring',
    
    // 领用归还
    'POST /goods/borrow'        => 'goods.php?action=borrow',
    'POST /goods/return'        => 'goods.php?action=return',
    'GET  /goods/borrow/list'   => 'goods.php?action=borrowList',
    
    // 图片上传
    'POST /upload/image'        => 'upload.php?action=image',
    
    // 标签
    'GET  /tag/list'            => 'tag.php?action=list',
    'POST /tag/create'          => 'tag.php?action=create',
    'POST /tag/delete'          => 'tag.php?action=delete',
    
    // 提醒
    'GET  /reminder/list'       => 'reminder.php?action=list',
    'POST /reminder/create'     => 'reminder.php?action=create',
    'POST /reminder/handle'     => 'reminder.php?action=handle',
    'GET  /reminder/stats'      => 'reminder.php?action=stats',
    
    // 数据同步
    'POST /sync/push'           => 'sync.php?action=push',
    'GET  /sync/pull'           => 'sync.php?action=pull',
    
    // 条码查询
    'GET  /barcode/lookup'      => 'barcode.php?action=lookup',
    
    // 图像识别
    'POST /image-recognize/recognize' => 'image-recognize.php?action=recognize',
    
    // 版本检查
    'GET  /version/check'       => 'version.php?action=check',
    'GET  /version/latest'      => 'version.php?action=latest',

    // 用户信息
    'GET  /user/profile'        => 'user.php?action=profile',
    'POST /user/update'         => 'user.php?action=update',

    // 操作日志
    'GET  /log'                 => 'log.php?action=list',

    // 数据统计
    'GET  /stats'               => 'stats.php',

    // 数据导出
    'GET  /export'              => 'export.php',
];

// 匹配路由
$routeKey = "$method $requestUri";
$matched = false;

foreach ($routes as $pattern => $handler) {
    [$routeMethod, $routePath] = explode(' ', $pattern, 2);
    if ($method === $routeMethod && $requestUri === $routePath) {
        $handlerFile = __DIR__ . '/' . strtok($handler, '?');
        $queryString = strstr($handler, '?') ? substr(strstr($handler, '?'), 1) : '';
        if ($queryString) {
            parse_str($queryString, $_GET_EXTRA);
            $_GET = array_merge($_GET, $_GET_EXTRA);
        }
        if (file_exists($handlerFile)) {
            require $handlerFile;
            $matched = true;
        }
        break;
    }
}

if (!$matched) {
    error('接口不存在: ' . $requestUri, 404);
}
