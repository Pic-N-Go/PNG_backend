-- 댓글 답글(대댓글) + 댓글 좋아요 마이그레이션
--
-- ⚠️ 반드시 새 버전 애플리케이션을 배포하기 "전에" 실행해야 한다.
--
--    운영은 ddl-auto=validate 라서(application-prod.yaml) Hibernate가 엔티티와
--    실제 테이블을 대조한 뒤, 없는 컬럼/테이블이 있으면 기동을 거부한다. 로컬은
--    ddl-auto=update 라 알아서 만들어지므로 이 차이를 놓치기 쉽다.
--    이 마이그레이션 없이 배포하면 앱이 아예 뜨지 않는다:
--      Schema-validation: missing column [parent_id] in table [community_post_comments]
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/comment-reply-and-like-migration.sql"
--
--   EC2(도커 컨테이너로 MySQL을 띄운 경우):
--   docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo \
--     < docs/comment-reply-and-like-migration.sql
--
-- 재실행 안전(멱등): 이미 있으면 건너뛴다.

-- 1. community_post_comments: parent_id / like_count / reply_count 추가 --------

SET @ddl := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'community_post_comments'
             AND COLUMN_NAME = 'parent_id'),
    'SELECT ''community_post_comments.parent_id 이미 존재 - 건너뜀''',
    'ALTER TABLE community_post_comments ADD COLUMN parent_id BIGINT NULL'
  )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'community_post_comments'
             AND COLUMN_NAME = 'like_count'),
    'SELECT ''community_post_comments.like_count 이미 존재 - 건너뜀''',
    'ALTER TABLE community_post_comments ADD COLUMN like_count INT NOT NULL DEFAULT 0'
  )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'community_post_comments'
             AND COLUMN_NAME = 'reply_count'),
    'SELECT ''community_post_comments.reply_count 이미 존재 - 건너뜀''',
    'ALTER TABLE community_post_comments ADD COLUMN reply_count INT NOT NULL DEFAULT 0'
  )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 자기참조 FK. 부모를 지울 때 답글은 애플리케이션이 먼저 지우지만,
-- 직접 DB를 건드리는 경우에 대비해 ON DELETE CASCADE로 고아 답글을 막는다.
SET @ddl := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'community_post_comments'
             AND CONSTRAINT_NAME = 'fk_community_post_comment_parent'),
    'SELECT ''fk_community_post_comment_parent 이미 존재 - 건너뜀''',
    'ALTER TABLE community_post_comments
       ADD CONSTRAINT fk_community_post_comment_parent
       FOREIGN KEY (parent_id) REFERENCES community_post_comments(id) ON DELETE CASCADE'
  )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 목록 조회(최상위만 / 특정 부모의 답글만)가 타는 인덱스
SET @ddl := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'community_post_comments'
             AND INDEX_NAME = 'idx_post_comment_parent'),
    'SELECT ''idx_post_comment_parent 이미 존재 - 건너뜀''',
    'CREATE INDEX idx_post_comment_parent ON community_post_comments (post_id, parent_id, created_at)'
  )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. community_post_comment_likes 생성 ----------------------------------------

CREATE TABLE IF NOT EXISTS community_post_comment_likes (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  comment_id BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_community_post_comment_like (comment_id, user_id),
  CONSTRAINT fk_community_post_comment_like_comment
    FOREIGN KEY (comment_id) REFERENCES community_post_comments(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 기존 데이터 정합성 -------------------------------------------------------
-- 이 마이그레이션 이전 댓글은 전부 최상위(parent_id IS NULL)이고 답글/좋아요가 없다.
-- like_count·reply_count의 DEFAULT 0이 곧 정답이라 별도 백필이 필요 없다.
