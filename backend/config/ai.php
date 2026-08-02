<?php
/**
 * AI 配置读取模块
 * 从 api_config 表(type='ai')读取当前启用的 AI 服务商配置
 */

/**
 * 获取当前启用的 AI 配置
 * @return array|null
 */
function get_ai_config() {
    $db = getDB();
    $stmt = $db->prepare("SELECT * FROM api_config WHERE type = 'ai' AND is_active = 1 ORDER BY priority DESC LIMIT 1");
    $stmt->execute();
    $config = $stmt->fetch();
    if (!$config) return null;

    $extra = json_decode($config['extra_params'] ?? '{}', true) ?: [];
    return [
        'id'        => $config['id'],
        'provider'  => $extra['provider'] ?? 'zhipu',
        'model'     => $extra['model'] ?? 'glm-4v-flash',
        'api_url'   => $config['api_url'],
        'api_key'   => $config['api_key'],
        'api_secret'=> $config['api_secret'] ?? '',
        'name'      => $config['name'],
    ];
}

/**
 * 获取分类列表（优先从数据库读取用户自定义分类，否则返回默认值）
 * @param array|null $customCategories APP 传来的自定义分类（可选）
 */
function get_ai_categories($customCategories = null) {
    // 1. 如果 APP 传了自定义分类，优先使用
    if (!empty($customCategories) && is_array($customCategories)) {
        return $customCategories;
    }
    // 2. 尝试从数据库读取用户已有的分类
    try {
        $db = getDB();
        $stmt = $db->query("SELECT DISTINCT category FROM goods WHERE category IS NOT NULL AND category != '' ORDER BY category LIMIT 20");
        $rows = $stmt->fetchAll(PDO::FETCH_COLUMN);
        if (!empty($rows)) {
            $defaults = ['食品', '药品', '日用品', '电子配件', '衣物', '厨具', '文具', '其他'];
            return array_values(array_unique(array_merge($rows, $defaults)));
        }
    } catch (Exception $e) {
        // 忽略，使用默认值
    }
    // 3. 默认分类
    return ['食品', '药品', '日用品', '电子配件', '衣物', '厨具', '文具', '其他'];
}

/**
 * AI 系统提示词
 * @param array|null $customCategories APP 传来的自定义分类（可选）
 */
function get_ai_system_prompt($customCategories = null) {
    $categories = implode('、', get_ai_categories($customCategories));
    return <<<PROMPT
你是家庭收纳物品识别助手。用户会上传家中物品的照片，你需要识别出这是什么物品并提取关键信息。

## 识别规则
1. 看到条形码就提取编号，没有条形码就从包装文字和外观判断
2. 这是家庭场景，不是电商商品识别——关注物品本身是什么，而不是商品详情页信息
3. 品牌只填你确定的，不确定就返回 null，不要猜
4. 规格只填包装上明确标注的（如 500ml、24粒装），没有就返回 null
5. 保质期只填包装上明确标注的日期，格式 YYYY-MM-DD，没有就返回 null
6. 存放建议根据物品类型给出实用建议（如药品→"药箱"，调料→"厨房调料架"）

## 返回格式
严格只返回一个 JSON 对象，不要有任何其他文字、解释或 markdown 标记：
{
  "barcode": "条形码编号或null",
  "goods_name": "物品名称（简短准确，如'蓝芩口服液'而非'龙凤堂蓝芩口服液 10ml*6支'）",
  "brand": "品牌名或null",
  "spec": "规格或null",
  "category": "分类（只能从以下选择: {$categories}）",
  "expire_date": "YYYY-MM-DD格式日期或null",
  "storage_tip": "存放建议（简短，10字以内）",
  "confidence": 0.85
}

注意：无信息的字段必须返回 null（不是空字符串，不是"无"，不是"暂无"）。
PROMPT;
}
