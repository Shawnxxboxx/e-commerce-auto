CREATE DATABASE IF NOT EXISTS `e-commerce`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `e-commerce`;

CREATE TABLE IF NOT EXISTS sop_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_id VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  title_prompt TEXT NOT NULL,
  main_image_prompt TEXT NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS listing_draft (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  draft_id VARCHAR(100) NOT NULL UNIQUE,
  template_id VARCHAR(100) NOT NULL,
  template_name VARCHAR(200) NOT NULL,
  title_prompt_snapshot TEXT NOT NULL,
  main_image_prompt_snapshot TEXT NOT NULL,
  material_package_path VARCHAR(1000) NOT NULL,
  status VARCHAR(50) NOT NULL,
  draft_json JSON NOT NULL,
  publish_request_json JSON NULL,
  last_error_type VARCHAR(100) NULL,
  last_error_message TEXT NULL,
  publish_screenshot_path VARCHAR(1000) NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL
);
