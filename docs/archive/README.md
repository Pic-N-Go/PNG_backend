# 아카이브 — Flyway 이전에 손으로 실행하던 마이그레이션

여기 있는 SQL은 **더 이상 실행하지 않는다.** 같은 내용이 Flyway로 옮겨졌고,
이제 앱이 뜰 때 자동으로 적용된다.

| 이 파일 | 지금은 어디에 |
|---|---|
| `search-fulltext-index-migration.sql` | `db/migration/V2__converge_search_schema.sql` |
| `search-normalized-column-migration.sql` | 〃 |
| `search-map-bounds-index-migration.sql` | 〃 |
| `spot-embedding-column-migration.sql` | `db/migration/V1__baseline_schema.sql` |
| `comment-reply-and-like-migration.sql` | 〃 |

## 지우지 않고 남긴 이유

옮겨간 쪽에는 **실제로 스키마를 바꾸는 부분만** 넣었다. 배포마다 실행돼봐야
볼 사람이 없는 검증용 조회는 뺐다. 그런데 그 조회들이 **왜 이 인덱스를 만들었는지**를
설명하는 근거였다.

예를 들어 `search-fulltext-index-migration.sql`에는 이런 것들이 들어 있다.

- 적용 전후의 `EXPLAIN` — `type: ALL, key: NULL, rows: 94,099`에서
  `type: fulltext, key: ft_spot_search`로 바뀌는 것
- 전문검색 인덱스가 차지하는 실제 용량을 재는 법
  (`information_schema.TABLES.INDEX_LENGTH`로는 안 잡히고
  `INNODB_TABLESPACES`에서 `fts_%` 보조 테이블을 합산해야 한다)
- ngram 토큰 크기가 왜 2여야 하는지

나중에 "이 인덱스 지워도 되나?", "왜 이렇게 만들었지?"를 물을 때 답이 되는 기록이다.
그래서 실행 대상에서만 빼고 문서로 남겼다.

## 주의

이 파일들을 다시 실행할 일은 없다. 스키마를 바꿔야 하면
`src/main/resources/db/migration/`에 새 버전 파일을 추가한다.
자세한 방법은 `docs/db-migration-runbook.md` 참고.
