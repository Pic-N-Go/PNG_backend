# 배포 체크리스트 (검색·임베딩 기능)

이 문서는 검색 4단계 폴백과 의미 검색을 운영에 올릴 때 무엇을 어떤 **순서로**
해야 하는지 적은 것이다. 순서가 핵심이다 — 뒤바뀌면 앱이 뜨지 않거나 검색이
전부 실패한다.

## 왜 순서가 중요한가

로컬과 운영은 스키마를 다루는 방식이 다르다.

| | 로컬 | 운영 |
|---|---|---|
| `ddl-auto` | `update` (없는 컬럼을 알아서 만듦) | `validate` (없으면 **기동 거부**) |

그래서 로컬에서 잘 되던 것이 운영에서는 부팅 실패로 나타난다. 게다가
FULLTEXT 인덱스와 생성 컬럼(`search_norm`)은 `ddl-auto`가 아예 관리하지 않으므로
(JPA로 표현할 수 없다) 어느 쪽에서도 자동으로 만들어지지 않는다. 반드시 손으로 적용한다.

---

## 0. 사전 확인

운영 DB에 무엇이 이미 적용돼 있는지부터 본다. 아래를 실행해 현재 상태를 파악한다.

```sql
-- 컬럼
SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spot'
  AND COLUMN_NAME IN ('embedding', 'search_norm');

-- 인덱스
SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spot';

-- ngram 토큰 크기 (2가 아니면 2글자 검색어가 색인에 잡히지 않는다)
SELECT @@ngram_token_size;

-- FULLTEXT 인덱스의 컬럼 목록. 이름만 봐서는 부족하다 — 코드가 MATCH하는 컬럼과
-- 인덱스 컬럼이 다르면 ERROR 1191로 검색이 전부 실패한다.
SELECT INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'spot' AND INDEX_TYPE = 'FULLTEXT'
GROUP BY INDEX_NAME;

-- users 컬럼·제약 (사용자 계정 기능)
SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
  AND COLUMN_NAME IN ('bio', 'social_profile_image_url', 'deleted_at');

SELECT INDEX_NAME FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'uk_users_nickname';
```

기대값:

| 항목 | 있어야 하는 값 |
|---|---|
| `spot` 컬럼 | `embedding`, `search_norm` |
| `spot` 인덱스 | `ft_spot_search`, `ft_spot_search_norm`, `idx_spot_map_bounds` |
| **`ft_spot_search` 컬럼 목록** | **`name,address`** (`overview`가 있으면 🔴 아래 2-1 참고) |
| `users` 컬럼 | `bio`, `social_profile_image_url`, `deleted_at` |
| `users` 인덱스 | `uk_users_nickname` |
| `@@ngram_token_size` | `2` |

없는 것만 아래에서 적용하면 된다. 모든 마이그레이션은 재실행해도 안전하다(멱등).
단 `ft_spot_search`는 **이미 있어도 컬럼 목록이 다르면 손으로 DROP해야 한다**(2-1).

---

## 1. 마이그레이션 적용 — **앱 배포 전에**

EC2에서 MySQL이 도커 컨테이너(`picngo-mysql`)로 떠 있는 기준이다.

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo \
  < docs/spot-embedding-column-migration.sql
```

순서대로 아래를 적용한다.

| 순서 | 파일 | 안 하면 |
|---|---|---|
| 1 | `spot-embedding-column-migration.sql` | 🔴 **앱이 기동하지 않는다** (`missing column [embedding]`) |
| 2 | `search-fulltext-index-migration.sql` | 🔴 `SEARCH_ENGINE=FULLTEXT`일 때 **검색 전부 실패** (ERROR 1191) |
| 3 | `search-normalized-column-migration.sql` | 🟠 2·3단계 폴백 쿼리 실패 |
| 4 | `search-map-bounds-index-migration.sql` | 🟡 지도 조회가 느려짐 (기능은 동작) |
| 5 | `user-bio-column-migration.sql` | 🔴 **앱이 기동하지 않는다** (`missing column [bio]`) |
| 6 | `user-social-profile-image-migration.sql` | 🔴 **앱이 기동하지 않는다** (`missing column [social_profile_image_url]`) |
| 7 | `user-nickname-unique-migration.sql` | 🟠 닉네임 중복이 동시 가입 경합으로 계속 뚫린다 (기동은 된다) |

5·6·7은 사용자 계정 기능(자기소개·프로필 사진·닉네임)에 딸린 것으로, **이번 배포에서
새로 적용해야 한다.** 6은 컬럼 추가에 더해 기존 값을 옮기는 백필이 들어 있어, 건너뛰면
카카오 사진이 "사용자가 올린 사진"으로 잡혀 재로그인 갱신·삭제가 어긋난다.
7은 중복이 있으면 ALTER가 실패하므로 파일 안의 확인 쿼리를 먼저 볼 것.

1번은 **이번 배포에서 반드시 새로 적용해야 한다.** 나머지는 이전에 적용했다면 건너뛴다
(0번에서 확인한 결과대로).

### 2-1. `ft_spot_search` 컬럼 목록이 다르면 — 🔴 스킵하면 장애

검색 쿼리가 `MATCH(name, address)`로 바뀌었다(`SpotRepository`). 운영에 이미 있는 인덱스가
`(name, address, overview)` 3컬럼이면 **컬럼 목록이 맞지 않아 검색이 전부 실패한다**
(`ERROR 1191: Can't find FULLTEXT index matching the column list`).

`search-fulltext-index-migration.sql`은 **인덱스 이름만 보고 건너뛰므로 이 경우를 고치지
못한다.** 0번에서 컬럼 목록에 `overview`가 보이면 손으로 지운 뒤 마이그레이션을 다시 돌린다.

```sql
ALTER TABLE spot DROP INDEX ft_spot_search;
```
```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo \
  < docs/search-fulltext-index-migration.sql
```

인덱스 재생성은 행 수에 비례해 시간이 걸린다. 그 사이 FULLTEXT 검색은 실패하므로
`SEARCH_ENGINE=LIKE`로 잠시 내려두거나 트래픽이 적은 시간에 할 것.

> 각 파일 안에 검증 쿼리가 들어 있다. 실행 후 출력에서 컬럼·인덱스가 생겼는지 확인할 것.
> 특히 FULLTEXT는 `EXPLAIN` 결과가 `type: fulltext, key: ft_spot_search`로 나와야 한다.

---

## 2. 환경변수 확인 (`.env.prod`)

```bash
SEARCH_ENGINE=FULLTEXT
SEARCH_NORMALIZE_FALLBACK=true
SEARCH_SIMILAR_FALLBACK=true
SEARCH_FUZZY_FALLBACK=true
SEARCH_SEMANTIC_FALLBACK=true
OPENAI_API_KEY=<발급받은 키>
```

⚠️ **이 값들은 1번이 끝난 뒤에 켜야 한다.** 인덱스 없이 `FULLTEXT`로 두면
검색 요청이 전부 500으로 떨어진다. 확신이 없으면 `SEARCH_ENGINE=LIKE`로 배포한 뒤,
인덱스를 확인하고 나서 `FULLTEXT`로 올리는 편이 안전하다(재배포 필요).

의미 검색 관련:
- `OPENAI_API_KEY`가 비어 있으면 그 단계는 항상 0건으로 끝난다. **장애는 나지 않는다.**
- 비용이 발생하는 기능이다. 새벽 3:30 백필 배치가 임베딩이 빈 스팟을 채운다.

---

## 3. 앱 배포

CI/CD로 배포한다. 기동 로그에서 아래를 확인한다.

- `Schema-validation` 오류가 없을 것 (있으면 1번이 덜 적용된 것)
- `Tomcat started on port(s)` 가 찍힐 것

---

## 4. 임베딩 백필 (의미 검색을 쓸 때만)

배포 직후에는 기존 스팟의 임베딩이 전부 비어 있다. 관리자 API로 채운다.

```
GET  /admin/embeddings              현황 확인 (total / withEmbedding / missing)
POST /admin/embeddings/backfill     일괄 채우기
```

- `ROLE_ADMIN` 계정이 필요하다. 관리자 계정이 아직 없으면 SQL로 한 번 승격시켜야 한다
  (앱 안에는 등급을 올려주는 기능이 없다):
  ```sql
  UPDATE users SET role = 'ADMIN' WHERE email = '<관리자 이메일>';
  ```
- 응답이 `{"saved":N,"failed":0}`이면 정상이다. `saved:0`이면 API 키나 외부 API 문제이므로
  로그에 남는 중단 경고를 확인한다.
- 이후에는 자동으로 유지된다: 새 스팟은 등록 이벤트로 즉시, 내용이 바뀌어 비워진 것은
  새벽 배치로 채워진다. 급하면 `POST /admin/embeddings/spots/{id}`로 하나만 다시 계산한다.

---

## 5. 배포 후 확인

| 확인할 것 | 방법 |
|---|---|
| 정상 검색 | 스팟 이름 그대로 검색 → 결과 나옴 |
| 오타 검색 | `헙재`, `오셜록` → 결과 나옴 (편집거리 단계) |
| 어느 단계가 쓰였나 | 그라파나에서 `spot_search_stage_total` 의 `stage` 라벨 확인 |
| 0건 비율 | `spot_search_result_total{outcome="zero"}` |

`stage` 라벨 값은 `primary` / `normalized` / `similar` / `fuzzy` / `semantic` / `none` 이다.
특정 단계의 비중이 계속 0이면 그 단계는 비용만 쓰고 있는 것이므로 유지 여부를 재검토한다.

---

## 되돌리기

| 대상 | 방법 |
|---|---|
| 검색 방식 | `SEARCH_ENGINE=LIKE`로 되돌리고 재배포 (인덱스는 그대로 둬도 됨) |
| 폴백 단계 | 해당 `SEARCH_*_FALLBACK=false`로 끄고 재배포 |
| 의미 검색 | `SEARCH_SEMANTIC_FALLBACK=false` (임베딩 데이터는 남겨두면 재계산 비용을 아낀다) |
| `embedding` 컬럼 | 지우려면 **이전 버전 앱으로 함께 롤백**해야 한다. 엔티티에 필드가 남아 있는 한 `validate`에서 막힌다 |

앱 설정만 되돌리는 것은 안전하다. 컬럼·인덱스를 지우는 쪽은 앱 버전과 짝을 맞춰야 한다.

---

## 알아둘 것 — 정합성이 깨지는 지점

수동으로 관리하는 것들이라, 코드와 DB가 어긋나면 **에러 없이 조용히 결과만 안 나오는**
경우가 있다. 실제로 겪었던 것들이다.

| 짝 | 어긋나면 |
|---|---|
| `MATCH(name, address, overview)` ↔ `ft_spot_search(name, address, overview)` | 컬럼 순서까지 같아야 한다. 다르면 검색 전부 실패 (ERROR 1191) |
| `search_norm` 생성 규칙 ↔ `FullTextKeyword`의 정규식 | 한쪽만 바꾸면 조용히 0건. 실제로 괄호 처리가 어긋나 자기 이름으로도 검색이 안 됐던 적이 있다 |
| `@@ngram_token_size = 2` | 2가 아니면 2글자 검색어가 색인에 잡히지 않는다 |
| 엔티티 컬럼 ↔ 실제 테이블 | 운영은 `validate`라 기동 자체가 막힌다 |

`spot_data.sql`은 `spring.sql.init.mode=always`라 **기동할 때마다 실행된다.**
INSERT는 `ON DUPLICATE KEY UPDATE`로 감싸져 있어 안전하지만, 여기에 `UPDATE` 문을
넣으면 재시작마다 데이터를 덮어쓴다. overview 텍스트를 이 파일에 두지 않는 이유다
(`docs/spot-overview-backfill-migration.sql`로 분리해 두었다).
