-- 코스 낙관적 락용 버전 컬럼(course.version) 추가 마이그레이션
--
-- ⚠️ 반드시 새 버전 애플리케이션을 배포하기 "전에" 실행해야 한다.
--
--    운영은 ddl-auto=validate 라서(application-prod.yaml) 엔티티에 있는 컬럼이
--    실제 테이블에 없으면 기동을 거부한다. 로컬은 ddl-auto=update 라 자동으로
--    만들어지므로 이 차이가 드러나지 않는다.
--    이 컬럼 없이 배포하면 앱이 아예 뜨지 않는다:
--      Schema-validation: missing column [version] in table [course]
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/course-version-column-migration.sql"
--
--   EC2(도커 컨테이너로 MySQL을 띄운 경우):
--   docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo \
--     < docs/course-version-column-migration.sql
--
-- 재실행 안전(멱등): 이미 컬럼이 있으면 건너뛴다.


-- ─────────────────────────────────────────────────────────────
-- 1) 컬럼 추가
--
-- @Version이 붙은 필드로, JPA가 저장할 때마다 1씩 올리고
--   UPDATE ... WHERE id = ? AND version = ?
-- 조건으로 충돌을 잡아낸다.
--
-- NOT NULL DEFAULT 0으로 넣는 이유: 이미 저장된 코스들이 NULL을 갖게 되면
-- Hibernate가 그 엔티티를 "아직 저장된 적 없는 것"으로 오해할 수 있다.
-- 기존 행은 전부 0에서 시작하고, 다음 저장부터 1, 2로 올라간다.
--
-- ⚠️ 이 컬럼은 사람이 채우거나 고치는 값이 아니다. 수동으로 UPDATE 하면
--    그 순간 진행 중이던 요청이 엉뚱하게 충돌 처리된다.
-- ⚠️ 컬럼이 "이미 있는" 경우도 처리해야 한다.
--
--    로컬(ddl-auto=update)에서 이 SQL보다 앱을 먼저 켜면 Hibernate가 컬럼을
--    자기 방식으로 만든다: ALTER TABLE course ADD COLUMN version BIGINT (NULL 허용).
--    그러면 이미 저장돼 있던 코스들이 version = NULL을 갖게 되고,
--    그 코스를 수정하는 순간 Hibernate가 "NULL + 1"을 시도하다 터진다:
--
--      NullPointerException: Cannot invoke "java.lang.Long.longValue()"
--        at org.hibernate.engine.internal.Versioning.increment
--
--    실제로 로컬에서 이 오류가 났다(코스 2건이 전부 NULL). 그래서 "없으면 추가"만으로는
--    부족하고, 이미 있는 컬럼의 NULL도 채우고 NOT NULL로 맞춰야 한다.
DROP PROCEDURE IF EXISTS add_course_version_column;
DELIMITER $$
CREATE PROCEDURE add_course_version_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'course'
          AND COLUMN_NAME = 'version'
    ) THEN
        -- 컬럼이 없는 경우(운영 등 깨끗한 상태)
        ALTER TABLE course
            ADD COLUMN `version` BIGINT NOT NULL DEFAULT 0
                COMMENT '낙관적 락 버전. JPA가 자동 증가시킨다(수동 변경 금지)';
    ELSE
        -- 이미 있는 경우(로컬에서 ddl-auto가 먼저 만든 상태).
        -- 순서가 중요하다: NULL을 먼저 채워야 NOT NULL로 바꿀 수 있다.
        UPDATE course SET `version` = 0 WHERE `version` IS NULL;

        ALTER TABLE course
            MODIFY COLUMN `version` BIGINT NOT NULL DEFAULT 0
                COMMENT '낙관적 락 버전. JPA가 자동 증가시킨다(수동 변경 금지)';
    END IF;
END$$
DELIMITER ;

CALL add_course_version_column();
DROP PROCEDURE add_course_version_column;


-- ─────────────────────────────────────────────────────────────
-- 2) 검증
--
-- COLUMN_TYPE이 'bigint'로 나와야 한다. 한 줄도 안 나오면 1)이 적용되지 않은
-- 것이니 배포하면 안 된다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'course'
  AND COLUMN_NAME = 'version';

-- null_version이 0이어야 한다. 하나라도 남아 있으면 그 코스를 수정할 때
-- "NULL + 1"로 터진다(Versioning.increment NPE).
SELECT
    COUNT(*)                    AS total_courses,
    SUM(`version` IS NULL)      AS null_version,
    MIN(`version`)              AS min_version,
    MAX(`version`)              AS max_version
FROM course;


-- ─────────────────────────────────────────────────────────────
-- 되돌리기
--
--   ALTER TABLE course DROP COLUMN `version`;
--
-- ⚠️ 엔티티에 @Version 필드가 남아 있는 한 validate에서 다시 막히므로,
--    되돌리려면 이전 버전 애플리케이션으로 함께 롤백해야 한다.
