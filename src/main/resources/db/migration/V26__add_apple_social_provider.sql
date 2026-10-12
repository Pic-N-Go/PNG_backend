-- Apple 로그인 추가(SocialProvider.APPLE).
--
-- users.provider는 varchar가 아니라 네이티브 ENUM이다. 값을 늘리지 않으면 Apple 가입 시
-- INSERT가 "Data truncated for column 'provider'"로 실패한다.
--
-- 값 순서는 Hibernate가 만드는 DDL(이름 알파벳순)과 맞춘다. MODIFY는 문자열 값 기준으로
-- 기존 행을 옮기므로 순서가 바뀌어도 데이터는 그대로다. 같은 정의로 다시 실행해도 안전하다.
ALTER TABLE `users` MODIFY `provider` enum('APPLE','KAKAO','LOCAL') NOT NULL;
