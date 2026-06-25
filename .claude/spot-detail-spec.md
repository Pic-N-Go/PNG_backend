# Spot Detail API 작업 스펙

> 브랜치: `feature/spot-detail`  
> 담당: 박예은  
> 기준 퍼블리싱: `PNG_frontend/src/components/ui/spot/spot-detail.html`

---

## 담당 범위

| 기능 | API | 우선순위 |
|------|-----|---------|
| 스팟 상세 조회 | `GET /spots/{spotId}` | P0 |
| 리뷰 요약 + 목록 | `GET /spots/{spotId}/reviews` | P0 |
| 리뷰 작성 | `POST /spots/{spotId}/reviews` | P0 |
| 리뷰 수정/삭제 | `PUT/DELETE /reviews/{reviewId}` | P1 |
| 북마크 토글 | `POST /spots/{spotId}/bookmark` | P1 |
| 포토제닉 지수 | `GET /spots/{spotId}/photogenic` | P1 — 외부 API 의존 |
| 스팟 사진 목록 | `GET /spots/{spotId}/photos` | P1 |

채팅 탭 → 소영재 담당. 사진 탭의 사진 업로드 → 소영재 담당.

---

## API 상세

### 1. 스팟 상세 조회

```
GET /spots/{spotId}
```

**Response**
```json
{
  "id": 1,
  "name": "광안리 해수욕장",
  "address": "부산광역시 수영구 광안해변로 219",
  "badge": true,
  "imageUrl": "...",
  "latitude": 35.153,
  "longitude": 129.118,
  "category": "해수욕장",
  "overview": "...",
  "tags": ["#채광맛집", "#야경", "#바다", "#일출명소"],

  "convenience": {
    "parking": "주차 가능",
    "wheelchairAccess": "가능",
    "strollerAccess": "가능",
    "petFriendly": "가능",
    "subwayAccess": "도보 10분",
    "usetime": null,
    "restdate": null,
    "infocenter": null
  },

  "stats": {
    "avgRating": 4.8,
    "reviewCount": 324,
    "photoCount": 1247
  },

  "checklist": [
    { "id": 1, "content": "삼각대 필요", "orderIndex": 1 },
    { "id": 2, "content": "광각렌즈 추천 (16-35mm)", "orderIndex": 2 }
  ],

  "isBookmarked": false
}
```

**구현 노트**
- `tags`: SpotTag 엔티티에서 JOIN
- `stats.photoCount`: SpotPhoto 카운트 (Review 사진 포함 여부 → 미결)
- `isBookmarked`: 인증 미구현 → `TEMP_USER_ID = 1L` 하드코딩

---

### 2. 리뷰 목록 (요약 포함)

```
GET /spots/{spotId}/reviews?sort=LATEST&page=0&size=20
```

**Query params**
- `sort`: `LATEST`(기본) | `RATING_HIGH` | `RATING_LOW`
- `page`, `size`: 페이지네이션

**Response**
```json
{
  "summary": {
    "avgRating": 4.8,
    "totalCount": 324,
    "distribution": {
      "5": 72,
      "4": 18,
      "3": 7,
      "2": 2,
      "1": 1
    }
  },
  "reviews": {
    "content": [
      {
        "id": 1,
        "userId": 10,
        "nickname": "한강뷰어",
        "rating": 5,
        "content": "광안대교 야경이 정말 환상적이에요...",
        "equipmentInfo": "Sony A7IV · 16-35mm f/2.8 GM",
        "photos": ["url1", "url2"],
        "visitedAt": "2026-04-12",  // nullable
        "createdAt": "2026-04-13T10:00:00"
      }
    ],
    "totalElements": 324,
    "totalPages": 17,
    "number": 0
  }
}
```

**구현 노트**
- `nickname`: User 엔티티에서 JOIN — user 도메인 확인 필요
- `visitedAt`: nullable, 사용자가 리뷰 작성 시 직접 입력. Review 엔티티에 추가 완료
- `distribution`: 별점별 비율(%) → 서비스 레이어에서 계산
- 요약 정보는 전체 리뷰 기준, 페이징과 무관하게 항상 반환

---

### 3. 리뷰 작성

```
POST /spots/{spotId}/reviews
Authorization: (미구현 시 TEMP_USER_ID)
```

**Request Body**
```json
{
  "rating": 5,
  "content": "광안대교 야경이 정말 환상적이에요...",
  "equipmentInfo": "Sony A7IV · 16-35mm f/2.8 GM"
}
```

**Response**: `201 Created` + 생성된 리뷰 객체

**구현 노트**
- 사진 업로드는 별도 API (소영재 담당)와 연계 — MVP에서 텍스트만 먼저
- 같은 스팟 중복 리뷰 정책 → **미결정**

---

### 4. 리뷰 수정

```
PUT /reviews/{reviewId}
```

**Request Body**: 작성과 동일 (`rating`, `content`, `equipmentInfo`)  
**검증**: 본인 리뷰만 수정 가능 (userId 비교)

---

### 5. 리뷰 삭제

```
DELETE /reviews/{reviewId}
```

**검증**: 본인 리뷰만 삭제 가능  
**Response**: `204 No Content`

---

### 6. 북마크 토글

```
POST /spots/{spotId}/bookmark
```

- 없으면 추가, 있으면 삭제 (토글)
- Bookmark 엔티티에 `(spot_id, user_id)` unique constraint 이미 있음

**Response**
```json
{ "isBookmarked": true }
```

**구현 노트**
- 현재 Bookmark 엔티티는 컬렉션 개념 없음 (단순 on/off)
- 퍼블리싱에서는 북마크 컬렉션 선택 UI가 있는데 → **미결정: 컬렉션 기능 포함 여부**

---

### 7. 포토제닉 지수

```
GET /spots/{spotId}/photogenic?date=2026-06-23&time=18:30
```

**Response**
```json
{
  "totalScore": 87,
  "grade": "매우 좋음",
  "recommendation": "지금 광안리 해수욕장에 방문하기 최적인 시간대예요",
  "goldenHour": {
    "minutesUntil": 23,
    "startTime": "18:53"
  },
  "factors": {
    "weather":     { "label": "날씨",       "value": "옅은 구름",  "score": 18, "ratio": 0.90 },
    "goldenHour":  { "label": "골든아워",   "value": "23분 후",   "score": 20, "ratio": 1.00 },
    "airQuality":  { "label": "미세먼지",   "value": "좋음",       "score": 15, "ratio": 0.75 },
    "congestion":  { "label": "예상 혼잡도", "value": "한산",       "score": 14, "ratio": 0.70 },
    "season":      { "label": "시즌",       "value": "벚꽃 92%",  "score": 20, "ratio": 0.92 }
  }
}
```

**외부 API 의존성**
| 팩터 | 외부 API | 담당 |
|------|---------|------|
| 날씨 | 기상청 단기예보 | 모정민 |
| 미세먼지 | 에어코리아 | 모정민 |
| 골든아워 | 자체 계산 (일출/일몰 시각 기반) | 모정민 |
| 혼잡도 | DB 정적값 (Spot.congestionLevel) | 박예은 |
| 시즌 | 미결정 | 박예은/모정민 협의 필요 |

> ⚠️ 포토제닉 지수는 모정민과 외부 API 연동 방식 협의 후 구현. MVP에서는 날씨/미세먼지 없이 혼잡도만으로 단순 계산하는 stub으로 먼저 개발해도 됨.

---

### 8. 스팟 사진 목록

```
GET /spots/{spotId}/photos?category=ALL&page=0&size=18
```

**category**: `ALL` | `일출` | `야경` | `인물` | `풍경`

**Response**
```json
{
  "content": [
    { "id": 1, "url": "...", "category": "야경", "uploadedAt": "..." }
  ],
  "totalElements": 1247
}
```

**구현 노트**
- 사진 업로드는 소영재 담당 → SpotPhoto 테이블 구조 맞춰서 조회만 구현
- category 필터: SpotPhoto 엔티티에 category 컬럼 있는지 확인 필요

---

## 엔티티 현황

| 엔티티 | 파일 | 비고 |
|--------|------|------|
| Spot | `spot/domain/Spot.java` | 완성. 편의정보 필드 다 있음 |
| Review | `spot/domain/Review.java` | `visitedAt` DATE nullable 추가 완료 |
| Bookmark | `spot/domain/Bookmark.java` | 컬렉션 개념 없음 |
| ChecklistItem | `spot/domain/ChecklistItem.java` | 스팟별 고정 항목 |
| SpotTag | `spot/domain/SpotTag.java` | 확인 필요 |
| SpotPhoto | `spot/domain/SpotPhoto.java` | category 컬럼 확인 필요 |
| ReviewPhoto | `spot/domain/ReviewPhoto.java` | 확인 필요 |

---

---

## TourAPI 활용 가능 여부 체크 (공모전 필수 조건)

> 공모전 제출 요건: **활용 API명 + 운영계정 신청정보(신청자명, 인증키)** 반드시 제출

### TourAPI 15종 서비스 중 우리가 쓸 수 있는 것

| API명 (서비스명) | 함수명 | 우리 화면 활용 | 비고 |
|-----------------|--------|--------------|------|
| 공통정보조회 | `detailCommon1` | 스팟 기본정보 | **핵심** |
| 소개정보조회 | `detailIntro1` | 편의정보 | **핵심** |
| 이미지정보조회 | `detailImage1` | 사진 탭 | **핵심** |
| 위치기반관광정보 | `locationBasedList1` | 주변 스팟 추천 | 이예인 담당과 연계 |
| 지역기반관광정보 | `areaBasedList1` | 스팟 목록 | 이예인 담당 |
| 키워드검색 | `searchKeyword1` | 검색 | 이예인 담당 |
| 반려동물 동반여행정보 | `detailPetTour1` | 반려동물 상세 정보 | 별도 API (data.go.kr 15135102) |

---

### contenttypeid=12 (관광지) 소개정보(detailIntro1) 필드 vs 우리 엔티티

| TourAPI 필드 | 우리 Spot 필드 | 제공 여부 | 비고 |
|-------------|--------------|---------|------|
| `title` (common) | `name` | ✅ | |
| `addr1` + `addr2` (common) | `address` | ✅ | |
| `zipcode` (common) | `zipcode` | ✅ | |
| `overview` (common) | `overview` | ✅ | |
| `mapx`, `mapy` (common) | `longitude`, `latitude` | ✅ | |
| `firstimage` (common) | `imageUrl` | ✅ | |
| `firstimage2` (common) | `thumbnailUrl` | ✅ | |
| `infocenter` (intro) | `infocenter` | ✅ | |
| `usetime` (intro) | `usetime` | ✅ | |
| `restdate` (intro) | `restdate` | ✅ | |
| `parking` (intro) | `parking` | ✅ | **텍스트**. 예: "서울랜드 동문 주차장 10,000원" |
| `chkpet` (intro) | `petFriendly` | ✅ | **텍스트**. 예: "불가", "가능" |
| `chkbabycarriage` (intro) | `strollerAccess` | ✅ | **텍스트**. 예: "없음", "가능" |
| `chkhandichief` (intro) | `wheelchairAccess` | ✅ | **텍스트**. 예: "가능", "없음" |
| `contenttypeid` (common) | `category` | ✅ | |
| `contentid` (common) | `tourContentId` | ✅ | |
| `accomcount` (intro) | ❌ 없음 | 있음 | 수용인원. 필요 시 추가 가능 |
| `expguide` (intro) | ❌ 없음 | 있음 | 체험안내. 선택적 추가 |
| `homepage` (common) | ❌ 없음 | 있음 | 홈페이지 URL. 선택적 추가 |

### TourAPI에 없는 것 → 우리가 직접 관리

| 우리 화면 항목 | 비고 |
|-------------|------|
| `tags` (SpotTag) | 직접 입력 또는 category/키워드에서 자동 파생 |
| `nightSafety` | 직접 관리 (정적 데이터) |
| `congestionLevel` | 직접 관리 (정적 데이터) |
| `subwayAccess` | 직접 관리 또는 추후 카카오 지도 API |
| 리뷰/별점 | 사용자 생성 |
| 포토제닉 지수 | 기상청 + 에어코리아 + 골든아워 계산 |
| 촬영 체크리스트 | 직접 관리 |

---

### ⚠️ 주의사항

1. **필드값이 텍스트**: `parking`, `chkpet`, `chkbabycarriage`, `chkhandichief` 모두 자유 텍스트 형태. UI에서 "가능/불가" boolean으로 보여주려면 파싱 필요 (예: "불가", "없음" → false, 그 외 → true)
2. **데이터 공백 많음**: 관광지마다 채워진 필드가 다름. 빈 문자열이나 null이 흔함
3. **이미지**: `firstimage`는 대표 1장. 추가 이미지는 `detailImage1` 별도 호출 필요
4. **반려동물**: `chkpet`은 단순 가능/불가. 더 상세한 반려동물 정보는 별도 API `detailPetTour1` 신청 필요
5. **badge**: TourAPI source이면 자동 true — 이미 Spot.badge로 구현되어 있음 ✅

---

### 공모전 제출 체크리스트

- [ ] 공공데이터포털(data.go.kr)에서 `한국관광공사_국문 관광정보 서비스` 인증키 신청
- [ ] 신청자명, 인증키 정보 보관 (제출 필요)
- [ ] 실제 TourAPI 호출 코드 작성 및 스팟 초기 데이터 입력
- [ ] 공모전 제출 시 "활용 API명: 한국관광공사_국문 관광정보 서비스(TourAPI 4.0)" 명시

---

## 미결정 사항

- [x] `Review.visitedAt` 필드 추가 — DATE nullable로 추가 완료
- [x] `SpotPhoto.category` — 제거 결정. 사진 탭 카테고리 필터 MVP 제외
- [x] `nightSafety`, `congestionLevel` — 데이터 채울 방법 없어 제거 결정
- [ ] 같은 스팟 중복 리뷰 허용 여부
- [ ] 북마크 컬렉션 기능 MVP 포함 여부
- [ ] `stats.photoCount` 집계 범위 (스팟 직접 사진만? 리뷰 사진 포함?)
- [ ] 포토제닉 지수 — 시즌 팩터 구현 방식 (모정민 협의)
- [ ] `SPOT.photogenic_score` 용도 — 정적값인지 캐시값인지 (모정민 협의)

---

## 구현 순서 (추천)

1. `GET /spots/{spotId}` — 기본 조회 (체크리스트, 태그 포함)
2. `GET /spots/{spotId}/reviews` — 리뷰 목록 + 요약
3. `POST /spots/{spotId}/reviews` — 리뷰 작성
4. `POST /spots/{spotId}/bookmark` — 북마크 토글
5. `GET /spots/{spotId}/photos` — 사진 목록
6. `GET /spots/{spotId}/photogenic` — 모정민 협의 후
