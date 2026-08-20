# 배포 체크리스트 (검색·임베딩 기능)

이 문서는 검색 4단계 폴백과 의미 검색을 운영에 올릴 때 무엇을 어떤 **순서로**
해야 하는지 적은 것이다. 순서가 핵심이다 — 뒤바뀌면 앱이 뜨지 않거나 검색이
전부 실패한다.


## 스키마는 이제 자동으로 적용된다

예전에는 이 문서에 "배포 전에 SQL을 손으로 실행하라"가 길게 적혀 있었다.
Flyway를 도입하면서 그 단계가 사라졌다.

앱이 뜰 때 `db/migration`의 마이그레이션이 순서대로 적용되고, 이력이
`flyway_schema_history` 테이블에 남는다. 로컬과 운영이 같은 SQL을 적용받으므로
"로컬은 되는데 운영에서 안 뜨는" 문제도 없어졌다.

스키마를 바꾸는 방법은 `docs/db-migration-runbook.md`를 참고한다.

> 이전 방식(손으로 실행하던 SQL)은 `docs/archive/`에 문서로 남아 있다.
> 실행 대상이 아니다.

---
## 1. 환경변수 확인 (`.env.prod`)

> ⚠️ **`.env.prod`에 넣는 것만으로는 앱에 전달되지 않는다.**
>
> CI는 `--env-file .env.prod`로 배포하는데, 이 옵션은 `docker-compose.prod.yml` 안의
> `${...}`를 치환할 뿐 컨테이너 환경변수로 넣어주지 않는다. 그래서 compose 파일의
> `app.environment:`에 **이름이 적혀 있는 변수만** 앱에 닿는다.
>
> 실제로 `SEARCH_*`와 `OPENAI_API_KEY`가 빠져 있어, `.env.prod`에 값이 있는데도
> 운영이 기본값(`LIKE`, 폴백 전부 `false`)으로 돌고 있었다. 에러가 나지 않고
> 조용히 예전 검색으로 동작해서 드러나지 않았다.
>
> 새 설정을 추가할 때는 `.env.prod`와 `docker-compose.prod.yml` **양쪽**에 넣어야 한다.

```bash
SEARCH_ENGINE=FULLTEXT
SEARCH_NORMALIZE_FALLBACK=true
SEARCH_SIMILAR_FALLBACK=true
SEARCH_FUZZY_FALLBACK=true
SEARCH_SEMANTIC_FALLBACK=true
OPENAI_API_KEY=<발급받은 키>
```

인덱스는 Flyway가 앱 기동 시 만들어주므로, 예전처럼 "인덱스를 먼저 넣고 나서
설정을 켜야 한다"는 순서 문제는 없다. 다만 마이그레이션이 실패하면 앱이 아예 뜨지
않으므로, 첫 배포에서는 기동 로그를 확인한다(2번).

의미 검색 관련:
- `OPENAI_API_KEY`가 비어 있으면 그 단계는 항상 0건으로 끝난다. **장애는 나지 않는다.**
- 비용이 발생하는 기능이다. 새벽 3:30 백필 배치가 임베딩이 빈 스팟을 채운다.

---

## 2. 앱 배포

CI/CD로 배포한다. 기동 로그에서 아래를 확인한다.

```
Successfully validated N migrations
Migrating schema `picngo` to version "..."
Successfully applied N migrations
```

- Flyway가 마이그레이션을 적용했는지 (이미 다 적용됐다면 `Schema is up to date`)
- `Schema-validation` 오류가 없을 것 — 있다면 엔티티와 마이그레이션이 어긋난 것이다
- `Tomcat started on port(s)` 가 찍힐 것

무엇이 적용됐는지는 이력 테이블로도 확인할 수 있다.

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" picngo \
  -e "SELECT version, description, success, installed_on FROM flyway_schema_history;"
```

---

## 3. 임베딩 백필 (의미 검색을 쓸 때만)

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

## 4. 배포 후 확인

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

코드와 DB가 어긋나면 **에러 없이 조용히 결과만 안 나오는**
경우가 있다. 실제로 겪었던 것들이다.

| 짝 | 어긋나면 |
|---|---|
| `MATCH(name, address)` ↔ `ft_spot_search(name, address)` | 컬럼 순서까지 같아야 한다. 다르면 검색 전부 실패 (ERROR 1191). 실제로 마이그레이션이 옛 3컬럼 정의를 들고 있어 운영 검색이 전부 500이었다 (V4에서 수정) |
| 엔티티에서 뺀 컬럼 ↔ 실제 테이블 | `validate`는 **없는 컬럼만** 잡고 남아도는 컬럼은 통과시킨다. 그 컬럼이 `NOT NULL`이면 기동은 되는데 INSERT만 실패한다. `review_photo.photo_url`이 그래서 사진 업로드를 막았다 (V4에서 수정) |
| `search_norm` 생성 규칙 ↔ `FullTextKeyword`의 정규식 | 한쪽만 바꾸면 조용히 0건. 실제로 괄호 처리가 어긋나 자기 이름으로도 검색이 안 됐던 적이 있다 |
| `@@ngram_token_size = 2` | 2가 아니면 2글자 검색어가 색인에 잡히지 않는다 |
| 엔티티 컬럼 ↔ 실제 테이블 | 운영은 `validate`라 기동 자체가 막힌다 |

`spot_data.sql`은 `spring.sql.init.mode=always`라 **기동할 때마다 실행된다.**
INSERT는 `ON DUPLICATE KEY UPDATE`로 감싸져 있어 안전하지만, 여기에 `UPDATE` 문을
넣으면 재시작마다 데이터를 덮어쓴다. overview 텍스트를 이 파일에 두지 않는 이유다
(`docs/spot-overview-backfill-migration.sql`로 분리해 두었다).
