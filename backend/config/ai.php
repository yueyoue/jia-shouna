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
 * 获取 AI 默认分类列表
 */
function get_ai_categories() {
    return ['食品', '药品', '日用品', '电子配件', '衣物', '厨具', '文具', '其他'];
}

/**
 * AI 系统提示词
 */
function get_ai_system_prompt() {
    $categories = implode('、', get_ai_categories());
    return <<<PROMPT
你是专业的家庭收纳物品识别助手。用户上传物品照片，你需要准确提取物品信息。

规则：
1. 优先尝试识别图片中的条形码编号
2. 如果有条形码，尝试匹配商品信息
3. 如果没有条形码或匹配失败，直接识别图片中的包装文字和物品外观
4. 严格返回 JSON 格式，包含以下字段：
   - barcode: 条形码(无则为空字符串)
   - goods_name: 物品名称
   - brand: 品牌
   - spec: 规格(如 500ml、24粒装)
   - category: 分类(仅限: {$categories})
   - expire_date: 保质期/有效期(格式 YYYY-MM-DD，无法识别则为空字符串)
   - storage_tip: 建议存放位置(如"厨房调料架""卧室衣柜")
   - confidence: 置信度(0-1之间的小数)
5. 只返回 JSON，不要返回其他任何文字
PROMPT;
}
