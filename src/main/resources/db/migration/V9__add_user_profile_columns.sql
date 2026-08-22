-- User 엔티티에만 있고 마이그레이션에는 없던 컬럼 3종.
--
-- V7·V8이 메운 것과 원인이 다르다. 그쪽은 "V1이 baseline으로 건너뛰어져 기존 DB에만
-- 없던 것"이라 빈 DB에는 멀쩡히 있었다. 이 셋은 어떤 마이그레이션에도 없어
-- 빈 DB에서도 안 만들어진다. 엔티티에 필드만 추가되고 마이그레이션이 누락됐다.
-- (엔티티 기준 스키마를 ddl-auto: create로 따로 만들어 대조해 확정)
--
-- 셋 다 nullable이라 기존 행에 채울 값이 없다. 존재검사도 필요 없다 —
-- 어느 환경에도 없는 컬럼이다. 다만 운영에서 장애 대응으로 손수 추가했을
-- 가능성이 있어 V5와 같은 방식으로 검사는 남겨둔다.

DROP PROCEDURE IF EXISTS pngo_add_user_col;
DELIMITER $$
CREATE PROCEDURE pngo_add_user_col(IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `users` ADD COLUMN ', ddl);
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

-- 소셜 로그인(카카오)이 내려주는 프로필 이미지 주소. User.updateSocialProfile이 쓴다.
CALL pngo_add_user_col('social_profile_image_url', '`social_profile_image_url` varchar(500) DEFAULT NULL');
-- 자기소개.
CALL pngo_add_user_col('bio',        '`bio` varchar(100) DEFAULT NULL');
-- 소프트 딜리트 시각. NULL이면 살아있는 계정.
CALL pngo_add_user_col('deleted_at', '`deleted_at` datetime(6) DEFAULT NULL');

DROP PROCEDURE pngo_add_user_col;
