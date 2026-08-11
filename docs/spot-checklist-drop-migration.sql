-- 스팟 체크리스트 테이블 수동 마이그레이션 (2026-08-11)
--   스팟 상세의 촬영 체크리스트 UI가 코스 화면으로 통합되면서 스팟 쪽 API/엔티티를 전부 제거했다.
--   ddl-auto는 테이블을 지우지 않으므로(dev: update, prod: validate) 두 테이블이 고아로 남는다.
--   엔티티 매핑 없음, 코드 참조 0건. 남겨둬도 앱 동작에는 영향이 없다 — 스키마 정리 목적이다.
--
-- ⚠️ 실행 순서: 애플리케이션 배포가 끝난 뒤에 실행할 것.
--    먼저 DROP하면 구 코드가 여전히 두 엔티티를 매핑하고 있어 운영(ddl-auto: validate) 부팅이 실패한다.
--
-- ⚠️ 파일을 통째로 실행하지 말 것 (`mysql < 이파일` 금지).
--    (1)로 남은 데이터를 확인하고, 버려도 되는지 판단한 뒤 (2)를 실행한다. (2)는 되돌릴 수 없다.

-- 1) 삭제 전 확인 — 사용자가 직접 추가한 항목과 기본 항목 숨김 기록이 몇 건 남아 있는지 본다.
--    프론트에서 접근 경로가 이미 없어진 데이터지만, 건수를 눈으로 보고 판단할 것.
SELECT COUNT(*) AS user_item_count FROM checklist_item;
SELECT COUNT(*) AS hidden_default_count FROM hidden_checklist_default;

-- 1-1) 버리기 아까우면 백업 (선택). 백업 테이블은 엔티티가 없으니 validate에 걸리지 않는다.
-- CREATE TABLE checklist_item_backup_20260811 AS SELECT * FROM checklist_item;
-- CREATE TABLE hidden_checklist_default_backup_20260811 AS SELECT * FROM hidden_checklist_default;

-- 2) 테이블 삭제 — 두 테이블 모두 spot을 참조하는 쪽이고, 이들을 참조하는 테이블은 없다.
--    서로 간 FK도 없어 순서는 무관하다.
DROP TABLE IF EXISTS hidden_checklist_default;
DROP TABLE IF EXISTS checklist_item;

-- 적용 현황 (마이그레이션 도구가 없어 수동 관리. 적용하면 이 표를 갱신할 것)
--                            (2) DROP
--   로컬 (박예은)              [ ]
--   개발                       [ ]
--   운영                       [ ]
