# 개발 세션 노트 (2026-06-17)

## 나의 담당 영역
스팟 상세, 리뷰, 북마크, 포토제닉 지수, 커뮤니티 피드

## 현재 브랜치
`feature/spot-detail` (develop 기반)

---

## 결정된 사항

- 스팟 초기 데이터: 한국관광공사 TourAPI 4.0에서 가져옴
- TourAPI 출처 스팟 = `badge=true` (관광공사 인증) 자동 부여
- 혼잡도: 정적 저장 (실시간 연동 X)
- 주차 가능 여부: TourAPI 편의 정보에서 가져옴
- 주변 주차장 상세: 공공데이터포털 전국주차장 API 별도 연동 (팀 결정 후 착수)

---

## 2026-06-18 회의 결정 필요 사항

1. **Spot 엔티티 스키마 합의** — 박예은/이예인/소영재 공동
2. **TourAPI 초기 데이터 입력 담당자**
3. **photogenic-score 외부 API 연동 방식** — 박예은/모정민 협의
4. **주변 주차장 API 연동 여부**

---

## 활용 외부 API

| 용도 | API | 제공처 | 비고 |
|------|-----|--------|------|
| 스팟 초기 데이터 | TourAPI 4.0 국문 관광정보 서비스 | 한국관광공사 | |
| 날씨 | 기상청 단기예보 API | 공공데이터포털 | |
| 미세먼지 | 에어코리아 대기오염정보 API | 한국환경공단 | |
| 길찾기 | 카카오맵/네이버 지도/Apple 지도 딥링크 | — | 백엔드 구현 없음. 프론트에서 스팟 좌표로 앱 딥링크 호출. |
| 주변 주차장 | 전국주차장정보표준데이터 API | 공공데이터포털 | 노출 항목: 주차장명, 거리, 무료/유료 |

---

## 노션 페이지

- SPOT Detail: https://app.notion.com/p/3821ffb43d7680388de7cba44d147ed0
- 회의 아젠다: https://app.notion.com/p/3821ffb43d7681afa2faee9b5d862a86

---

## 코드 패턴 (Wishlist 기준)

- 엔티티: `@Builder` + `@NoArgsConstructor(access = PROTECTED)` + `extends BaseTimeEntity`
- DTO: `record` + 정적 팩토리 `from()`
- Controller: `ApiSpec` 인터페이스 implements (Swagger 분리)
- Service: `@Transactional(readOnly = true)` 기본, 변경 메서드만 `@Transactional`
- 예외: `CustomException` + 도메인별 `ErrorCode` enum
- 인증 전: `TEMP_USER_ID = 1L` 하드코딩
