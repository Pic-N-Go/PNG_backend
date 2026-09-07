package com.project.picngo.course.agent;

import com.project.picngo.course.agent.dto.PlanGoal;
import com.project.picngo.course.agent.dto.SpotCandidate;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * [워커 2: 스팟 탐색 전문 에이전트]
 * 플래너의 목표(지역, 카테고리 등)에 맞는 실제 스팟들을 DB에서 추출합니다.
 * 실존하는 스팟만 반환하므로 LLM의 환각(Hallucination)을 사전에 차단합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpotSearchAgent {

    private final SpotRepository spotRepository;

    public List<SpotCandidate> searchCandidates(PlanGoal goal) {
        log.info("SpotSearchAgent 후보 검색 시작: region={}, categories={}", goal.region(), goal.categories());

        List<Spot> spots = List.of();
        if (goal.categories() != null && !goal.categories().isEmpty()) {
            spots = spotRepository.findByRegionAndCategories(
                    goal.region(),
                    goal.categories(),
                    SpotStatus.APPROVED,
                    PageRequest.of(0, 15)
            );
        }

        if (spots.isEmpty()) {
            log.info("카테고리 일치 스팟 부족, 지역 전체 스팟으로 폴백: region={}", goal.region());
            spots = spotRepository.findByRegion(
                    goal.region(),
                    SpotStatus.APPROVED,
                    PageRequest.of(0, 15)
            );
        }

        return spots.stream()
                .map(s -> new SpotCandidate(
                        s.getId(),
                        s.getName(),
                        s.getAddress(),
                        s.getLatitude(),
                        s.getLongitude(),
                        s.getCategories().stream().findFirst().orElse(null),
                        s.getOverview()
                ))
                .toList();
    }
}
