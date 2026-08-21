-- 콘테스트 기능 테이블 생성
--
-- 콘테스트 기능(PR #73)이 마이그레이션 없이 머지됐다. Flyway 도입 직후라
-- "새 엔티티를 만들면 마이그레이션도 써야 한다"는 규칙이 아직 공유되기 전이었다.
-- 그 전까지는 ddl-auto: update가 알아서 만들어줬으니 자연스러운 실수다.
--
-- 결과: 이 테이블들이 없는 환경(다른 팀원 로컬, 운영)에서 validate가 기동을 막는다.
--   Schema validation: missing table [contest]
--
-- DDL은 Hibernate가 엔티티에서 생성한 것을 그대로 떴다. 작성자가 의도한 인덱스가
-- 더 있다면 다음 버전 파일로 추가하면 된다.
--
-- ─────────────────────────────────────────────────────────────
-- 곁들여 고친 것: contest_ranking_snapshot의 rank 컬럼
--
-- rank는 MySQL 8의 예약어라(윈도우 함수 RANK) 따옴표 없이 컬럼명으로 쓸 수 없다.
-- 그래서 이 테이블만 create table이 문법 오류로 실패했고, 어느 환경에도 존재하지
-- 않았다. ddl-auto: update가 DDL 실패를 WARN으로만 남기고 넘어가는 탓에
-- 앱은 정상 기동했고, 자정 순위 집계 스케줄러가 도는 시점에야 터졌을 것이다.
--
-- 엔티티의 자바 필드명은 rank 그대로 두고 컬럼명만 ranking으로 매핑했다
-- (ContestRankingSnapshot 참고). 역따옴표로 감싸는 방법도 있지만 그러면 이 컬럼을
-- 건드리는 SQL마다 따옴표를 챙겨야 해서, 예약어를 스키마에서 아예 뺐다.
-- API 응답과 자바 코드에서는 여전히 rank라 겉으로 달라지는 것은 없다.
--
-- IF NOT EXISTS를 쓰는 이유: 이 기능을 개발한 환경에는 ddl-auto: update가 만들어둔
-- 테이블이 이미 있다. 없는 환경에만 만들어야 하므로 여기서도 조건 검사가 필요하다
-- (contest_ranking_snapshot만은 예약어 문제로 어디에도 없어서 항상 새로 만들어진다).
--
-- 외래키가 서로를 참조하므로 순서에 상관없이 만들 수 있도록 검사를 잠시 끈다.
-- (V1과 같은 방식)

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `contest` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `max_entries_per_user` int NOT NULL,
  `result_open_at` datetime(6) NOT NULL,
  `submit_end_at` datetime(6) NOT NULL,
  `submit_start_at` datetime(6) NOT NULL,
  `theme_image_url` varchar(500) DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `vote_end_at` datetime(6) NOT NULL,
  `vote_limit` int NOT NULL,
  `vote_start_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `contest_entry` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `caption` varchar(80) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `photo_url` varchar(500) NOT NULL,
  `spot_name` varchar(100) DEFAULT NULL,
  `vote_count` int NOT NULL,
  `contest_id` bigint NOT NULL,
  `spot_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1axea9flg9w6uiy0m4a9n0q6s` (`contest_id`),
  KEY `FKb3kkjtg100j6mvx6j4dwcxne7` (`spot_id`),
  KEY `FKs1u52mjqkfgi00kvqdj85ggkx` (`user_id`),
  CONSTRAINT `FK1axea9flg9w6uiy0m4a9n0q6s` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`),
  CONSTRAINT `FKb3kkjtg100j6mvx6j4dwcxne7` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`),
  CONSTRAINT `FKs1u52mjqkfgi00kvqdj85ggkx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `contest_ranking_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `ranking` int NOT NULL,
  `snapshot_date` date NOT NULL,
  `vote_count` int NOT NULL,
  `contest_id` bigint NOT NULL,
  `entry_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_snapshot_entry` (`contest_id`,`snapshot_date`,`entry_id`),
  KEY `FK152jqpxt5t0vt9fo615vs98kk` (`entry_id`),
  CONSTRAINT `FK152jqpxt5t0vt9fo615vs98kk` FOREIGN KEY (`entry_id`) REFERENCES `contest_entry` (`id`),
  CONSTRAINT `FKsm9qqtx1xue2smwn52umlt178` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `contest_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `reason` enum('ABUSE','COPYRIGHT','ETC','INAPPROPRIATE','SPAM') NOT NULL,
  `entry_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_report_user_entry` (`user_id`,`entry_id`),
  KEY `FKqwpncl9fygfk08aa83eugb45c` (`entry_id`),
  CONSTRAINT `FKqwpncl9fygfk08aa83eugb45c` FOREIGN KEY (`entry_id`) REFERENCES `contest_entry` (`id`),
  CONSTRAINT `FKti03f1bamje83vpn5hnvykqy0` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `contest_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `contest_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_subscription_user_contest` (`user_id`,`contest_id`),
  KEY `FKnwimcy9ps1qner5ydeur25ywx` (`contest_id`),
  CONSTRAINT `FKmfvtc8rislgwreje9r88m1toj` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKnwimcy9ps1qner5ydeur25ywx` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `contest_vote` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `contest_id` bigint NOT NULL,
  `entry_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_vote_user_entry` (`user_id`,`entry_id`),
  KEY `FK49d11rgit2lq54of2fliwkr9k` (`contest_id`),
  KEY `FK6r2imij8nbv4dsysydy105h8e` (`entry_id`),
  CONSTRAINT `FK49d11rgit2lq54of2fliwkr9k` FOREIGN KEY (`contest_id`) REFERENCES `contest` (`id`),
  CONSTRAINT `FK6r2imij8nbv4dsysydy105h8e` FOREIGN KEY (`entry_id`) REFERENCES `contest_entry` (`id`),
  CONSTRAINT `FKejbsy1n3m2r8rm37h34nw48hl` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
