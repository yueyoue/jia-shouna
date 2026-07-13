-- Migration: Add manufacturer column to goods table
-- Date: 2025-07-13
-- Description: Adds manufacturer field for barcode lookup results

ALTER TABLE `goods` ADD COLUMN `manufacturer` VARCHAR(200) DEFAULT '' COMMENT '生产厂商' AFTER `brand`;
