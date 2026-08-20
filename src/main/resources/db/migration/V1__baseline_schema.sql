-- 기준 스키마 (baseline)
--
-- Flyway 도입 시점의 스키마 전체를 담는다. 목적은 하나다:
-- "빈 DB에서도 앱을 띄울 수 있게" 하는 것.
--
-- 이미 테이블이 있는 DB(운영, 기존 팀원 로컬)에서는 실행되지 않는다.
-- baseline-on-migrate 설정이 이 버전을 "이미 적용됨"으로 기록하고 다음 버전부터 시작한다.
--
-- 어디서 왔나: ddl-auto=update로 만들어진 로컬 스키마를 SHOW CREATE TABLE로 뜬 것.
-- 다만 그대로 옮기지는 않았다.
--   - 엔티티가 없는 고아 테이블 둘(checklist_item, hidden_checklist_default)은 제외했다.
--     스팟 체크리스트가 코스로 통합되면서 코드에서 사라졌는데 ddl-auto가 테이블을
--     지우지 않아 남아 있던 것이다. 전체 테이블을 소스와 대조해 확인했고 고아는 이 둘뿐이었다.
--   - AUTO_INCREMENT 현재값을 뺐다. 스키마가 아니라 그 DB의 런타임 상태다.
--
-- 주의: 한 번 적용된 뒤에는 이 파일을 수정할 수 없다(Flyway가 내용 지문을 대조한다).
--       스키마를 바꾸려면 다음 번호의 파일을 새로 추가할 것.

-- 테이블끼리 외래키로 물려 있어 생성 순서에 걸리지 않도록 잠시 끈다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `admin_audit_logs` (
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

CREATE TABLE `album_photos` (
  `album_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `image_url` varchar(500) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKe80qly1n5jka4x6o4vpxice7b` (`album_id`),
  CONSTRAINT `FKe80qly1n5jka4x6o4vpxice7b` FOREIGN KEY (`album_id`) REFERENCES `albums` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `albums` (
  `is_public` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `category` enum('BEACH','CAFE','CITY','ETC','FESTIVAL','FLOWER','FOREST','HANOK','HERITAGE','MILKY_WAY','MOUNTAIN','NIGHT_VIEW','PARK','SUNRISE_SUNSET') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcfmaqhra991wm7iiddqlnw88n` (`user_id`),
  CONSTRAINT `FKcfmaqhra991wm7iiddqlnw88n` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `bookmark` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '북마크 고유 ID',
  `spot_id` bigint NOT NULL COMMENT '스팟 FK',
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL COMMENT '유저 ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKp04d7tix62bdtu86iwxfd97q` (`spot_id`,`user_id`),
  CONSTRAINT `FKcr0gi4cgnnxl5o7robp2vw5ym` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `bookmark_collection` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '북마크 컬렉션 ID',
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL COMMENT '유저 ID',
  `color` varchar(20) NOT NULL COMMENT '색상 키 (pink, blue 등)',
  `icon` varchar(20) NOT NULL COMMENT '아이콘 키 (star, heart 등)',
  `name` varchar(20) NOT NULL COMMENT '컬렉션 이름 (최대 20자)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_user_name` (`user_id`,`name`),
  KEY `idx_collection_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `bookmark_collection_spot` (
  `collection_id` bigint NOT NULL COMMENT '컬렉션 FK',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '멤버십 ID',
  `spot_id` bigint NOT NULL COMMENT '스팟 ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgpjcgpu55omgpjlmngfnlvnye` (`collection_id`,`spot_id`),
  KEY `idx_membership_spot` (`spot_id`),
  CONSTRAINT `FK54hwor6899h0un8upw242splx` FOREIGN KEY (`collection_id`) REFERENCES `bookmark_collection` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_message` (
  `chat_room_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sender_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `sender_nickname` varchar(100) NOT NULL,
  `content` varchar(1000) NOT NULL,
  `type` enum('IMAGE','SYSTEM','TEXT') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_room` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spot_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','INACTIVE') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKis203njm18vb9f32e9nd2eo66` (`spot_id`),
  CONSTRAINT `FKn9brsj1n1s4kmewvss2ba2e7` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `community_images` (
  `image_height` int DEFAULT NULL,
  `image_width` int DEFAULT NULL,
  `iso` int DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `post_order` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_id` bigint NOT NULL,
  `post_id` bigint DEFAULT NULL,
  `taken_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `color_space` varchar(50) DEFAULT NULL,
  `exposure_time` varchar(50) DEFAULT NULL,
  `f_number` varchar(50) DEFAULT NULL,
  `file_format` varchar(50) DEFAULT NULL,
  `focal_length` varchar(50) DEFAULT NULL,
  `focal_length_35mm` varchar(50) DEFAULT NULL,
  `shutter_speed` varchar(50) DEFAULT NULL,
  `camera_make` varchar(100) DEFAULT NULL,
  `camera_model` varchar(100) DEFAULT NULL,
  `exposure_mode` varchar(100) DEFAULT NULL,
  `flash` varchar(100) DEFAULT NULL,
  `lens_make` varchar(100) DEFAULT NULL,
  `metering_mode` varchar(100) DEFAULT NULL,
  `white_balance` varchar(100) DEFAULT NULL,
  `lens_model` varchar(150) DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `software` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK58auwebu6399v08649ppbey3x` (`object_key`),
  KEY `FKruip9nxw1q22w4ff3epamro0y` (`post_id`),
  CONSTRAINT `FKruip9nxw1q22w4ff3epamro0y` FOREIGN KEY (`post_id`) REFERENCES `community_posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `community_post_bookmarks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_post_bookmark` (`post_id`,`user_id`),
  CONSTRAINT `FKnbok700w17v1s2277nsxdcuhm` FOREIGN KEY (`post_id`) REFERENCES `community_posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `community_post_comment_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `comment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_post_comment_like` (`comment_id`,`user_id`),
  CONSTRAINT `FKlro37sfyqg78ml2ssx5o4gmt4` FOREIGN KEY (`comment_id`) REFERENCES `community_post_comments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `community_post_comments` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `content` varchar(500) NOT NULL,
  `like_count` int NOT NULL,
  `reply_count` int NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9jo7md0xfm38p5ff9tdhaijh9` (`user_id`),
  KEY `idx_post_comment_parent` (`post_id`,`parent_id`,`created_at`),
  KEY `FK7snkitn642678sfs4jj3t0ynk` (`parent_id`),
  CONSTRAINT `FK7snkitn642678sfs4jj3t0ynk` FOREIGN KEY (`parent_id`) REFERENCES `community_post_comments` (`id`),
  CONSTRAINT `FK9jo7md0xfm38p5ff9tdhaijh9` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKawmqv21u0w9o5f9bnigvy84d4` FOREIGN KEY (`post_id`) REFERENCES `community_posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `community_post_likes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_post_like` (`post_id`,`user_id`),
  CONSTRAINT `FKs98gntvorodjgg3tc6h3etwr` FOREIGN KEY (`post_id`) REFERENCES `community_posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `community_post_tags` (
  `tag_order` int NOT NULL,
  `post_id` bigint NOT NULL,
  `tag` varchar(30) NOT NULL,
  PRIMARY KEY (`tag_order`,`post_id`),
  KEY `FKptsqbawmvvsbmtbiys5g450gr` (`post_id`),
  CONSTRAINT `FKptsqbawmvvsbmtbiys5g450gr` FOREIGN KEY (`post_id`) REFERENCES `community_posts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `community_posts` (
  `shooting_time` time NOT NULL,
  `bookmark_count` bigint NOT NULL,
  `comment_count` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `like_count` bigint NOT NULL,
  `spot_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `camera_model` varchar(100) DEFAULT NULL,
  `lens_model` varchar(150) DEFAULT NULL,
  `content` text NOT NULL,
  `weather` enum('CLEAR','CLOUDY','NIGHT','PARTLY_CLOUDY','RAIN','SNOW') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK44o0kkmkldhul00k2lm08bqje` (`user_id`),
  KEY `FK9d7sotior5dsaa3nm4v4fjhbc` (`spot_id`),
  CONSTRAINT `FK44o0kkmkldhul00k2lm08bqje` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9d7sotior5dsaa3nm4v4fjhbc` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `course` (
  `end_date` date DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `title` varchar(100) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0' COMMENT '낙관적 락 버전. JPA가 자동 증가시킨다(수동 변경 금지)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `course_checklist` (
  `is_checked` bit(1) NOT NULL,
  `course_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(200) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKh3sbd7oj7fx7arhx1viuh2hcl` (`course_id`),
  CONSTRAINT `FKh3sbd7oj7fx7arhx1viuh2hcl` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `course_spot` (
  `day_number` int NOT NULL,
  `sequence_order` int NOT NULL,
  `travel_time_estimated` bit(1) NOT NULL DEFAULT b'0',
  `travel_time_minutes` int DEFAULT NULL,
  `course_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spot_id` bigint NOT NULL,
  `memo` text,
  PRIMARY KEY (`id`),
  KEY `FKscmsw3tehrlpeq8yd4nc2x434` (`course_id`),
  CONSTRAINT `FKscmsw3tehrlpeq8yd4nc2x434` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `follows` (
  `created_at` datetime(6) DEFAULT NULL,
  `follower_id` bigint NOT NULL,
  `following_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_following` (`follower_id`,`following_id`),
  KEY `FKonkdkae2ngtx70jqhsh7ol6uq` (`following_id`),
  CONSTRAINT `FKonkdkae2ngtx70jqhsh7ol6uq` FOREIGN KEY (`following_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqnkw0cwwh6572nyhvdjqlr163` FOREIGN KEY (`follower_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inquiries` (
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

CREATE TABLE `notification` (
  `is_read` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spot_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `type` varchar(50) NOT NULL,
  `title` varchar(100) NOT NULL,
  `content` text NOT NULL,
  `dedupe_key` varchar(255) DEFAULT NULL,
  `deep_link` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKngfdgqrx9alto1sg6rpn0ii7v` (`dedupe_key`),
  KEY `idx_notification_user_created_at` (`user_id`,`created_at`),
  KEY `idx_notification_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notification_setting` (
  `dnd_end_time` time DEFAULT NULL,
  `dnd_start_time` time DEFAULT NULL,
  `is_community_push_enabled` bit(1) NOT NULL,
  `is_dnd_enabled` bit(1) NOT NULL,
  `is_golden_hour_push_enabled` bit(1) NOT NULL,
  `is_spot_alert_push_enabled` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `fcm_token` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKprsli08qedapfuoqx92jd8o7x` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `review` (
  `rating` int NOT NULL COMMENT '별점. 1~5',
  `visited_at` date DEFAULT NULL COMMENT '방문 날짜. 사용자 직접 입력, nullable',
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '리뷰 고유 ID',
  `spot_id` bigint NOT NULL COMMENT '스팟 FK',
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL COMMENT '작성자 유저 ID',
  `equipment_info` varchar(100) DEFAULT NULL COMMENT '촬영 기기 정보. 예: Sony A7IV + 35mm f1.8',
  `content` text NOT NULL COMMENT '리뷰 본문',
  `time_period` enum('DAYTIME','NIGHT','SUNRISE','SUNSET') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_spot_user` (`spot_id`,`user_id`),
  KEY `idx_review_user_id` (`user_id`),
  CONSTRAINT `FKdbuk0k3a1m7onbgkkm7yinebr` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `review_photo` (
  `image_height` int DEFAULT NULL,
  `image_width` int DEFAULT NULL,
  `iso` int DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '리뷰 사진 고유 ID',
  `review_id` bigint NOT NULL COMMENT '리뷰 FK',
  `taken_at` datetime(6) DEFAULT NULL,
  `color_space` varchar(50) DEFAULT NULL,
  `exposure_time` varchar(50) DEFAULT NULL,
  `f_number` varchar(50) DEFAULT NULL,
  `file_format` varchar(50) DEFAULT NULL,
  `focal_length` varchar(50) DEFAULT NULL,
  `focal_length_35mm` varchar(50) DEFAULT NULL,
  `shutter_speed` varchar(50) DEFAULT NULL,
  `camera_make` varchar(100) DEFAULT NULL,
  `camera_model` varchar(100) DEFAULT NULL,
  `exposure_mode` varchar(100) DEFAULT NULL,
  `flash` varchar(100) DEFAULT NULL,
  `lens_make` varchar(100) DEFAULT NULL,
  `metering_mode` varchar(100) DEFAULT NULL,
  `white_balance` varchar(100) DEFAULT NULL,
  `lens_model` varchar(150) DEFAULT NULL,
  `object_key` varchar(500) NOT NULL COMMENT 'S3 Object Key',
  `original_file_name` varchar(255) DEFAULT NULL,
  `software` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKj43whaop39fk8vagh71c1qxeb` (`object_key`),
  KEY `FK80ti8nek4uv8vn4vjhpre6mwg` (`review_id`),
  CONSTRAINT `FK80ti8nek4uv8vn4vjhpre6mwg` FOREIGN KEY (`review_id`) REFERENCES `review` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `review_tag` (
  `review_id` bigint NOT NULL,
  `tag` enum('BEST_SHOT','EASY_PARKING','GOOD_ACCESS','GOOD_FOR_SOLO','LIGHTING','MOODY','NIGHT_VIEW','SUNRISE','TRIPOD_NEEDED') NOT NULL,
  PRIMARY KEY (`review_id`,`tag`),
  CONSTRAINT `FKea2voymuynf2rmdx7ph30cwoe` FOREIGN KEY (`review_id`) REFERENCES `review` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='리뷰 태그. 고정 9종 중 최대 5개';

CREATE TABLE `season_event` (
  `is_active` bit(1) DEFAULT NULL,
  `max_score` int DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `eligible_cat3` varchar(255) DEFAULT NULL,
  `month_day_end` varchar(255) DEFAULT NULL,
  `month_day_peak_end` varchar(255) DEFAULT NULL,
  `month_day_peak_start` varchar(255) DEFAULT NULL,
  `month_day_start` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `region` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_event_name_region` (`name`,`region`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `spot` (
  `badge` bit(1) NOT NULL COMMENT '관광공사 인증 여부. source=TOUR_API면 true',
  `bookmark_count` int NOT NULL COMMENT '북마크 수 (denormalized)',
  `is_active` bit(1) NOT NULL COMMENT '활성화 여부',
  `latitude` double NOT NULL COMMENT 'GPS 위도. TourAPI: mapy',
  `longitude` double NOT NULL COMMENT 'GPS 경도. TourAPI: mapx',
  `photogenic_score` int NOT NULL COMMENT '포토제닉 점수',
  `review_average` double NOT NULL COMMENT '리뷰 평균 별점 (denormalized)',
  `review_count` int NOT NULL COMMENT '리뷰 수 (denormalized)',
  `toilet` bit(1) NOT NULL COMMENT '화장실 여부',
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '스팟 고유 ID',
  `updated_at` datetime(6) DEFAULT NULL,
  `cat3` varchar(10) DEFAULT NULL COMMENT 'TourAPI cat3 소분류 코드. 체크리스트 매핑에 사용 (예: A0201=해수욕장)',
  `zipcode` varchar(20) DEFAULT NULL COMMENT '우편번호. TourAPI: zipcode',
  `name` varchar(100) NOT NULL COMMENT '스팟명. TourAPI: title',
  `tour_content_id` varchar(100) DEFAULT NULL COMMENT 'TourAPI contentId. 사용자 등록 스팟은 null',
  `image_url` varchar(500) DEFAULT NULL COMMENT '대표 이미지 원본 URL. TourAPI: firstimage',
  `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '대표 이미지 썸네일 URL. TourAPI: firstimage2',
  `address` varchar(255) NOT NULL COMMENT '주소. TourAPI: addr1 + addr2',
  `infocenter` text COMMENT '문의 및 안내 전화. TourAPI: infocenter',
  `overview` text COMMENT '스팟 개요/설명. TourAPI: overview',
  `parking` text COMMENT '주차 안내. TourAPI: parking',
  `pet_friendly` varchar(255) DEFAULT NULL COMMENT '반려동물 동반 안내. TourAPI: chkpet',
  `restdate` text COMMENT '쉬는날/휴무일. TourAPI: restdate',
  `stroller_access` varchar(255) DEFAULT NULL COMMENT '유모차 대여 안내. TourAPI: chkbabycarriage',
  `subway_access` varchar(255) DEFAULT NULL COMMENT '대중교통 접근성. 예: 도보 10분',
  `usetime` text COMMENT '이용시간. TourAPI: usetime',
  `wheelchair_access` varchar(255) DEFAULT NULL COMMENT '장애인 시설 안내. TourAPI: chkhandicap',
  `category` enum('BEACH','CAFE','CITY','ETC','FESTIVAL','FLOWER','FOREST','HANOK','MILKY_WAY','MOUNTAIN','NIGHT_VIEW','PARK','PET','PORTRAIT','SUNRISE_SUNSET') NOT NULL COMMENT '카테고리. TourAPI: cat1/cat2/cat3',
  `source` enum('TOUR_API','USER') NOT NULL COMMENT '데이터 출처. TOUR_API | USER',
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL COMMENT '스팟 승인 상태. PENDING | APPROVED | REJECTED',
  `access_type` enum('NEEDS_ENTRANCE','RESOLVE_FAILED','ROAD_ACCESSIBLE','UNKNOWN') NOT NULL COMMENT '진입 도로 속성. ROAD_ACCESSIBLE | NEEDS_ENTRANCE | UNKNOWN',
  `primary_access_point_id` bigint DEFAULT NULL,
  `search_norm` varchar(400) GENERATED ALWAYS AS (concat_ws(_utf8mb4' ',regexp_replace(`name`,_utf8mb4'[^가-힣a-zA-Z0-9]',_utf8mb4''),regexp_replace(`address`,_utf8mb4'[^가-힣a-zA-Z0-9]',_utf8mb4''))) STORED,
  `embedding` mediumblob COMMENT '의미 검색용 임베딩 벡터. float32 배열을 그대로 이진 저장(4층 폴백 전용, 응답 DTO에 노출 안 함)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKinyci7r6wl68h8r5hfde0y0hh` (`primary_access_point_id`),
  KEY `idx_spot_map_bounds` (`status`,`is_active`,`latitude`,`longitude`),
  FULLTEXT KEY `ft_spot_search` (`name`,`address`,`overview`) /*!50100 WITH PARSER `ngram` */ ,
  FULLTEXT KEY `ft_spot_search_norm` (`search_norm`) /*!50100 WITH PARSER `ngram` */ ,
  CONSTRAINT `FKhhw6wqeq1h54jpa2d7xgbt9v3` FOREIGN KEY (`primary_access_point_id`) REFERENCES `spot_access_point` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `spot_access_point` (
  `latitude` double NOT NULL,
  `longitude` double NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spot_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `label` varchar(100) DEFAULT NULL,
  `source` enum('KAKAO_LOCAL','MANUAL','USER_FEEDBACK') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKii7n10tmrtmi177i7ueujx4h9` (`spot_id`),
  CONSTRAINT `FKii7n10tmrtmi177i7ueujx4h9` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `spot_alert` (
  `alert_timing_days` int NOT NULL,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spot_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `memo` varchar(200) DEFAULT NULL,
  `air_quality_condition` enum('GOOD','NONE','NORMAL_OR_BETTER') DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `spot_alert_time_conditions` (
  `spot_alert_id` bigint NOT NULL,
  `time_condition` enum('AFTERNOON','DAWN','MORNING','NIGHT','NONE','SUNRISE','SUNSET') DEFAULT NULL,
  KEY `FKppq5q4ivnod4r8p7joa2eqr5w` (`spot_alert_id`),
  CONSTRAINT `FKppq5q4ivnod4r8p7joa2eqr5w` FOREIGN KEY (`spot_alert_id`) REFERENCES `spot_alert` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `spot_alert_weather_conditions` (
  `spot_alert_id` bigint NOT NULL,
  `weather_condition` enum('CLEAR','CLOUDY','NONE','RAINY','SNOWY') DEFAULT NULL,
  KEY `FKgw9w5xs35hqqib87uodk6pg3f` (`spot_alert_id`),
  CONSTRAINT `FKgw9w5xs35hqqib87uodk6pg3f` FOREIGN KEY (`spot_alert_id`) REFERENCES `spot_alert` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `spot_categories` (
  `spot_id` bigint NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  UNIQUE KEY `uk_spot_categories` (`spot_id`,`category`),
  CONSTRAINT `FKbf3dbqr1h4ipi4myj97frs4gs` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`),
  CONSTRAINT `spot_categories_chk_1` CHECK ((`category` in (_utf8mb4'PARK',_utf8mb4'BEACH',_utf8mb4'MOUNTAIN',_utf8mb4'HANOK',_utf8mb4'FOREST',_utf8mb4'HERITAGE',_utf8mb4'CAFE',_utf8mb4'CITY',_utf8mb4'NIGHT_VIEW',_utf8mb4'FESTIVAL',_utf8mb4'FLOWER',_utf8mb4'SUNRISE_SUNSET',_utf8mb4'MILKY_WAY',_utf8mb4'ETC')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사진테마 카테고리(다중). cat3 + overview 키워드로 태깅. 태그 없으면 ETC';

CREATE TABLE `spot_photo` (
  `created_at` datetime(6) NOT NULL COMMENT '등록일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '사진 고유 ID',
  `spot_id` bigint NOT NULL COMMENT '스팟 FK',
  `user_id` bigint DEFAULT NULL COMMENT '업로드한 유저 ID. TourAPI 사진은 null',
  `photo_url` varchar(500) NOT NULL COMMENT '사진 URL(원본)',
  `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '썸네일 URL. TourAPI 사진만 존재, 유저 업로드는 null',
  PRIMARY KEY (`id`),
  KEY `FKlamd00av5a6ylhwidrx4vhu60` (`spot_id`),
  CONSTRAINT `FKlamd00av5a6ylhwidrx4vhu60` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `spot_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '태그 고유 ID',
  `spot_id` bigint NOT NULL COMMENT '스팟 FK',
  `tag` varchar(30) NOT NULL COMMENT '태그명. 예: #야경, #바다',
  PRIMARY KEY (`id`),
  KEY `FKi0au9bqswj2musl9iun4hbjps` (`spot_id`),
  CONSTRAINT `FKi0au9bqswj2musl9iun4hbjps` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_equipment` (
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

CREATE TABLE `user_spot_categories` (
  `user_id` bigint NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  KEY `FK2vjjl4j3fku2du6a4tp0m1g1w` (`user_id`),
  CONSTRAINT `FK2vjjl4j3fku2du6a4tp0m1g1w` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `user_spot_categories_chk_1` CHECK ((`category` in (_utf8mb4'PARK',_utf8mb4'BEACH',_utf8mb4'MOUNTAIN',_utf8mb4'HANOK',_utf8mb4'FOREST',_utf8mb4'HERITAGE',_utf8mb4'CAFE',_utf8mb4'CITY',_utf8mb4'NIGHT_VIEW',_utf8mb4'FESTIVAL',_utf8mb4'FLOWER',_utf8mb4'SUNRISE_SUNSET',_utf8mb4'MILKY_WAY',_utf8mb4'ETC')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `nickname` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `provider_id` varchar(100) DEFAULT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `provider` enum('KAKAO','LOCAL') NOT NULL,
  `role` enum('ADMIN','USER') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `uk_users_provider_provider_id` (`provider`,`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
