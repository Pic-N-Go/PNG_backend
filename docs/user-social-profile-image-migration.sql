-- 프로필 사진 컬럼 분리 (users.social_profile_image_url 추가)
--
-- ⚠️ 반드시 새 버전 애플리케이션을 배포하기 "전에" 실행해야 한다.
--
--    운영은 ddl-auto=validate 라서(application-prod.yaml) Hibernate가 엔티티와
--    실제 테이블을 대조한 뒤, 없는 컬럼/테이블이 있으면 기동을 거부한다.
--    이 마이그레이션 없이 배포하면 앱이 아예 뜨지 않는다:
--      Schema-validation: missing column [social_profile_image_url] in table [users]
--
-- 왜 나누는가:
--   한 칸(profile_image_url)에 카카오가 준 URL과 사용자가 올린 S3 objectKey를 섞어 쓰면,
--   사용자가 사진을 올리는 순간 카카오 URL이 덮여 사라진다. 그러면 올린 사진을 지웠을 때
--   되돌릴 원본이 없다(카카오 액세스 토큰을 저장하지 않아 다시 물어볼 수도 없다).
--
--   분리 후:
--     profile_image_url        = 사용자가 올린 것 (S3 objectKey). 없으면 NULL
--     social_profile_image_url = 소셜에서 받은 것 (URL). 로그인마다 갱신
--   표시값은 "올린 것 ?? 소셜 것"이라, 올린 사진을 지우면 카카오 사진으로 되돌아간다.
--
-- 적용:
--   mysql -u root -p --default-character-set=utf8mb4 picngo -e "source docs/user-social-profile-image-migration.sql"
--
--   EC2(도커 컨테이너로 MySQL을 띄운 경우):
--   docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo \
--     < docs/user-social-profile-image-migration.sql
--
-- 재실행 안전(멱등): 이미 있으면 건너뛰고, 백필도 조건이 남은 행에만 적용된다.

-- 1. social_profile_image_url 추가 -------------------------------------------

SET @ddl := (
  SELECT IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'users'
             AND COLUMN_NAME = 'social_profile_image_url'),
    'SELECT ''users.social_profile_image_url 이미 존재 - 건너뜀''',
    'ALTER TABLE users ADD COLUMN social_profile_image_url VARCHAR(500) NULL'
  )
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 기존 데이터 이전 -------------------------------------------------------
-- 지금까지 소셜 사진이 profile_image_url에 들어 있었다. http로 시작하면 외부 URL이므로
-- (S3 objectKey는 'profile/...' 형태라 http로 시작하지 않는다) 소셜 칸으로 옮기고 원래 칸은 비운다.
-- 그래야 "올린 사진 없음" 상태가 되어 삭제·업로드 판정이 맞는다.
--
-- ⚠️ 전제 확인: "http로 시작하면 카카오 URL"이 항상 참은 아니다. 분리 전에는
--    UserProfileUpdateRequest.profileImageUrl로 클라이언트가 보낸 임의 문자열을 그대로
--    저장했으므로, LOCAL 계정에도 http 값이 들어가 있을 수 있다. 그 값이 소셜 칸으로 옮겨지면
--    LOCAL은 재로그인 갱신 대상이 아니고 hasUploadedProfileImage=false라
--    "사용자가 지울 수도 없는 사진"이 된다.
--
--    아래가 0건이어야 그대로 진행할 수 있다. 있으면 개별 판단이 필요하다
--    (대개 그냥 NULL로 비우는 게 맞다 — 만료된 URL일 가능성이 높다).
--    개발 DB 기준 0건이었다(2026-08-19 확인).
--
--    확인 쿼리 결과를 눈으로 볼 거라 믿을 수 없다 — 배포 절차는 파일을 통째로 파이프로
--    밀어넣게 되어 있어(docs/deployment-checklist.md) 출력이 그냥 스크롤되어 지나간다.
--    그래서 0건이 아니면 아래 백필 UPDATE 전에 스크립트를 세운다.

SELECT COUNT(*) AS local_http_profile_image
FROM users WHERE profile_image_url LIKE 'http%' AND provider = 'LOCAL';

DROP PROCEDURE IF EXISTS assert_no_local_http_profile_image;
DELIMITER $$
CREATE PROCEDURE assert_no_local_http_profile_image()
BEGIN
    DECLARE local_http INT;

    SELECT COUNT(*) INTO local_http
    FROM users WHERE profile_image_url LIKE 'http%' AND provider = 'LOCAL';

    IF local_http > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT =
            'LOCAL 계정에 http profile_image_url이 남아 있어 백필을 중단했다. 위 확인 쿼리의 행을 먼저 정리(대개 NULL)한 뒤 다시 실행할 것.';
    END IF;
END$$
DELIMITER ;

CALL assert_no_local_http_profile_image();
DROP PROCEDURE assert_no_local_http_profile_image;

UPDATE users
SET social_profile_image_url = profile_image_url,
    profile_image_url = NULL
WHERE profile_image_url LIKE 'http%'
  AND social_profile_image_url IS NULL;

-- 3. 검증 --------------------------------------------------------------------
-- 컬럼이 생겼는지, 그리고 profile_image_url에 http로 시작하는 값이 남아 있지 않은지 본다.
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'social_profile_image_url';

SELECT COUNT(*) AS remaining_http_in_profile_image_url
FROM users WHERE profile_image_url LIKE 'http%';
