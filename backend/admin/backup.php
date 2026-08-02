<?php
/**
 * 备份管理 API
 * 支持：数据库备份、图片打包、JSON/CSV导出、备份恢复、备份删除
 */
session_start();
require_once __DIR__ . '/../config/database.php';
require_once __DIR__ . '/../config/helpers.php';

if (empty($_SESSION['admin_id'])) {
    header('Content-Type: application/json; charset=utf-8');
    die(json_encode(['code' => 401, 'msg' => '未授权'], JSON_UNESCAPED_UNICODE));
}

$db = getDB();
$action = $_REQUEST['action'] ?? '';
$backupDir = UPLOAD_PATH . 'backups/';
if (!is_dir($backupDir)) mkdir($backupDir, 0755, true);

switch ($action) {

    // ==========================================
    //  数据库备份
    // ==========================================
    case 'backup_db':
        try {
            $filename = 'db_' . date('Ymd_His') . '.sql';
            $filepath = $backupDir . $filename;

            $tables = $db->query("SHOW TABLES")->fetchAll(PDO::FETCH_COLUMN);

            $sql = "-- ============================================\n";
            $sql .= "-- 家收纳 数据库备份\n";
            $sql .= "-- 时间: " . date('Y-m-d H:i:s') . "\n";
            $sql .= "-- 数据库: " . DB_NAME . "\n";
            $sql .= "-- 表数量: " . count($tables) . "\n";
            $sql .= "-- ============================================\n\n";
            $sql .= "SET NAMES utf8mb4;\n";
            $sql .= "SET FOREIGN_KEY_CHECKS = 0;\n\n";

            $totalRows = 0;
            foreach ($tables as $table) {
                // 建表语句
                $create = $db->query("SHOW CREATE TABLE `$table`")->fetch();
                $sql .= "-- ----------------------------\n";
                $sql .= "-- Table: $table\n";
                $sql .= "-- ----------------------------\n";
                $sql .= "DROP TABLE IF EXISTS `$table`;\n";
                $sql .= $create['Create Table'] . ";\n\n";

                // 数据
                $rows = $db->query("SELECT * FROM `$table`")->fetchAll();
                if (count($rows) > 0) {
                    $sql .= "-- Data for table: $table (" . count($rows) . " rows)\n";
                    foreach ($rows as $row) {
                        $values = array_map(function($v) use ($db) {
                            return $v === null ? 'NULL' : $db->quote($v);
                        }, array_values($row));
                        $sql .= "INSERT INTO `$table` VALUES (" . implode(',', $values) . ");\n";
                    }
                    $totalRows += count($rows);
                    $sql .= "\n";
                }
            }

            $sql .= "\nSET FOREIGN_KEY_CHECKS = 1;\n";
            $sql .= "-- 备份完成: " . count($tables) . " 张表, $totalRows 条记录\n";

            file_put_contents($filepath, $sql);
            $fileSize = filesize($filepath);

            $db->prepare("INSERT INTO backup_record (filename, file_size, type, method, status, operator_id, created_at) VALUES (?, ?, 'database', 'manual', 1, ?, ?)")
                ->execute([$filename, $fileSize, $_SESSION['admin_id'], time()]);

            jsonResponse(0, '备份成功', [
                'filename' => $filename,
                'file_size' => $fileSize,
                'tables' => count($tables),
                'rows' => $totalRows
            ]);
        } catch (Exception $e) {
            jsonResponse(500, '备份失败: ' . $e->getMessage());
        }
        break;

    // ==========================================
    //  图片打包下载
    // ==========================================
    case 'backup_images':
        if (!class_exists('ZipArchive')) {
            jsonResponse(500, '服务器未安装 PHP Zip 扩展，无法打包图片');
            break;
        }

        try {
            $imagesDir = UPLOAD_PATH . 'images/';
            if (!is_dir($imagesDir)) {
                jsonResponse(400, '图片目录不存在');
                break;
            }

            $filename = 'images_' . date('Ymd_His') . '.zip';
            $filepath = $backupDir . $filename;

            $zip = new ZipArchive();
            if ($zip->open($filepath, ZipArchive::CREATE | ZipArchive::OVERWRITE) !== true) {
                jsonResponse(500, '无法创建ZIP文件');
                break;
            }

            $fileCount = 0;
            $addDir = function($dir) use ($zip, &$fileCount, $addDir) {
                $items = scandir($dir);
                foreach ($items as $item) {
                    if ($item === '.' || $item === '..') continue;
                    $fullPath = $dir . '/' . $item;
                    $relPath = str_replace(UPLOAD_PATH, '', $fullPath);
                    if (is_dir($fullPath)) {
                        $addDir($fullPath);
                    } else {
                        $zip->addFile($fullPath, $relPath);
                        $fileCount++;
                    }
                }
            };
            $addDir($imagesDir);

            // 也把缩略图加进去
            $zip->close();

            $fileSize = filesize($filepath);
            $db->prepare("INSERT INTO backup_record (filename, file_size, type, method, status, operator_id, created_at) VALUES (?, ?, 'images', 'manual', 1, ?, ?)")
                ->execute([$filename, $fileSize, $_SESSION['admin_id'], time()]);

            // 直接输出文件供下载
            header('Content-Type: application/zip');
            header('Content-Disposition: attachment; filename="' . $filename . '"');
            header('Content-Length: ' . $fileSize);
            readfile($filepath);
            exit;

        } catch (Exception $e) {
            jsonResponse(500, '打包失败: ' . $e->getMessage());
        }
        break;

    // ==========================================
    //  JSON 全量导出
    // ==========================================
    case 'export_json':
        try {
            $data = [];

            // 物品
            $stmt = $db->query("SELECT g.*, s.name as space_name, h.name as house_name
                FROM goods g 
                LEFT JOIN storage_space s ON g.space_id = s.id 
                LEFT JOIN house h ON g.house_id = h.id
                WHERE g.status = 1 ORDER BY g.id ASC");
            $data['goods'] = $stmt->fetchAll();

            // 物品图片
            $stmt = $db->query("SELECT * FROM goods_image ORDER BY goods_id, sort_order ASC");
            $data['goods_images'] = $stmt->fetchAll();

            // 空间
            $stmt = $db->query("SELECT s.*, h.name as house_name FROM storage_space s LEFT JOIN house h ON s.house_id = h.id ORDER BY s.house_id, s.level, s.sort_order ASC");
            $data['spaces'] = $stmt->fetchAll();

            // 标签
            $stmt = $db->query("SELECT * FROM tag ORDER BY id ASC");
            $data['tags'] = $stmt->fetchAll();

            // 物品-标签关联
            $stmt = $db->query("SELECT * FROM goods_tag ORDER BY goods_id ASC");
            $data['goods_tags'] = $stmt->fetchAll();

            // 家庭
            $stmt = $db->query("SELECT * FROM house WHERE status = 1 ORDER BY id ASC");
            $data['houses'] = $stmt->fetchAll();

            // 家庭成员
            $stmt = $db->query("SELECT hm.*, u.username, u.nickname FROM house_member hm LEFT JOIN sys_user u ON hm.user_id = u.id ORDER BY hm.house_id ASC");
            $data['house_members'] = $stmt->fetchAll();

            // 用户（不含密码）
            $stmt = $db->query("SELECT id, username, nickname, phone, avatar, role, status, created_at FROM sys_user WHERE status = 1 ORDER BY id ASC");
            $data['users'] = $stmt->fetchAll();

            // 提醒
            $stmt = $db->query("SELECT * FROM reminder ORDER BY id ASC");
            $data['reminders'] = $stmt->fetchAll();

            // 领用记录
            $stmt = $db->query("SELECT * FROM goods_borrow ORDER BY id ASC");
            $data['borrow_records'] = $stmt->fetchAll();

            // 系统设置
            $stmt = $db->query("SELECT skey, svalue FROM sys_setting ORDER BY skey ASC");
            $data['settings'] = $stmt->fetchAll();

            // 额外统计
            $data['_export_info'] = [
                'exported_at' => date('Y-m-d H:i:s'),
                'database' => DB_NAME,
                'goods_count' => count($data['goods']),
                'spaces_count' => count($data['spaces']),
                'users_count' => count($data['users']),
            ];

            $filename = 'full_export_' . date('Ymd_His') . '.json';

            header('Content-Type: application/json; charset=utf-8');
            header('Content-Disposition: attachment; filename="' . $filename . '"');
            echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT);
            exit;

        } catch (Exception $e) {
            jsonResponse(500, '导出失败: ' . $e->getMessage());
        }
        break;

    // ==========================================
    //  CSV 物品导出
    // ==========================================
    case 'export_csv':
        try {
            $stmt = $db->query("SELECT g.id, g.name, g.barcode, g.category, g.brand, g.manufacturer, g.spec, 
                g.quantity, g.unit, g.purchase_date, g.expiry_date, g.purchase_price, g.stock_threshold,
                g.note, g.is_private, s.name as space_name, h.name as house_name, u.nickname as creator_name
                FROM goods g 
                LEFT JOIN storage_space s ON g.space_id = s.id 
                LEFT JOIN house h ON g.house_id = h.id
                LEFT JOIN sys_user u ON g.creator_id = u.id
                WHERE g.status = 1 ORDER BY g.id ASC");
            $items = $stmt->fetchAll();

            $filename = 'items_export_' . date('Ymd_His') . '.csv';

            header('Content-Type: text/csv; charset=utf-8');
            header('Content-Disposition: attachment; filename="' . $filename . '"');

            $output = fopen('php://output', 'w');
            fprintf($output, chr(0xEF) . chr(0xBB) . chr(0xBF)); // UTF-8 BOM
            fputcsv($output, ['ID', '物品名称', '条形码', '分类', '品牌', '厂家', '规格', '数量', '单位', '购买日期', '保质期', '价格', '库存阈值', '备注', '隐私', '存放空间', '所属家庭', '录入者']);
            foreach ($items as $item) {
                fputcsv($output, [
                    $item['id'], $item['name'], $item['barcode'], $item['category'],
                    $item['brand'], $item['manufacturer'] ?? '', $item['spec'],
                    $item['quantity'], $item['unit'], $item['purchase_date'],
                    $item['expiry_date'], $item['purchase_price'], $item['stock_threshold'] ?? '',
                    $item['note'], $item['is_private'] ? '隐藏' : '共享',
                    $item['space_name'], $item['house_name'], $item['creator_name']
                ]);
            }
            fclose($output);
            exit;

        } catch (Exception $e) {
            jsonResponse(500, '导出失败: ' . $e->getMessage());
        }
        break;

    // ==========================================
    //  备份恢复 - 预览
    // ==========================================
    case 'preview':
        $file = $_GET['file'] ?? '';
        if (empty($file)) {
            jsonResponse(400, '缺少文件名');
            break;
        }

        $filepath = $backupDir . basename($file);
        if (!file_exists($filepath)) {
            jsonResponse(404, '备份文件不存在');
            break;
        }

        $ext = strtolower(pathinfo($file, PATHINFO_EXTENSION));
        if ($ext !== 'sql') {
            jsonResponse(400, '仅支持预览 .sql 备份文件');
            break;
        }

        try {
            $content = file_get_contents($filepath, false, null, 0, 8192); // 读前8KB

            // 统计信息
            $fullContent = file_get_contents($filepath);
            $tableCount = preg_match_all('/CREATE TABLE/', $fullContent);
            $insertCount = preg_match_all('/INSERT INTO/', $fullContent);

            // 提取表名
            preg_match_all('/CREATE TABLE.*?`(\w+)`/', $fullContent, $matches);
            $tables = $matches[1] ?? [];

            // 提取头部注释
            $header = '';
            if (preg_match('/^--.*$/m', $fullContent, $headerMatch)) {
                $headerLines = [];
                foreach (explode("\n", $fullContent) as $line) {
                    if (strpos($line, '--') === 0) $headerLines[] = $line;
                    else break;
                }
                $header = implode("\n", array_slice($headerLines, 0, 8));
            }

            jsonResponse(0, 'ok', [
                'filename' => $file,
                'file_size' => filesize($filepath),
                'tables' => $tables,
                'table_count' => $tableCount,
                'insert_count' => $insertCount,
                'header' => $header,
                'preview' => substr($fullContent, 0, 2000)
            ]);
        } catch (Exception $e) {
            jsonResponse(500, '预览失败: ' . $e->getMessage());
        }
        break;

    // ==========================================
    //  备份恢复 - 执行
    // ==========================================
    case 'restore':
        if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
            jsonResponse(405, '仅支持 POST 请求');
            break;
        }

        // 支持两种方式：1.从已有备份恢复 2.上传文件恢复
        $file = $_POST['file'] ?? '';
        $confirmCode = $_POST['confirm_code'] ?? '';

        if ($confirmCode !== 'RESTORE_CONFIRM') {
            jsonResponse(400, '确认码不正确，请输入 RESTORE_CONFIRM');
            break;
        }

        $filepath = '';
        if (!empty($file)) {
            $filepath = $backupDir . basename($file);
        } elseif (!empty($_FILES['backup_file']['tmp_name'])) {
            // 上传文件
            $uploadFile = $_FILES['backup_file'];
            $ext = strtolower(pathinfo($uploadFile['name'], PATHINFO_EXTENSION));
            if (!in_array($ext, ['sql'])) {
                jsonResponse(400, '仅支持 .sql 文件恢复');
                break;
            }
            $safeFilename = 'upload_' . date('Ymd_His') . '.' . $ext;
            $filepath = $backupDir . $safeFilename;
            if (!move_uploaded_file($uploadFile['tmp_name'], $filepath)) {
                jsonResponse(500, '文件上传失败');
                break;
            }
        } else {
            jsonResponse(400, '请选择备份文件或上传文件');
            break;
        }

        if (!file_exists($filepath)) {
            jsonResponse(404, '备份文件不存在');
            break;
        }

        try {
            $sql = file_get_contents($filepath);
            if (empty($sql)) {
                jsonResponse(400, '备份文件为空');
                break;
            }

            // 先备份当前数据库（安全网）
            $safetyFilename = 'pre_restore_' . date('Ymd_His') . '.sql';
            $safetyPath = $backupDir . $safetyFilename;

            $tables = $db->query("SHOW TABLES")->fetchAll(PDO::FETCH_COLUMN);
            $safetySql = "-- 恢复前自动备份 - " . date('Y-m-d H:i:s') . "\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS = 0;\n\n";
            foreach ($tables as $table) {
                $create = $db->query("SHOW CREATE TABLE `$table`")->fetch();
                $safetySql .= "DROP TABLE IF EXISTS `$table`;\n";
                $safetySql .= $create['Create Table'] . ";\n";
                $rows = $db->query("SELECT * FROM `$table`")->fetchAll();
                foreach ($rows as $row) {
                    $values = array_map(function($v) use ($db) {
                        return $v === null ? 'NULL' : $db->quote($v);
                    }, array_values($row));
                    $safetySql .= "INSERT INTO `$table` VALUES (" . implode(',', $values) . ");\n";
                }
                $safetySql .= "\n";
            }
            $safetySql .= "SET FOREIGN_KEY_CHECKS = 1;\n";
            file_put_contents($safetyPath, $safetySql);

            // 执行恢复
            $db->exec("SET FOREIGN_KEY_CHECKS = 0");
            $db->exec($sql);
            $db->exec("SET FOREIGN_KEY_CHECKS = 1");

            $db->prepare("INSERT INTO backup_record (filename, file_size, type, method, status, operator_id, created_at) VALUES (?, ?, 'database', 'restore_safety', 1, ?, ?)")
                ->execute([$safetyFilename, filesize($safetyPath), $_SESSION['admin_id'], time()]);

            jsonResponse(0, '数据恢复成功！恢复前已自动备份当前数据: ' . $safetyFilename, [
                'safety_backup' => $safetyFilename,
                'restored_from' => basename($filepath)
            ]);

        } catch (Exception $e) {
            jsonResponse(500, '恢复失败: ' . $e->getMessage() . '。如果数据异常，可使用恢复前的安全备份回滚。');
        }
        break;

    // ==========================================
    //  删除备份
    // ==========================================
    case 'delete':
        if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
            jsonResponse(405, '仅支持 POST');
            break;
        }

        $input = getJsonInput();
        $id = intval($input['id'] ?? 0);
        $filename = $input['filename'] ?? '';

        if ($id) {
            $stmt = $db->prepare("SELECT * FROM backup_record WHERE id = ?");
            $stmt->execute([$id]);
            $record = $stmt->fetch();
            if ($record) {
                $filepath = $backupDir . $record['filename'];
                if (file_exists($filepath)) unlink($filepath);
                $db->prepare("DELETE FROM backup_record WHERE id = ?")->execute([$id]);
                jsonResponse(0, '删除成功');
            } else {
                // 文件可能不在记录中，尝试直接删除
                jsonResponse(404, '备份记录不存在');
            }
        } elseif ($filename) {
            $filepath = $backupDir . basename($filename);
            if (file_exists($filepath)) {
                unlink($filepath);
                $db->prepare("DELETE FROM backup_record WHERE filename = ?")->execute([basename($filename)]);
                jsonResponse(0, '删除成功');
            } else {
                jsonResponse(404, '文件不存在');
            }
        } else {
            jsonResponse(400, '缺少参数');
        }
        break;

    // ==========================================
    //  下载备份
    // ==========================================
    case 'download':
        $file = $_GET['file'] ?? '';
        if (empty($file)) {
            jsonResponse(400, '缺少文件名');
            break;
        }

        $filepath = $backupDir . basename($file);
        if (!file_exists($filepath)) {
            jsonResponse(404, '文件不存在');
            break;
        }

        $ext = strtolower(pathinfo($file, PATHINFO_EXTENSION));
        $mimeTypes = [
            'sql' => 'application/sql',
            'zip' => 'application/zip',
            'json' => 'application/json',
        ];
        $mime = $mimeTypes[$ext] ?? 'application/octet-stream';

        header('Content-Type: ' . $mime);
        header('Content-Disposition: attachment; filename="' . basename($filepath) . '"');
        header('Content-Length: ' . filesize($filepath));
        readfile($filepath);
        exit;

    // ==========================================
    //  列出备份
    // ==========================================
    case 'list':
        $backups = $db->query("SELECT * FROM backup_record ORDER BY created_at DESC LIMIT 50")->fetchAll();

        // 也扫描目录中可能没记录的文件
        $recordedFiles = array_column($backups, 'filename');
        $extraFiles = [];
        if (is_dir($backupDir)) {
            $scanFiles = scandir($backupDir);
            foreach ($scanFiles as $f) {
                if ($f === '.' || $f === '..' || $f === '.gitkeep') continue;
                if (!in_array($f, $recordedFiles)) {
                    $extraFiles[] = [
                        'id' => 0,
                        'filename' => $f,
                        'file_size' => filesize($backupDir . $f),
                        'type' => pathinfo($f, PATHINFO_EXTENSION) === 'sql' ? 'database' : (pathinfo($f, PATHINFO_EXTENSION) === 'zip' ? 'images' : 'other'),
                        'method' => 'unknown',
                        'created_at' => filemtime($backupDir . $f),
                    ];
                }
            }
        }

        $allBackups = array_merge($backups, $extraFiles);
        // 按时间倒排
        usort($allBackups, function($a, $b) {
            return ($b['created_at'] ?? 0) - ($a['created_at'] ?? 0);
        });

        jsonResponse(0, 'ok', ['list' => $allBackups]);
        break;

    default:
        jsonResponse(400, '未知操作: ' . $action);
}
