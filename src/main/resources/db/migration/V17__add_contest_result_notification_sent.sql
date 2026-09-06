-- 콘테스트 결과 발표 알림 발송 여부 플래그 추가
ALTER TABLE contest
    ADD COLUMN result_notification_sent BOOLEAN NOT NULL DEFAULT FALSE;
