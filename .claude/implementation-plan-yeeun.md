# 박예은 담당 API 구현 계획

브랜치: `feature/spot-detail`

---

## 사전 확인 (구현 시작 전)

- [x] `User` 엔티티 위치 및 `nickname` 필드 확인
- [ ] `stats.photoCount` 집계 범위 결정 — SpotPhoto만? 리뷰 사진(ReviewPhoto) 포함?
- [x] `SPOT.photogenic_score` 용도 확인 — 실시간 계산으로 결정 (DB 필드 미사용)
- [ ] 같은 스팟 중복 리뷰 허용 여부 결정

---

## Phase 1 — P0

### 1. `GET /spots/{spotId}` — 스팟 상세 조회
- [x] `SpotRepository` — `findById`
- [x] `ReviewRepository` — avgRating, reviewCount 집계 쿼리 (`findAvgAndCountBySpotId`)
- [x] `BookmarkRepository` — `existsBySpotIdAndUserId` (TEMP_USER_ID = 1L)
- [x] `SpotResponse` DTO 작성
- [x] `SpotService.getSpotDetail()` 작성
- [x] `SpotController` + `SpotControllerApiSpec` 작성

### 2. `GET /spots/{spotId}/reviews` — 리뷰 목록 + 요약
- [x] `ReviewRepository` — 페이징 + sort(LATEST / RATING_HIGH / RATING_LOW)
- [x] `ReviewRepository` — 별점 분포 집계 쿼리 (`findRatingDistributionBySpotId`)
- [x] `ReviewListResponse`, `ReviewResponse` DTO 작성
- [x] `ReviewService.getReviews()` 작성
- [x] `ReviewController` + `ReviewControllerApiSpec` 작성

### 3. `POST /spots/{spotId}/reviews` — 리뷰 작성
- [x] `ReviewRequest` DTO (rating, content, equipmentInfo, visitedAt)
- [x] `ReviewService.createReview()` 작성

---

## Phase 2 — P1

### 4. `PUT /reviews/{reviewId}` — 리뷰 수정
- [x] 본인 리뷰 검증 (userId 비교)
- [x] `ReviewService.updateReview()`

### 5. `DELETE /reviews/{reviewId}` — 리뷰 삭제
- [x] 본인 리뷰 검증
- [x] 204 No Content 반환

### 6. `POST /spots/{spotId}/bookmark` — 북마크 토글
- [ ] `BookmarkRepository.findBySpotIdAndUserId()`
- [ ] 없으면 INSERT / 있으면 DELETE
- [ ] `{ "isBookmarked": true/false }` 반환

### 7. `GET /spots/{spotId}/photos` — TourAPI 사진 목록
- [x] `TourApiClient.getDetailImages()` 구현
- [x] `SpotPhotoResponse` DTO 작성
- [x] `SpotService.getSpotPhotos()` 작성
- [x] `SpotController` 엔드포인트 추가

### 8. `GET /spots/{spotId}/nearby-parking` — 주변 주차장
- [ ] 공공데이터 전국주차장정보표준데이터 API 연동
- [ ] 노출 항목: 주차장명, 거리, 무료/유료

---

## Phase 3 — 포토제닉 지수

### 9. `GET /spots/{spotId}/photogenic-score`
- [x] `AirQualityClient` 구현 (에어코리아 `/getCtprvnRltmMesureDnsty`)
- [x] 미세먼지 점수 (20점): grade 없으면 pm10Value로 직접 판단
- [x] 오존 점수 (10점): grade 없으면 o3Value로 직접 판단
- [x] 시즌 점수 (10점): `SeasonEvent` DB 기반, 없는 달 최대 90점
- [ ] 날씨 점수 (40점): 모정민 영역, 연동 대기
- [ ] 골든아워 점수 (20점): 모정민 영역, 연동 대기
- **현재**: 날씨/골든아워 "구현 예정" stub, 총점 = 미세먼지+오존+시즌만 합산

---

## 외부 API 현황

| API | 키 이름 | 상태 |
|-----|---------|------|
| 한국관광공사 TourAPI 4.0 | `PUBLIC_DATA_SERVICE_KEY` | ✅ 사용 중 |
| 에어코리아 대기오염정보 | `PUBLIC_DATA_SERVICE_KEY` (동일) | ✅ 사용 중 |
| 기상청 단기예보 | `WEATHER_SERVICE_KEY` | 모정민 영역 |

> TourAPI 일일 한도: 1000건/엔드포인트. 서울(areaCode=1) 456건 sync 완료.
> overview/parking/usetime/wheelchair_access 필드: 429 오류로 NULL → 재sync 필요 (areaCode=1부터)

---

## DB 상태

- `spot` 테이블: 서울 456건 저장 (tour_content_id 기준)
- `season_event` 테이블: 5건, max_score = 10으로 설정 완료
- overview/parking 등 상세 필드: NULL (detailIntro 재sync 필요)

---

## 남은 작업

| 항목 | 우선순위 | 비고 |
|------|----------|------|
| 북마크 토글 API | P1 | - |
| 주변 주차장 API | P1 | 공공데이터 API |
| 포토제닉 날씨/골든아워 | P2 | 모정민 연동 후 |
| TourAPI 재sync | 언제든 | overview/parking NULL 해소 |
| `/tour-api/**` 권한 | 배포 전 | permitAll → hasRole("ADMIN") |

---

## ErrorCode 추가 완료

| 상황 | ErrorCode |
|------|-----------|
| 스팟 없음 | `SPOT_NOT_FOUND` |
| 리뷰 없음 | `REVIEW_NOT_FOUND` |
| 본인 리뷰 아님 | `REVIEW_FORBIDDEN` |
