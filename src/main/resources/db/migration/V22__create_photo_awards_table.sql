-- V22: 한국관광공사 사진공모전 수상작 메타데이터 테이블 및 스팟 출처(PHOTO_CONTEST) 추가

-- 1) spot 테이블의 source enum에 PHOTO_CONTEST 추가
ALTER TABLE `spot`
    MODIFY COLUMN `source` enum('TOUR_API','USER','PHOTO_CONTEST') NOT NULL COMMENT '데이터 출처. TOUR_API | USER | PHOTO_CONTEST';

-- 2) photo_awards 테이블 생성
CREATE TABLE `photo_awards` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '사진공모전 수상작 고유 ID',
  `content_id` varchar(100) NOT NULL COMMENT '한국관광공사 공모전 수상작 contentId',
  `title` varchar(200) NOT NULL COMMENT '작품명',
  `photographer` varchar(100) DEFAULT NULL COMMENT '촬영자 / 작가명',
  `award_year_month` varchar(10) DEFAULT NULL COMMENT '촬영 연월 (YYYYMM)',
  `award_name` varchar(100) DEFAULT NULL COMMENT '수상 내역 (예: 대상, 금상, 디지털카메라 부문 [은상])',
  `location_name` varchar(255) DEFAULT NULL COMMENT '촬영 장소명 (koFilmst)',
  `image_url` varchar(500) DEFAULT NULL COMMENT '원본 이미지 URL',
  `thumbnail_url` varchar(500) DEFAULT NULL COMMENT '썸네일 이미지 URL',
  `copyright_type` varchar(50) DEFAULT NULL COMMENT '저작권 유형 (예: Type1 - 공공누리 제1유형)',
  `ldong_regn_cd` int DEFAULT NULL COMMENT '법정동 시도 코드',
  `spot_id` bigint NOT NULL COMMENT '연계된 스팟 FK',
  `created_at` datetime(6) NOT NULL COMMENT '등록 일시',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_photo_award_content_id` (`content_id`),
  KEY `idx_photo_award_spot_id` (`spot_id`),
  CONSTRAINT `fk_photo_awards_spot` FOREIGN KEY (`spot_id`) REFERENCES `spot` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
