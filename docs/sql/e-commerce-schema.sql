CREATE DATABASE IF NOT EXISTS `e-commerce`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `e-commerce`;

CREATE TABLE IF NOT EXISTS sop_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  title_prompt TEXT NOT NULL,
  main_image_prompt TEXT NOT NULL,
  gmt_create_time DATETIME NOT NULL,
  gmt_modified_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS listing_draft (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  draft_id VARCHAR(100) NOT NULL UNIQUE,
  template_id VARCHAR(100) NOT NULL,
  template_name VARCHAR(200) NOT NULL,
  title_prompt_snapshot TEXT NOT NULL,
  main_image_prompt_snapshot TEXT NOT NULL,
  material_package_id VARCHAR(100) NULL,
  material_package_path VARCHAR(1000) NOT NULL,
  status VARCHAR(50) NOT NULL,
  draft_json JSON NOT NULL,
  publish_request_json JSON NULL,
  last_error_type VARCHAR(100) NULL,
  last_error_message TEXT NULL,
  publish_screenshot_path VARCHAR(1000) NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_listing_draft_material_package_id (material_package_id)
);

-- This statement is idempotent for both fresh and existing installations.
CREATE TABLE IF NOT EXISTS material_package (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_package_id VARCHAR(100) NOT NULL UNIQUE,
  original_directory_name VARCHAR(255) NOT NULL,
  storage_path VARCHAR(1000) NOT NULL,
  parsed_json JSON NOT NULL,
  file_count INT NOT NULL,
  total_size BIGINT NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

-- Existing installations: add the column and index only when they are absent.
-- Fresh installations already receive them in the listing_draft definition above.
SET @schema_name = DATABASE();
SET @add_material_package_id = IF(
  EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'listing_draft'
      AND column_name = 'material_package_id'
  ),
  'SELECT 1',
  'ALTER TABLE listing_draft ADD COLUMN material_package_id VARCHAR(100) NULL'
);
PREPARE add_material_package_id FROM @add_material_package_id;
EXECUTE add_material_package_id;
DEALLOCATE PREPARE add_material_package_id;

SET @add_material_package_id_index = IF(
  EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'listing_draft'
      AND index_name = 'uk_listing_draft_material_package_id'
  ),
  'SELECT 1',
  'ALTER TABLE listing_draft ADD UNIQUE INDEX uk_listing_draft_material_package_id (material_package_id)'
);
PREPARE add_material_package_id_index FROM @add_material_package_id_index;
EXECUTE add_material_package_id_index;
DEALLOCATE PREPARE add_material_package_id_index;
