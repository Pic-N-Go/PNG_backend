-- 스팟 카테고리 Set 전환 마이그레이션 (기존 스팟 태깅)
-- TourAPI 재조회 없음. 기존 cat3 + overview 로만 태깅. SpotCategoryTagger 규칙과 일치.
-- 재실행 안전(멱등): 매번 spot_categories 비우고 다시 채움.

-- 0) 다중 태그 테이블. ddl-auto=update가 만들지만 수동 실행도 대비.
CREATE TABLE IF NOT EXISTS spot_categories (
    spot_id  BIGINT      NOT NULL,
    category VARCHAR(50) NOT NULL,
    PRIMARY KEY (spot_id, category),
    CONSTRAINT fk_spot_categories_spot FOREIGN KEY (spot_id) REFERENCES spot(id)
);

DELETE FROM spot_categories;

-- 1) 장소형 (cat3 정확 매핑)
INSERT INTO spot_categories (spot_id, category) SELECT id, 'PARK'     FROM spot WHERE cat3 IN ('A02020700','A02020600');
INSERT INTO spot_categories (spot_id, category) SELECT id, 'BEACH'    FROM spot WHERE cat3 = 'A01011200';
INSERT INTO spot_categories (spot_id, category) SELECT id, 'MOUNTAIN' FROM spot WHERE cat3 IN ('A01010400','A01020200');
INSERT INTO spot_categories (spot_id, category) SELECT id, 'HANOK'    FROM spot WHERE cat3 IN ('A02010400','A02010600','A02010100');
INSERT INTO spot_categories (spot_id, category) SELECT id, 'FOREST'   FROM spot WHERE cat3 IN ('A01010600','A01010700','A01010500');
INSERT INTO spot_categories (spot_id, category) SELECT id, 'HERITAGE' FROM spot WHERE cat3 IN ('A02010700','A02010800','A02010200','A02010900');

-- 2) 장면형 (name/overview 키워드)
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

-- 3) 아무 태그도 없으면 ETC
INSERT INTO spot_categories (spot_id, category)
SELECT s.id, 'ETC' FROM spot s
WHERE NOT EXISTS (SELECT 1 FROM spot_categories sc WHERE sc.spot_id = s.id);

-- 4) 옛 단일 category 컬럼 제거. ⚠️ 1회성(재실행 시 "컬럼 없음" 에러 정상 — MySQL은 DROP IF EXISTS 미지원).
--    손실 0: category 값 = LEFT(cat3,3)로 4,465건 전건 일치 확인됨 → 필요 시 cat3에서 복원 가능.
ALTER TABLE spot DROP COLUMN category;
