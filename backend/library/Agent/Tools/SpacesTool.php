<?php
/**
 * 收纳空间查询工具 - 获取用户的收纳空间列表
 * 用于 AI 推荐存放位置
 */

function register_spaces_tool($agent) {
    $agent->registerTool(
        'get_storage_spaces',
        '获取用户的收纳空间列表。返回空间名称、层级关系，用于推荐物品存放位置。',
        [
            'type' => 'object',
            'properties' => [
                'house_id' => [
                    'type' => 'integer',
                    'description' => '房屋ID'
                ]
            ],
            'required' => ['house_id']
        ],
        function($args) {
            $houseId = intval($args['house_id'] ?? 0);
            if (!$houseId) {
                return ['spaces' => [], 'message' => '未指定房屋'];
            }

            $db = getDB();
            $stmt = $db->prepare("SELECT id, parent_id, name, level, icon, item_count FROM storage_space WHERE house_id = ? ORDER BY level ASC, sort_order ASC");
            $stmt->execute([$houseId]);
            $spaces = $stmt->fetchAll();

            // 构建树形结构
            $tree = buildSpaceTree($spaces, 0);

            return [
                'spaces' => $tree,
                'total' => count($spaces),
                'message' => '获取成功'
            ];
        }
    );
}

/**
 * 递归构建空间树
 */
function buildSpaceTree($spaces, $parentId) {
    $result = [];
    foreach ($spaces as $space) {
        if (intval($space['parent_id']) === $parentId) {
            $children = buildSpaceTree($spaces, intval($space['id']));
            $node = [
                'id' => $space['id'],
                'name' => $space['name'],
                'icon' => $space['icon'],
                'item_count' => $space['item_count'],
            ];
            if (!empty($children)) {
                $node['children'] = $children;
            }
            $result[] = $node;
        }
    }
    return $result;
}
