-- ============================================================
-- V12: 출사알림 더미/중복 데이터 정리 및 UNIQUE 키 제약조건 추가
-- ============================================================

-- 1. 더미 유저(1~100번) 관련 알림 조건 및 알림 데이터 정리
DELETE FROM spot_alert_time_conditions WHERE spot_alert_id IN (SELECT id FROM spot_alert WHERE user_id BETWEEN 1 AND 100);
DELETE FROM spot_alert_weather_conditions WHERE spot_alert_id IN (SELECT id FROM spot_alert WHERE user_id BETWEEN 1 AND 100);
DELETE FROM spot_alert WHERE user_id BETWEEN 1 AND 100;

-- 2. 더미 유저(2~100번) 알림 설정 및 유저 삭제 (1번 메인 테스터는 코스 등이 연결되어 있어 유지)
DELETE FROM notification_setting WHERE user_id BETWEEN 2 AND 100;
DELETE FROM users WHERE id BETWEEN 2 AND 100;

-- 3. 기존 잔여 알림 조건 중복 데이터 정리 (DISTINCT 임시 테이블 활용)
CREATE TEMPORARY TABLE temp_spot_alert_time AS
SELECT DISTINCT spot_alert_id, time_condition FROM spot_alert_time_conditions;

DELETE FROM spot_alert_time_conditions;

INSERT INTO spot_alert_time_conditions (spot_alert_id, time_condition)
SELECT spot_alert_id, time_condition FROM temp_spot_alert_time;

DROP TEMPORARY TABLE temp_spot_alert_time;

CREATE TEMPORARY TABLE temp_spot_alert_weather AS
SELECT DISTINCT spot_alert_id, weather_condition FROM spot_alert_weather_conditions;

DELETE FROM spot_alert_weather_conditions;

INSERT INTO spot_alert_weather_conditions (spot_alert_id, weather_condition)
SELECT spot_alert_id, weather_condition FROM temp_spot_alert_weather;

DROP TEMPORARY TABLE temp_spot_alert_weather;

-- 4. 향후 중복 삽입 방지를 위한 복합 유니크 제약조건 추가
ALTER TABLE spot_alert_time_conditions
ADD CONSTRAINT uk_spot_alert_time UNIQUE (spot_alert_id, time_condition);

ALTER TABLE spot_alert_weather_conditions
ADD CONSTRAINT uk_spot_alert_weather UNIQUE (spot_alert_id, weather_condition);
