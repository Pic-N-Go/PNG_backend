-- 스팟 카테고리 Set 전환 마이그레이션 (기존 스팟 태깅)
-- TourAPI 재조회 없음. 기존 cat3 + overview 로만 태깅. SpotCategoryTagger 규칙과 일치.
--
-- ⚠️ 옛 spot.category 컬럼(NOT NULL, DEFAULT 없음)이 남아 있는 기존 DB라면
--    이 SQL을 먼저 실행한 뒤 애플리케이션을 기동해야 한다. 그 컬럼이 남은 채로 앱을 띄우면
--    data.sql이 "Field 'category' doesn't have a default value"로 실패해 기동이 막힌다.
--    완전히 빈 신규 DB는 앱이 스키마를 만든 뒤 3) 백필만 돌리면 된다.
--
-- 재실행 안전(멱등): 1)~4)는 몇 번을 돌려도 안전하다. 5)만 1회성이며 파일 맨 끝에 있다.

-- ─────────────────────────────────────────────────────────────
-- 1) 다중 태그 테이블. ddl-auto=update도 만들지만 이 정의가 정본.
--    category는 VARCHAR — 엔티티의 @JdbcTypeCode(SqlTypes.VARCHAR)와 짝을 이룬다.
--    (Hibernate 6.2+는 @Enumerated(STRING)을 MySQL 네이티브 ENUM으로 만들고, enum 값이
--     바뀔 때마다 기동 시 `modify column ... enum(...)` DDL을 날린다. VARCHAR로 고정하면
--     그 DDL도, 스키마가 Java enum 목록에 결합되는 것도 사라진다.)
--
--    제약 이름을 엔티티(@UniqueConstraint(name="uk_spot_categories"))와 맞춘 것이 중요하다.
--    PRIMARY KEY로 선언하면 MySQL이 인덱스 이름을 PRIMARY로 붙이는데, Hibernate는 제약을
--    "이름으로" 찾으므로 uk_spot_categories를 못 찾고 똑같은 인덱스를 하나 더 만든다.
--    InnoDB는 NOT NULL UNIQUE 중 첫 번째를 클러스터드 인덱스로 승격하므로 PK를 잃지 않는다.
--
--    idx_spot_categories_category: 카테고리 필터용 역방향 인덱스.
--    uk는 (spot_id, category) 순이라 `WHERE category = ?`에 쓰이지 못한다. 이게 없으면
--    필터가 spot 전체를 훑는다. EXPLAIN 상 커버링 인덱스로 동작한다.
CREATE TABLE IF NOT EXISTS spot_categories (
    spot_id  BIGINT      NOT NULL,
    category VARCHAR(50) NOT NULL,
    CONSTRAINT uk_spot_categories UNIQUE (spot_id, category),
    KEY idx_spot_categories_category (category, spot_id),
    CONSTRAINT fk_spot_categories_spot FOREIGN KEY (spot_id) REFERENCES spot(id)
);

-- 2) 이 파일의 옛 버전(PRIMARY KEY + 별도 CREATE INDEX)으로 이미 마이그레이션한 DB 정리.
--    PK와 uk_spot_categories가 완전히 동일한 인덱스로 중복 존재하게 되므로 PK를 떨군다.
--    uk가 클러스터드 인덱스를 이어받는다. 해당 없으면 에러가 나며 무시해도 된다.
-- ALTER TABLE spot_categories DROP PRIMARY KEY;

-- ─────────────────────────────────────────────────────────────
-- 3) 백필. 전체를 하나의 트랜잭션으로 (중간 실패 시 부분 태깅 방지)
START TRANSACTION;
DELETE FROM spot_categories;

-- 3-1) 장소형 (cat3 정확 매핑)
--      A02020600(실내 테마파크/아쿠아리움)은 제외 — 야외 공원 필터 오탐. SpotCategoryTagger와 동일.
INSERT INTO spot_categories (spot_id, category) SELECT id, 'PARK'     FROM spot WHERE cat3 = 'A02020700';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'BEACH'    FROM spot WHERE cat3 = 'A01011200';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'MOUNTAIN' FROM spot WHERE cat3 IN ('A01010400','A01020200');
INSERT INTO spot_categories (spot_id, category) SELECT id, 'HANOK'    FROM spot WHERE cat3 IN ('A02010400','A02010600','A02010100');
INSERT INTO spot_categories (spot_id, category) SELECT id, 'FOREST'   FROM spot WHERE cat3 IN ('A01010600','A01010700','A01010500');
INSERT INTO spot_categories (spot_id, category) SELECT id, 'HERITAGE' FROM spot WHERE cat3 IN ('A02010700','A02010800','A02010200','A02010900');

-- 3-2) 장면형 (name/overview 키워드)
INSERT INTO spot_categories (spot_id, category) SELECT id, 'NIGHT_VIEW'
    FROM spot WHERE name LIKE '%야경%' OR overview LIKE '%야경%' OR name LIKE '%전망대%' OR overview LIKE '%전망대%';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'CAFE'
    FROM spot WHERE name LIKE '%카페%' OR overview LIKE '%카페%' OR name LIKE '%커피%' OR overview LIKE '%커피%';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'SUNRISE_SUNSET'
    FROM spot WHERE name LIKE '%일출%' OR overview LIKE '%일출%' OR name LIKE '%일몰%' OR overview LIKE '%일몰%' OR name LIKE '%노을%' OR overview LIKE '%노을%';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'FLOWER'
    FROM spot WHERE name LIKE '%벚꽃%' OR overview LIKE '%벚꽃%' OR name LIKE '%단풍%' OR overview LIKE '%단풍%' OR name LIKE '%유채%' OR overview LIKE '%유채%';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'FESTIVAL'
    FROM spot WHERE name LIKE '%축제%' OR overview LIKE '%축제%' OR name LIKE '%페스티벌%' OR overview LIKE '%페스티벌%';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'MILKY_WAY'
    FROM spot WHERE name LIKE '%은하수%' OR overview LIKE '%은하수%';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'CITY'
    FROM spot WHERE name LIKE '%골목%' OR overview LIKE '%골목%' OR name LIKE '%번화가%' OR overview LIKE '%번화가%'
              OR name LIKE '%야시장%' OR overview LIKE '%야시장%' OR name LIKE '%로데오%' OR overview LIKE '%로데오%';

-- 3-3) 아무 태그도 없으면 ETC (모든 스팟이 최소 1개 태그를 갖도록 보장)
INSERT INTO spot_categories (spot_id, category)
SELECT s.id, 'ETC' FROM spot s
WHERE NOT EXISTS (SELECT 1 FROM spot_categories sc WHERE sc.spot_id = s.id);
COMMIT;

-- ─────────────────────────────────────────────────────────────
-- 4) 유저 관심테마 정리. User.spotCategories도 같은 SpotCategory를 @Enumerated(STRING)으로 쓴다.

-- 4-1) 컬럼을 VARCHAR로 먼저 전환. 이 테이블은 앱(ddl-auto)이 만들어서 네이티브 ENUM 컬럼이다.
--      ddl-auto=update가 기동마다 `modify column ... enum(...)`을 날려 목록을 맞춰주긴 하지만
--      (실측 확인), 그 DDL 자체가 불필요하므로 VARCHAR로 고정한다.
--      이후 enum 값을 추가·삭제해도 DB 스키마를 신경 쓸 필요가 없어진다.
--      ※ 4-2보다 먼저 와야 한다. ENUM 상태에서 삭제된 값의 행은 ''(빈 문자열)로 변환돼
--        `IN ('PORTRAIT','PET')`에 안 걸리고 살아남을 수 있다.
ALTER TABLE user_spot_categories MODIFY category VARCHAR(50);

-- 4-2) 현재 SpotCategory에 없는 값 전부 제거 (PORTRAIT/PET + ENUM 잔재인 '' 포함).
--      남아 있으면 해당 유저 조회 시 enum 역직렬화로 터진다.
DELETE FROM user_spot_categories
WHERE category NOT IN ('PARK','BEACH','MOUNTAIN','HANOK','FOREST','HERITAGE',
                       'CAFE','CITY','NIGHT_VIEW','FESTIVAL','FLOWER',
                       'SUNRISE_SUNSET','MILKY_WAY','ETC');

-- ─────────────────────────────────────────────────────────────
-- 5) 옛 단일 category 컬럼 제거. ⚠️ 1회성(재실행 시 "컬럼 없음" 에러 정상 — MySQL은 DROP IF EXISTS 미지원).
--    이 컬럼은 NOT NULL + DEFAULT 없음이라, 남아 있으면 새 코드의 data.sql / 스팟 등록 INSERT가
--    "Field 'category' doesn't have a default value"로 실패한다. 반드시 제거할 것.
--    손실 0: TourAPI 스팟은 SpotUpsertService가 전건 SpotCategory.ETC로 하드코딩해 넣던 값이라 실질 정보가 없음
--    (cat3 → category 매핑 로직은 존재한 적 없음). 실제 값을 가진 건 data.sql 데모 스팟 4건뿐이고
--    해당 4건은 data.sql의 spot_categories INSERT로 이관 완료.
ALTER TABLE spot DROP COLUMN category;

-- ─────────────────────────────────────────────────────────────
-- 6) 검증 (실행 후 확인용)
-- SELECT COUNT(*) FROM spot;                              -- 참고: 4465 (2026-07 기준)
-- SELECT COUNT(DISTINCT spot_id) FROM spot_categories;    -- 위와 같아야 함 (태그 0개 스팟 없음)
-- SELECT category, COUNT(*) FROM spot_categories GROUP BY 1 ORDER BY 2 DESC;
-- SHOW COLUMNS FROM spot LIKE 'category';                 -- Empty set 이어야 함
-- SHOW CREATE TABLE user_spot_categories;                 -- category varchar(50) 이어야 함
