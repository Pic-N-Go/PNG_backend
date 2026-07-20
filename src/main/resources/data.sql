-- 유저 데이터 (테스트용 계정)
INSERT INTO users (id, email, nickname, role, provider, provider_id, created_at, updated_at) 
VALUES (1, 'test@example.com', '테스터', 'USER', 'KAKAO', 'kakao_1234', NOW(), NOW())
ON DUPLICATE KEY UPDATE id=1;

-- 스팟 데이터 (제주도 샘플 스팟)
INSERT INTO spot (id, name, address, latitude, longitude, category, source, badge, status, bookmark_count, review_count, photogenic_score, is_active, review_average, toilet, created_at, updated_at)
VALUES 
(1, '성산일출봉', '제주특별자치도 서귀포시 성산읍 일출로 284-12', 33.4580, 126.9425, 'MOUNTAIN', 'TOUR_API', true, 'APPROVED', 0, 0, 95, true, 4.5, true, NOW(), NOW()),
(2, '함덕해수욕장', '제주특별자치도 제주시 조천읍 조함해안로 525', 33.5433, 126.6692, 'BEACH', 'TOUR_API', true, 'APPROVED', 0, 0, 90, true, 4.8, true, NOW(), NOW()),
(3, '오설록 티 뮤지엄', '제주특별자치도 서귀포시 안덕면 신화역사로 15', 33.3060, 126.2895, 'ETC', 'TOUR_API', true, 'APPROVED', 0, 0, 85, true, 4.2, true, NOW(), NOW()),
(4, '카멜리아힐', '제주특별자치도 서귀포시 안덕면 병악로 166', 33.2840, 126.3533, 'PARK', 'TOUR_API', true, 'APPROVED', 0, 0, 88, true, 4.6, true, NOW(), NOW())
ON DUPLICATE KEY UPDATE id=id;

-- 코스 데이터 (제주도 2박 3일 출사 코스)
INSERT INTO course (id, user_id, title, start_date, end_date, created_at, updated_at)
VALUES (1, 1, '제주도 2박 3일 풍경 출사', '2026-08-01', '2026-08-03', NOW(), NOW())
ON DUPLICATE KEY UPDATE id=1;

-- 코스 스팟 데이터 (1일차, 2일차)
INSERT INTO course_spot (id, course_id, spot_id, day_number, sequence_order, memo, travel_time_minutes)
VALUES 
(1, 1, 2, 1, 1, '도착 직후 함덕 바다 스냅', null), -- 1일차 첫번째: 함덕
(2, 1, 1, 1, 2, '성산일출봉 일몰 촬영', 45),   -- 1일차 두번째: 성산
(3, 1, 3, 2, 1, '녹차밭 아침햇살 촬영', null),   -- 2일차 첫번째: 오설록
(4, 1, 4, 2, 2, '동백꽃 감성 샷', 20)           -- 2일차 두번째: 카멜리아힐
ON DUPLICATE KEY UPDATE id=id;

-- 코스 체크리스트 데이터
INSERT INTO course_checklist (id, course_id, content, is_checked)
VALUES 
(1, 1, '카메라 바디 및 여분 배터리 챙기기', false),
(2, 1, '풍경용 광각 렌즈 (16-35mm)', false),
(3, 1, '삼각대 및 ND필터', false),
(4, 1, '편한 운동화 신고 가기', true)
ON DUPLICATE KEY UPDATE id=id;
