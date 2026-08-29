-- 사진 EXIF의 GPS 좌표를 역지오코딩한 주소를 저장한다.
--
-- 기존 사진은 업로드 당시 주소를 조회하지 않았으므로 NULL을 허용한다.
-- 새로 업로드되는 커뮤니티 이미지와 리뷰 사진부터 애플리케이션이 값을 채운다.
-- 운영 또는 로컬 DB에 같은 컬럼을 수동으로 추가한 경우에도 안전하도록
-- information_schema에서 존재 여부를 확인한 뒤 추가한다.

DROP PROCEDURE IF EXISTS pngo_add_image_address_col;
DELIMITER $$
CREATE PROCEDURE pngo_add_image_address_col(IN p_table_name VARCHAR(64))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = p_table_name
          AND COLUMN_NAME = 'address'
    ) THEN
        SET @s = CONCAT(
            'ALTER TABLE `', p_table_name,
            '` ADD COLUMN `address` varchar(255) DEFAULT NULL'
        );
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

CALL pngo_add_image_address_col('community_images');
CALL pngo_add_image_address_col('review_photo');

DROP PROCEDURE pngo_add_image_address_col;
