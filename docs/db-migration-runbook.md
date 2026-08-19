# DB 마이그레이션 안내

스키마 변경은 **Flyway가 앱 기동 시 자동으로 적용**한다.
손으로 SQL을 실행하거나, 팀원에게 "이거 돌리세요"라고 전달할 일이 없다.

## 요약

| 상황 | 할 일 |
|---|---|
| 팀에 새로 합류 | `git pull` 후 앱 실행 |
| 다른 사람이 스키마를 바꿈 | `git pull` 후 앱 실행 |
| 내가 스키마를 바꿈 | `db/migration/`에 파일 하나 추가하고 커밋 |
| 운영 배포 | 평소대로 배포 |

---

## 스키마를 바꿀 때

`src/main/resources/db/migration/`에 파일을 만든다.

```
V3__add_spot_rating_column.sql
 ↑  ↑↑
 │  └┴─ 밑줄 두 개 (하나면 인식하지 않는다)
 └─ 다음 번호. 이 순서대로 실행된다
```

```sql
-- V3__add_spot_rating_column.sql
ALTER TABLE spot ADD COLUMN rating_sum INT NOT NULL DEFAULT 0;
```

커밋하면 끝이다. 각자 앱을 켤 때 알아서 적용된다.

**조건 검사(`IF NOT EXISTS` 같은 것)를 넣을 필요가 없다.** Flyway가 각 파일을
정확히 한 번만, 그리고 앞 버전이 모두 적용된 상태에서 실행하는 것을 보장한다.
(V2만 예외인데, 그 이유는 파일 안에 적어뒀다.)

### ⚠️ 한 번 적용된 파일은 고치지 말 것

Flyway가 파일 내용의 지문을 기록해두고 매번 대조한다. 이미 적용된 파일을 수정하면
기동을 거부한다.

```
Migration checksum mismatch for migration version 2
```

고쳐야 하면 **다음 번호 파일을 새로 만든다.** 불편해 보이지만, 이것이
모든 환경의 DB가 같은 상태임을 보장하는 장치다.

---

## 현재 구성

| 파일 | 내용 |
|---|---|
| `V1__baseline_schema.sql` | 기준 스키마 (테이블 37개) |
| `V2__converge_search_schema.sql` | 검색 인덱스·생성 컬럼 |

**V1은 빈 DB에서만 실행된다.** 이미 테이블이 있는 DB에서는
`baseline-on-migrate` 설정이 "V1까지는 이미 적용됨"으로 기록하고 V2부터 시작한다.

그래서 V2가 따로 필요했다. Flyway 도입 이전에 `docs/*.sql`을 실행하지 않은
환경(실제로 팀원 한 명의 로컬이 그랬다)에 인덱스를 채워 넣는 역할이다.

---

## DB를 처음부터 다시 만들 때

데이터가 꼬였거나 실제 데이터를 새로 받기 전이라면 스키마째 지운다.

```bash
mysql -u "$DB_USERNAME" -p -e "DROP DATABASE picngo; CREATE DATABASE picngo CHARACTER SET utf8mb4;"
```

앱을 켜면 이 순서로 진행된다.

```
Flyway     빈 DB 확인 → V1(테이블 생성) → V2(인덱스)
Hibernate  validate — 엔티티와 테이블이 맞는지 검사만
Spring     data.sql, spot_data.sql 실행 (시드 데이터)
```

### ⚠️ 테이블만 골라 지우지 말 것

`flyway_schema_history` 테이블에 적용 이력이 남는다. 테이블은 지우고 이 이력만
남기면 Flyway가 "이미 다 적용했다"고 판단해 건너뛰고, 테이블 없이 기동하다 실패한다.

**스키마째 지우면 이력도 같이 사라지므로 안전하다.**

---

## 무엇이 적용됐는지 확인

```bash
mysql -u "$DB_USERNAME" -p picngo -e "SELECT version, description, success, installed_on FROM flyway_schema_history;"
```

```
version | description            | success | installed_on
1       | baseline schema        | 1       | 2026-08-19 ...
2       | converge search schema | 1       | 2026-08-19 ...
```

지금까지 사람이 기억하던 것을 DB가 대신 기억한다.

---

## 마이그레이션을 실제 MySQL로 검증하기

평소 테스트는 H2로 돌기 때문에 이 SQL들을 검증할 수 없다.
`FULLTEXT ... WITH PARSER ngram`, 생성 컬럼의 `REGEXP_REPLACE`, `DELIMITER` +
프로시저 같은 것들이 H2에는 없다. 그래서 테스트 환경에서는 Flyway를 끈다.

대신 실제 MySQL에 적용해보는 테스트를 따로 두었다. 환경변수가 있을 때만 실행된다.

```bash
MIGRATION_TEST_URL="jdbc:mysql://127.0.0.1:3306" \
MIGRATION_TEST_USER="$DB_USERNAME" \
MIGRATION_TEST_PASSWORD="$DB_PASSWORD" \
./gradlew test --tests "*FlywayMigrationOnMySqlTest"
```

검사용 스키마를 새로 만들고 끝나면 지우므로 기존 DB는 건드리지 않는다.
새 마이그레이션을 추가했다면 커밋 전에 한 번 돌려보는 것이 좋다.

---

## 아직 Flyway가 관리하지 않는 것

### 시드 데이터

`data.sql`, `spot_data.sql`은 Flyway와 별개로 **기동할 때마다** 실행된다
(`spring.sql.init.mode=always`). `INSERT`가 `ON DUPLICATE KEY UPDATE id=id`로
감싸져 있어 여러 번 실행돼도 안전하다.

**`UPDATE` 문을 넣으면 안 된다.** 그 보호는 `INSERT`에만 붙고, 따로 쓴 `UPDATE`는
재시작마다 기존 값을 덮어쓴다. 실제로 그런 일이 있었다(자세한 내용은
`spot_data.sql` 상단 주석).

### 옛 스키마 정리용 SQL

`docs/`에 남아 있는 아래 파일들은 "이미 사라진 기능의 잔재를 지우는" 작업이다.
안 지워도 앱은 정상 동작하므로 Flyway에 넣지 않았다.

- `db-migration-wishlist-to-spotalert.sql`
- `review-time-slot-drop-migration.sql`
- `spot-checklist-drop-migration.sql`
- `spot-categories-migration.sql`

### 데이터 백필

`spot-overview-backfill-migration.sql`은 스팟 135건의 설명문을 채운다.
관광공사 API 한도 때문에 실제 설명문을 못 받아오던 시기에 **기능 확인용으로
생성한 텍스트**라, 실제 설명문을 받아온 뒤에는 실행하지 않는다.

---

## 참고

Flyway 도입 이전에 손으로 실행하던 SQL은 `docs/archive/`에 있다.
실행 대상이 아니지만, 인덱스를 만든 근거(적용 전후 `EXPLAIN`, 용량 측정 방법 등)가
주석에 남아 있어 문서로 보관한다.
