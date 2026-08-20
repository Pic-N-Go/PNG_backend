package com.project.picngo.spot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카운터가 새면 "요청당 SQL 몇 개" 수치가 통째로 틀어진다. 그런데 틀어져도
 * 예외가 나지 않고 그럴듯한 숫자가 나와서 눈으로는 못 잡는다. 여기서 고정한다.
 */
class SqlCountingStatementInspectorTest {

    private final SqlCountingStatementInspector inspector = new SqlCountingStatementInspector();

    @Test
    @DisplayName("구간 안에서 실행된 SQL 개수를 센다")
    void countsStatementsWithinSpan() {
        SqlCountingStatementInspector.start();
        inspector.inspect("select 1");
        inspector.inspect("select 2");
        inspector.inspect("select 3");

        assertThat(SqlCountingStatementInspector.stopAndGet()).isEqualTo(3);
    }

    @Test
    @DisplayName("SQL 문자열을 변형하지 않고 그대로 돌려준다")
    void returnsSqlUnchanged() {
        // 이 인터페이스는 SQL을 고칠 수도 있는 자리다. 계측용이므로 원본을 그대로 넘겨야 한다.
        String sql = "select s.* from spot s where s.id = ?";
        assertThat(inspector.inspect(sql)).isEqualTo(sql);
    }

    @Test
    @DisplayName("stopAndGet 이후 카운터가 초기화되어 다음 구간에 새지 않는다")
    void doesNotLeakIntoNextSpan() {
        SqlCountingStatementInspector.start();
        inspector.inspect("select 1");
        SqlCountingStatementInspector.stopAndGet();

        // 스레드 풀에서 재사용되는 요청 스레드를 흉내낸다.
        SqlCountingStatementInspector.start();
        inspector.inspect("select 2");

        assertThat(SqlCountingStatementInspector.stopAndGet()).isEqualTo(1);
    }

    @Test
    @DisplayName("측정 구간 밖의 SQL은 세지 않고, start 없이 stop해도 0을 준다")
    void ignoresStatementsOutsideSpan() {
        inspector.inspect("select 1");

        assertThat(SqlCountingStatementInspector.stopAndGet()).isZero();
    }

    @Test
    @DisplayName("스레드마다 카운터가 격리된다 - 동시 요청이 서로를 오염시키면 안 된다")
    void isolatesCountersPerThread() throws Exception {
        SqlCountingStatementInspector.start();
        inspector.inspect("select 1");

        Thread other = new Thread(() -> {
            SqlCountingStatementInspector.start();
            inspector.inspect("select 2");
            inspector.inspect("select 3");
            assertThat(SqlCountingStatementInspector.stopAndGet()).isEqualTo(2);
        });
        other.start();
        other.join();

        assertThat(SqlCountingStatementInspector.stopAndGet()).isEqualTo(1);
    }
}
