package com.project.picngo.spot.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * 요청 하나가 실제로 몇 개의 SQL을 실행했는지 센다.
 *
 * <p>동시 사용자 수를 늘려 한계를 찾는 측정과는 목적이 다르다. 여기서 보려는 건
 * <b>사용자 한 명이 화면 하나를 여는 데 드는 비용</b>이다. 지연시간만 봐서는
 * "쿼리 하나가 100ms"인지 "쿼리 100개가 각 1ms"인지 구별할 수 없는데,
 * 후자라면 부하가 아니라 매핑 설계의 문제라 해법이 완전히 다르다.
 *
 * <p>Hibernate가 SQL을 내보낼 때마다 {@link #inspect(String)}가 불린다.
 * 카운터를 ThreadLocal에 두는 이유는 요청 스레드별로 격리하기 위해서다 -
 * 인스턴스 필드에 두면 동시 요청이 서로의 카운트를 오염시킨다.
 *
 * <p>SQL 문자열은 손대지 않고 그대로 돌려준다. 이 인터페이스는 SQL을 고칠 수도
 * 있는 자리라, 계측 목적으로 쓸 때는 원본을 반드시 그대로 반환해야 한다.
 */
public class SqlCountingStatementInspector implements StatementInspector {

    // int[]를 쓰는 이유: Integer를 넣으면 증가시킬 때마다 set()을 다시 호출해야 한다.
    // 가변 배열 한 칸이면 참조를 한 번만 꺼내고 값만 올리면 된다.
    private static final ThreadLocal<int[]> COUNTER = new ThreadLocal<>();

    /** 측정 구간 시작. 반드시 {@link #stopAndGet()}과 try-finally로 짝지어야 한다. */
    public static void start() {
        COUNTER.set(new int[1]);
    }

    /**
     * 측정 구간 종료. ThreadLocal을 지운다.
     *
     * <p>지우지 않으면 스레드 풀에서 재사용되는 요청 스레드에 값이 남아
     * 다음 요청의 카운트가 이어져 올라간다.
     *
     * @return 구간 안에서 실행된 SQL 수. start() 없이 호출하면 0.
     */
    public static int stopAndGet() {
        int[] counter = COUNTER.get();
        COUNTER.remove();
        return (counter == null) ? 0 : counter[0];
    }

    @Override
    public String inspect(String sql) {
        int[] counter = COUNTER.get();
        if (counter != null) {
            counter[0]++;
        }
        return sql;
    }
}
