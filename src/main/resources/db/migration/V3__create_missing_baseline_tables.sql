-- V1에는 있지만 기존 로컬 DB에는 없을 수 있는 테이블 생성
--
-- V1(기준 스키마)은 빈 DB에서만 실행된다. 이미 테이블이 있는 DB는 baseline-on-migrate로
-- "V1까지 적용됨"으로 기록하고 건너뛴다. 그런데 admin_audit_logs·inquiries는 Flyway 도입
-- 직전(2026-08-18)에 추가된 테이블이라, 해당 기능 브랜치를 켜본 적 없는 로컬 DB에는 없다.
-- 그 상태로 ddl-auto: validate가 돌면 missing table로 기동이 죽는다.
--
-- 그래서 이 두 개만 다시 만든다. DDL은 V1에서 그대로 복사했다.
-- 이미 있는 환경은 IF NOT EXISTS로 건너간다.
--
-- V2와 같은 이유로 "있으면 건너뛴다"를 쓰는 마지막 파일이다. 이후 모두 같은 상태가 되므로
-- V4부터는 평범한 CREATE/ALTER만 쓴다.

CREATE TABLE IF NOT EXISTS `admin_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `action_type` enum('EMBEDDING_BACKFILL','EMBEDDING_RECALCULATE','INQUIRY_ANSWER','ROLE_UPDATE','TOUR_API_SYNC') NOT NULL,
  `admin_email` varchar(100) DEFAULT NULL,
  `admin_nickname` varchar(50) DEFAULT NULL,
  `admin_user_id` bigint NOT NULL,
  `details` text,
  `ip_address` varchar(45) DEFAULT NULL,
  `target_entity` varchar(50) DEFAULT NULL,
  `target_id` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_admin_audit_user_id` (`admin_user_id`),
  KEY `idx_admin_audit_action_type` (`action_type`),
  KEY `idx_admin_audit_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `inquiries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `answer` text,
  `answered_at` datetime(6) DEFAULT NULL,
  `content` text NOT NULL,
  `is_resolved` bit(1) NOT NULL,
  `status` enum('ANSWERED','PENDING','RESOLVED') NOT NULL,
  `title` varchar(150) NOT NULL,
  `answered_by_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `type` enum('ACCOUNT','BUG','FEATURE','OTHER','SPOT') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgd81vuy1gfjnflfxclx98dvwp` (`answered_by_id`),
  KEY `FKfks94q8sobcuibrudbr3im380` (`user_id`),
  CONSTRAINT `FKfks94q8sobcuibrudbr3im380` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKgd81vuy1gfjnflfxclx98dvwp` FOREIGN KEY (`answered_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
