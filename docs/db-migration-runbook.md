# DB 마이그레이션 실행 안내

`docs/*.sql`을 **언제 누가** 실행해야 하는지 정리한 문서입니다.
로컬 개발 환경과 운영 서버는 실행해야 할 목록이 다릅니다.

## 왜 환경마다 다른가

| | 로컬 | 운영 |
|---|---|---|
| `ddl-auto` | `update` | `validate` |
| 엔티티에 새 컬럼이 생기면 | **자동으로 만들어 준다** | **없으면 기동 거부** |

그래서 로컬에서는 **Hibernate가 못 만드는 것만** 손으로 넣으면 됩니다.
운영은 전부 손으로 넣어야 합니다.

Hibernate가 못 만드는 것은 이런 것들입니다. JPA로 표현할 방법이 없어서
어느 환경에서도 자동 생성되지 않습니다.

- FULLTEXT 인덱스 (ngram 파서)
- 생성 컬럼(generated column)
- 옛 컬럼·테이블 삭제 (`update`는 추가만 하고 지우지 않는다)

---

## 로컬 개발 환경 — 처음 세팅할 때 한 번

`.env`에 DB 접속 정보가 있다고 가정합니다. 프로젝트 루트에서 실행하세요.

```bash
mysql -u "$DB_USERNAME" -p --default-character-set=utf8mb4 picngo -e "source docs/search-fulltext-index-migration.sql"
```

```bash
mysql -u "$DB_USERNAME" -p --default-character-set=utf8mb4 picngo -e "source docs/search-normalized-column-migration.sql"
```

```bash
mysql -u "$DB_USERNAME" -p --default-character-set=utf8mb4 picngo -e "source docs/search-map-bounds-index-migration.sql"
```

이 세 개만 하면 됩니다. 나머지 컬럼은 앱을 켜면 Hibernate가 알아서 만듭니다.

### ⚠️ 코스를 수정할 때 500이 난다면 — `course.version` 하나 더 실행

```bash
mysql -u "$DB_USERNAME" -p --default-character-set=utf8mb4 picngo -e "source docs/course-version-column-migration.sql"
```

증상은 이렇습니다.

```
Could not commit JPA transaction
Caused by: NullPointerException: Cannot invoke "java.lang.Long.longValue()" because "current" is null
    at org.hibernate.engine.internal.Versioning.increment
```

낙관적 락을 넣으면서 `course`에 `version` 컬럼이 생겼는데, **로컬은 `ddl-auto=update`라
Hibernate가 `NULL 허용`으로 만듭니다.** 그러면 그 전에 저장돼 있던 코스들이 `version = NULL`이
되고, 그 코스를 수정하는 순간 `NULL + 1`을 시도하다 터집니다.

새로 만드는 코스는 0이 들어가서 괜찮고, **기존 코스만** 문제입니다.
위 SQL이 `NULL`을 0으로 채우고 컬럼을 `NOT NULL`로 맞춰줍니다.

> 이미 컬럼이 있어도 안전합니다. 없으면 만들고, 있으면 `NULL`만 채웁니다.

> `mysql` 명령이 없으면 `mysqlsh --sql --user=... --schema=picngo --file=docs/....sql` 로도 됩니다.

### 검색 기능을 켜려면

위 마이그레이션을 마친 뒤 `.env`에 추가하세요. **순서를 지켜야 합니다.**
인덱스 없이 `FULLTEXT`로 켜면 검색 요청이 전부 실패합니다.

```
SEARCH_ENGINE=FULLTEXT
SEARCH_NORMALIZE_FALLBACK=true
SEARCH_SIMILAR_FALLBACK=true
SEARCH_FUZZY_FALLBACK=true
```

의미 검색(5단계)까지 쓰려면 `OPENAI_API_KEY`와 `SEARCH_SEMANTIC_FALLBACK=true`가 추가로 필요하고,
스팟별 임베딩을 채워야 합니다(`POST /admin/embeddings/backfill`, `ROLE_ADMIN` 필요).

### 확인

```bash
mysql -u "$DB_USERNAME" -p picngo -e "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='picngo' AND TABLE_NAME='spot'; SELECT @@ngram_token_size;"
```

`ft_spot_search`, `ft_spot_search_norm`, `idx_spot_map_bounds`가 보이고
`@@ngram_token_size`가 `2`면 정상입니다.

---

## 운영 서버 — 배포할 때마다 확인

**앱 배포보다 먼저** 실행합니다. 순서가 바뀌면 앱이 뜨지 않거나 검색이 전부 실패합니다.

EC2에는 git 저장소가 없고 CI/CD가 `docs/`를 함께 보내므로, 배포 후
`/home/ubuntu/docs/`에 파일이 있습니다.

### 0) 현재 상태 확인 — 무엇이 이미 적용됐는지부터

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" picngo -e "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='picngo' AND TABLE_NAME IN ('spot','course') AND COLUMN_NAME IN ('embedding','search_norm','version'); SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='picngo' AND TABLE_NAME='spot'; SELECT @@ngram_token_size;"
```

여기 없는 것만 아래에서 실행하면 됩니다. 모든 마이그레이션은 재실행해도 안전합니다(멱등).

### 1) 스키마 — 없으면 앱이 기동하지 않는 것

| 파일 | 무엇 | 안 하면 |
|---|---|---|
| `spot-embedding-column-migration.sql` | `spot.embedding` | 🔴 기동 거부 |
| `course-version-column-migration.sql` | `course.version` | 🔴 기동 거부 |
| `comment-reply-and-like-migration.sql` | 댓글 좋아요 테이블 | 🔴 기동 거부 |

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo < docs/spot-embedding-column-migration.sql
```

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo < docs/course-version-column-migration.sql
```

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo < docs/comment-reply-and-like-migration.sql
```

### 2) 검색 인덱스 — 설정을 켜기 전에 필요한 것

| 파일 | 안 하면 |
|---|---|
| `search-fulltext-index-migration.sql` | 🔴 `SEARCH_ENGINE=FULLTEXT`일 때 검색 전부 실패 |
| `search-normalized-column-migration.sql` | 🟠 2·3단계 폴백 쿼리 실패 |
| `search-map-bounds-index-migration.sql` | 🟡 지도 조회 느려짐 (기능은 동작) |

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo < docs/search-fulltext-index-migration.sql
```

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo < docs/search-normalized-column-migration.sql
```

```bash
docker exec -i picngo-mysql mysql -uroot -p"$DB_PASSWORD" --default-character-set=utf8mb4 picngo < docs/search-map-bounds-index-migration.sql
```

### 3) 정리 — 옛 스키마 제거 (한 번만, 급하지 않음)

`ddl-auto`는 추가만 하고 지우지 않아서 남은 것들입니다. 안 지워도 앱은 동작합니다.

- `db-migration-wishlist-to-spotalert.sql`
- `review-time-slot-drop-migration.sql`
- `spot-checklist-drop-migration.sql`
- `spot-categories-migration.sql`

### 4) 배포 후

`.env.prod`는 git으로 전달되지 않습니다. CI/CD가 GitHub Secrets의 `ENV_PROD`로
매 배포마다 덮어쓰므로, **서버에서 손으로 고치면 다음 배포에 사라집니다.**
검색 설정을 바꾸려면 `ENV_PROD` 시크릿을 갱신하세요.

기동 로그에 `Schema-validation` 오류가 없는지 확인하고, 의미 검색을 쓴다면
`POST /admin/embeddings/backfill`로 임베딩을 채웁니다.

---

## 데이터 마이그레이션 (선택)

`spot-overview-backfill-migration.sql`은 스팟 135건의 설명문을 채웁니다.
관광공사 API 한도 때문에 실제 설명문을 못 받아오던 시기에 **기능 확인용으로 생성한 텍스트**라,
실제 설명문을 받아온 뒤에는 실행하지 마세요.

> ⚠️ 이 파일은 `spot_data.sql`(앱 기동 시마다 실행)에 넣으면 안 됩니다.
> 재시작할 때마다 실제 설명문을 덮어씁니다. 그래서 `docs/`로 분리해 두었습니다.

---

## 왜 아직 Flyway를 안 쓰나

이 문서의 존재 자체가 "사람이 기억해야 하는 일"이 있다는 뜻입니다.
Flyway를 쓰면 배포할 때 자동으로 적용되고 이력이 DB에 남습니다.

바로 도입하지 않은 이유는 지금 `docs/*.sql`이 그대로 들어갈 수 없기 때문입니다.
측정하면서 만든 파일이라 검증용 `SELECT`·`EXPLAIN`이 섞여 있고(파일당 3~10줄),
실제로 스키마를 바꾸는 부분만 뽑아 버전별로 재정리해야 합니다.
운영 DB에 이미 테이블이 있으므로 시작 기준점(baseline)도 잡아야 합니다.

이 문서가 그 정리의 초안 역할을 합니다.
