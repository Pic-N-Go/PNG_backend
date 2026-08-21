-- 코드와 어긋난 스키마 바로잡기
--
-- 둘 다 원인이 같다. ddl-auto: update는 추가만 하고 지우지 않는다.
-- 코드에서 뭔가를 빼도 DB에는 그대로 남고, 그 잔재가 나중에 사고를 낸다.
-- V2가 고아 "테이블"은 지웠지만 고아 컬럼과 옛 인덱스는 보지 못했다.
--
-- 둘 다 운영에서 실제로 터진 것들이라 근거는 아래 각 절에 적어둔다.


-- ─────────────────────────────────────────────────────────────
-- 1) review_photo.photo_url 제거 — 사진 업로드가 전부 실패하던 원인
--
-- 사진 위치를 저장하는 방식이 바뀌었다.
--   전: photo_url  "https://.../abc.jpg"   전체 주소를 통째로 저장
--   후: object_key "reviews/2026/abc.jpg"  버킷 안 경로만 저장하고 주소는 조립
-- 버킷·도메인이 바뀌어도 저장된 값을 고칠 필요가 없게 하려는 변경이었다.
--
-- 그런데 옛 컬럼이 NOT NULL인 채로 남았다. 지금 코드는 이 컬럼을 아예 모르므로
-- 값을 넣지 않고, INSERT가 통째로 거부된다:
--   Field 'photo_url' doesn't have a default value
--
-- 기동은 멀쩡했다. ddl-auto: validate는 "없는 컬럼"만 잡지 "남아도는 컬럼"은
-- 문제 삼지 않기 때문이다. 그래서 앱은 뜨는데 저장만 안 되는 상태로 지나갔다.
--
-- 지워도 되는 근거: 매핑된 필드가 없어 코드가 읽지도 쓰지도 않는다.
-- object_key가 NOT NULL이므로 모든 사진 행이 이미 새 방식의 값을 갖고 있다.
--
-- MySQL에는 DROP COLUMN IF EXISTS가 없어서 존재 검사를 직접 한다.
-- (운영은 장애 대응으로 이미 손으로 지운 상태라, 그 환경에서도 그냥 넘어가야 한다.)
DROP PROCEDURE IF EXISTS pngo_drop_review_photo_url;
DELIMITER $$
CREATE PROCEDURE pngo_drop_review_photo_url()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'review_photo'
          AND COLUMN_NAME = 'photo_url'
    ) THEN
        ALTER TABLE review_photo DROP COLUMN `photo_url`;
    END IF;
END$$
DELIMITER ;

CALL pngo_drop_review_photo_url();
DROP PROCEDURE pngo_drop_review_photo_url;


-- ─────────────────────────────────────────────────────────────
-- 2) ft_spot_search를 (name, address)로 재생성 — 검색이 전부 500이던 원인
--
-- MySQL은 MATCH()에 적은 컬럼 목록과 정확히 같은 FULLTEXT 인덱스를 요구한다.
-- 하나라도 다르면 비슷한 것으로 대신 처리하지 않고 그냥 거절한다:
--   ERROR 1191: Can't find FULLTEXT index matching the column list
--
--   코드:   match(s.name, s.address)                 SpotRepository
--   인덱스: ft_spot_search(name, address, overview)  V1·V2가 만든 것
--
-- 코드 쪽이 맞다. overview까지 훑으니 "실내"로 검색했을 때 설명문에 그 단어가
-- 있는 절이 이름이 일치하는 곳보다 먼저 나오는 문제가 있어서 뺀 것이다(ea6650a).
-- 그 커밋은 docs/search-fulltext-index-migration.sql도 2컬럼으로 고쳤는데,
-- V1·V2를 만들 때 그 변경 전의 정의를 옮겨 적어서 3컬럼이 그대로 굳었다.
--
-- V2가 이걸 못 고친다. 인덱스 "이름"만 보고 있으면 건너뛰기 때문이다.
-- 바로 옆 search_norm 절은 GENERATION_EXPRESSION까지 비교해 옛 정의면 다시 만드는데,
-- 인덱스 쪽에는 같은 검사를 넣지 않았다. 여기서는 컬럼 목록까지 비교한다.
--
-- 운영에서 이 문제가 늦게 드러난 이유: SEARCH_ENGINE이 컨테이너에 전달되지 않아
-- 그동안 LIKE 검색으로 돌고 있었다. FULLTEXT 경로가 처음 실행된 날 바로 터졌다.
DROP PROCEDURE IF EXISTS pngo_rebuild_fulltext_index;
DELIMITER $$
CREATE PROCEDURE pngo_rebuild_fulltext_index()
BEGIN
    DECLARE current_cols TEXT DEFAULT NULL;

    SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) INTO current_cols
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'spot'
      AND INDEX_NAME = 'ft_spot_search';

    -- 컬럼 목록이 기대와 다르면(옛 3컬럼 등) 걷어낸다. 인덱스는 원본에서 다시
    -- 만들어지는 것이라 지워도 잃는 데이터가 없다.
    IF current_cols IS NOT NULL AND current_cols <> 'name,address' THEN
        ALTER TABLE spot DROP INDEX ft_spot_search;
        SET current_cols = NULL;
    END IF;

    IF current_cols IS NULL THEN
        ALTER TABLE spot
            ADD FULLTEXT INDEX ft_spot_search (name, address) WITH PARSER ngram;
    END IF;
END$$
DELIMITER ;

CALL pngo_rebuild_fulltext_index();
DROP PROCEDURE pngo_rebuild_fulltext_index;
