package com.project.picngo.spot.service;

import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 이벤트 리스너로 못 채운 임베딩(과거 스팟, API 실패로 넘어간 스팟)을 매일 새벽에 쓸어담는다.
 *
 * <p>매 회차 항상 0페이지를 다시 조회한다 - findMissingEmbeddings는 embedding이 null인
 * 행만 돌려주므로, 채워진 행은 다음 조회에서 자동으로 빠진다. 오프셋을 옮기지 않아야
 * 실패한 행을 건너뛰지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpotEmbeddingBackfillScheduler {

    private static final int BATCH_SIZE = 50;
    // 배치 하나가 계속 실패해서 남은 개수가 줄지 않는 상황(예: API 키 미설정)에
    // 매일 무한히 도는 것을 막는 안전판.
    private static final int MAX_BATCHES_PER_RUN = 200;

    private final SpotRepository spotRepository;
    private final SpotEmbeddingService spotEmbeddingService;

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void backfillMissingEmbeddings() {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        int totalProcessed = 0;

        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<SpotRepository.EmbeddingSource> missing =
                    spotRepository.findMissingEmbeddings(SpotStatus.APPROVED, pageable);

            if (missing.isEmpty()) {
                break;
            }

            for (SpotRepository.EmbeddingSource source : missing) {
                try {
                    spotEmbeddingService.embed(source.getId(), source.getName(), source.getAddress(), source.getOverview());
                } catch (Exception e) {
                    log.warn("임베딩 백필 실패 (spotId: {})", source.getId(), e);
                }
            }
            totalProcessed += missing.size();
        }

        log.info("임베딩 백필 배치 완료: {}건 처리", totalProcessed);
    }
}
