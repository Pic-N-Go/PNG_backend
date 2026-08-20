package com.project.picngo.spot.config;

import org.hibernate.cfg.AvailableSettings;
// Spring Boot 4에서 org.springframework.boot.autoconfigure.orm.jpa 에서 이 자리로 옮겨왔다.
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * {@link SqlCountingStatementInspector}를 Hibernate에 등록한다.
 *
 * <p>application.yaml에 클래스 이름 문자열로 적는 방법도 있지만, 그러면 클래스를
 * 옮기거나 이름을 바꿨을 때 컴파일은 통과하고 런타임에 조용히 계측만 꺼진다.
 * 상수와 인스턴스를 코드로 넘기면 그런 실패가 컴파일 시점에 잡힌다.
 */
@Configuration
public class SqlCountingConfig implements HibernatePropertiesCustomizer {

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.STATEMENT_INSPECTOR, new SqlCountingStatementInspector());
    }
}
