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

    public record RegionPair(String primary, String secondary) {}

    public List<SpotCandidate> searchCandidates(PlanGoal goal) {
        RegionPair regions = resolveRegionPair(goal.region());
        log.info("SpotSearchAgent 후보 검색 시작: rawRegion={}, primary={}, secondary={}, durationDays={}, categories={}",
                goal.region(), regions.primary(), regions.secondary(), goal.durationDays(), goal.categories());

        int limit = Math.max(15, goal.durationDays() * 6);
        List<Spot> spots = List.of();

        // 1차: 정규화된 지역(primary, secondary) + 카테고리 매칭
        if (goal.categories() != null && !goal.categories().isEmpty()) {
            spots = spotRepository.findByRegionAndCategories(
                    regions.primary(),
                    regions.secondary(),
                    goal.categories(),
                    SpotStatus.APPROVED,
                    PageRequest.of(0, limit)
            );
        }

        // 2차: 해당 지역 전체 스팟 (카테고리 폴백)
        if (spots.isEmpty()) {
            log.info("카테고리 일치 스팟 부족, 지역 전체 스팟으로 폴백: primary={}, secondary={}",
                    regions.primary(), regions.secondary());
            spots = spotRepository.findByRegion(
                    regions.primary(),
                    regions.secondary(),
                    SpotStatus.APPROVED,
                    PageRequest.of(0, limit)
            );
        }

        // 3차: 전국 인기 스팟 최종 폴백 (유효 대한민국 좌표만, 인기순 정렬)
        if (spots.isEmpty()) {
            log.info("해당 지역 스팟 0건, 전국 인기 스팟으로 최종 폴백");
            spots = spotRepository.findListByStatusAndIsActiveTrue(
                    SpotStatus.APPROVED,
                    PageRequest.of(0, limit)
            );
        }

        // 최종 가드: 대한민국 유효 좌표(위도 33.0~38.9, 경도 124.0~132.0)만 엄격히 통과
        return spots.stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null
                        && s.getLatitude() >= 33.0 && s.getLatitude() <= 38.9
                        && s.getLongitude() >= 124.0 && s.getLongitude() <= 132.0)
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

    public static RegionPair resolveRegionPair(String rawRegion) {
        if (rawRegion == null || rawRegion.isBlank()) {
            return new RegionPair("서울", "서울특별시");
        }
        String r = rawRegion.trim();
        if (r.contains("충남") || r.contains("충청남")) return new RegionPair("충청남도", "충남");
        if (r.contains("충북") || r.contains("충청북")) return new RegionPair("충청북도", "충북");
        if (r.contains("전남") || r.contains("전라남")) return new RegionPair("전라남도", "전남");
        if (r.contains("전북") || r.contains("전라북")) return new RegionPair("전북", "전라북도");
        if (r.contains("경남") || r.contains("경상남")) return new RegionPair("경상남도", "경남");
        if (r.contains("경북") || r.contains("경상북")) return new RegionPair("경상북도", "경북");
        if (r.contains("강원")) return new RegionPair("강원", "강원특별자치도");
        if (r.contains("제주")) return new RegionPair("제주", "제주특별자치도");
        if (r.contains("경기")) return new RegionPair("경기", "경기도");
        if (r.contains("서울")) return new RegionPair("서울", "서울특별시");
        if (r.contains("부산")) return new RegionPair("부산", "부산광역시");
        if (r.contains("대구")) return new RegionPair("대구", "대구광역시");
        if (r.contains("인천")) return new RegionPair("인천", "인천광역시");
        if (r.contains("광주")) return new RegionPair("광주", "광주광역시");
        if (r.contains("대전")) return new RegionPair("대전", "대전광역시");
        if (r.contains("울산")) return new RegionPair("울산", "울산광역시");
        if (r.contains("세종")) return new RegionPair("세종", "세종특별자치시");

        // 충남 서해안 주요 지자체
        if (r.contains("태안")) return new RegionPair("태안", "충청남도");
        if (r.contains("보령")) return new RegionPair("보령", "충청남도");
        if (r.contains("서산")) return new RegionPair("서산", "충청남도");
        if (r.contains("당진")) return new RegionPair("당진", "충청남도");

        // 기타 주요 지자체
        if (r.contains("강릉")) return new RegionPair("강릉", "강원");
        if (r.contains("속초")) return new RegionPair("속초", "강원");
        if (r.contains("경주")) return new RegionPair("경주", "경상북도");
        if (r.contains("여수")) return new RegionPair("여수", "전라남도");
        if (r.contains("순천")) return new RegionPair("순천", "전라남도");
        if (r.contains("전주")) return new RegionPair("전주", "전북");
        if (r.contains("춘천")) return new RegionPair("춘천", "강원");
        if (r.contains("포항")) return new RegionPair("포항", "경상북도");
        if (r.contains("통영")) return new RegionPair("통영", "경상남도");
        if (r.contains("거제")) return new RegionPair("거제", "경상남도");

        if (r.equals("전국") || r.equals("미정") || r.equals("국내") || r.equals("대한민국") || r.equals("한국")) {
            return new RegionPair("서울", "서울특별시");
        }

        return new RegionPair(r, null);
    }

    public static String normalizeRegion(String rawRegion) {
        return resolveRegionPair(rawRegion).primary();
    }
}
