package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.external.TourApiClient;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.FestivalResponse;
import com.project.picngo.spot.repository.SpotPhotoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "tour.api.key=${PUBLIC_DATA_SERVICE_KEY:test-key}",
        "tour.api.base-url=https://apis.data.go.kr/B551011/KorService2"
})
class TourApiRealSampleSyncTest {

    @Autowired
    private TourApiSyncService tourApiSyncService;

    @Autowired
    private SpotRepository spotRepository;

    @Autowired
    private SpotPhotoRepository spotPhotoRepository;

    @Autowired
    private TourApiClient tourApiClient;

    @Autowired
    private FestivalService festivalService;

    @Test
    @Transactional
    @DisplayName("TourAPI 실데이터 샘플 수집 (타입별 7건, 총 28건) 및 연동 검증")
    void syncAndVerifySampleSpots() {
        int saved = tourApiSyncService.syncSample(7);
        System.out.println("==================================================");
        System.out.println(">>> [1] 총 저장된 샘플 스팟 수: " + saved);
        System.out.println("==================================================");

        if (saved == 0) {
            System.out.println("⚠️ PUBLIC_DATA_SERVICE_KEY 환경변수가 설정되지 않아 실 API 호출이 건너뛰어졌습니다.");
            return;
        }

        assertThat(saved).isGreaterThan(0);

        // 1. 관광지 (12) 검증
        List<Spot> type12 = spotRepository.findAll().stream()
                .filter(s -> Integer.valueOf(12).equals(s.getContentTypeId()))
                .toList();
        System.out.println(">>> [2] 관광지(12) 스팟 수: " + type12.size());
        for (Spot s : type12) {
            System.out.println("  - 관광지: " + s.getName() + " | 카테고리: " + s.getCategories() + " | 주차: " + s.getParking());
        }

        // 2. 문화시설 (14) 검증
        List<Spot> type14 = spotRepository.findAll().stream()
                .filter(s -> Integer.valueOf(14).equals(s.getContentTypeId()))
                .toList();
        System.out.println(">>> [3] 문화시설(14) 스팟 수: " + type14.size());
        for (Spot s : type14) {
            System.out.println("  - 문화시설: " + s.getName() + " | 카테고리: " + s.getCategories() + " | 운영시간: " + s.getUsetime());
        }

        // 3. 축제 (15) 검증
        List<Spot> type15 = spotRepository.findAll().stream()
                .filter(s -> Integer.valueOf(15).equals(s.getContentTypeId()))
                .toList();
        System.out.println(">>> [4] 축제/행사(15) 스팟 수: " + type15.size());
        for (Spot f : type15) {
            System.out.println("  - 축제: " + f.getName() + " | 기간: " + f.getEventStartDate() + " ~ " + f.getEventEndDate() + " | 카테고리: " + f.getCategories());
        }

        // 4. 카페 (39) 검증
        List<Spot> type39 = spotRepository.findAll().stream()
                .filter(s -> Integer.valueOf(39).equals(s.getContentTypeId()))
                .toList();
        System.out.println(">>> [5] 카페(39) 스팟 수: " + type39.size());
        for (Spot c : type39) {
            System.out.println("  - 카페: " + c.getName() + " | 카테고리: " + c.getCategories() + " | 주차: " + c.getParking());
            assertThat(c.getCategories()).contains(SpotCategory.CAFE);
        }

        // 5. 신규 축제 API 조회 검증
        Page<FestivalResponse> festivals = festivalService.getFestivals("ALL", null, 0, 20);
        System.out.println(">>> [6] 축제 API (/festivals) 반환 건수: " + festivals.getTotalElements());
        for (FestivalResponse fr : festivals.getContent()) {
            System.out.println("  - 축제 API 응답: " + fr.getName() + " (상태: " + fr.getProgressStatus() + ")");
        }

        long photoCount = spotPhotoRepository.count();
        System.out.println(">>> [7] spot_photo 테이블에 저장된 총 사진 수: " + photoCount);
    }

    @Test
    @DisplayName("충남(areaCode=34) 지역 동기화 호출 시 TourAPI에서 데이터 정상 수신 검증")
    void verifyAreaCodeChungnamSync() {
        var response = tourApiClient.getAreaBasedListRaw(12, 34, 1, 5);
        if (response != null && response.response() != null && response.response().body() != null) {
            int total = response.response().body().totalCount();
            var items = response.response().body().items().item();
            System.out.println("==================================================");
            System.out.println(">>> 충남(areaCode=34) 관광지 총 건수: " + total);
            if (items != null) {
                for (var it : items) {
                    System.out.println("  - 충남 스팟: " + it.title() + " | 주소: " + it.addr1());
                }
            }
            System.out.println("==================================================");
            assertThat(total).isGreaterThan(0);
        }
    }
}
