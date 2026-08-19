-- 지도 조회용 인덱스 마이그레이션
--
-- [문제]
-- 지도 API는 "화면에 보이는 사각형 안의 스팟을 달라"고 요청한다.
-- 그런데 위도/경도에 인덱스가 없어서 MySQL이 spot 10만 건을 처음부터 끝까지 다 읽는다.
-- 책에서 단어 하나 찾겠다고 1페이지부터 전부 넘기는 것과 같다.
--
-- [적용 전 실측값]
--   혼자 사용    DB 쿼리 721ms
--   20명 동시    DB 쿼리 878ms, API 응답 p95 2,003ms
--   EXPLAIN     type=ALL, key=NULL, rows≈94,000, Extra=Using filesort
--   (참고) 검색 API는 FULLTEXT 적용 후 혼자 사용 시 38ms. 지도가 19배 느리다.
--
-- [이 인덱스가 하는 일]
-- 위도 순으로 정렬된 목록을 만든다. 전화번호부에서 '김'씨 구간을 바로 펼치듯,
-- 위도 37.4~37.6 구간으로 바로 건너뛸 수 있게 된다.
-- 우리나라 위도 폭은 약 5.6도인데 지도 화면 하나는 0.02~0.6도라,
-- 위도로만 걸러도 대상이 10만 건에서 1천 건 아래로 줄어든다.
--
-- [컬럼 순서 이유]
-- status, is_active 는 "정확히 이 값" 조건이고 latitude 는 "범위" 조건이다.
-- 정확한 값 조건을 앞에 둬야 그 구간 안에서 범위 탐색을 할 수 있다.
--
-- longitude 를 맨 뒤에 넣은 이유는 조금 다르다. 이미 위도로 정렬해둔 목록이라
-- 그 안에서 경도는 순서가 뒤죽박죽이고, 그래서 경도로 범위를 더 좁히지는 못한다.
-- 대신 경도 값이 인덱스 안에 같이 들어 있어서, 실제 데이터 행을 읽기 전에
-- 인덱스만 보고 걸러낼 수 있다. 이것만으로도 읽는 양이 줄어든다.
--
-- [이 인덱스로 해결되지 않는 것]
-- 쿼리 끝의 ORDER BY photogenic_score DESC, bookmark_count DESC 정렬은 그대로 남는다
-- (EXPLAIN 의 Using filesort). 다만 10만 건을 정렬하던 것이 1천 건 정렬로 줄어드니
-- 부담은 크게 준다. 그래도 느리면 그때 공간 인덱스(R-Tree)를 검토한다.
--
-- [비용]
-- FULLTEXT 인덱스와 달리 이건 한 행당 항목 하나만 만든다.
-- (FULLTEXT 는 overview 500자를 두 글자씩 쪼개 한 행당 500개쯤 만들어서 69MB가 됐다.)
-- 예상 용량은 5~10MB 수준이며, 아래 3)에서 실제 값을 확인한다.
-- 코드 변경도, 스키마 변경도 없다. 되돌리려면 DROP INDEX 한 줄이면 된다.
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/search-map-bounds-index-migration.sql"


-- ─────────────────────────────────────────────────────────────
-- 1) 인덱스 생성 (여러 번 실행해도 안전)
DROP PROCEDURE IF EXISTS add_spot_map_bounds_index;
DELIMITER $$
CREATE PROCEDURE add_spot_map_bounds_index()
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

CALL add_spot_map_bounds_index();
DROP PROCEDURE add_spot_map_bounds_index;


-- ─────────────────────────────────────────────────────────────
-- 2) 인덱스를 실제로 쓰는지 확인
--
-- 봐야 할 것:
--   type  ALL  -> range   (전부 읽기 -> 범위만 읽기 로 바뀌어야 성공)
--   key   NULL -> idx_spot_map_bounds
--   rows  94000 -> 훨씬 작은 수
--   Extra 에 "Using index condition" 이 보이면 경도까지 인덱스에서 걸러낸 것이다
--   Extra 의 "Using filesort" 는 그대로 남는다 (정렬은 이 인덱스로 해결 안 됨)
EXPLAIN
SELECT * FROM spot
WHERE status = 'APPROVED' AND is_active = 1
  AND latitude  BETWEEN 37.45 AND 37.65
  AND longitude BETWEEN 126.85 AND 127.15
ORDER BY photogenic_score DESC, bookmark_count DESC
LIMIT 100;

-- 추정치가 아니라 실제로 읽은 행 수와 걸린 시간을 본다.
EXPLAIN ANALYZE
SELECT * FROM spot
WHERE status = 'APPROVED' AND is_active = 1
  AND latitude  BETWEEN 37.45 AND 37.65
  AND longitude BETWEEN 126.85 AND 127.15
ORDER BY photogenic_score DESC, bookmark_count DESC
LIMIT 100;


-- ─────────────────────────────────────────────────────────────
-- 3) 인덱스 용량
--
-- 일반 인덱스는 FULLTEXT 와 달리 본 테이블의 INDEX_LENGTH 에 정상적으로 잡힌다.
-- 적용 전 값이 1.52MB 였으므로, 늘어난 만큼이 이 인덱스의 크기다.
SELECT
    TABLE_NAME,
    ROUND(DATA_LENGTH  / 1024 / 1024, 2) AS data_mb,
    ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS index_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spot';


-- ─────────────────────────────────────────────────────────────
-- 되돌리기
--
--   ALTER TABLE spot DROP INDEX idx_spot_map_bounds;
--
-- 애플리케이션 코드는 이 인덱스를 전혀 모른다. 지워도 쿼리는 그대로 동작하고,
-- 다시 느려지기만 한다.
