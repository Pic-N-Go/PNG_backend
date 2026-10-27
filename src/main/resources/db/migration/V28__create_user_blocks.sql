-- 사용자 차단. App Store 가이드라인 1.2는 UGC·소셜 앱에 "악성 사용자를 차단하는 수단"을
-- 요구한다. 신고만으로는 충족되지 않아 심사에서 거절 사유가 된다.
--
-- 차단은 단방향이다(A가 B를 차단해도 B는 A를 차단한 게 아니다). 같은 쌍을 두 번 넣지 못하도록
-- unique를 건다 — 중복 차단 요청이 와도 조용히 무시하면 되도록.
CREATE TABLE `user_blocks` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT,
    `blocker_id` BIGINT      NOT NULL,
    `blocked_id` BIGINT      NOT NULL,
    `created_at` DATETIME(6) NOT NULL,
    `updated_at` DATETIME(6) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_blocker_blocked` (`blocker_id`, `blocked_id`),
    -- 피드 조회마다 "내가 차단한 사람들"을 읽으므로 blocker_id 단독 조회가 잦다.
    -- 위 복합 unique의 선두 컬럼이라 별도 인덱스는 두지 않는다.
    CONSTRAINT `fk_user_blocks_blocker` FOREIGN KEY (`blocker_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_blocks_blocked` FOREIGN KEY (`blocked_id`) REFERENCES `users` (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
