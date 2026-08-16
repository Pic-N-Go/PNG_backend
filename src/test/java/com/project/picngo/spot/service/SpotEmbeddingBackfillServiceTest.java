package com.project.picngo.spot.service;

import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 백필 배치의 핵심은 "언제 멈추는가"다.
 *
 * <p>조회 조건이 "임베딩이 빈 스팟"이라, 계산에 실패한 스팟은 다음 회차에 그대로 다시
 * 딸려 나온다. 멈추는 조건이 없으면 API 키가 틀렸을 때 같은 스팟에 대고 외부 API를
 * 수천 번 호출하게 된다 - 결과는 똑같이 실패인데 비용만 나간다.
 */
@ExtendWith(MockitoExtension.class)
class SpotEmbeddingBackfillServiceTest {

    @Mock
    private SpotRepository spotRepository;

    @Mock
    private SpotEmbeddingService spotEmbeddingService;

    @InjectMocks
    private SpotEmbeddingBackfillService backfillService;

    private SpotRepository.EmbeddingSource source(Long id) {
        return new SpotRepository.EmbeddingSource() {
            @Override
            public Long getId() { return id; }
            @Override
            public String getName() { return "스팟" + id; }
            @Override
            public String getAddress() { return "서울특별시 종로구"; }
            @Override
            public String getOverview() { return "설명문"; }
        };
    }

    @Test
    @DisplayName("채울 스팟이 없으면 아무것도 하지 않는다")
    void doesNothingWhenNothingIsMissing() {
        given(spotRepository.findMissingEmbeddings(eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of());

        var result = backfillService.backfillMissingEmbeddings();

        assertThat(result.saved()).isZero();
        assertThat(result.failed()).isZero();
        verify(spotEmbeddingService, times(0)).embed(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("한 배치에서 전부 실패하면 즉시 멈춘다 - 같은 대상을 반복 호출하지 않는다")
    void stopsImmediatelyWhenAWholeBatchFails() {
        // 실패한 스팟은 embedding이 그대로 비어 있어 다음 조회에도 똑같이 나온다.
        given(spotRepository.findMissingEmbeddings(eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of(source(1L), source(2L)));
        given(spotEmbeddingService.embed(anyLong(), any(), any(), any())).willReturn(false);

        var result = backfillService.backfillMissingEmbeddings();

        assertThat(result.saved()).isZero();
        assertThat(result.failed()).isEqualTo(2);
        // 두 건을 한 번씩만 시도하고 끝나야 한다. 멈추지 않으면 200배치를 헛돈다.
        verify(spotEmbeddingService, times(2)).embed(anyLong(), any(), any(), any());
        verify(spotRepository, times(1)).findMissingEmbeddings(eq(SpotStatus.APPROVED), any(Pageable.class));
    }

    @Test
    @DisplayName("일부만 성공하면 계속 진행하고, 남은 게 없어지면 끝난다")
    void continuesWhenAtLeastOneSucceeded() {
        given(spotRepository.findMissingEmbeddings(eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of(source(1L), source(2L)))   // 1건 성공 1건 실패 → 계속
                .willReturn(List.of());                        // 더 없음 → 종료
        given(spotEmbeddingService.embed(anyLong(), any(), any(), any()))
                .willReturn(true)
                .willReturn(false);

        var result = backfillService.backfillMissingEmbeddings();

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(spotRepository, times(2)).findMissingEmbeddings(eq(SpotStatus.APPROVED), any(Pageable.class));
    }

    @Test
    @DisplayName("한 건이 예외로 죽어도 나머지는 계속 처리한다")
    void keepsGoingWhenOneSpotThrows() {
        given(spotRepository.findMissingEmbeddings(eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of(source(1L), source(2L)))
                .willReturn(List.of());
        given(spotEmbeddingService.embed(anyLong(), any(), any(), any()))
                .willThrow(new RuntimeException("외부 API 오류"))
                .willReturn(true);

        var result = backfillService.backfillMissingEmbeddings();

        assertThat(result.saved()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    @DisplayName("배치가 계속 성공해도 상한을 넘겨 돌지 않는다")
    void neverExceedsTheBatchLimit() {
        // 끝나지 않는 상황을 가정한다(조회는 계속 뭔가를 돌려주고 계산도 계속 성공).
        given(spotRepository.findMissingEmbeddings(eq(SpotStatus.APPROVED), any(Pageable.class)))
                .willReturn(List.of(source(1L)));
        given(spotEmbeddingService.embed(anyLong(), any(), any(), any())).willReturn(true);

        backfillService.backfillMissingEmbeddings();

        verify(spotRepository, atMost(200))
                .findMissingEmbeddings(eq(SpotStatus.APPROVED), any(Pageable.class));
    }

    @Test
    @DisplayName("현황은 전체·완료·남은 건수를 함께 돌려준다")
    void coverageReportsTotalAndMissing() {
        given(spotRepository.countSearchable(SpotStatus.APPROVED)).willReturn(135L);
        given(spotRepository.countWithEmbedding(SpotStatus.APPROVED)).willReturn(100L);

        var coverage = backfillService.coverage();

        assertThat(coverage.total()).isEqualTo(135L);
        assertThat(coverage.withEmbedding()).isEqualTo(100L);
        assertThat(coverage.missing()).isEqualTo(35L);
    }
}
