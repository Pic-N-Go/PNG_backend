-- ============================================================
-- 콘테스트 시드 데이터 (로컬·개발 DB 전용, 수동 실행)
-- ============================================================
--
-- 평상시 회차 개설은 POST /admin/contests를 쓴다. 기간을 코드(Contest.create)에서
-- 파생시키므로 규칙이 어긋날 일이 없다.
--
-- 이 파일은 그걸로는 못 만드는 상태를 위한 것이다 — 이미 끝난 지난 회차,
-- 그리고 "지금 당장 투표 기간인 회차"처럼 시간을 거슬러야 하는 경우.
--
-- ⚠️ data.sql·spot_data.sql과 달리 기동 시 자동 실행되지 않는다.
--    application.yaml의 sql.init.data-locations에 넣지 말 것 — 재시작할 때마다 중복 생성된다.
--      mysql -u <user> -p <db> < docs/contest-seed.sql
--
-- 출품작·투표는 넣지 않는다. 출품작은 S3에 실제 사진이 있어야 하고 사용자 계정도
-- 필요해서, 앱에서 직접 출품하는 게 유일하게 말이 되는 경로다.
--
-- 날짜는 Contest.create()와 같은 규칙으로 파생한다:
--   출품 2주 → 투표 2주 → 투표 종료 다음 날 오전 9시 발표
-- 여기서 이 계산을 손으로 반복하는 건 규칙이 두 곳에 생긴다는 뜻이다.
-- 규칙이 바뀌면 Contest.create()와 이 파일을 같이 고쳐야 한다.


-- ── 어떤 phase의 "진행 중 콘테스트"를 만들지 고른다 ──────────────
--   SUBMITTING : 출품 기간
--   VOTING     : 투표 기간 (순위 변동 패널)
--   COUNTING   : 투표 마감 ~ 발표 전 (백엔드 phase는 RESULT, 결과 비공개)
--                발표가 항상 미래가 되도록 투표 종료를 12시간 전으로 당겨 둔다
--   NONE       : 진행 중 회차 없음 (예고 + 알림 신청)
SET @phase = 'SUBMITTING';


-- ── 1. 진행 중 콘테스트 (@phase = 'NONE'이면 건너뜀) ────────────
-- COUNTING은 투표 종료를 12시간 전으로 당겨 둔다. 발표(익일 09:00)가 항상 미래여야 하는데,
-- 1시간으로 잡으면 아침에 시드할 때 이미 발표 시각을 지나 ENDED로 떨어진다.
SET @submit_start = CASE @phase
        WHEN 'SUBMITTING' THEN NOW() - INTERVAL 5 DAY
        WHEN 'VOTING'     THEN NOW() - INTERVAL 20 DAY
        WHEN 'COUNTING'   THEN NOW() - INTERVAL 28 DAY - INTERVAL 12 HOUR
        ELSE NULL
    END;

INSERT INTO `contest` (
    `title`, `description`, `theme_image_url`,
    `submit_start_at`, `submit_end_at`, `vote_start_at`, `vote_end_at`, `result_open_at`,
    `max_entries_per_user`, `vote_limit`, `active`, `created_at`, `updated_at`
)
SELECT
    '골든아워',
    '해 뜨거나 지는 시간의 빛을 담아보세요',
    NULL,
    @submit_start,
    @submit_start + INTERVAL 14 DAY,
    @submit_start + INTERVAL 14 DAY,
    @submit_start + INTERVAL 28 DAY,
    TIMESTAMP(DATE(@submit_start + INTERVAL 28 DAY) + INTERVAL 1 DAY, '09:00:00'),
    3, 3, TRUE, NOW(), NOW()
WHERE @submit_start IS NOT NULL;


-- ── 2. 다음 예정 콘테스트 ────────────────────────────────────────
-- GET /contests/upcoming이 잡아가는 행. phase는 UPCOMING이라 출품 API가 막혀 있다. 진행 중 회차가 없을 때의 예고 화면과
-- 알림 신청(구독) 버튼이 이 데이터로 그려진다. 이 행을 빼면 "예고 없음" 상태가 된다.
SET @submit_start = NOW() + INTERVAL 40 DAY;

INSERT INTO `contest` (
    `title`, `description`, `theme_image_url`,
    `submit_start_at`, `submit_end_at`, `vote_start_at`, `vote_end_at`, `result_open_at`,
    `max_entries_per_user`, `vote_limit`, `active`, `created_at`, `updated_at`
) VALUES (
    '밤의 도시', '해가 진 뒤의 거리와 불빛을 담아보세요', NULL,
    @submit_start,
    @submit_start + INTERVAL 14 DAY,
    @submit_start + INTERVAL 14 DAY,
    @submit_start + INTERVAL 28 DAY,
    TIMESTAMP(DATE(@submit_start + INTERVAL 28 DAY) + INTERVAL 1 DAY, '09:00:00'),
    3, 3, TRUE, NOW(), NOW()
);


-- ── 3. 지난 콘테스트 2회차 ───────────────────────────────────────
-- result_open_at이 과거라 phase가 ENDED고, GET /contests(지난 목록)에 잡힌다.
-- 가장 최근 것이 진행중 탭 상단의 "지난 회차 수상" 배너 데이터가 된다.
-- 출품작이 없어서 우승자·표수는 비어 보인다 — 목록 레이아웃 확인용이다.
SET @submit_start = NOW() - INTERVAL 70 DAY;

INSERT INTO `contest` (
    `title`, `description`, `theme_image_url`,
    `submit_start_at`, `submit_end_at`, `vote_start_at`, `vote_end_at`, `result_open_at`,
    `max_entries_per_user`, `vote_limit`, `active`, `created_at`, `updated_at`
) VALUES (
    '비 오는 날', '젖은 도시의 색을 담아보세요', NULL,
    @submit_start,
    @submit_start + INTERVAL 14 DAY,
    @submit_start + INTERVAL 14 DAY,
    @submit_start + INTERVAL 28 DAY,
    TIMESTAMP(DATE(@submit_start + INTERVAL 28 DAY) + INTERVAL 1 DAY, '09:00:00'),
    3, 3, TRUE, NOW(), NOW()
);

SET @submit_start = NOW() - INTERVAL 100 DAY;

INSERT INTO `contest` (
    `title`, `description`, `theme_image_url`,
    `submit_start_at`, `submit_end_at`, `vote_start_at`, `vote_end_at`, `result_open_at`,
    `max_entries_per_user`, `vote_limit`, `active`, `created_at`, `updated_at`
) VALUES (
    '밤하늘', '별과 달이 있는 밤을 담아보세요', NULL,
    @submit_start,
    @submit_start + INTERVAL 14 DAY,
    @submit_start + INTERVAL 14 DAY,
    @submit_start + INTERVAL 28 DAY,
    TIMESTAMP(DATE(@submit_start + INTERVAL 28 DAY) + INTERVAL 1 DAY, '09:00:00'),
    3, 3, TRUE, NOW(), NOW()
);


-- ── 다시 돌리기 전에 지우려면 ────────────────────────────────────
-- 출품작·투표가 딸려 있으면 FK 때문에 안 지워진다. 자식부터 지운다.
--
--   DELETE FROM `contest_ranking_snapshot`;
--   DELETE FROM `contest_vote`;
--   DELETE FROM `contest_report`;
--   DELETE FROM `contest_subscription`;
--   DELETE FROM `contest_entry`;
--   DELETE FROM `contest`;
