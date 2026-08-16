-- 검색용 FULLTEXT(ngram) 인덱스 마이그레이션
--
-- 목적: LIKE '%키워드%' 가 인덱스를 못 타는 문제를 MySQL 안에서 해결할 수 있는지 확인한다.
--       선행 와일드카드라 B-Tree를 쓸 수 없으니 전문검색 인덱스로 바꾼다.
--
-- ⚠️ ddl-auto=update는 이 인덱스를 만들지도 지우지도 않는다. 엔티티에 선언하지 않았기 때문이다
--    (@Index로 FULLTEXT를 표현할 수 없다). 이 파일이 정본이며 수동으로 적용해야 한다.
--
-- 재실행 안전(멱등): 1)은 확인용, 2)는 이미 있으면 건너뛰도록 프로시저로 감쌌다.
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/search-fulltext-index-migration.sql"


-- ─────────────────────────────────────────────────────────────
-- 1) 사전 확인: ngram 토큰 크기
--
-- ngram_token_size는 기동 시점에만 정해지는 읽기 전용 변수다(my.ini/my.cnf).
-- 기본값 2가 한국어에 적합하다 - '한라산'이 '한라', '라산' 두 토큰으로 쪼개진다.
--
-- 결과가 2가 아니면 아래 인덱스의 동작이 달라진다. 특히 값이 N이면
-- N글자 미만 검색어는 토큰이 하나도 안 만들어져 절대 매칭되지 않는다.
-- 골든셋의 최소 검색어 길이가 2글자이므로 2여야 한다.
SELECT @@ngram_token_size AS ngram_token_size;

-- 참고: 인덱스를 만들기 전 spot 테이블의 현재 크기. 아래 4)와 비교해
-- 전문검색 인덱스가 차지하는 저장 비용을 알 수 있다.
SELECT
    TABLE_NAME,
    ROUND(DATA_LENGTH  / 1024 / 1024, 1) AS data_mb,
    ROUND(INDEX_LENGTH / 1024 / 1024, 1) AS index_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spot';


-- ─────────────────────────────────────────────────────────────
-- 2) FULLTEXT 인덱스 생성
--
-- 컬럼 구성은 기존 LIKE 쿼리와 정확히 같게 맞춘다(name, address, overview).
-- 대상 컬럼이 달라지면 성능 차이가 인덱스 덕분인지 검색 범위가 줄어서인지 구별할 수 없다.
--
-- MATCH()의 컬럼 목록은 FULLTEXT 인덱스의 컬럼 목록과 순서까지 정확히 일치해야 한다.
-- 하나라도 다르면 MySQL이 인덱스를 찾지 못하고 에러를 낸다:
--   ERROR 1191 (HY000): Can't find FULLTEXT index matching the column list
--
-- 10만 건 기준 생성에 수십 초가 걸릴 수 있다. overview가 TEXT라 bigram이 많이 나온다.
DROP PROCEDURE IF EXISTS add_spot_fulltext_index;
DELIMITER $$
CREATE PROCEDURE add_spot_fulltext_index()
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

CALL add_spot_fulltext_index();
DROP PROCEDURE add_spot_fulltext_index;


-- ─────────────────────────────────────────────────────────────
-- 3) 검증: 인덱스를 실제로 타는지
--
-- type이 'fulltext', key가 'ft_spot_search'로 나와야 한다.
-- 적용 전 LIKE 쿼리는 type=ALL, key=NULL, rows≈100000 이었다.
EXPLAIN
SELECT s.* FROM spot s
WHERE s.status = 'APPROVED'
  AND s.is_active = true
  AND MATCH(s.name, s.address, s.overview) AGAINST ('"한라산"' IN BOOLEAN MODE)
ORDER BY s.created_at DESC
LIMIT 20;

-- 실제 읽은 행 수와 소요 시간. EXPLAIN의 rows는 추정치라 이쪽이 근거로 강하다.
EXPLAIN ANALYZE
SELECT s.* FROM spot s
WHERE s.status = 'APPROVED'
  AND s.is_active = true
  AND MATCH(s.name, s.address, s.overview) AGAINST ('"한라산"' IN BOOLEAN MODE)
ORDER BY s.created_at DESC
LIMIT 20;


-- ─────────────────────────────────────────────────────────────
-- 4) 인덱스 저장 비용
--
-- ⚠️ information_schema.TABLES.INDEX_LENGTH로는 FULLTEXT 크기를 알 수 없다.
--    InnoDB는 전문검색 인덱스를 본 테이블이 아니라 별도의 보조 테이블
--    (fts_<table_id>_<index_id>_index_1 ~ _6 등)에 저장하는데, 그 용량은
--    INDEX_LENGTH에 잡히지 않는다. 1)에서 본 index_mb는 PK/보조 인덱스만의 값이라
--    FULLTEXT를 만들기 전과 후가 거의 같게 나온다.
--
-- 보조 테이블을 직접 합산해야 실제 비용이 나온다.
SELECT
    ROUND(SUM(FILE_SIZE)      / 1024 / 1024, 1) AS fulltext_file_mb,
    ROUND(SUM(ALLOCATED_SIZE) / 1024 / 1024, 1) AS fulltext_alloc_mb,
    COUNT(*)                                    AS aux_table_count
FROM information_schema.INNODB_TABLESPACES
WHERE NAME LIKE CONCAT(DATABASE(), '/fts_%');

-- 보조 테이블 개별 내역. _index_1 ~ _index_6이 실제 색인이고,
-- deleted/config/being_deleted는 관리용이라 작다.
SELECT
    NAME,
    ROUND(FILE_SIZE / 1024 / 1024, 2) AS file_mb
FROM information_schema.INNODB_TABLESPACES
WHERE NAME LIKE CONCAT(DATABASE(), '/fts_%')
ORDER BY FILE_SIZE DESC;

-- 본 테이블 크기(비교용). ngram은 모든 위치의 bigram을 색인하므로
-- 전문검색 보조 테이블이 원문 데이터에 근접하거나 더 커지는 일도 흔하다.
-- "속도를 얻는 대신 저장 공간과 쓰기 비용을 치렀다"까지 말할 수 있어야 한다.
SELECT
    TABLE_NAME,
    ROUND(DATA_LENGTH  / 1024 / 1024, 1) AS data_mb,
    ROUND(INDEX_LENGTH / 1024 / 1024, 1) AS non_fulltext_index_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spot';


-- ─────────────────────────────────────────────────────────────
-- 되돌리기
--
--   ALTER TABLE spot DROP INDEX ft_spot_search;
--
-- 인덱스를 지워도 애플리케이션은 picngo.search.engine=LIKE 로 되돌리면 그대로 동작한다.
-- (FULLTEXT로 둔 채 인덱스만 지우면 위 ERROR 1191이 난다.)
