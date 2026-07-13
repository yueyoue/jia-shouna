-- Migration: 修正 ApiZero 条码接口 URL
-- 旧URL: https://apizero.cn/marketplace/barcode-gs1?barcode={barcode}&api_key=
-- 新URL: https://v1.apizero.cn/api/barcode-lookup?barcode={barcode}
-- 原因: 旧接口无法返回完整商品信息，新接口可返回名称、品牌、厂商、规格、价格、图片等

UPDATE `api_config` 
SET `api_url` = 'https://v1.apizero.cn/api/barcode-lookup?barcode={barcode}',
    `updated_at` = UNIX_TIMESTAMP()
WHERE `type` = 'barcode' 
  AND `name` = 'ApiZero' 
  AND `api_url` LIKE '%apizero.cn/marketplace/barcode-gs1%';
