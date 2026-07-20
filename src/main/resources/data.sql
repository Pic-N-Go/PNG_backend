-- ============================================================
-- 시즌 이벤트 초기 데이터
-- ============================================================
-- 포토제닉 지수 계산 시 시즌 보너스 점수에 사용됩니다.
-- 각 이벤트는 시작일~종료일(MonthDay 형식, MM-dd) 기간 동안 활성화되며,
-- 피크 구간(peak_start~peak_end)에는 max_score 만점, 그 외 절반 점수를 부여합니다.
--
-- region: 지역 한정 이벤트인 경우 도시명 (예: '제주'), 전국은 NULL
-- is_active: false 로 변경해 특정 시즌을 비활성화할 수 있습니다.
--
-- 주의: 겨울 설경처럼 연도 경계를 넘는 이벤트는 두 개 행으로 분리되어 있습니다.
--       (MonthDay 비교가 연도 경계를 지원하지 않으므로)
--
-- eligible_cat3: 이 시즌 보너스가 적용되는 TourAPI cat3 코드(콤마 구분). NULL이면 카테고리 무관 전국 전체 적용.
--                코드는 ChecklistMapper의 주석이 아니라 실제 spot 테이블 데이터를 샘플링해 검증한 값입니다
--                (ChecklistMapper 주석은 실제와 다른 것으로 확인됨 — 예: A01011200이 진짜 "해변", A02010700은 "해수욕장"이 아니라 "유적지").
--                자연 핵심 세트(국립/도립/군립공원, 산, 생태공원, 휴양림, 수목원):
--                A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700
--
-- 이 파일은 spring.sql.init.mode=always 설정으로 서버 시작 시 자동 실행됩니다.
-- (name, region) 기준 중복 방지 — 새 시즌 이벤트는 행을 추가하면 다음 재시작 시 자동 반영됩니다.
--
-- [1회 마이그레이션] season_event 유니크 제약이 name 단독 -> (name, region) 복합으로 변경됨.
-- ddl-auto=update는 기존 name 단독 제약을 자동으로 안 지우므로, 이 변경 이전에 만들어진 DB는
-- 아래를 한 번 실행 후 재기동해야 지역별 동명 이벤트가 동작합니다 (season_event는 이 파일로 100% 재생성됨):
--     DROP TABLE season_event;
-- ============================================================

INSERT INTO season_event (name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active, eligible_cat3)
SELECT '벚꽃', '03-15', '03-28', '04-10', '04-20', NULL, 15, 1,
       'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A01010900,A02020700,A02010100,A02010600' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM season_event WHERE name = '벚꽃' AND region IS NULL);

INSERT INTO season_event (name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active, eligible_cat3)
SELECT '유채꽃', '03-01', '03-10', '03-25', '04-05', '제주', 15, 1,
       'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A02020700' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM season_event WHERE name = '유채꽃' AND region = '제주');

INSERT INTO season_event (name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active, eligible_cat3)
SELECT '여름 해변', '07-01', '07-15', '08-15', '08-31', NULL, 15, 1,
       'A01011200,A01011100' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM season_event WHERE name = '여름 해변' AND region IS NULL);

INSERT INTO season_event (name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active, eligible_cat3)
SELECT '억새', '09-20', '10-05', '10-20', '11-01', NULL, 15, 1,
       'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM season_event WHERE name = '억새' AND region IS NULL);

INSERT INTO season_event (name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active, eligible_cat3)
SELECT '단풍', '10-01', '10-20', '11-05', '11-15', NULL, 15, 1,
       'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A01010800,A01010900,A02020700,A02010100,A02010800,A02010600' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM season_event WHERE name = '단풍' AND region IS NULL);

INSERT INTO season_event (name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active, eligible_cat3)
SELECT '초겨울 설경', '12-01', '12-15', '12-31', '12-31', NULL, 15, 1,
       'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A01010800,A01010900,A02020700,A02010100,A02010800,A02010600' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM season_event WHERE name = '초겨울 설경' AND region IS NULL);

INSERT INTO season_event (name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active, eligible_cat3)
SELECT '한겨울 설경', '01-01', '01-10', '02-10', '02-28', NULL, 15, 1,
       'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A01010800,A01010900,A02020700,A02010100,A02010800,A02010600' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM season_event WHERE name = '한겨울 설경' AND region IS NULL);

-- 시즌 배점은 포토제닉 총점(80점) 산정 시 고정 15점 배점 — 기존 값이 다르면 여기서 강제 통일
-- (INSERT는 NOT EXISTS 가드라 이미 생성된 row엔 안 먹으므로 별도 UPDATE 필요)
UPDATE season_event SET max_score = 15 WHERE max_score IS NULL OR max_score <> 15;

-- eligible_cat3도 동일한 이유로 기존 row엔 INSERT가 안 먹으므로 이름 기준 UPDATE로 반영
-- ponytail: 아래 UPDATE는 name 단독 기준 — 현재 시드엔 동명 중복이 없어 안전. 지역별 동명 이벤트(예: 벚꽃-제주)를 추가하면 region 조건도 함께 걸 것.
UPDATE season_event SET eligible_cat3 = 'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A01010900,A02020700,A02010100,A02010600' WHERE name = '벚꽃';
UPDATE season_event SET eligible_cat3 = 'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A02020700' WHERE name = '유채꽃';
UPDATE season_event SET eligible_cat3 = 'A01011200,A01011100' WHERE name = '여름 해변';
UPDATE season_event SET eligible_cat3 = 'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600' WHERE name = '억새';
UPDATE season_event SET eligible_cat3 = 'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A01010800,A01010900,A02020700,A02010100,A02010800,A02010600' WHERE name = '단풍';
UPDATE season_event SET eligible_cat3 = 'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A01010800,A01010900,A02020700,A02010100,A02010800,A02010600' WHERE name = '초겨울 설경';
UPDATE season_event SET eligible_cat3 = 'A01010100,A01010200,A01010300,A01010400,A01010500,A01010600,A01010700,A01010800,A01010900,A02020700,A02010100,A02010800,A02010600' WHERE name = '한겨울 설경';

-- 개명 전 잔존 row 정리 (예: '벚꽃 시즌' -> '벚꽃') — NOT EXISTS 가드는 새 이름 기준이라 옛 이름 row가 안 지워지고 남아있었음
DELETE FROM season_event WHERE name IN ('벚꽃 시즌', '단풍 시즌', '설경 시즌');
