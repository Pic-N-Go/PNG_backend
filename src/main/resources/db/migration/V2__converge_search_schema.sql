-- 검색 스키마 수렴
--
-- Flyway 도입 이전에 손으로 관리하던 것들을 여기로 모은다. 목적은
-- "각자 어떤 상태든 실행 후에는 모두 같아지게" 하는 것이다.
--
-- 왜 필요한가: V1(기준 스키마)은 빈 DB에서만 실행된다. 이미 테이블이 있는 DB는
-- baseline으로 건너뛰므로, 그동안 docs/*.sql을 실행하지 않은 환경은 인덱스가 없는
-- 채로 남는다. 실제로 팀원 한 명의 로컬에 검색 인덱스가 없었다.
--
-- 그래서 이 파일만 예외적으로 "있으면 건너뛴다"를 직접 검사한다. 상태가 제각각인
-- 지금 한 번만 필요한 처리다. V3부터는 모두 같은 상태에서 시작하므로
-- 조건 검사 없이 평범한 ALTER TABLE만 쓰면 된다.
--
-- 원본: docs/search-fulltext-index-migration.sql
--       docs/search-normalized-column-migration.sql
--       docs/search-map-bounds-index-migration.sql
-- 세 파일에서 실제로 스키마를 바꾸는 부분만 옮겼다. 측정하며 만든 파일이라
-- 검증용 SELECT·EXPLAIN이 섞여 있었는데, 배포마다 실행돼봐야 볼 사람이 없어 뺐다.


-- ─────────────────────────────────────────────────────────────
-- 1) 검색용 FULLTEXT 인덱스
--
-- LIKE '%키워드%'는 앞에 와일드카드가 붙어 B-Tree 인덱스를 탈 수 없다.
-- 부분 일치를 색인으로 처리하려면 전문검색 인덱스가 필요하다.
--
-- 컬럼 목록과 순서는 MATCH() 절과 정확히 같아야 한다. 하나라도 다르면
-- MySQL이 인덱스를 못 찾고 ERROR 1191로 검색이 전부 실패한다.
--
-- ngram 파서는 한국어를 두 글자 단위로 쪼갠다. @@ngram_token_size가 2여야
-- 두 글자 검색어가 색인에 잡힌다(기본값 2, 서버 기동 시점에만 바꿀 수 있음).
DROP PROCEDURE IF EXISTS pngo_add_fulltext_index;
DELIMITER $$
CREATE PROCEDURE pngo_add_fulltext_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'spot'
          AND INDEX_NAME = 'ft_spot_search'
    ) THEN
        ALTER TABLE spot
            ADD FULLTEXT INDEX ft_spot_search (name, address, overview) WITH PARSER ngram;
    END IF;
END$$
DELIMITER ;

CALL pngo_add_fulltext_index();
DROP PROCEDURE pngo_add_fulltext_index;


-- ─────────────────────────────────────────────────────────────
-- 2) 띄어쓰기·문장부호 무시 검색용 생성 컬럼과 색인
--
-- 이름과 주소에서 한글·영문·숫자만 남긴 값을 저장해둔다. 검색어도 같은 규칙으로
-- 정리해서 맞춰야 짝이 맞는다(FullTextKeyword의 정규식과 같아야 한다).
-- 한쪽만 바꾸면 예외 없이 결과만 조용히 안 나온다.
--
-- STORED인 이유: VIRTUAL 컬럼에는 FULLTEXT 색인을 걸 수 없다.
--
-- 옛 정의(REGEXP_REPLACE 없이 공백만 지우던 버전)로 만들어져 있으면 걷어내고
-- 다시 만든다. 생성 컬럼은 원본에서 계산되는 값이라 지워도 잃는 데이터가 없다.
DROP PROCEDURE IF EXISTS pngo_rebuild_search_norm;
DELIMITER $$
CREATE PROCEDURE pngo_rebuild_search_norm()
BEGIN
    DECLARE current_expr TEXT DEFAULT NULL;

    SELECT GENERATION_EXPRESSION INTO current_expr
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'spot'
      AND COLUMN_NAME = 'search_norm';

    IF current_expr IS NOT NULL AND current_expr NOT LIKE '%regexp_replace%' THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'spot'
              AND INDEX_NAME = 'ft_spot_search_norm'
        ) THEN
            ALTER TABLE spot DROP INDEX ft_spot_search_norm;
        END IF;
        ALTER TABLE spot DROP COLUMN search_norm;
        SET current_expr = NULL;
    END IF;

    IF current_expr IS NULL THEN
        ALTER TABLE spot
            ADD COLUMN search_norm VARCHAR(400)
                GENERATED ALWAYS AS (
                    CONCAT_WS(' ',
                        REGEXP_REPLACE(name,    '[^가-힣a-zA-Z0-9]', ''),
                        REGEXP_REPLACE(address, '[^가-힣a-zA-Z0-9]', ''))
                ) STORED;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'spot'
          AND INDEX_NAME = 'ft_spot_search_norm'
    ) THEN
        ALTER TABLE spot
            ADD FULLTEXT INDEX ft_spot_search_norm (search_norm) WITH PARSER ngram;
    END IF;
END$$
DELIMITER ;

CALL pngo_rebuild_search_norm();
DROP PROCEDURE pngo_rebuild_search_norm;


-- ─────────────────────────────────────────────────────────────
-- 3) 지도 영역 조회용 복합 인덱스
--
-- 좌표 범위 조회가 매번 테이블 전체를 읽고 있었다. 일반적인 확대 수준에서는
-- 이 인덱스로 range 스캔이 된다(넓게 축소한 화면은 조건에 맞는 행이 많아
-- 옵티마이저가 풀스캔을 고르는데, 그 구간은 서버 클러스터링으로 다룰 문제로 분리했다).
DROP PROCEDURE IF EXISTS pngo_add_map_bounds_index;
DELIMITER $$
CREATE PROCEDURE pngo_add_map_bounds_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'spot'
          AND INDEX_NAME = 'idx_spot_map_bounds'
    ) THEN
        ALTER TABLE spot
            ADD INDEX idx_spot_map_bounds (status, is_active, latitude, longitude);
    END IF;
END$$
DELIMITER ;

CALL pngo_add_map_bounds_index();
DROP PROCEDURE pngo_add_map_bounds_index;
