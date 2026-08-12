package com.project.picngo.spot.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 검색 구현 방식 설정.
 *
 * <p>@ConfigurationProperties 대신 생성자 @Value를 쓴 이유: 이 프로젝트에는 아직
 * @ConfigurationProperties 사용처가 없어 @ConfigurationPropertiesScan 설정이 없다.
 * 값 하나 때문에 스캔 설정을 새로 도입하는 것보다 생성자 주입이 가볍고,
 * 테스트에서 {@code new SearchProperties(SearchEngine.LIKE)}로 바로 만들 수 있다.
 */
@Getter
@Component
public class SearchProperties {

    private final SearchEngine engine;
    private final boolean normalizeFallback;
    private final boolean similarFallback;

    // 생성자는 하나만 둔다. 편의 생성자를 추가하면 Spring이 어느 쪽으로 주입할지
    // 모호해져 기동에 실패한다(@Autowired를 따로 붙여야 하는데, 그럴 만큼 얻는 게 없다).
    public SearchProperties(
            @Value("${picngo.search.engine:LIKE}") SearchEngine engine,
            @Value("${picngo.search.normalize-fallback:false}") boolean normalizeFallback,
            @Value("${picngo.search.similar-fallback:false}") boolean similarFallback
    ) {
        this.engine = engine;
        this.normalizeFallback = normalizeFallback;
        this.similarFallback = similarFallback;
    }
}
