package com.project.picngo.course.agent;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.course.agent.RouteOptimizerAgent.OptimizedRoute;
import com.project.picngo.course.agent.dto.SpotCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOptimizerAgentTest {

    private final RouteOptimizerAgent agent = new RouteOptimizerAgent();

    @Test
    @DisplayName("후보 스팟들이 주어지면 최근접 이웃(Nearest Neighbor) 알고리즘으로 동선을 최적화하고 이동시간을 산출한다")
    void optimizeRoute_ordersSpotsByProximityAndCalculatesTravelTime() {
        // given: 서울 시내 스팟 5개 (광화문, 경복궁, 남산타워, 한강공원, 멀리 떨어진 수원화성)
        SpotCandidate gwanghwamun = new SpotCandidate(1L, "광화문광장", "서울 종로구", 37.5759, 126.9768, SpotCategory.PARK, "광화문");
        SpotCandidate gyeongbokgung = new SpotCandidate(2L, "경복궁", "서울 종로구", 37.5796, 126.9770, SpotCategory.HERITAGE, "경복궁");
        SpotCandidate namsan = new SpotCandidate(3L, "남산타워", "서울 용산구", 37.5511, 126.9882, SpotCategory.NIGHT_VIEW, "남산타워");
        SpotCandidate suwon = new SpotCandidate(4L, "수원화성", "경기 수원시", 37.2871, 127.0118, SpotCategory.HERITAGE, "수원화성");

        List<SpotCandidate> candidates = List.of(gwanghwamun, suwon, gyeongbokgung, namsan);

        // when: 3개 스팟 최적 동선 추출
        OptimizedRoute route = agent.optimizeRoute(candidates, 3);

        // then:
        assertThat(route.orderedSpots()).hasSize(3);
        // 광화문(1번) 다음에 가장 가까운 경복궁(2번)이 와야 하고, 먼 수원화성은 제외되어야 함
        assertThat(route.orderedSpots().get(0).name()).isEqualTo("광화문광장");
        assertThat(route.orderedSpots().get(1).name()).isEqualTo("경복궁");
        assertThat(route.orderedSpots().get(2).name()).isEqualTo("남산타워");

        // 이동 시간 리스트 검증
        assertThat(route.travelMinutesList()).hasSize(3);
        assertThat(route.travelMinutesList().get(0)).isEqualTo(0); // 첫 번째 스팟 0분
        assertThat(route.travelMinutesList().get(1)).isGreaterThanOrEqualTo(10); // 광화문->경복궁 최소 10분
    }
}
