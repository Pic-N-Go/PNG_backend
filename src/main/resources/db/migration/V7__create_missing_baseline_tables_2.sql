-- V1이 baseline으로 건너뛰어진 DB(기존 팀원 로컬·운영)에는 V1에서만 정의된
-- 두 테이블이 없다. V3에서 한 차례 메웠으나 아래 둘이 빠져 있어 ddl-auto: validate가
-- "missing table"로 기동을 거부한다. 빈 DB에는 V1이 이미 만들었으므로 IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS `community_post_comment_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `comment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_post_comment_like` (`comment_id`,`user_id`),
  CONSTRAINT `FKlro37sfyqg78ml2ssx5o4gmt4` FOREIGN KEY (`comment_id`) REFERENCES `community_post_comments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_equipment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `equipment_name` varchar(100) NOT NULL,
  `equipment_type` enum('CAMERA','LENS') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_equipment_type_name` (`user_id`,`equipment_type`,`equipment_name`),
  KEY `idx_user_equipment_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
