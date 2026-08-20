package com.project.picngo.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이그레이션이 <b>실제 MySQL</b>에서 끝까지 적용되는지 확인한다.
 *
 * <p>평소 테스트는 H2로 돌기 때문에 이 SQL들을 검증할 수 없다. FULLTEXT ... WITH PARSER ngram,
 * 생성 컬럼의 REGEXP_REPLACE, DELIMITER + 프로시저 같은 것들이 H2에는 없어서다.
 * 그래서 이 테스트만 실제 MySQL을 쓰고, 환경변수가 있을 때만 돈다.
 *
 * <p>실행 방법(로컬 MySQL이 떠 있어야 한다):
 * <pre>
 * MIGRATION_TEST_URL="jdbc:mysql://127.0.0.1:3306" \
 * MIGRATION_TEST_USER=picngo MIGRATION_TEST_PASSWORD=... \
 * ./gradlew test --tests "*FlywayMigrationOnMySqlTest"
 * </pre>
 *
 * <p>검사용 스키마를 새로 만들고 끝나면 지우므로 기존 DB는 건드리지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_URL", matches = ".+")
class FlywayMigrationOnMySqlTest {

    private static final String SCHEMA = "picngo_flyway_check";

    private static String baseUrl() { return System.getenv("MIGRATION_TEST_URL"); }
    private static String user() { return System.getenv("MIGRATION_TEST_USER"); }
    private static String password() { return System.getenv("MIGRATION_TEST_PASSWORD"); }

    private void exec(String sql) throws Exception {
        try (Connection c = DriverManager.getConnection(baseUrl(), user(), password());
             Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private long count(String query) throws Exception {
        try (Connection c = DriverManager.getConnection(baseUrl() + "/" + SCHEMA, user(), password());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(query)) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    /** 인덱스의 컬럼 목록을 순서대로 이어붙여 돌려준다. MATCH()와 정확히 같아야 하므로 순서까지 본다. */
    private String fulltextColumns(String indexName) throws Exception {
        try (Connection c = DriverManager.getConnection(baseUrl() + "/" + SCHEMA, user(), password());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) "
                             + "FROM information_schema.STATISTICS "
                             + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'spot' "
                             + "AND INDEX_NAME = '" + indexName + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private MigrateResult migrate() {
        return Flyway.configure()
                .dataSource(baseUrl() + "/" + SCHEMA, user(), password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load()
                .migrate();
    }

    @Test
    @DisplayName("빈 DB에 V1부터 전부 적용된다 - 프로시저(DELIMITER) 포함")
    void appliesAllMigrationsOnEmptySchema() throws Exception {
        exec("DROP DATABASE IF EXISTS " + SCHEMA);
        exec("CREATE DATABASE " + SCHEMA);

        try {
            MigrateResult result = migrate();

            assertThat(result.migrationsExecuted)
                    .as("빈 DB에서는 V1부터 전부 실행되어야 한다")
                    .isGreaterThanOrEqualTo(2);

            assertThat(count("SELECT COUNT(*) FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "'"))
                    .as("기준 스키마의 테이블이 만들어졌는지")
                    .isGreaterThan(30);

            // V2가 DELIMITER + 프로시저로 쓰여 있다. Flyway가 이 문법을 못 읽으면 여기서 걸린다.
            assertThat(count("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'spot' "
                    + "AND INDEX_NAME IN ('ft_spot_search','ft_spot_search_norm','idx_spot_map_bounds')"))
                    .as("검색 인덱스 3종이 모두 생성되어야 한다")
                    .isEqualTo(3);

            assertThat(count("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'spot' "
                    + "AND COLUMN_NAME = 'search_norm'"))
                    .as("생성 컬럼이 만들어져야 한다")
                    .isEqualTo(1);

            // 임시로 만든 프로시저를 남기지 않는지. 남으면 DB마다 쓰레기가 쌓인다.
            assertThat(count("SELECT COUNT(*) FROM information_schema.ROUTINES "
                    + "WHERE ROUTINE_SCHEMA = '" + SCHEMA + "' AND ROUTINE_NAME LIKE 'pngo_%'"))
                    .as("V2가 쓴 프로시저는 스스로 정리해야 한다")
                    .isZero();

            // 빈 DB는 V1(3컬럼) → V4(2컬럼으로 재생성) 순서로 지나간다.
            // 최종 상태가 코드의 MATCH(name, address)와 맞아야 검색이 500을 내지 않는다.
            assertThat(fulltextColumns("ft_spot_search"))
                    .as("새로 만든 DB의 인덱스도 코드가 요구하는 컬럼과 같아야 한다")
                    .isEqualTo("name,address");
        } finally {
            exec("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }

    @Test
    @DisplayName("이미 테이블이 있는 DB는 V1을 건너뛰고 V2부터 적용한다")
    void skipsBaselineOnExistingSchema() throws Exception {
        exec("DROP DATABASE IF EXISTS " + SCHEMA);
        exec("CREATE DATABASE " + SCHEMA);

        try {
            // Flyway 이전부터 쓰던 DB를 흉내낸다: 테이블은 있는데 이력 테이블이 없는 상태.
            // 검색 인덱스가 없고 고아 테이블은 남아 있는, 팀원 로컬의 모습이다.
            try (Connection c = DriverManager.getConnection(baseUrl() + "/" + SCHEMA, user(), password());
                 Statement s = c.createStatement()) {
                // V3의 inquiries가 외래키로 참조한다. 실제 DB에는 당연히 있는 테이블이다.
                s.execute("CREATE TABLE users ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "email VARCHAR(100) NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("CREATE TABLE spot ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "name VARCHAR(100) NOT NULL,"
                        + "address VARCHAR(255) NOT NULL,"
                        + "overview TEXT,"
                        + "status VARCHAR(20) NOT NULL,"
                        + "is_active BIT(1) NOT NULL,"
                        + "latitude DOUBLE NOT NULL,"
                        + "longitude DOUBLE NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                // 낙관적 락 도입 전의 course. version 컬럼이 아직 없는 상태다.
                s.execute("CREATE TABLE course ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "title VARCHAR(100) NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("INSERT INTO course (title) VALUES ('기존 코스')");
                // 스팟 체크리스트가 코스로 통합되며 코드에서 사라졌지만 테이블은 남은 상태
                s.execute("CREATE TABLE checklist_item ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "spot_id BIGINT NOT NULL,"
                        + "CONSTRAINT fk_ci_spot FOREIGN KEY (spot_id) REFERENCES spot(id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("CREATE TABLE hidden_checklist_default ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "spot_id BIGINT NOT NULL,"
                        + "CONSTRAINT fk_hcd_spot FOREIGN KEY (spot_id) REFERENCES spot(id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                // 사진 저장이 photo_url에서 object_key로 바뀌기 전의 review_photo.
                // 옛 컬럼이 NOT NULL로 남아 있으면 지금 코드로는 INSERT가 전부 거부된다.
                s.execute("CREATE TABLE review_photo ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "photo_url VARCHAR(500) NOT NULL,"
                        + "object_key VARCHAR(500) NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                // 이름이 비슷하지만 이쪽 photo_url은 지금도 쓰는 컬럼이다. 같이 지우면 안 된다.
                s.execute("CREATE TABLE spot_photo ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "photo_url VARCHAR(500) NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }

            MigrateResult result = migrate();

            assertThat(result.migrationsExecuted)
                    .as("V1은 baseline으로 건너뛰고 V2부터 끝까지 실행되어야 한다")
                    .isEqualTo(3);

            assertThat(count("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'spot' "
                    + "AND INDEX_NAME IN ('ft_spot_search','ft_spot_search_norm','idx_spot_map_bounds')"))
                    .as("인덱스가 없던 DB에 V2가 채워 넣어야 한다")
                    .isEqualTo(3);

            assertThat(count("SELECT COUNT(*) FROM information_schema.TABLES "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' "
                    + "AND TABLE_NAME IN ('checklist_item','hidden_checklist_default')"))
                    .as("코드에서 사라진 고아 테이블은 V2가 지워야 한다")
                    .isZero();

            assertThat(count("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'course' "
                    + "AND COLUMN_NAME = 'version' AND IS_NULLABLE = 'NO'"))
                    .as("V1을 건너뛴 DB에도 version 컬럼이 NOT NULL로 생겨야 한다")
                    .isEqualTo(1);

            assertThat(fulltextColumns("ft_spot_search"))
                    .as("V2가 3컬럼으로 만든 인덱스를 V4가 코드에 맞춰 다시 만들어야 한다")
                    .isEqualTo("name,address");

            assertThat(count("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'review_photo' "
                    + "AND COLUMN_NAME = 'photo_url'"))
                    .as("업로드를 막던 옛 컬럼은 사라져야 한다")
                    .isZero();

            assertThat(count("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'spot_photo' "
                    + "AND COLUMN_NAME = 'photo_url'"))
                    .as("spot_photo.photo_url은 지금도 쓰는 컬럼이라 남아 있어야 한다")
                    .isEqualTo(1);
        } finally {
            exec("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }

    @Test
    @DisplayName("ddl-auto가 먼저 만든 NULL 허용 version 컬럼을 V2가 바로잡는다")
    void fixesNullableVersionColumnLeftByDdlAuto() throws Exception {
        exec("DROP DATABASE IF EXISTS " + SCHEMA);
        exec("CREATE DATABASE " + SCHEMA);

        try {
            // 앱을 먼저 켠 로컬의 모습이다. Hibernate는 컬럼을 NULL 허용으로 만들기 때문에
            // 그 전에 저장돼 있던 코스가 version = NULL로 남는다. 그 코스를 수정하는 순간
            // "NULL + 1"을 시도하다 500이 났다(Versioning.increment NPE). 실제로 겪은 일이다.
            try (Connection c = DriverManager.getConnection(baseUrl() + "/" + SCHEMA, user(), password());
                 Statement s = c.createStatement()) {
                // V3의 inquiries가 외래키로 참조한다.
                s.execute("CREATE TABLE users (id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "email VARCHAR(100) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("CREATE TABLE spot (id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "name VARCHAR(100) NOT NULL, address VARCHAR(255) NOT NULL,"
                        + "overview TEXT, status VARCHAR(20) NOT NULL, is_active BIT(1) NOT NULL,"
                        + "latitude DOUBLE NOT NULL, longitude DOUBLE NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("CREATE TABLE course (id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "title VARCHAR(100) NOT NULL, `version` BIGINT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
                s.execute("INSERT INTO course (title, `version`) VALUES ('락 도입 전 코스', NULL)");
            }

            migrate();

            assertThat(count("SELECT COUNT(*) FROM course WHERE `version` IS NULL"))
                    .as("NULL이 남아 있으면 그 코스를 수정할 때 NPE가 난다")
                    .isZero();
            assertThat(count("SELECT `version` FROM course WHERE title = '락 도입 전 코스'"))
                    .as("기존 코스는 0에서 시작해야 한다")
                    .isZero();
            assertThat(count("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'course' "
                    + "AND COLUMN_NAME = 'version' AND IS_NULLABLE = 'NO'"))
                    .as("다시 NULL이 들어오지 못하도록 NOT NULL로 바뀌어야 한다")
                    .isEqualTo(1);
        } finally {
            exec("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }

    @Test
    @DisplayName("이미 인덱스가 있는 DB에 V2를 적용해도 실패하지 않는다")
    void v2IsSafeWhenIndexesAlreadyExist() throws Exception {
        exec("DROP DATABASE IF EXISTS " + SCHEMA);
        exec("CREATE DATABASE " + SCHEMA);

        try {
            // 빈 DB에 전부 적용 (V1이 인덱스까지 만든다)
            migrate();

            // 이력만 지워서 "V2를 아직 안 돌린 DB"로 위장한다.
            // 인덱스는 이미 있는 상태에서 V2가 다시 도는 상황을 만드는 것이다.
            try (Connection c = DriverManager.getConnection(baseUrl() + "/" + SCHEMA, user(), password());
                 Statement s = c.createStatement()) {
                s.execute("DROP TABLE flyway_schema_history");
            }

            MigrateResult result = migrate();

            assertThat(result.migrationsExecuted)
                    .as("이력을 지웠으니 V2부터 끝까지 다시 실행되어야 한다")
                    .isEqualTo(3);
            assertThat(count("SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS "
                    + "WHERE TABLE_SCHEMA = '" + SCHEMA + "' AND TABLE_NAME = 'spot' "
                    + "AND INDEX_NAME IN ('ft_spot_search','ft_spot_search_norm','idx_spot_map_bounds')"))
                    .as("있는 것을 또 만들려다 실패하지 않고 그대로 유지되어야 한다")
                    .isEqualTo(3);
        } finally {
            exec("DROP DATABASE IF EXISTS " + SCHEMA);
        }
    }
}
