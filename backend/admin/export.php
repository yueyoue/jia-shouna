<?php
/**
 * 数据导出
 */
session_start();
require_once __DIR__ . '/../config/database.php';

if (empty($_SESSION['admin_id'])) die('未授权');

$type = $_GET['type'] ?? 'items';
$db = getDB();

if ($type === 'items') {
    $stmt = $db->query("SELECT g.id, g.name, g.barcode, g.category, g.brand, g.spec, g.quantity, g.unit, g.purchase_date, g.expiry_date, g.purchase_price, g.note, g.is_private, s.name as space_name 
        FROM goods g LEFT JOIN storage_space s ON g.space_id = s.id 
        WHERE g.status = 1 ORDER BY g.id ASC");
    $items = $stmt->fetchAll();

    header('Content-Type: text/csv; charset=utf-8');
    header('Content-Disposition: attachment; filename=goods_export_' . date('YmdHis') . '.csv');
    
    echo "\xEF\xBB\xBF"; // UTF-8 BOM
    echo "ID,物品名称,条形码,分类,品牌,规格,数量,单位,购买日期,保质期,购买价格,备注,隐私,存放位置\n";
    foreach ($items as $item) {
        echo implode(',', array_map(function($v) {
            return '"' . str_replace('"', '""', $v) . '"';
        }, [
            $item['id'], $item['name'], $item['barcode'], $item['category'], $item['brand'], $item['spec'],
            $item['quantity'], $item['unit'], $item['purchase_date'], $item['expiry_date'],
            $item['purchase_price'], $item['note'], $item['is_private'] ? '隐藏' : '共享', $item['space_name']
        ])) . "\n";
    }
    exit;
}
