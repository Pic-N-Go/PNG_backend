-- Spot 엔티티에 추가된 하이브리드 축제 및 관광타입 필드 (contentTypeId, eventStartDate, eventEndDate)
--
-- content_type_id: 12(관광지), 14(문화시설), 15(축제/행사), 39(카페/음식점) 등 공공데이터 관광타입 ID
-- event_start_date: 축제/행사 시작일자
-- event_end_date: 축제/행사 종료일자

DROP PROCEDURE IF EXISTS pngo_add_spot_col;
DELIMITER $$
CREATE PROCEDURE pngo_add_spot_col(IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spot' AND COLUMN_NAME = col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `spot` ADD COLUMN ', ddl);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL pngo_add_spot_col('content_type_id', '`content_type_id` INT NULL');
CALL pngo_add_spot_col('event_start_date', '`event_start_date` DATE NULL');
CALL pngo_add_spot_col('event_end_date', '`event_end_date` DATE NULL');

DROP PROCEDURE IF EXISTS pngo_add_spot_col;
