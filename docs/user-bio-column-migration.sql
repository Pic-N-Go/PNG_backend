-- 사용자 자기소개(users.bio) 컬럼 추가
--
-- ⚠️ 반드시 새 버전 애플리케이션을 배포하기 "전에" 실행해야 한다.
--
--    운영은 ddl-auto=validate 라서(application-prod.yaml) Hibernate가 엔티티와
--    실제 테이블을 대조한 뒤, 없는 컬럼/테이블이 있으면 기동을 거부한다. 로컬은
--    ddl-auto=update 라 알아서 만들어지므로 이 차이를 놓치기 쉽다.
--    이 마이그레이션 없이 배포하면 앱이 아예 뜨지 않는다:
--      Schema-validation: missing column [bio] in table [users]
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/user-bio-column-migration.sql"
--
--   EC2(도커 컨테이너로 MySQL을 띄운 경우):
--   docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo \
--     < docs/user-bio-column-migration.sql
--
-- 재실행 안전(멱등): 이미 있으면 건너뛴다.

-- 1. users.bio 추가 -----------------------------------------------------------
-- User 엔티티의 @Column(length = 100), UserProfileUpdateRequest의 @Size(max = 100)와
-- 같은 값이다. NULL 허용 — 가입 시에는 비어 있고 프로필 수정에서만 채워진다.

SET @ddl := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'users'
             AND COLUMN_NAME = 'bio'),
    'SELECT ''users.bio 이미 존재 - 건너뜀''',
    'ALTER TABLE users ADD COLUMN bio VARCHAR(100) NULL'
  )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 기존 데이터 정합성 -------------------------------------------------------
-- 기존 사용자는 자기소개가 없다. NULL이 곧 "아직 안 씀"이고 클라이언트는 이를
-- 플레이스홀더로 표시하므로 백필이 필요 없다.

-- 3. 검증 --------------------------------------------------------------------
-- bio 행이 나와야 한다. 안 나오면 위 ALTER가 적용되지 않은 것이다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'bio';
