package com.project.picngo.spot.scheduler;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.repository.SpotPhotoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FestivalCleanupScheduler {

    private final SpotRepository spotRepository;
    private final SpotPhotoRepository spotPhotoRepository;

    /**
     * 매일 새벽 4시에 동작하여 오늘 날짜 기준으로 종료된 축제(eventEndDate < today)를
     * DB에서 영구 삭제(Hard Delete)합니다.
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public int cleanupExpiredFestivals() {
        LocalDate today = LocalDate.now();
        List<Spot> expiredFestivals = spotRepository.findExpiredFestivals(SpotCategory.FESTIVAL, today);

        if (expiredFestivals.isEmpty()) {
            log.info("종료된 축제 정리 작업 완료: 삭제 대상 없음 (기준일: {})", today);
            return 0;
        }

        int count = expiredFestivals.size();
        log.info("종료된 축제 영구 삭제 시작: 총 {}건 대상 (기준일: {})", count, today);

        // 1. 자식 사진 데이터 일괄 삭제
        spotPhotoRepository.deleteBySpotIn(expiredFestivals);

        // 2. 스팟 엔티티 삭제 (spot_categories 및 accessPoints는 JPA Cascade로 함께 삭제됨)
        spotRepository.deleteAll(expiredFestivals);

        log.info("종료된 축제 영구 삭제 완료: 총 {}건 삭제됨", count);
        return count;
    }
}
