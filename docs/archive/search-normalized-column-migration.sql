-- 띄어쓰기 무시 검색용 정규화 컬럼 마이그레이션
--
-- [문제]
-- 저장된 이름과 사용자가 친 검색어의 띄어쓰기가 다르면 못 찾는다.
--   저장: 갈산공원          검색: 갈 산공원        -> 실패
--   저장: 강남 마이스 관광특구  검색: 강남마이스관광특구  -> 실패
--
-- 골든셋 829건 기준 실측(FULLTEXT 적용 상태):
--   공백 삽입 130건 -> 적중률 7.7%
--   공백 제거  30건 -> 적중률 3.3%
--   합쳐서 160건이 사실상 전부 실패한다.
--
-- [해결]
-- 이름과 주소에서 공백을 뺀 문자열을 컬럼 하나에 따로 만들고 거기에도 전문검색 색인을 건다.
-- 검색할 때도 검색어의 공백을 빼고 이 컬럼을 뒤지면 양방향으로 맞는다.
--   저장 '갈산공원'  <- 검색 '갈 산공원'을 '갈산공원'으로 바꿔서 조회
--   저장 '강남마이스관광특구' <- 검색 '강남마이스관광특구' 그대로 조회
--
-- [생성 컬럼(Generated Column)을 쓰는 이유]
-- 값을 애플리케이션에서 채우면 name이나 address가 바뀔 때 같이 갱신해야 하고,
-- 한 군데라도 빠뜨리면 색인과 원본이 조용히 어긋난다. 생성 컬럼은 MySQL이
-- 원본에서 자동으로 계산하므로 어긋날 수가 없다.
--
-- [이름과 주소 사이에 공백을 남기는 이유]
-- 둘을 완전히 붙이면 이름 끝과 주소 앞이 이어져 없는 단어가 생긴다.
-- 예: 이름 '한강' + 주소 '서울시...' -> '한강서울시' 안에 '강서'라는 없는 조각이 생긴다.
-- 공백을 하나 남기면 ngram이 거기서 끊어주므로 이런 오탐이 없다.
--
-- [범위 제한]
-- overview(본문)는 포함하지 않는다. 200~800자짜리 TEXT를 정규화해 한 번 더 색인하면
-- 저장 비용이 크게 늘어나는데, 띄어쓰기 문제는 주로 이름에서 생기기 때문이다.
-- 따라서 이 폴백은 이름과 주소만 대상으로 한다.
--
-- [엔티티에 매핑하지 않는다]
-- 이 컬럼은 검색 색인을 위한 보조 데이터이지 도메인 값이 아니다.
-- Spot 엔티티에 필드를 추가하지 않으며, 네이티브 쿼리에서만 참조한다.
-- ddl-auto=update는 매핑되지 않은 컬럼을 지우지 않으므로 안전하다.
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/search-normalized-column-migration.sql"


-- ─────────────────────────────────────────────────────────────
-- 1) 정규화 컬럼 추가 (여러 번 실행해도 안전)
--
-- 정규화 규칙: 한글/영문/숫자가 아닌 문자를 전부 지운다.
--
-- ⚠️ 이 규칙은 FullTextKeyword.toSpacelessPhrase()와 정확히 같아야 한다.
--    한쪽만 바꾸면 검색어와 저장값의 짝이 어긋나서, 예외 없이 결과만 조용히 안 나온다.
--
-- 공백만 지우던 첫 버전에서 실제로 그랬다. 검색어에서는 BOOLEAN MODE 연산자라는 이유로
-- 괄호를 지웠는데 저장값에는 괄호가 남아 있어서 '극락사(서울)'이 자기 이름으로도
-- 검색되지 않았다. 골든셋 측정에서 이런 이름 11건이 무결과로 잡혔다.
--
-- 문장부호를 지우는 게 맞기도 하다. ngram 파서는 문장부호를 토큰 경계로 보므로,
-- 남겨두면 '국립4·19민주묘지'가 '국립4'와 '19민주묘지'로 쪼개져 구문 검색이 실패한다.
--
-- STORED로 만드는 이유: VIRTUAL 컬럼에는 FULLTEXT 색인을 걸 수 없다.
-- 10만 건 기준 테이블 재작성이 일어나므로 잠시 걸릴 수 있다.
--
-- 이미 옛 정의(REGEXP_REPLACE 없이 REPLACE만 쓰던 버전)로 만들어져 있으면
-- 색인과 컬럼을 지우고 다시 만든다. 생성 컬럼은 원본에서 계산되는 값이라
-- 지웠다 다시 만들어도 잃는 데이터가 없다.
DROP PROCEDURE IF EXISTS rebuild_spot_search_norm;
DELIMITER $$
CREATE PROCEDURE rebuild_spot_search_norm()
BEGIN
    DECLARE current_expr TEXT DEFAULT NULL;

    SELECT GENERATION_EXPRESSION INTO current_expr
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'spot'
      AND COLUMN_NAME = 'search_norm';

    -- 옛 정의로 존재하면 걷어낸다
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
END$$
DELIMITER ;

CALL rebuild_spot_search_norm();
DROP PROCEDURE rebuild_spot_search_norm;


-- ─────────────────────────────────────────────────────────────
-- 2) 전문검색 색인 (여러 번 실행해도 안전)
DROP PROCEDURE IF EXISTS add_spot_search_norm_index;
DELIMITER $$
CREATE PROCEDURE add_spot_search_norm_index()
BEGIN
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

CALL add_spot_search_norm_index();
DROP PROCEDURE add_spot_search_norm_index;


-- ─────────────────────────────────────────────────────────────
-- 3) 값이 제대로 만들어졌는지 확인
--
-- 공백과 문장부호가 사라졌는지 본다.
-- '극락사(서울)' -> '극락사서울', '국립4·19민주묘지' -> '국립419민주묘지' 가 되어야 한다.
SELECT id, name, search_norm
FROM spot
WHERE id <= 135 AND (name LIKE '% %' OR name LIKE '%(%' OR name LIKE '%·%')
LIMIT 8;

-- 띄어쓰기를 틀린 검색어로 찾아본다. 원래 이름은 '강남 마이스 관광특구'다.
SELECT id, name
FROM spot
WHERE status = 'APPROVED' AND is_active = 1
  AND MATCH(search_norm) AGAINST ('"강남마이스관광특구"' IN BOOLEAN MODE)
LIMIT 5;

-- 문장부호가 든 이름도 찾아본다. 원래 이름은 '극락사(서울)'다.
SELECT id, name
FROM spot
WHERE status = 'APPROVED' AND is_active = 1
  AND MATCH(search_norm) AGAINST ('"극락사서울"' IN BOOLEAN MODE)
LIMIT 5;


-- ─────────────────────────────────────────────────────────────
-- 4) 추가 저장 비용
--
-- 기존 ft_spot_search(name, address, overview)는 보조 테이블 69.1MB였다.
-- 이번 색인은 overview가 빠져 훨씬 작아야 한다. 실제 값을 확인한다.
SELECT
    ROUND(SUM(FILE_SIZE) / 1024 / 1024, 1) AS fulltext_total_mb,
    COUNT(*)                               AS aux_table_count
FROM information_schema.INNODB_TABLESPACES
WHERE NAME LIKE CONCAT(DATABASE(), '/fts_%');

ANALYZE TABLE spot;

SELECT
    TABLE_NAME,
    ROUND(DATA_LENGTH  / 1024 / 1024, 2) AS data_mb,
    ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS index_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spot';


-- ─────────────────────────────────────────────────────────────
-- 되돌리기
--
--   ALTER TABLE spot DROP INDEX ft_spot_search_norm;
--   ALTER TABLE spot DROP COLUMN search_norm;
--
-- 애플리케이션은 picngo.search.normalize-fallback=false 로 두면
-- 이 컬럼을 전혀 참조하지 않는다. 설정을 먼저 끄고 지울 것.
