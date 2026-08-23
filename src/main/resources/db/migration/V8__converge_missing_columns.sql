-- V1이 baseline으로 건너뛰어진 DB(기존 팀원 로컬·운영)에 남은 컬럼 드리프트를 메운다.
--
-- V3가 테이블을, V5가 고아 컬럼을 정리했지만 "V1에만 있고 실DB에는 없는 컬럼"은
-- 아무도 보지 않았다. ddl-auto: validate가 한 번에 하나씩만 알려주는 탓에
-- 매번 다른 컬럼으로 기동이 막힌다. 정답 스키마(빈 DB에 V1~V7 적용)와
-- information_schema를 통째로 대조해 아래 3개를 확정했다.
--
-- 빈 DB에서는 V1이 이미 만들었으므로 전부 존재검사 후에만 실행한다.
-- (MySQL에는 ADD COLUMN IF NOT EXISTS가 없다 — V5와 같은 방식)


-- ─────────────────────────────────────────────────────────────
-- 1) community_post_comments — 대댓글·좋아요 집계 컬럼 3종
--
-- NOT NULL이라 기존 행을 채울 DEFAULT가 필요하다. 넣은 뒤 DEFAULT를 떼어
-- V1이 만든 스키마와 정의를 일치시킨다(빈 DB에서는 DROP DEFAULT가 no-op).
-- 집계값 backfill은 하지 않는다. parent_id가 방금 생겨 대댓글이 존재할 수 없고,
-- 좋아요 테이블(V7)도 비어 있어 양쪽 모두 0이 정답이다.

DROP PROCEDURE IF EXISTS pngo_add_col;
DELIMITER $$
CREATE PROCEDURE pngo_add_col(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

CALL pngo_add_col('community_post_comments', 'like_count',  '`like_count` int NOT NULL DEFAULT 0');
CALL pngo_add_col('community_post_comments', 'reply_count', '`reply_count` int NOT NULL DEFAULT 0');
CALL pngo_add_col('community_post_comments', 'parent_id',   '`parent_id` bigint DEFAULT NULL');
DROP PROCEDURE pngo_add_col;

ALTER TABLE `community_post_comments` ALTER COLUMN `like_count`  DROP DEFAULT;
ALTER TABLE `community_post_comments` ALTER COLUMN `reply_count` DROP DEFAULT;


-- parent_id의 인덱스·FK. validate는 인덱스를 보지 않아 기동에는 무관하지만,
-- 대댓글 조회가 이 인덱스를 타므로 V1과 같은 모양으로 맞춰둔다.
DROP PROCEDURE IF EXISTS pngo_add_comment_parent_idx;
DELIMITER $$
CREATE PROCEDURE pngo_add_comment_parent_idx()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'community_post_comments'
          AND INDEX_NAME = 'idx_post_comment_parent'
    ) THEN
        ALTER TABLE `community_post_comments`
            ADD KEY `idx_post_comment_parent` (`post_id`, `parent_id`, `created_at`);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'community_post_comments'
          AND CONSTRAINT_NAME = 'FK7snkitn642678sfs4jj3t0ynk'
    ) THEN
        ALTER TABLE `community_post_comments`
            ADD CONSTRAINT `FK7snkitn642678sfs4jj3t0ynk`
            FOREIGN KEY (`parent_id`) REFERENCES `community_post_comments` (`id`);
    END IF;
END$$
DELIMITER ;

CALL pngo_add_comment_parent_idx();
DROP PROCEDURE pngo_add_comment_parent_idx;
