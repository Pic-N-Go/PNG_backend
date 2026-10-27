-- Apple 계정 탈퇴 시 토큰 폐기(가이드라인 5.1.1(v))에 필요한 refresh token 보관.
--
-- 로그인 때 받는 authorizationCode는 5분이면 만료되므로 탈퇴까지 들고 있을 수 없다.
-- 로그인 시점에 Apple과 교환해 refresh token으로 바꿔 두고, 탈퇴할 때 그걸로 폐기한다.
--
-- Apple 사용자에게만 값이 있다. 파기 배치(purgePersonalData)가 비운다.
ALTER TABLE `users` ADD COLUMN `apple_refresh_token` VARCHAR(500) NULL;
