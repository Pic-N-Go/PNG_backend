-- 관심테마 기반 추천(SpotRepository.findRecommendedSpots)의 조인 인덱스.
--
-- 조인 조건은 uc.category = sc.category 인데, spot_categories에는
-- UNIQUE KEY (spot_id, category)만 있어 category가 선행 컬럼이 아니다.
-- 그래서 유저 관심테마 행마다 spot_categories를 풀스캔한다.
-- (category, spot_id) 순서면 조인 조건으로 탐색하고 spot_id까지 인덱스에서 읽는다(커버링).
--
-- 인덱스 추가는 ddl-auto: validate가 검사하지 않으므로 기동에 영향 없다.

CREATE INDEX `idx_spot_categories_category` ON `spot_categories` (`category`, `spot_id`);
