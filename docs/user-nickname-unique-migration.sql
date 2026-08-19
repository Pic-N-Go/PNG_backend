-- users.nickname 유니크 제약 추가
--
-- ⚠️ 배포 전에 실행하는 편이 맞지만, 이것만은 앱 기동을 막지 않는다.
--    Hibernate ddl-auto=validate는 테이블·컬럼·타입만 대조하고 unique 제약은 검사하지 않는다.
--    안 하면 기동은 되고, 대신 닉네임 중복이 동시 가입 경합으로 계속 뚫린 상태가 유지된다.
--
-- 왜 필요한가:
--   닉네임 중복은 지금까지 서비스 계층의 existsByNickname 검사로만 막고 있었다. 검사와
--   INSERT 사이에 다른 요청이 같은 값을 넣으면 둘 다 통과한다(check-then-insert 경합).
--   카카오 신규 가입은 접미사를 붙여 회피하지만, 그 판정도 같은 구조라 경합에 뚫린다.
--
-- ⚠️ 적용 전 반드시 중복을 확인할 것. 있으면 ALTER가 실패한다(Duplicate entry).
--    개발 DB 기준 중복 0건이었으나, 운영은 별도로 확인해야 한다.
--
--   SELECT nickname, COUNT(*) c FROM users GROUP BY nickname HAVING c > 1;
--
--   중복이 있으면 먼저 정리한다 — 나중에 만들어진 계정의 닉네임에 접미사를 붙이는 식으로.
--   자동 정리 SQL은 두지 않았다. 어느 계정을 살릴지는 사람이 정할 문제다.
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/user-nickname-unique-migration.sql"
--
--   EC2(도커 컨테이너로 MySQL을 띄운 경우):
--   docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo \
--     < docs/user-nickname-unique-migration.sql
--
-- 재실행 안전(멱등): 이미 있으면 건너뛴다.

-- 1. 중복 확인 (결과가 비어 있어야 아래가 성공한다) -----------------------------
SELECT nickname, COUNT(*) AS duplicate_count
FROM users GROUP BY nickname HAVING duplicate_count > 1;

-- 2. 유니크 인덱스 추가 --------------------------------------------------------
SET @ddl := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'users'
             AND INDEX_NAME = 'uk_users_nickname'),
    'SELECT ''uk_users_nickname 이미 존재 - 건너뜀''',
    'ALTER TABLE users ADD CONSTRAINT uk_users_nickname UNIQUE (nickname)'
  )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 검증 --------------------------------------------------------------------
SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'uk_users_nickname';
