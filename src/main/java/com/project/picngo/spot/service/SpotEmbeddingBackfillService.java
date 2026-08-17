package com.project.picngo.spot.service;

import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 임베딩이 비어 있는 스팟을 채운다. 새벽 배치와 관리자 API가 같은 로직을 쓴다.
 *
 * <p>임베딩이 비는 경로는 두 가지다:
 * <ul>
 *   <li>의미 검색을 처음 켠 시점 - 기존 스팟이 전부 비어 있다
 *   <li>스팟 내용이 바뀐 경우 - 낡은 임베딩을 지워두면 여기서 다시 채운다
 * </ul>
 *
 * <p>매 회차 항상 0페이지를 다시 조회한다. 조회 조건이 "embedding이 비어 있는 스팟"이라
 * 채워진 행은 다음 조회에서 저절로 빠지기 때문이다. 오프셋을 옮기면 오히려
 * 실패한 행을 건너뛰게 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpotEmbeddingBackfillService {

    private static final int BATCH_SIZE = 50;
    // 한 번 실행에서 도는 배치 수 상한. 아래 "한 건도 못 채우면 중단" 규칙이 정상
    // 상황을 다 막아주지만, 예상 못 한 경우에 무한정 도는 것을 막는 마지막 방어선이다.
    private static final int MAX_BATCHES_PER_RUN = 200;

    private final SpotRepository spotRepository;
    private final SpotEmbeddingService spotEmbeddingService;

    /** 저장 성공/실패 건수. 관리자 API가 그대로 응답으로 돌려준다. */
    public record BackfillResult(int saved, int failed) {
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Seoul")
    public void backfillOnSchedule() {
        BackfillResult result = backfillMissingEmbeddings();
        log.info("임베딩 백필(스케줄) 완료: 저장 {}건, 실패 {}건", result.saved(), result.failed());
    }

    public BackfillResult backfillMissingEmbeddings() {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE);
        int saved = 0;
        int failed = 0;

        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            List<SpotRepository.EmbeddingSource> missing =
                    spotRepository.findMissingEmbeddings(SpotStatus.APPROVED, pageable);

            if (missing.isEmpty()) {
                break;
            }

            int savedInBatch = 0;
            for (SpotRepository.EmbeddingSource source : missing) {
                try {
                    if (spotEmbeddingService.embed(
                            source.getId(), source.getName(), source.getAddress(), source.getOverview())) {
                        savedInBatch++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("임베딩 백필 실패 (spotId: {})", source.getId(), e);
                }
            }
            saved += savedInBatch;

            // 한 배치에서 하나도 못 채웠으면 멈춘다. 실패한 스팟은 embedding이 여전히
            // 비어 있어 다음 회차 조회에 그대로 다시 딸려 나오므로, 그냥 두면 똑같은
            // 대상에 대고 외부 API를 반복 호출하게 된다(설정이 틀렸을 때 비용만 나간다).
            if (savedInBatch == 0) {
                log.warn("임베딩 백필 중단: 이번 배치에서 하나도 저장하지 못했다. "
                        + "API 키(picngo.embedding.api-key)와 외부 API 상태를 확인할 것");
                break;
            }
        }

        return new BackfillResult(saved, failed);
    }

    /** 전체 대비 얼마나 채워졌는지. 백필을 돌리기 전후로 확인하는 용도다. */
    public EmbeddingCoverage coverage() {
        long total = spotRepository.countSearchable(SpotStatus.APPROVED);
        long withEmbedding = spotRepository.countWithEmbedding(SpotStatus.APPROVED);
        return new EmbeddingCoverage(total, withEmbedding, total - withEmbedding);
    }

    public record EmbeddingCoverage(long total, long withEmbedding, long missing) {
    }
}
