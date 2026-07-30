-- 1. 기존 프로시저가 있다면 삭제
DROP PROCEDURE IF EXISTS InsertDummyNotificationSettings;

-- 2. 1,000건 더미 생성 프로시저 정의
DELIMITER //
CREATE PROCEDURE InsertDummyNotificationSettings()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 1000 DO
            INSERT INTO notification_setting (
                user_id,
                fcm_token,
                is_spot_alert_push_enabled,
                is_golden_hour_push_enabled,
                is_community_push_enabled,
                is_dnd_enabled,
                dnd_start_time,
                dnd_end_time,
                created_at,
                updated_at
            )
            VALUES (
                       i,
                       CONCAT('dummy_fcm_token_', i),
                       TRUE,  -- 출사알림 수신 동의
                       TRUE,  -- 골든아워 수신 동의
                       TRUE,  -- 커뮤니티 수신 동의
                       FALSE, -- DND 꺼짐
                       '23:00:00',
                       '07:00:00',
                       NOW(),
                       NOW()
                   )
            ON DUPLICATE KEY UPDATE fcm_token = VALUES(fcm_token);

            SET i = i + 1;
        END WHILE;
END //
DELIMITER ;

-- 3. 프로시저 실행 (1,000건 생성)
CALL InsertDummyNotificationSettings();

-- 4. 생성 완료 후 프로시저 정리
DROP PROCEDURE InsertDummyNotificationSettings;