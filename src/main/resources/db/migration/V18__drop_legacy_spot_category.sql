-- spot 테이블의 레거시 category 컬럼 삭제
-- 사진테마 카테고리는 다중 카테고리 지원을 위해 spot_categories 조인 테이블(@ElementCollection)로 이전되었으며,
-- spot 테이블의 단일 category 컬럼은 더 이상 코드나 쿼리에서 참조되지 않는 레거시 잔재이다.
-- MySQL은 ALTER TABLE ... DROP COLUMN IF EXISTS 문법을 지원하지 않으므로 프로시저로 컬럼 존재 여부를 확인 후 안전하게 삭제한다.

DROP PROCEDURE IF EXISTS pngo_drop_spot_category;
DELIMITER $$
CREATE PROCEDURE pngo_drop_spot_category()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'spot'
          AND COLUMN_NAME = 'category'
    ) THEN
        ALTER TABLE `spot` DROP COLUMN `category`;
    END IF;
END$$
DELIMITER ;

CALL pngo_drop_spot_category();
DROP PROCEDURE pngo_drop_spot_category;
