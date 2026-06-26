<?php
/**
 * 商品匹配工具 - 根据名称在本地库中搜索相似物品
 */

function register_match_goods_tool($agent) {
    $agent->registerTool(
        'match_goods',
        '根据物品名称在本地商品库中搜索相似物品。返回匹配到的历史物品信息，可用于参考分类、品牌等。',
        [
            'type' => 'object',
            'properties' => [
                'goods_name' => [
                    'type' => 'string',
                    'description' => '物品名称关键词'
                ],
                'house_id' => [
                    'type' => 'integer',
                    'description' => '房屋ID(可选，限定搜索范围)'
                ]
            ],
            'required' => ['goods_name']
        ],
        function($args) {
            $name = trim($args['goods_name'] ?? '');
            $houseId = intval($args['house_id'] ?? 0);
            if (empty($name)) {
                return ['found' => false, 'message' => '名称为空'];
            }

            $db = getDB();
            $where = ["g.status = 1", "g.name LIKE ?"];
            $params = ["%$name%"];
            if ($houseId) {
                $where[] = "g.house_id = ?";
                $params[] = $houseId;
            }

            $whereStr = implode(' AND ', $where);
            $stmt = $db->prepare("SELECT g.name, g.brand, g.category, g.spec, g.barcode, g.expiry_date, s.name as space_name
                FROM goods g
                LEFT JOIN storage_space s ON g.space_id = s.id
                WHERE $whereStr
                ORDER BY g.updated_at DESC LIMIT 5");
            $stmt->execute($params);
            $items = $stmt->fetchAll();

            if (!empty($items)) {
                return [
                    'found' => true,
                    'count' => count($items),
                    'items' => $items,
                    'message' => "找到 " . count($items) . " 个相似物品"
                ];
            }

            return [
                'found' => false,
                'message' => '本地库中未找到相似物品'
            ];
        }
    );
}
