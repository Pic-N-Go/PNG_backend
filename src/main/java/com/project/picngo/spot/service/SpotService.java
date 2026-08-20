package com.project.picngo.spot.service;

import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.external.EmbeddingClient;
import com.project.picngo.spot.config.SearchEngine;
import com.project.picngo.spot.config.SearchProperties;
import com.project.picngo.spot.config.SqlCountingStatementInspector;
import com.project.picngo.spot.domain.EditDistance;
import com.project.picngo.spot.domain.EmbeddingVector;
import com.project.picngo.spot.domain.FullTextKeyword;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotTag;
import com.project.picngo.spot.domain.enums.ReviewTag;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.NearbySpotResponse;
import com.project.picngo.spot.dto.RecommendedSpotResponse;
import com.project.picngo.spot.dto.SpotDetailResponse;
import com.project.picngo.spot.dto.SpotPhotoResponse;
import com.project.picngo.spot.dto.SpotMapResponse;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.dto.SpotSummaryResponse;
import com.project.picngo.spot.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpotService {

    private static final int MAX_PAGE_SIZE = 50;

    // 검색 계측 지표 이름. 버킷/SLO 설정은 application.yaml의 management.metrics.distribution에 있다.
    private static final String SEARCH_TIMER = "spot.search.duration";
    private static final String SEARCH_RESULT_COUNTER = "spot.search.result";
    private static final String SEARCH_SQL_COUNT = "spot.search.sql.count";
    private static final String SEARCH_RESULT_SIZE = "spot.search.result.size";
    private static final String SEARCH_STAGE_COUNTER = "spot.search.stage";
    private static final String STAGE_PRIMARY = "primary";
    private static final String STAGE_NORMALIZED = "normalized";
    private static final String STAGE_SIMILAR = "similar";
    private static final String STAGE_FUZZY = "fuzzy";
    private static final String STAGE_SEMANTIC = "semantic";
    private static final String STAGE_NONE = "none";
    private static final String TYPE_KEYWORD = "keyword";
    private static final String TYPE_MAP = "map";
    private static final String PHASE_QUERY = "query";
    private static final String PHASE_MAPPING = "mapping";

    private final SpotRepository spotRepository;
    private final SpotTagRepository spotTagRepository;
    private final ReviewRepository reviewRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final BookmarkCollectionSpotRepository bookmarkCollectionSpotRepository;
    private final MeterRegistry meterRegistry;
    private final SearchProperties searchProperties;
    private final EmbeddingClient embeddingClient;

    public SpotDetailResponse getSpotDetail(Long spotId, Long userId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        List<SpotTag> tags = spotTagRepository.findBySpotId(spotId);
        // 2회 이상 쓰인 태그 중 상위 3개만 노출 (스팟 상세 카드에 한 줄로 들어가는 분량)
        List<String> reviewTags = reviewRepository.findFrequentTagsBySpotId(spotId).stream()
                .limit(3)
                .map(row -> ((ReviewTag) row[0]).name())
                .toList();

        List<Object[]> rows = reviewRepository.findAvgAndCountBySpotId(spotId);
        Double avgRating = rows.isEmpty() || rows.get(0)[0] == null ? null : (Double) rows.get(0)[0];
        int reviewCount = rows.isEmpty() || rows.get(0)[1] == null ? 0 : ((Long) rows.get(0)[1]).intValue();
        long photoCount = spotPhotoRepository.countBySpotId(spotId);
        // 북마크 = 1개 이상 컬렉션에 소속. userId가 null(익명 조회)이면 항상 false.
        boolean isBookmarked = userId != null
                && bookmarkCollectionSpotRepository.existsByCollection_UserIdAndSpotId(userId, spotId);
        Long myReviewId = userId == null ? null
                : reviewRepository.findIdsBySpotIdAndUserId(spotId, userId).stream().findFirst().orElse(null);

        return SpotDetailResponse.of(
                spot, tags, reviewTags,
                avgRating != null ? Math.round(avgRating * 10) / 10.0 : 0.0,
                reviewCount, photoCount, isBookmarked, myReviewId
        );
    }

    public List<RecommendedSpotResponse> getRecommendedSpots(Long userId, int limit) {
        List<Spot> spots = spotRepository.findRecommendedSpots(userId, Math.min(limit, 20));
        Set<Long> bookmarked = bookmarkedSpotIds(userId, spots);
        return spots.stream()
                .map(spot -> RecommendedSpotResponse.from(spot, isBookmarked(bookmarked, spot)))
                .toList();
    }

    public List<NearbySpotResponse> getNearbySpots(Double lat, Double lng, Double radiusKm, int limit) {
        List<Spot> spots = spotRepository.findNearbySpots(lat, lng, radiusKm, Math.min(limit, 50));
        return spots.stream()
                .map(spot -> {
                    double distance = calcDistance(lat, lng, spot.getLatitude(), spot.getLongitude());
                    return NearbySpotResponse.of(spot, distance);
                })
                .toList();
    }

    private double calcDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public SpotPhotoResponse getSpotPhotos(Long spotId) {
        if (!spotRepository.existsById(spotId)) {
            throw new CustomException(SpotErrorCode.SPOT_NOT_FOUND);
        }
        // sync 때 저장한 TourAPI 사진을 DB에서 조회 (실시간 외부 호출 제거)
        return SpotPhotoResponse.of(spotId, spotPhotoRepository.findBySpotIdAndUserIdIsNullOrderByIdAsc(spotId));
    }

    public Page<SpotResponse> getSpots(List<String> category, String sort, int page, int size, Long userId) {
        List<SpotCategory> spotCategories = parseCategories(category);
        Pageable pageable = createPageable(page, size, sort);

        Page<Spot> spots = spotCategories == null
                ? spotRepository.findAllByStatusAndIsActiveTrue(SpotStatus.APPROVED, pageable)
                : spotRepository.findAllByCategoriesAndStatusAndIsActiveTrue(spotCategories, SpotStatus.APPROVED, pageable);

        Set<Long> bookmarked = bookmarkedSpotIds(userId, spots.getContent());
        return spots.map(spot -> SpotResponse.from(spot, isBookmarked(bookmarked, spot)));
    }

    // 북마크 수와 리뷰 수를 기준으로 인기스팟 조회
    public List<SpotResponse> getPopularSpots(List<String> category, int size, Long userId){
        List<SpotCategory> spotCategories = parseCategories(category);
        Pageable pageable = createPageable(0, size, "popular");

        List<Spot> spots = spotCategories == null
                ? spotRepository.findListByStatusAndIsActiveTrue(SpotStatus.APPROVED, pageable)
                : spotRepository.findListByCategoriesAndStatusAndIsActiveTrue(spotCategories, SpotStatus.APPROVED, pageable);

        Set<Long> bookmarked = bookmarkedSpotIds(userId, spots);
        return spots.stream()
                .map(spot -> SpotResponse.from(spot, isBookmarked(bookmarked, spot)))
                .toList();
    }

    // 키워드로 스팟 검색하기
    public Page<SpotResponse> searchSpots(String keyword, List<String> category, int page, int size, Long userId) {
        if (keyword == null || keyword.isBlank()) {
            throw new CustomException(SpotErrorCode.SEARCH_KEYWORD_REQUIRED);
        }

        List<SpotCategory> spotCategories = parseCategories(category);
        boolean filtered = (spotCategories != null);

        SqlCountingStatementInspector.start();
        try {
            Timer.Sample querySample = Timer.start(meterRegistry);
            Page<Spot> spots = searchInStages(keyword.trim(), spotCategories, page, size);
            querySample.stop(searchTimer(TYPE_KEYWORD, PHASE_QUERY, filtered));

            recordSearchOutcome(TYPE_KEYWORD, spots.getTotalElements());

            // 북마크 조회는 쿼리라 매핑 타이머 밖에 둔다 — 안에 넣으면 mapping 지표가 DB 시간까지 삼킨다.
            Set<Long> bookmarked = bookmarkedSpotIds(userId, spots.getContent());

            Timer.Sample mappingSample = Timer.start(meterRegistry);
            Page<SpotResponse> response = spots.map(spot -> SpotResponse.from(spot, isBookmarked(bookmarked, spot)));
            mappingSample.stop(searchTimer(TYPE_KEYWORD, PHASE_MAPPING, filtered));

            recordResultSize(TYPE_KEYWORD, response.getNumberOfElements(), filtered);
            return response;
        } finally {
            // 예외가 나도 ThreadLocal은 반드시 정리해야 한다. 남겨두면 요청 스레드가
            // 재사용될 때 다음 요청의 카운트가 이어서 올라간다.
            recordSqlCount(TYPE_KEYWORD, filtered);
        }
    }

    /**
     * 목록에 실린 스팟 중 이 유저가 북마크한 것들의 ID.
     * 비로그인이거나 목록이 비면 쿼리를 아예 날리지 않는다(빈 IN 절 방지).
     */
    private Set<Long> bookmarkedSpotIds(Long userId, List<Spot> spots) {
        if (userId == null || spots.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> spotIds = spots.stream().map(Spot::getId).filter(Objects::nonNull).toList();
        if (spotIds.isEmpty()) {
            return Collections.emptySet();
        }
        return Set.copyOf(bookmarkCollectionSpotRepository.findBookmarkedSpotIds(userId, spotIds));
    }

    // Set.copyOf()는 contains(null)에서 NPE를 던지므로 id를 여기서 확인한다. 세 목록 메서드가 같은
    // 판정을 쓰므로 한 곳에 둔다 — 호출부에 복사되면 한쪽만 틀려도 드러나지 않는다.
    private static boolean isBookmarked(Set<Long> bookmarkedSpotIds, Spot spot) {
        Long id = spot.getId();
        return id != null && bookmarkedSpotIds.contains(id);
    }

    // 검색을 단계로 나눈다. 앞 단계가 0건일 때만 다음 단계로 물러난다.
    //
    //   primary     현재 방식(LIKE 또는 FULLTEXT) 그대로
    //   normalized  띄어쓰기와 문장부호를 무시하고 재시도
    //   similar     ngram 조각이 얼마나 겹치는지로 가장 비슷한 것 (긴 검색어의 오타)
    //   fuzzy       글자를 직접 맞대어 몇 글자 다른지로 (짧은 검색어의 오타)
    //   semantic    글자가 아니라 뜻이 가까운 것 (동의어·자연어)
    //
    // 단계별로 나눠두면 층을 하나씩 얹으면서 골든셋으로 유형별 개선폭을 따로 잴 수 있다.
    // 그리고 어느 단계에서 결과가 나왔는지 세어두면 "이 층이 실제로 쓰이긴 하나"에 답할 수 있다.
    // 폴백은 앞 단계가 빈 결과일 때만 도는 추가 쿼리라, 정상 검색의 비용은 그대로다.
    private Page<Spot> searchInStages(String keyword, List<SpotCategory> categories, int page, int size) {
        Page<Spot> primary = (searchProperties.getEngine() == SearchEngine.FULLTEXT)
                ? searchByFullText(keyword, categories, page, size)
                : searchByLike(keyword, categories, page, size);

        if (!primary.isEmpty()) {
            recordSearchStage(STAGE_PRIMARY);
            return primary;
        }

        if (searchProperties.isNormalizeFallback()) {
            Page<Spot> normalized = searchByNormalized(keyword, categories, page, size);
            if (!normalized.isEmpty()) {
                recordSearchStage(STAGE_NORMALIZED);
                return normalized;
            }
        }

        if (searchProperties.isSimilarFallback()) {
            Page<Spot> similar = searchBySimilarity(keyword, categories, page, size);
            if (!similar.isEmpty()) {
                recordSearchStage(STAGE_SIMILAR);
                return similar;
            }
        }

        if (searchProperties.isFuzzyFallback()) {
            Page<Spot> fuzzy = searchByFuzzy(keyword, categories, page, size);
            if (!fuzzy.isEmpty()) {
                recordSearchStage(STAGE_FUZZY);
                return fuzzy;
            }
        }

        if (searchProperties.isSemanticFallback()) {
            Page<Spot> semantic = searchBySemantic(keyword, categories, page, size);
            if (!semantic.isEmpty()) {
                recordSearchStage(STAGE_SEMANTIC);
                return semantic;
            }
        }

        recordSearchStage(STAGE_NONE);
        return primary;
    }

    // 마지막 4단계. 앞의 세 단계는 전부 "글자가 얼마나 겹치는가"로 찾는데, 이 단계는
    // 검색어를 임베딩으로 바꿔서 뜻이 가까운 스팟을 찾는다. "해질녘 걷기 좋은 곳"처럼
    // 검색어와 스팟 설명에 겹치는 글자가 하나도 없어도 잡아낼 수 있는 유일한 단계다.
    //
    // 인덱스 없이 후보 전부를 코사인 유사도로 훑는 브루트포스다. 스팟 수가 수천 건
    // 규모라 이 정도로 충분하고(search-eval/evaluate-vector.js로 확인), pgvector 같은
    // 별도 ANN 인덱스/DB를 새로 들일 이유가 없다.
    private Page<Spot> searchBySemantic(String keyword, List<SpotCategory> categories, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        Optional<float[]> queryVector = embeddingClient.embed(keyword);
        if (queryVector.isEmpty()) {
            return Page.empty(pageable);
        }

        List<SpotRepository.EmbeddingCandidate> candidates = (categories == null)
                ? spotRepository.findEmbeddingCandidates(SpotStatus.APPROVED)
                : spotRepository.findEmbeddingCandidatesByCategories(categories, SpotStatus.APPROVED);

        if (candidates.isEmpty()) {
            return Page.empty(pageable);
        }

        float[] query = queryVector.get();
        Map<Long, Float> scoreById = candidates.stream()
                .collect(Collectors.toMap(
                        SpotRepository.EmbeddingCandidate::getId,
                        c -> EmbeddingVector.cosineSimilarity(query, EmbeddingVector.decode(c.getEmbedding()))
                ));

        List<Long> rankedIds = scoreById.entrySet().stream()
                .sorted(Map.Entry.<Long, Float>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();

        return pageOfRankedIds(rankedIds, pageable);
    }

    // 마지막 오타 교정 단계. 위 유사도 검색과 목적은 같지만 방식이 다르다.
    //
    // 유사도 검색은 두 글자짜리 조각(ngram)이 겹치는지로 찾는데, 검색어가 짧으면
    // 조각 수가 적어 오타 하나에 전멸한다 - '헙재'는 조각이 하나뿐이라 그게 틀리면
    // 남는 게 없고, '오셜록'은 가운데를 틀리면 조각 둘이 모두 깨진다. 색인을 어떻게
    // 손봐도 이 한계는 남는다.
    //
    // 여기서는 색인을 포기하고 후보 전부를 자바에서 글자 단위로 비교한다.
    // 대신 조각이 깨지든 말든 상관이 없어 짧은 검색어의 오타에 강하다.
    // 완전탐색이라 비싸지만, 앞 단계가 모두 0건일 때만 도는 마지막 폴백이고
    // 스팟이 수천 건 규모라 감당할 수 있다(의미 검색 단계와 같은 판단).
    private Page<Spot> searchByFuzzy(String keyword, List<SpotCategory> categories, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        // 색인과 같은 규칙으로 정규화한다 - 공백/문장부호를 뺀 글자만 남긴다.
        String normalized = FullTextKeyword.toSimilarityTerms(keyword);
        if (normalized == null || normalized.length() < EditDistance.MIN_KEYWORD_LENGTH) {
            return Page.empty(pageable);
        }

        int allowed = EditDistance.allowedDistance(normalized.length());

        List<SpotRepository.FuzzyCandidate> candidates = (categories == null)
                ? spotRepository.findFuzzyCandidates(SpotStatus.APPROVED)
                : spotRepository.findFuzzyCandidatesByCategories(categories, SpotStatus.APPROVED);

        List<ScoredSpot> scored = new ArrayList<>();
        for (SpotRepository.FuzzyCandidate candidate : candidates) {
            String target = FullTextKeyword.toSimilarityTerms(
                    (candidate.getName() == null ? "" : candidate.getName())
                            + (candidate.getAddress() == null ? "" : candidate.getAddress()));
            if (target == null) {
                continue;
            }

            int distance = EditDistance.bestSubstringDistance(normalized, target);
            if (distance <= allowed) {
                scored.add(new ScoredSpot(candidate.getId(), distance, target.length()));
            }
        }

        if (scored.isEmpty()) {
            return Page.empty(pageable);
        }

        // 덜 틀린 것부터. 거리가 같으면 짧은 이름을 앞에 둔다 - 같은 오차라면
        // 긴 이름 어딘가에 우연히 걸린 것보다 이름 전체가 비슷한 쪽이 사용자가 찾던 것에
        // 가깝다. 마지막 id 정렬은 순서를 매 요청 똑같이 만들기 위한 것이다
        // (같은 검색어인데 페이지마다 순서가 달라지면 중복/누락이 생긴다).
        List<Long> rankedIds = scored.stream()
                .sorted(Comparator.comparingInt(ScoredSpot::distance)
                        .thenComparingInt(ScoredSpot::targetLength)
                        .thenComparing(ScoredSpot::id))
                .map(ScoredSpot::id)
                .toList();

        return pageOfRankedIds(rankedIds, pageable);
    }

    private record ScoredSpot(Long id, int distance, int targetLength) {
    }

    // 점수순으로 줄 세운 id 목록에서 요청한 페이지만 잘라 엔티티로 되돌린다.
    // 완전탐색 단계(fuzzy/semantic)가 공유한다 - 점수를 매기는 방식만 다르고
    // 그 뒤 처리는 같다.
    private Page<Spot> pageOfRankedIds(List<Long> rankedIds, Pageable pageable) {
        List<Long> pageIds = rankedIds.stream()
                .skip(pageable.getOffset())
                .limit(pageable.getPageSize())
                .toList();

        if (pageIds.isEmpty()) {
            return Page.empty(pageable);
        }

        // 페이지에 들어갈 것만 엔티티로 읽는다. 여기서만 EAGER 연관 비용을 치른다.
        Map<Long, Spot> spotsById = spotRepository.findByIdIn(pageIds).stream()
                .collect(Collectors.toMap(Spot::getId, spot -> spot));
        List<Spot> ordered = pageIds.stream()
                .map(spotsById::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(ordered, pageable, rankedIds.size());
    }

    // 마지막 수단. 정확히 일치하는 게 없을 때 가장 비슷한 것을 돌려준다.
    // 앞 단계들과 달리 "포함하는가"가 아니라 "얼마나 겹치는가"로 찾으므로
    // 오타가 섞여도 원본을 잡아낼 수 있다. 대신 엉뚱한 결과도 섞인다.
    private Page<Spot> searchBySimilarity(String keyword, List<SpotCategory> categories, int page, int size) {
        String terms = FullTextKeyword.toSimilarityTerms(keyword);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        if (terms == null) {
            return Page.empty(pageable);
        }

        if (categories == null) {
            return spotRepository.searchSpotsSimilar(terms, SpotStatus.APPROVED.name(), pageable);
        }

        List<String> categoryNames = categories.stream().map(SpotCategory::name).toList();
        return spotRepository.searchSpotsSimilarByCategories(
                terms, categoryNames, SpotStatus.APPROVED.name(), pageable);
    }

    // 띄어쓰기 무시 검색. 대상 컬럼(search_norm)이 공백을 뺀 값이라 검색어에서도 뺀다.
    private Page<Spot> searchByNormalized(String keyword, List<SpotCategory> categories, int page, int size) {
        String phrase = FullTextKeyword.toSpacelessPhrase(keyword);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        if (phrase == null) {
            return Page.empty(pageable);
        }

        // 정렬용 키워드는 검색식과 따로 넘긴다. phrase는 따옴표가 붙은 구문식이라 LIKE로 쓰면
        // 아무것도 맞지 않는다. 비교 대상이 search_norm이므로 공백을 뺀 원본을 준다.
        String sortKeyword = FullTextKeyword.toSimilarityTerms(keyword);

        if (categories == null) {
            return spotRepository.searchSpotsNormalized(phrase, sortKeyword, SpotStatus.APPROVED.name(), pageable);
        }

        List<String> categoryNames = categories.stream().map(SpotCategory::name).toList();
        return spotRepository.searchSpotsNormalizedByCategories(
                phrase, sortKeyword, categoryNames, SpotStatus.APPROVED.name(), pageable);
    }

    // 이름 우선 정렬이 쿼리 안에 있으므로 정렬 없는 Pageable을 넘긴다
    // (Sort를 얹으면 Spring이 ORDER BY를 덧붙여 이름 우선순위가 뒤로 밀린다).
    private Page<Spot> searchByLike(String keyword, List<SpotCategory> categories, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        return (categories == null)
                ? spotRepository.searchSpots(keyword, SpotStatus.APPROVED, pageable)
                : spotRepository.searchSpotsByCategories(keyword, categories, SpotStatus.APPROVED, pageable);
    }

    // 네이티브 쿼리라 정렬은 쿼리 안에 박혀 있다. 여기서 Sort를 얹으면 ORDER BY가
    // 두 번 붙으므로 정렬 없는 Pageable을 넘긴다(SpotRepository 주석 참고).
    private Page<Spot> searchByFullText(String keyword, List<SpotCategory> categories, int page, int size) {
        String phrase = FullTextKeyword.toPhrase(keyword);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        // 연산자만으로 이뤄진 검색어는 전문검색식으로 만들 수 없다. 억지로 검색하면
        // 엉뚱한 결과가 나오므로 결과 없음으로 처리한다.
        if (phrase == null) {
            log.debug("전문검색식으로 변환할 수 없는 검색어라 빈 결과 반환: {}", keyword);
            return Page.empty(pageable);
        }

        // 정렬용 키워드는 검색식과 따로 넘긴다 — phrase는 따옴표가 붙은 BOOLEAN MODE 구문식이라
        // LIKE 비교에 쓰면 '%"한라산"%'이 되어 어떤 이름에도 맞지 않는다(이름 우선 정렬이 무동작).
        if (categories == null) {
            return spotRepository.searchSpotsFullText(phrase, keyword, SpotStatus.APPROVED.name(), pageable);
        }

        List<String> categoryNames = categories.stream().map(SpotCategory::name).toList();
        return spotRepository.searchSpotsFullTextByCategories(
                phrase, keyword, categoryNames, SpotStatus.APPROVED.name(), pageable);
    }

    private Pageable createPageable(int page, int size, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        return PageRequest.of(safePage, safeSize, resolveSort(sort));
    }

    private Sort resolveSort(String sort) {
        if ("popular".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "bookmarkCount")
                    .and(Sort.by(Sort.Direction.DESC, "reviewCount"));
        }

        if ("score".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "photogenicScore");
        }

        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    // 카테고리 다중 선택. 값이 없으면 null을 돌려주고 호출부가 필터 없는 쿼리로 분기한다.
    // (컬렉션 파라미터에 null/빈 리스트를 넘기면 IN 절 렌더링이 깨지므로 빈 리스트를 만들지 않는다.)
    private List<SpotCategory> parseCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }

        List<SpotCategory> parsed = categories.stream()
                .filter(c -> c != null && !c.isBlank())
                .flatMap(c -> java.util.Arrays.stream(c.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseCategoryOrNull)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return parsed.isEmpty() ? null : parsed;
    }

    private SpotCategory parseCategoryOrNull(String category) {
        try {
            return SpotCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("미지원/유효하지 않은 스팟 카테고리 요청 파라미터 스킵: {}", category);
            return null;
        }
    }

    // 지도 영역 좌표를 기준으로 화면에 표시할 스팟 핀 목록 조회
    public List<SpotMapResponse> getMapSpots(com.project.picngo.spot.dto.MapBoundsRequest request) {
        validateMapBounds(
                request.southWestLat(),
                request.southWestLng(),
                request.northEastLat(),
                request.northEastLng()
        );

        List<SpotCategory> spotCategories = parseCategories(request.category());
        Pageable mapPageable = PageRequest.of(0, request.getSizeOrDefault());
        boolean filtered = (spotCategories != null);

        SqlCountingStatementInspector.start();
        try {
            Timer.Sample querySample = Timer.start(meterRegistry);
            List<Spot> spots = filtered
                    ? spotRepository.findSpotsInMapBoundsByCategories(
                            request.southWestLat(),
                            request.southWestLng(),
                            request.northEastLat(),
                            request.northEastLng(),
                            spotCategories,
                            SpotStatus.APPROVED,
                            mapPageable)
                    : spotRepository.findSpotsInMapBounds(
                            request.southWestLat(),
                            request.southWestLng(),
                            request.northEastLat(),
                            request.northEastLng(),
                            SpotStatus.APPROVED,
                            mapPageable);
            querySample.stop(searchTimer(TYPE_MAP, PHASE_QUERY, filtered));

            recordSearchOutcome(TYPE_MAP, spots.size());

            Timer.Sample mappingSample = Timer.start(meterRegistry);
            List<SpotMapResponse> response = spots.stream()
                    .map(SpotMapResponse::from)
                    .toList();
            mappingSample.stop(searchTimer(TYPE_MAP, PHASE_MAPPING, filtered));

            recordResultSize(TYPE_MAP, response.size(), filtered);
            return response;
        } finally {
            recordSqlCount(TYPE_MAP, filtered);
        }
    }

    // 검색 지연을 쿼리 구간과 DTO 매핑 구간으로 쪼개서 잰다.
    // 매핑 구간에도 DB 시간이 섞이는 게 정상이다 - Spot.categories가 LAZY라
    // SpotResponse.from()의 getCategoryNames()에서 스팟마다 추가 select가 나간다.
    // 두 구간을 나눠 재야 "검색 SQL 자체가 느린 것"과 "N+1이 느린 것"을 구분할 수 있고,
    // 이 구분이 없으면 어떤 개선(인덱스 / fetch join / 스토리지 교체)이 유효한지 알 수 없다.
    //
    // filtered 태그를 붙이는 이유: 카테고리 필터가 붙으면 컬렉션 상관 서브쿼리가
    // 추가돼서 실행 계획이 완전히 달라진다. 한 계열로 섞으면 두 분포가 겹쳐 보인다.
    // 태그는 전부 저카디널리티 값만 쓴다(검색어를 태그로 넣으면 시계열이 폭발한다).
    //
    // engine 태그는 LIKE/FULLTEXT 두 번의 측정을 한 대시보드에서 나란히 보기 위한 것이다.
    // 지도 검색에도 같은 태그를 붙이는데, 지도 쿼리는 엔진 설정과 무관하므로
    // 두 측정에서 값이 같아야 정상이다 - 대조군 역할을 해서, 지도까지 달라졌다면
    // 차이의 원인이 인덱스가 아니라 측정 환경에 있다는 신호가 된다.
    private Timer searchTimer(String type, String phase, boolean filtered) {
        return Timer.builder(SEARCH_TIMER)
                .description("스팟 검색 처리 시간")
                .tag("type", type)
                .tag("phase", phase)
                .tag("filtered", String.valueOf(filtered))
                .tag("engine", engineTag())
                .register(meterRegistry);
    }

    // application.yaml의 공통 태그(management.metrics.tags.engine)와 대소문자가 같아야 한다.
    // 다르면 hikaricp는 engine="FULLTEXT", 검색 지표는 engine="fulltext"가 되어
    // 그라파나에서 engine으로 필터할 때 한쪽만 걸린다. enum 이름을 그대로 쓴다.
    private String engineTag() {
        return searchProperties.getEngine().name();
    }

    // 요청 하나가 실행한 SQL 개수. 지연시간만으로는 "무거운 쿼리 1개"와
    // "가벼운 쿼리 100개"를 구별할 수 없는데, 둘은 해법이 완전히 다르다.
    // 전자는 인덱스 문제고 후자는 N+1이라 페치 전략이나 DTO 프로젝션으로 풀어야 한다.
    //
    // 결과 건수와 같이 기록하는 이유: 지도는 한 번에 0~100건을 돌려주므로
    // SQL 수도 같이 오르내린다. 두 값이 있어야 "건당 SQL 몇 개"를 계산할 수 있고,
    // 그 비율이 1에 가까울수록 N+1이 없다는 뜻이다.
    private void recordSqlCount(String type, boolean filtered) {
        int executed = SqlCountingStatementInspector.stopAndGet();

        DistributionSummary.builder(SEARCH_SQL_COUNT)
                .description("요청 하나가 실행한 SQL 개수")
                .baseUnit("queries")
                .tag("type", type)
                .tag("filtered", String.valueOf(filtered))
                .tag("engine", engineTag())
                .register(meterRegistry)
                .record(executed);
    }

    // 어느 단계에서 결과가 나왔는지. 폴백 층을 추가할 때마다 그 층이 실제로
    // 얼마나 쓰이는지 알아야 유지할 값어치를 판단할 수 있다.
    // normalized 비중이 0에 가깝다면 그 층은 비용만 쓰고 있는 것이다.
    private void recordSearchStage(String stage) {
        Counter.builder(SEARCH_STAGE_COUNTER)
                .description("검색 결과가 나온 단계")
                .tag("stage", stage)
                .tag("engine", engineTag())
                .register(meterRegistry)
                .increment();
    }

    private void recordResultSize(String type, int size, boolean filtered) {
        DistributionSummary.builder(SEARCH_RESULT_SIZE)
                .description("응답에 담긴 스팟 수")
                .baseUnit("spots")
                .tag("type", type)
                .tag("filtered", String.valueOf(filtered))
                .tag("engine", engineTag())
                .register(meterRegistry)
                .record(size);
    }

    // 결과가 0건인 검색의 비율.
    // LIKE '%키워드%' 기반 검색은 오타/동의어/의미 검색을 원리적으로 못 잡는데,
    // 이건 인덱스를 붙여도 해결되지 않는 한계라 응답 속도와는 별개로 추적해야 한다.
    // "검색 요청의 N%가 0건 반환"이 검색 방식 자체를 바꿀지 판단하는 근거가 된다.
    private void recordSearchOutcome(String type, long resultCount) {
        Counter.builder(SEARCH_RESULT_COUNTER)
                .description("스팟 검색 결과 유무")
                .tag("type", type)
                .tag("outcome", resultCount == 0 ? "zero" : "hit")
                .tag("engine", engineTag())
                .register(meterRegistry)
                .increment();
    }

    private void validateMapBounds(
            Double southWestLat,
            Double southWestLng,
            Double northEastLat,
            Double northEastLng
    ){
        if (southWestLat > northEastLat || southWestLng > northEastLng) {
            throw new CustomException(SpotErrorCode.INVALID_MAP_BOUNDS);
        }
    }

    // 지도 핀을 선택했을 때 보여줄 스팟 요약 정보 조회
    public SpotSummaryResponse getSpotSummary(Long id){
        return spotRepository.findByIdAndStatusAndIsActiveTrue(id, SpotStatus.APPROVED)
                .map(SpotSummaryResponse::from)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
    }
}
