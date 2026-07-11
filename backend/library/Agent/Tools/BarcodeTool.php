<?php
/**
 * 条码查询工具 - 根据条码编号查询本地商品库和第三方API
 * 支持 RollToolsApi、Open Food Facts、ApiZero 等多个接口
 */

function register_barcode_tool($agent) {
    $agent->registerTool(
        'lookup_barcode',
        '根据条形码编号查询本地商品库。如果找到匹配商品，返回商品名称、品牌等信息。用于在识别到条码后快速获取商品信息。',
        [
            'type' => 'object',
            'properties' => [
                'barcode' => [
                    'type' => 'string',
                    'description' => '条形码编号(如EAN-13格式: 6901234567890)'
                ]
            ],
            'required' => ['barcode']
        ],
        function($args) {
            $barcode = trim($args['barcode'] ?? '');
            if (empty($barcode)) {
                return ['found' => false, 'message' => '条码为空'];
            }

            $db = getDB();

            // 1. 查本地 goods 表
            $stmt = $db->prepare("SELECT name, brand, category, spec FROM goods WHERE barcode = ? AND status = 1 LIMIT 5");
            $stmt->execute([$barcode]);
            $localItems = $stmt->fetchAll();

            if (!empty($localItems)) {
                return [
                    'found' => true,
                    'source' => 'local',
                    'items' => $localItems,
                    'message' => '在本地商品库中找到匹配'
                ];
            }

            // 2. 依次尝试所有启用的第三方接口
            $apiStmt = $db->prepare("SELECT * FROM api_config WHERE type = 'barcode' AND is_active = 1 ORDER BY priority DESC");
            $apiStmt->execute();
            $apis = $apiStmt->fetchAll();

            foreach ($apis as $apiConfig) {
                $result = tryBarcodeApi($db, $barcode, $apiConfig);
                if ($result !== null) {
                    return $result;
                }
            }

            return [
                'found' => false,
                'barcode' => $barcode,
                'message' => '本地和第三方均未找到该条码的商品信息，请从图片中识别商品'
            ];
        }
    );
}

/**
 * 尝试单个接口查询条码
 */
function tryBarcodeApi($db, $barcode, $apiConfig) {
    $apiUrl = $apiConfig['api_url'];
    if (strpos($apiUrl, '{barcode}') !== false) {
        $url = str_replace('{barcode}', urlencode($barcode), $apiUrl);
    } else {
        $url = $apiUrl . urlencode($barcode);
    }

    // 处理需要 app_id/app_secret 的接口
    if (!empty($apiConfig['api_key']) || !empty($apiConfig['api_secret'])) {
        if (strpos($url, 'mxnzp.com') !== false) {
            $url = str_replace('app_id=&app_secret=', 'app_id=' . urlencode($apiConfig['api_key']) . '&app_secret=' . urlencode($apiConfig['api_secret'] ?? ''), $url);
        } elseif (strpos($url, 'apizero.cn') !== false) {
            $url .= urlencode($apiConfig['api_key']);
        }
    }

    try {
        $ch = curl_init($url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_TIMEOUT, 8);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        $resp = curl_exec($ch);
        $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        if ($httpCode !== 200 || !$resp) {
            return null;
        }

        $data = json_decode($resp, true);
        if (!$data) {
            return null;
        }

        // RollToolsApi (mxnzp) 格式
        if (isset($data['code']) && $data['code'] == 0 && isset($data['data'])) {
            $d = $data['data'];
            return [
                'found' => true,
                'source' => 'api',
                'name' => $d['goodsName'] ?? $d['name'] ?? '',
                'brand' => $d['goodsBrand'] ?? $d['brand'] ?? '',
                'category' => $d['goodsCategory'] ?? $d['category'] ?? '',
                'message' => 'RollToolsApi 查询成功'
            ];
        }

        // Open Food Facts 格式
        if (isset($data['product'])) {
            $p = $data['product'];
            $status = $data['status'] ?? 1;
            if ($status == 0) return null;
            return [
                'found' => true,
                'source' => 'api',
                'name' => $p['product_name'] ?? $p['product_name_zh'] ?? '',
                'brand' => $p['brands'] ?? '',
                'category' => $p['categories'] ?? '',
                'message' => 'Open Food Facts 查询成功'
            ];
        }

        // ApiZero 格式
        if (isset($data['data']['goodsName'])) {
            $d = $data['data'];
            return [
                'found' => true,
                'source' => 'api',
                'name' => $d['goodsName'] ?? '',
                'brand' => $d['brand'] ?? '',
                'message' => 'ApiZero 查询成功'
            ];
        }

        // 通用格式
        if (isset($data['goodsName']) || isset($data['name']) || isset($data['product_name'])) {
            return [
                'found' => true,
                'source' => 'api',
                'name' => $data['goodsName'] ?? $data['name'] ?? $data['product_name'] ?? '',
                'brand' => $data['brand'] ?? $data['brands'] ?? '',
                'message' => '条码API查询成功'
            ];
        }

        return null;

    } catch (Exception $e) {
        return null;
    }
}
