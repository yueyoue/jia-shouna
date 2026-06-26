<?php
/**
 * 条码查询工具 - 根据条码编号查询本地商品库
 * 复用现有 barcode.php 的查询逻辑
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

            // 2. 尝试调用第三方条码 API
            $apiStmt = $db->prepare("SELECT * FROM api_config WHERE type = 'barcode' AND is_active = 1 ORDER BY priority DESC LIMIT 1");
            $apiStmt->execute();
            $apiConfig = $apiStmt->fetch();

            if ($apiConfig && !empty($apiConfig['api_key'])) {
                $url = str_replace('{barcode}', urlencode($barcode), $apiConfig['api_url']);
                if (!empty($apiConfig['api_key'])) {
                    // 根据不同 API 拼接 key
                    if (strpos($url, 'apizero.cn') !== false) {
                        $url .= urlencode($apiConfig['api_key']);
                    } elseif (strpos($url, 'mxnzp.com') !== false) {
                        $url .= '&app_id=' . urlencode($apiConfig['api_key']);
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

                    if ($httpCode === 200 && $resp) {
                        $data = json_decode($resp, true);
                        if ($data) {
                            // ApiZero 格式
                            if (isset($data['data']['goodsName'])) {
                                return [
                                    'found' => true,
                                    'source' => 'api',
                                    'name' => $data['data']['goodsName'] ?? '',
                                    'brand' => $data['data']['brand'] ?? '',
                                    'message' => '条码API查询成功'
                                ];
                            }
                            // Open Food Facts 格式
                            if (isset($data['product']['product_name'])) {
                                return [
                                    'found' => true,
                                    'source' => 'api',
                                    'name' => $data['product']['product_name'] ?? '',
                                    'brand' => $data['product']['brands'] ?? '',
                                    'message' => '条码API查询成功'
                                ];
                            }
                        }
                    }
                } catch (Exception $e) {
                    // API 调用失败，不影响主流程
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
