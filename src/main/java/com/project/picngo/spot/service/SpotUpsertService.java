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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SpotUpsertService {

    private final SpotRepository spotRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void upsertSpot(Item item, Item detail, IntroItem intro, List<ImageItem> images) {
        String overview = detail != null ? detail.overview() : null;
        Set<SpotCategory> categories = SpotCategoryTagger.tag(item.cat3(), item.title(), overview);

        Spot spot = spotRepository.findByTourContentId(item.contentid()).map(
                existing -> {
                    existing.updateFromTourApi(
                            overview,
                            intro != null ? intro.parking() : null,
                            intro != null ? intro.usetime() : null,
                            intro != null ? intro.restdate() : null,
                            intro != null ? intro.infocenter() : null,
                            intro != null ? intro.chkhandicap() : null,
                            intro != null ? intro.chkbabycarriage() : null,
                            intro != null ? intro.chkpet() : null
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
                    .cat3(item.cat3())
                    .source(SpotSource.TOUR_API)
                    .badge(true)
                    .tourContentId(item.contentid())
                    .imageUrl(item.firstimage())
                    .thumbnailUrl(item.firstimage2())
                    .status(SpotStatus.APPROVED)
                    .parking(intro != null ? intro.parking() : null)
                    .usetime(intro != null ? intro.usetime() : null)
                    .restdate(intro != null ? intro.restdate() : null)
                    .infocenter(intro != null ? intro.infocenter() : null)
                    .strollerAccess(intro != null ? intro.chkbabycarriage() : null)
                    .petFriendly(intro != null ? intro.chkpet() : null)
                    .wheelchairAccess(intro != null ? intro.chkhandicap() : null)
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

    private Double parseDouble(String value) {
        try { return value != null ? Double.parseDouble(value) : 0.0; }
        catch (NumberFormatException e) { return 0.0; }
    }

    private String trim(String value) {
        return value != null ? value.trim() : "";
    }
}
