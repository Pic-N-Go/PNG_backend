package com.project.picngo.spot.service;

import com.project.picngo.external.EmbeddingClient;
import com.project.picngo.spot.domain.EmbeddingVector;
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

    @Transactional
    public void embedSpot(Long spotId) {
        spotRepository.findById(spotId)
                .ifPresent(spot -> embed(spot.getId(), spot.getName(), spot.getAddress(), spot.getOverview()));
    }

    @Transactional
    public void embed(Long id, String name, String address, String overview) {
        String text = buildText(name, address, overview);
        if (text.isBlank()) {
            return;
        }

        embeddingClient.embed(text).ifPresentOrElse(
                vector -> spotRepository.updateEmbedding(id, EmbeddingVector.encode(vector)),
                () -> log.debug("임베딩 계산 실패/스킵 (spotId: {})", id)
        );
    }

    // 이름/주소/개요를 하나의 문장으로 합쳐 의미 검색 대상 텍스트로 삼는다.
    private String buildText(String name, String address, String overview) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) {
            sb.append(name.trim()).append(". ");
        }
        if (address != null && !address.isBlank()) {
            sb.append(address.trim()).append(". ");
        }
        if (overview != null && !overview.isBlank()) {
            sb.append(overview.trim());
        }
        return sb.toString().trim();
    }
}
