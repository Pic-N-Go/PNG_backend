# 박예은 담당 API 구현 계획

브랜치: `feature/spot-detail`

---

## 사전 확인 (구현 시작 전)

- [ ] `User` 엔티티 위치 및 `nickname` 필드 확인 (리뷰 목록에서 JOIN 필요)
- [ ] `stats.photoCount` 집계 범위 결정 — SpotPhoto만? 리뷰 사진(ReviewPhoto) 포함?
- [ ] `SPOT.photogenic_score` 용도 확인 — 모정민과 협의
- [ ] 같은 스팟 중복 리뷰 허용 여부 결정

---

## Phase 1 — P0

### 1. `GET /spots/{spotId}` — 스팟 상세 조회
- [ ] `SpotRepository` — `findById`
- [ ] `SpotTagRepository` — `findBySpotId`
- [ ] `ChecklistItemRepository` — `findBySpotIdOrderByOrderIndex`
- [ ] `ReviewRepository` — avgRating, reviewCount 집계 쿼리
- [ ] `SpotPhotoRepository` — photoCount 집계 쿼리
- [ ] `BookmarkRepository` — `existsBySpotIdAndUserId` (TEMP_USER_ID = 1L)
- [ ] `SpotResponse` DTO 작성
- [ ] `SpotService.getSpotDetail()` 작성
- [ ] `SpotController` + `SpotControllerApiSpec` 작성

### 2. `GET /spots/{spotId}/reviews` — 리뷰 목록 + 요약
- [ ] `ReviewRepository` — 페이징 + sort(LATEST / RATING_HIGH / RATING_LOW)
- [ ] `ReviewRepository` — 별점 분포 집계 쿼리
- [ ] `ReviewResponse`, `ReviewSummaryResponse` DTO 작성
- [ ] `ReviewService.getReviews()` 작성
- [ ] `ReviewController` + `ReviewControllerApiSpec` 작성

### 3. `POST /spots/{spotId}/reviews` — 리뷰 작성
- [ ] `ReviewRequest` DTO (rating, content, equipmentInfo, visitedAt)
- [ ] `ReviewService.createReview()` 작성
- [ ] `Spot.reviewCount` 증가 처리
- [ ] 중복 리뷰 정책 반영 (결정 후)

---

## Phase 2 — P1

### 4. `PUT /reviews/{reviewId}` — 리뷰 수정
- [ ] 본인 리뷰 검증 (userId 비교)
- [ ] `ReviewService.updateReview()`

### 5. `DELETE /reviews/{reviewId}` — 리뷰 삭제
- [ ] 본인 리뷰 검증
- [ ] `Spot.reviewCount` 감소 처리
- [ ] 204 No Content 반환

### 6. `POST /spots/{spotId}/bookmark` — 북마크 토글
- [ ] `BookmarkRepository.findBySpotIdAndUserId()`
- [ ] 없으면 INSERT / 있으면 DELETE
- [ ] `Spot.bookmarkCount` 증가/감소 처리
- [ ] `{ "isBookmarked": true/false }` 반환

### 7. `GET /spots/{spotId}/photos` — 사진 목록
- [ ] 소영재 업로드 API 완료 후 진행
- [ ] `SpotPhotoRepository` 페이징 조회

---

## Phase 3 — 협의 후

### 8. `GET /spots/{spotId}/photogenic` — 포토제닉 지수
- [ ] 모정민과 외부 API 연동 방식 협의 후 구현
- [ ] MVP stub: photogenic_score 정적값만 반환

---

## ErrorCode 추가 필요 목록

| 상황 | ErrorCode |
|------|-----------|
| 스팟 없음 | `SPOT_NOT_FOUND` |
| 리뷰 없음 | `REVIEW_NOT_FOUND` |
| 본인 리뷰 아님 | `REVIEW_FORBIDDEN` |

---

## 미결 사항

| 항목 | 협의 대상 | 상태 |
|------|----------|------|
| stats.photoCount 집계 범위 | 팀 전체 | 미결 |
| 중복 리뷰 허용 여부 | 팀 전체 | 미결 |
| photogenic_score 용도 | 모정민 | 미결 |
| 북마크 컬렉션 기능 MVP 포함 여부 | 팀 전체 | 미결 |
