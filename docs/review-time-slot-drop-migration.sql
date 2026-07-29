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

-- 적용 현황 (마이그레이션 도구가 없어 수동 관리. 적용하면 이 표를 갱신할 것)
--                            (2) DROP    (3) INDEX
--   로컬 (프론트 작성자)        [x] 07-28   [ ]
--   로컬 (박예은)               [ ]         [ ]
--   개발                        [ ]         [ ]
--   운영                        [ ]         [ ]
