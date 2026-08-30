package com.project.picngo.spot.service;

import com.project.picngo.common.event.SpotCreatedEvent;
import com.project.picngo.external.dto.TourApiImageResponse.ImageItem;
import com.project.picngo.external.dto.TourApiIntroResponse.IntroItem;
import com.project.picngo.external.dto.TourApiResponse.Item;
import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategoryTagger;
import com.project.picngo.spot.domain.SpotPhoto;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotPhotoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpotUpsertService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SpotRepository spotRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void upsertSpot(Item item, Item detail, IntroItem intro, List<ImageItem> images) {
        Integer contentTypeId = item.getContentTypeIdOrNull();
        String overview = detail != null ? detail.overview() : null;
        String categoryCode = item.getEffectiveCategoryCode();
        Set<SpotCategory> categories = SpotCategoryTagger.tag(categoryCode, item.title(), overview);

        // 음식점(39) 타입인 경우 출사 여행 테마에 맞춰 '카페(CAFE)'만 선별 저장
        if (Integer.valueOf(39).equals(contentTypeId)) {
            boolean isCafe = categories.contains(SpotCategory.CAFE)
                    || (categoryCode != null && categoryCode.startsWith("FD05"));
            if (!isCafe) {
                log.debug("음식점(39) 중 일반 식당은 건너뜁니다 (카페 아님): contentId={}, title={}",
                        item.contentid(), item.title());
                return;
            }
        }

        // 축제(15) 시작일/종료일 파싱
        LocalDate eventStartDate = parseDate(item.eventstartdate() != null ? item.eventstartdate() : (intro != null ? intro.eventstartdate() : null));
        LocalDate eventEndDate = parseDate(item.eventenddate() != null ? item.eventenddate() : (intro != null ? intro.eventenddate() : null));

        Spot spot = spotRepository.findByTourContentId(item.contentid()).map(
                existing -> {
                    existing.updateFromTourApi(
                            overview,
                            intro != null ? intro.getEffectiveParking() : null,
                            intro != null ? intro.getEffectiveUsetime() : null,
                            intro != null ? intro.getEffectiveRestdate() : null,
                            intro != null ? intro.getEffectiveInfocenter() : null,
                            intro != null ? intro.getEffectiveWheelchairAccess() : null,
                            intro != null ? intro.getEffectiveStrollerAccess() : null,
                            intro != null ? intro.getEffectivePetFriendly() : null,
                            contentTypeId,
                            eventStartDate,
                            eventEndDate
                    );
                    existing.updateCategories(categories);
                    return existing;
                }).orElseGet(() -> {
            Spot createdSpot = spotRepository.save(Spot.builder()
                    .name(item.title())
                    .address(trim(item.addr1()) + (item.addr2() != null ? " " + item.addr2().trim() : ""))
                    .zipcode(item.zipcode())
                    .overview(overview)
                    .latitude(parseDouble(item.mapy()))
                    .longitude(parseDouble(item.mapx()))
                    .categories(categories)
                    .cat3(categoryCode)
                    .contentTypeId(contentTypeId)
                    .eventStartDate(eventStartDate)
                    .eventEndDate(eventEndDate)
                    .source(SpotSource.TOUR_API)
                    .badge(true)
                    .tourContentId(item.contentid())
                    .imageUrl(item.firstimage())
                    .thumbnailUrl(item.firstimage2())
                    .status(SpotStatus.APPROVED)
                    .parking(intro != null ? intro.getEffectiveParking() : null)
                    .usetime(intro != null ? intro.getEffectiveUsetime() : null)
                    .restdate(intro != null ? intro.getEffectiveRestdate() : null)
                    .infocenter(intro != null ? intro.getEffectiveInfocenter() : null)
                    .strollerAccess(intro != null ? intro.getEffectiveStrollerAccess() : null)
                    .petFriendly(intro != null ? intro.getEffectivePetFriendly() : null)
                    .wheelchairAccess(intro != null ? intro.getEffectiveWheelchairAccess() : null)
                    .build());

            eventPublisher.publishEvent(new SpotCreatedEvent(createdSpot.getId()));

            return createdSpot;
        });
        syncTourPhotos(spot, images);
    }

    // TourAPI 사진(userId=null)만 갈아끼운다. 유저 업로드 사진은 건드리지 않음.
    private void syncTourPhotos(Spot spot, List<ImageItem> images) {
        // ponytail: 빈 리스트면 스킵 — API 실패 시에도 emptyList라 기존 사진 보존
        if (images == null || images.isEmpty()) return;

        spotPhotoRepository.deleteBySpotIdAndUserIdIsNull(spot.getId());
        images.stream()
                .filter(img -> img.originimgurl() != null && !img.originimgurl().isBlank())
                .forEach(img -> spotPhotoRepository.save(
                        SpotPhoto.builder()
                                .spot(spot)
                                .photoUrl(img.originimgurl())
                                .thumbnailUrl(img.smallimageurl())
                                .build()));
    }

    private LocalDate parseDate(String yyyyMMdd) {
        if (yyyyMMdd == null || yyyyMMdd.isBlank() || yyyyMMdd.trim().length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(yyyyMMdd.trim().substring(0, 8), YYYYMMDD);
        } catch (Exception e) {
            log.debug("날짜 파싱 실패 ({}): {}", yyyyMMdd, e.getMessage());
            return null;
        }
    }

    private Double parseDouble(String value) {
        try { return value != null ? Double.parseDouble(value) : 0.0; }
        catch (NumberFormatException e) { return 0.0; }
    }

    private String trim(String value) {
        return value != null ? value.trim() : "";
    }
}
