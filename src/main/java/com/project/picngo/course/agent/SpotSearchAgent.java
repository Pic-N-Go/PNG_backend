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
        String normalizedRegion = normalizeRegion(goal.region());
        log.info("SpotSearchAgent 후보 검색 시작: rawRegion={}, normalizedRegion={}, durationDays={}, categories={}",
                goal.region(), normalizedRegion, goal.durationDays(), goal.categories());

        int limit = Math.max(15, goal.durationDays() * 6);
        List<Spot> spots = List.of();

        // 1차: 정규화된 지역 + 카테고리 매칭
        if (goal.categories() != null && !goal.categories().isEmpty()) {
            spots = spotRepository.findByRegionAndCategories(
                    normalizedRegion,
                    goal.categories(),
                    SpotStatus.APPROVED,
                    PageRequest.of(0, limit)
            );
        }

        // 2차: 해당 지역 전체 스팟 (카테고리 폴백)
        if (spots.isEmpty()) {
            log.info("카테고리 일치 스팟 부족, 지역 전체 스팟으로 폴백: region={}", normalizedRegion);
            spots = spotRepository.findByRegion(
                    normalizedRegion,
                    SpotStatus.APPROVED,
                    PageRequest.of(0, limit)
            );
        }

        // 3차: 전국 인기 스팟 최종 폴백 (스팟이 0개인 빈 코스 생성 방지 안전망)
        if (spots.isEmpty()) {
            log.info("해당 지역 스팟 0건, 전국 인기 스팟으로 최종 폴백");
            spots = spotRepository.findListByStatusAndIsActiveTrue(
                    SpotStatus.APPROVED,
                    PageRequest.of(0, limit)
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

    public static String normalizeRegion(String rawRegion) {
        if (rawRegion == null || rawRegion.isBlank()) {
            return "서울";
        }
        String r = rawRegion.trim();
        if (r.contains("제주")) return "제주";
        if (r.contains("강원")) return "강원";
        if (r.contains("경기")) return "경기";
        if (r.contains("서울")) return "서울";
        if (r.contains("부산")) return "부산";
        if (r.contains("대구")) return "대구";
        if (r.contains("인천")) return "인천";
        if (r.contains("광주")) return "광주";
        if (r.contains("대전")) return "대전";
        if (r.contains("울산")) return "울산";
        if (r.contains("세종")) return "세종";
        if (r.contains("전남") || r.contains("전라남도")) return "전남";
        if (r.contains("전북") || r.contains("전라북도")) return "전북";
        if (r.contains("전라")) return "전라";
        if (r.contains("경남") || r.contains("경상남도")) return "경남";
        if (r.contains("경북") || r.contains("경상북도")) return "경북";
        if (r.contains("경상")) return "경상";
        if (r.contains("충남") || r.contains("충청남도")) return "충남";
        if (r.contains("충북") || r.contains("충청북도")) return "충북";
        if (r.contains("충청")) return "충청";
        if (r.contains("포항")) return "포항";
        if (r.contains("경주")) return "경주";
        if (r.contains("여수")) return "여수";
        if (r.contains("순천")) return "순천";
        if (r.contains("통영")) return "통영";
        if (r.contains("거제")) return "거제";
        if (r.contains("남해")) return "남해";
        if (r.contains("전주")) return "전주";
        if (r.contains("군산")) return "군산";
        if (r.contains("춘천")) return "춘천";
        if (r.contains("강릉")) return "강릉";
        if (r.contains("속초")) return "속초";

        if (r.equals("전국") || r.equals("미정") || r.equals("국내") || r.equals("대한민국") || r.equals("한국")) {
            return "서울";
        }

        return r;
    }
}
