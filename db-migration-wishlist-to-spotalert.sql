-- =====================================================================
-- Wishlist → SpotAlert 리네임: 개발/운영 DB 마이그레이션
-- ---------------------------------------------------------------------
-- ddl-auto: update 는 컬럼/테이블을 "추가"만 하고 옛 것을 지우지 않으므로,
-- 앱 기동 시 새 객체(spot_alert*, is_spot_alert_push_enabled)는 자동 생성되지만
-- 옛 객체(wishlist*, is_wishlist_push_enabled)가 orphan 으로 남는다.
-- 특히 is_wishlist_push_enabled(NOT NULL, default 없음)가 남아있으면
-- data.sql 의 notification_setting INSERT 가 실패한다.
--
-- 실행 순서: 앱을 한 번 기동해 새 객체가 생성된 뒤(또는 아래처럼 직접 옮긴 뒤),
--            이 스크립트로 옛 객체를 제거한다.
-- 운영 DB라면 아래 "데이터 이관" 블록으로 기존 데이터를 먼저 옮길 것.
-- =====================================================================

-- (선택) 운영 데이터 이관 — 개발 DB는 data.sql 이 매 기동마다 재시딩하므로 불필요.
-- INSERT INTO spot_alert (id, user_id, spot_id, memo, air_quality_condition, alert_timing_days, is_active, created_at, updated_at)
--     SELECT id, user_id, spot_id, memo, air_quality_condition, alert_timing_days, is_active, created_at, updated_at FROM wishlist;
-- INSERT INTO spot_alert_time_conditions (spot_alert_id, time_condition)
--     SELECT wishlist_id, time_condition FROM wishlist_time_conditions;
-- INSERT INTO spot_alert_weather_conditions (spot_alert_id, weather_condition)
--     SELECT wishlist_id, weather_condition FROM wishlist_weather_conditions;

-- notification_setting: 옛 알림 토글 컬럼 값 이관 후 제거
UPDATE notification_setting SET is_spot_alert_push_enabled = is_wishlist_push_enabled;
ALTER TABLE notification_setting DROP COLUMN is_wishlist_push_enabled;

-- orphan 테이블 제거 (자식 컬렉션 테이블 먼저)
DROP TABLE IF EXISTS wishlist_time_conditions;
DROP TABLE IF EXISTS wishlist_weather_conditions;
DROP TABLE IF EXISTS wishlist;
