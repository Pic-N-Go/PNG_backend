-- review 테이블 수동 마이그레이션 (2026-07-29)
--   (2) time_slot 죽은 컬럼 제거 — timeSlot -> timePeriod 이름 변경 당시
--       ddl-auto: update가 옛 컬럼을 지우지 않아 남은 것. 엔티티 매핑 없음, 코드 참조 0건.
--   (3) 내 리뷰 목록 조회용 user_id 인덱스
--
-- ⚠️ 파일을 통째로 실행하지 말 것 (`mysql < 이파일` 금지).
--    (1)의 결과가 0인지 눈으로 확인한 뒤 (2)를 실행한다. (2)는 되돌릴 수 없다.
--    (2)를 이미 적용한 환경에서 파일을 통째로 실행하면 (2)에서 에러 1091로 중단되어
--    (3)이 실행되지 않는다. 아래 체크리스트를 보고 남은 것만 개별 실행할 것.

-- 1) 삭제 전 확인 — 반드시 0이어야 한다 (0이 아니면 중단하고 원인 확인)
SELECT COUNT(time_slot) AS non_null_count FROM review;

-- 2) 죽은 컬럼 삭제 (1)이 0일 때만
ALTER TABLE review DROP COLUMN time_slot;

-- 3) 내 리뷰 목록 조회용 인덱스
--    user_id는 FK가 아니라 MySQL 자동 인덱스가 없어 풀스캔이 된다.
--    엔티티에도 @Index를 걸어 뒀지만(ddl-auto: update가 만들어 줄 것으로 기대) 환경별로
--    확인 가능하게 여기에도 남긴다. MySQL 8에서 ADD INDEX는 온라인 작업이라 운영도 안전하다.
--    MySQL은 CREATE INDEX IF NOT EXISTS를 지원하지 않는다 —
--    이미 있으면 에러 1061 "Duplicate key name"이 나는데 그건 무시해도 된다.
CREATE INDEX idx_review_user_id ON review (user_id);

-- 4) 스팟당 1인 1리뷰 DB 제약
--    앱 레벨 검사(existsBySpotIdAndUserId)는 조회와 삽입 사이가 원자적이지 않아
--    동시 요청 두 건이 모두 통과할 수 있다. 엔티티에도 @UniqueConstraint를 걸었지만
--    ddl-auto: update가 기존 테이블에 유니크 제약을 안정적으로 추가하지 않으므로 직접 적용한다.
--    ⚠️ 중복 행이 이미 있으면 ALTER가 실패한다. 먼저 아래 조회로 확인하고 정리할 것.
SELECT spot_id, user_id, COUNT(*) AS cnt FROM review
 GROUP BY spot_id, user_id HAVING COUNT(*) > 1;

ALTER TABLE review ADD CONSTRAINT uk_review_spot_user UNIQUE (spot_id, user_id);

-- 적용 현황 (마이그레이션 도구가 없어 수동 관리. 적용하면 이 표를 갱신할 것)
--                            (2) DROP      (3) INDEX   (4) UNIQUE
--   로컬 (프론트 작성자)        [x] 07-28     [ ]         [ ]
--   로컬 (박예은)               [-] 해당없음  [x] 07-29   [x] 07-29
--   개발                        [ ]           [ ]         [ ]
--   운영                        [ ]           [ ]         [ ]
--
--   [-] 해당없음: 이 환경에는 time_slot 컬럼이 애초에 없었다(엔티티 변경 시점 이후 생성된 DB).
--   (4) UNIQUE 적용 시 로컬 리뷰 데이터 31건이 중복(spot 1에 30건)이라 전량 삭제 후 적용했다.
--   개발/운영은 실사용 데이터일 수 있으니 아래 조회로 중복을 확인하고 남길 행을 판단할 것.
