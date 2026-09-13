-- 콘테스트 시작 알림 발송 여부 플래그 추가
ALTER TABLE contest
    ADD COLUMN start_notification_sent BOOLEAN NOT NULL DEFAULT FALSE;
