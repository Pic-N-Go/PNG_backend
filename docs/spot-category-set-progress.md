# 스팟 카테고리 Set 전환 — 작업 진행 공유

> 팀 공유용 진행 문서. 단계 끝날 때마다 갱신됨. 상세 설계는 별도 초안 참고.
> **브랜치:** `feature/spot-category-set` (base: `develop`)

## 무엇을 왜 바꾸나 (요약)

- **문제:** `Spot.category`가 단일 enum인데 DB엔 옛 TourAPI 코드(`A01`/`A02`)가 들어 있어 `/spots/**` 전건 500.
- **방향:** `Spot.category`(단일) → `Spot.categories`(다중 `Set<SpotCategory>`). 유저 취향(사진테마) 기반 추천을 성립시킴.
- **태깅:** cat3(장소형) + overview 키워드(장면형) + **HERITAGE**(역사/전통) → **매핑 커버율 72.8% (3,251/4,465)**.
- **최종 카테고리 13개 + ETC** (PORTRAIT·PET 삭제, CITY 유지). 상세는 아래 1단계.
- **응답 스펙 변경(⚠️ 프론트):** `category: "BEACH"` → `categories: ["BEACH","NIGHT_VIEW"]` (배열).

## 진행 상황

| # | 단계 | 상태 | 비고 |
|---|---|---|---|
| 1 | enum 통합 + `HERITAGE` 추가 + `PORTRAIT`·`PET` 삭제 | ✅ 완료 | 아래 상세 |
| 2 | `Spot` 엔티티 단일→Set | ✅ 완료 | `categories` Set, 빌더/`updateCategories` |
| 3 | `SpotUpsertService` 2단계 태깅 | ✅ 완료 | `SpotCategoryTagger` 신규 + 단위테스트 |
| 4 | Repository `member of` JPQL | ✅ 완료 | 필터 4곳 전환 (컴파일 필수라 함께) |
| 5 | DTO 7개 + course 배열 매핑 | ✅ 완료 | ⚠️ 응답 배열화(프론트 협의) |
| 6 | 마이그레이션 SQL (기존 4,465건) | ✅ 로컬 완료 | 태깅 + 옛 `category` 컬럼 DROP까지. 운영은 미실행 |

## 단계별 상세

### ✅ 1단계 — enum 통합 + HERITAGE 추가 + PORTRAIT·PET 삭제
- `common/domain/SpotCategory`에 **`HERITAGE`** 값 추가 (유적지/사찰/성/종교성지 담당).
- **`PORTRAIT`(인물)·`PET`(반려동물) 삭제** — 데이터 소스 없음. PORTRAIT는 피사체(장소 속성 아님), PET는 facet(`pet_friendly` 컬럼도 비어있음). 코드 참조 0 확인 후 제거.
- **중복 enum 제거:** `spot/domain/SpotCategory.java` 삭제. 스팟 도메인도 이제 `common`의 enum 하나만 사용.
- import 경로 재지정 6개 파일: `Spot`, `SpotService`, `SpotRepository`, `SpotResponse`, `SpotSummaryResponse`, `SpotUpsertService` + 테스트 1건(`NotificationSchedulerTest`).
- `compileJava`/`compileTestJava` 통과.
- **최종 enum 14개:** `PARK, CAFE, BEACH, MOUNTAIN, CITY, NIGHT_VIEW, HANOK, FESTIVAL, FLOWER, FOREST, SUNRISE_SUNSET, MILKY_WAY, HERITAGE, ETC`.
- **팀 영향(소영재):** 온보딩 관심테마 enum에 **"역사/전통" 추가** + **"인물"·"반려동물" 칩 제거** 필요.

### ✅ 2·3·4·5단계 — 엔티티 Set화 + 태깅 + 쿼리 + DTO 배열
- **엔티티:** `Spot.category`(단일) → `Spot.categories`(`@ElementCollection Set<SpotCategory>`, `spot_categories` 테이블). `updateCategories()` 추가. (4는 컴파일 필수 종속이라 2·3·5와 함께 처리)
- **태깅:** `spot/domain/SpotCategoryTagger` 신규 — cat3 매핑(15코드) + 키워드 규칙(7테마). `SpotUpsertService`가 신규·기존 스팟 모두 태깅. 단위테스트 `SpotCategoryTaggerTest` 통과.
- **Repository:** `s.category = :category` → `:category member of s.categories` 4곳(목록/인기/검색/지도필터). **필터 입력은 단일 유지**(유저가 한 번에 한 테마 필터).
- **DTO 배열화:** `SpotResponse`·`SpotSummaryResponse`는 `Set<SpotCategory> categories`, `SpotDetail`·`Map`·`Nearby`·`Recommended`·`CourseSpotResponse`는 `List<String> categories`.
- `compileJava`/`compileTestJava` + 태거 테스트 통과.

> [!danger] 🔴 6단계(마이그레이션) 전까지 앱 정상 동작 안 함
> `ddl-auto: update`라 기동 시 `spot_categories` 테이블은 **생성**되지만 기존 `spot.category`(NOT NULL) 컬럼은 **안 지워짐**. 그 결과:
> - 신규 스팟 INSERT가 `category` NOT NULL 위반으로 **실패**
> - 기존 4,465건은 `spot_categories`가 비어 있어 **필터/추천 0건**
>
> **6단계에서 반드시:** ① cat3+키워드로 `spot_categories` 채우기 ② `spot.category` 컬럼 DROP(또는 nullable). 실행 전까지 로컬에서 앱 기동 시 주의.

### 코드리뷰 반영 (커밋 전)
- 🔴 **`data.sql` 수정** — 시드 INSERT가 삭제된 `category` 컬럼을 참조해 부팅/컨텍스트 테스트가 깨지던 것. 컬럼 제거 + `spot_categories` 시드 추가. 컨텍스트 로딩 테스트 통과 확인.
- 🟡 **N+1 방지** — `default_batch_fetch_size: 100` 추가 (목록 조회 시 `categories` 일괄 로딩).
- 🟡 **개인화 추천 엔드포인트는 후속 작업** — 이번 변경은 축 정렬/태깅까지. `findRecommendedSpots`는 아직 유저 취향(User.spotCategories) 교집합을 안 씀. 다음 단계에서 연결.

### 🟡 6단계 — 마이그레이션 (로컬 검증 완료)
- 스크립트: `docs/spot-categories-migration.sql` (TourAPI 재조회 0건, cat3+overview로만. `SpotCategoryTagger` 규칙과 일치, 멱등).
- **로컬 실행 결과 (4,465건):** 모든 스팟 ≥1 태그, 실매핑 **3,251(72.8%)**, ETC 1,214, 다중태그 **528**. 테마별 건수·`member of` 필터(BEACH=147) 검증 완료.
- **옛 `spot.category` 컬럼 DROP 완료(로컬).** 손실 0 — `category` = `LEFT(cat3,3)`로 4,465건 전건 일치 확인, cat3에서 복원 가능한 완전 중복 컬럼이라 삭제. 로컬 스키마 = 최종 스키마 일치.
- **운영/팀원 DB:** 아직 미실행. 각 환경에서 `docs/spot-categories-migration.sql` 1회 실행하면 동일 상태로 수렴(populate는 멱등, DROP은 1회성).


## 팀원이 알아야 할 것 (누적)

- ⚠️ **프론트:** 스팟 응답의 `category`(문자열) → `categories`(문자열 배열)로 바뀔 예정 (5단계). 목록/상세/지도/추천 응답 전부 해당.
- ⚠️ **소영재:** 온보딩 관심테마 enum에 `HERITAGE` 추가 + **"인물"·"반려동물" 칩 제거** + 카테고리 아닌 항목(드론/필름/비오는날/커플) 분리 검토.
- ⚠️ **이예인·박예은:** 필터·추천(이예인) / 스팟 상세 DTO(박예은) 양쪽 걸침. 5단계에서 DTO 함께 수정.
- ⚠️ **전원:** 마이그레이션(6단계) 전까지 로컬 DB에 옛 `A01`/`A02` 있으면 조회 500. 임시 언블록: `UPDATE spot SET category='ETC';` (최종 아님).
