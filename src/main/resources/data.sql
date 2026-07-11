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
-- 이 파일은 spring.sql.init.mode=always 설정으로 서버 시작 시 자동 실행되며,
-- name 기준 WHERE NOT EXISTS 조건으로 중복 삽입을 방지합니다.
-- 새 시즌 이벤트는 행을 추가하면 다음 서버 재시작 시 자동 반영됩니다.
-- ============================================================

INSERT INTO season_event (name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active)
SELECT v.name, v.month_day_start, v.month_day_peak_start, v.month_day_peak_end, v.month_day_end, v.region, v.max_score, v.is_active
FROM (VALUES
    ('벚꽃',        '03-15', '03-28', '04-10', '04-20', NULL,   15, true),
    ('유채꽃',      '03-01', '03-10', '03-25', '04-05', '제주', 15, true),
    ('여름 해변',   '07-01', '07-15', '08-15', '08-31', NULL,   10, true),
    ('억새',        '09-20', '10-05', '10-20', '11-01', NULL,    8, true),
    ('단풍',        '10-01', '10-20', '11-05', '11-15', NULL,   15, true),
    ('초겨울 설경', '12-01', '12-15', '12-31', '12-31', NULL,   10, true),
    ('한겨울 설경', '01-01', '01-10', '02-10', '02-28', NULL,   10, true)
) AS v(name, month_day_start, month_day_peak_start, month_day_peak_end, month_day_end, region, max_score, is_active)
WHERE NOT EXISTS (SELECT 1 FROM season_event WHERE name = v.name);
