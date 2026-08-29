-- 출품작에 촬영 시각(EXIF DateTimeOriginal)을 저장한다.
--
-- 투표 카드가 "광안리 · 05:32"처럼 장소와 촬영 시각을 함께 보여준다.
-- 골든아워가 테마인 콘테스트에서는 이 시각이 작품 정보의 핵심이라 출품 시각(created_at)으로 대체할 수 없다.
--
-- 존재검사 없이 그냥 ADD COLUMN 한다 — contest_entry 자체가 V6에서 생긴 테이블이라
-- 이 컬럼이 미리 있는 DB는 존재할 수 없다(V5·V8의 드리프트 상황과 다르다).
-- EXIF가 없는 사진도 있으므로 NULL 허용.

ALTER TABLE `contest_entry` ADD COLUMN `shot_at` datetime(6) DEFAULT NULL;
