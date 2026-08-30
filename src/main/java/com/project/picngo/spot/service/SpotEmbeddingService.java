package com.project.picngo.spot.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.external.EmbeddingClient;
import com.project.picngo.spot.domain.EmbeddingVector;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스팟 하나의 임베딩을 계산해 저장한다. 신규 스팟 이벤트({@link SpotEmbeddingEventListener})와
 * 백필 배치({@link SpotEmbeddingBackfillScheduler})가 이 로직을 공유한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotEmbeddingService {

    private final SpotRepository spotRepository;
    private final EmbeddingClient embeddingClient;

    /**
     * 스팟이 없으면 조용히 false. 이벤트 리스너가 쓰는 경로라, 커밋 직후 스팟이
     * 지워진 드문 경우에 예외를 던져봐야 되돌릴 것도 없고 로그만 지저분해진다.
     */
    @Transactional
    public boolean embedSpot(Long spotId) {
        return spotRepository.findById(spotId)
                .map(this::embedSpot)
                .orElse(false);
    }

    /**
     * 관리자가 특정 스팟을 다시 계산할 때 쓴다. 이미 임베딩이 있어도 새로 덮어쓴다
     * (내용을 고친 뒤 부르는 것이 이 메서드의 용도다).
     *
     * <p>위와 달리 스팟이 없으면 예외를 던진다 - 사람이 부른 요청이므로
     * "없는 스팟"과 "계산 실패"가 구분돼야 한다.
     */
    @Transactional
    public boolean recompute(Long spotId) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));
        return embedSpot(spot);
    }

    private boolean embedSpot(Spot spot) {
        return embed(spot.getId(), spot.getName(), spot.getAddress(), spot.getOverview(), spot.getCategories());
    }

    /**
     * @return 실제로 임베딩을 저장했으면 true. 호출부(백필 배치)는 이 값으로 진행 여부를
     *         판단한다 - 전부 false면 API 키나 외부 API 문제이므로 계속 시도해봐야
     *         비용만 든다.
     */
    @Transactional
    public boolean embed(Long id, String name, String address, String overview) {
        return embed(id, name, address, overview, null);
    }

    @Transactional
    public boolean embed(Long id, String name, String address, String overview, java.util.Set<com.project.picngo.common.domain.SpotCategory> categories) {
        String text = buildText(name, address, overview, categories);
        if (text.isBlank()) {
            return false;
        }

        return embeddingClient.embed(text)
                .map(vector -> {
                    spotRepository.updateEmbedding(id, EmbeddingVector.encode(vector));
                    return true;
                })
                .orElseGet(() -> {
                    log.debug("임베딩 계산 실패/스킵 (spotId: {})", id);
                    return false;
                });
    }

    // 이름/주소/카테고리/개요를 하나의 문장으로 합쳐 의미 검색 대상 텍스트로 삼는다.
    private String buildText(String name, String address, String overview, java.util.Set<com.project.picngo.common.domain.SpotCategory> categories) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) {
            sb.append(name.trim()).append(". ");
        }
        if (address != null && !address.isBlank()) {
            sb.append(address.trim()).append(". ");
        }
        if (categories != null && !categories.isEmpty()) {
            String categoryKeywords = categories.stream()
                    .map(com.project.picngo.common.domain.SpotCategory::getKeywords)
                    .collect(java.util.stream.Collectors.joining(", "));
            sb.append("테마: ").append(categoryKeywords).append(". ");
        }
        if (overview != null && !overview.isBlank()) {
            sb.append(overview.trim());
        }
        return sb.toString().trim();
    }
}
