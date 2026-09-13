-- spot_tag 테이블 삭제
-- 사진 테마/특성은 spot_categories(다중 카테고리) 시스템으로 통합되어,
-- 실제 데이터가 저장되지 않고 미사용 상태로 방치된 spot_tag 테이블을 완전히 제거한다.

DROP TABLE IF EXISTS `spot_tag`;
