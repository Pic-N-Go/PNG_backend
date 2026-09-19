package com.project.picngo.spot.service;

import com.project.picngo.common.domain.SpotCategory;
import com.project.picngo.common.event.SpotCreatedEvent;
import com.project.picngo.external.KakaoAddressClient;
import com.project.picngo.external.dto.PhotoAwardApiResponse.PhotoAwardItem;
import com.project.picngo.spot.domain.PhotoAward;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.SpotCategoryTagger;
import com.project.picngo.spot.domain.SpotPhoto;
import com.project.picngo.spot.domain.enums.SpotSource;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.dto.Coordinate;
import com.project.picngo.spot.repository.PhotoAwardRepository;
import com.project.picngo.spot.repository.SpotPhotoRepository;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoAwardUpsertService {

    public enum UpsertResult {
        CREATED, ENRICHED, SKIPPED
    }

    private final SpotRepository spotRepository;
    private final SpotPhotoRepository spotPhotoRepository;
    private final PhotoAwardRepository photoAwardRepository;
    private final KakaoAddressClient kakaoAddressClient;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UpsertResult upsertAward(PhotoAwardItem item, Coordinate coord) {
        if (item.contentId() != null && photoAwardRepository.existsByContentId(item.contentId())) {
            log.debug("[PhotoAwardUpsertService] 이미 등록된 공모전 수상작 건너뜀: contentId={}", item.contentId());
            return UpsertResult.SKIPPED;
        }

        // 1. 반경 300m 이내 기존 스팟 조회
        List<Spot> nearby = spotRepository.findNearbySpots(coord.latitude(), coord.longitude(), 0.3, 5);

        if (!nearby.isEmpty()) {
            // 기존 스팟이 존재하면 첫 번째(가장 가까운 거리) 스팟 보강
            Spot existingSpot = nearby.get(0);
            existingSpot.enrichFromPhotoAward(item.orgImage(), item.thumbImage());

            // 수상작 고화질 사진 등록
            if (item.orgImage() != null && !item.orgImage().isBlank()) {
                spotPhotoRepository.save(SpotPhoto.builder()
                        .spot(existingSpot)
                        .photoUrl(item.orgImage())
                        .thumbnailUrl(item.thumbImage())
                        .build());
            }

            // 공모전 수상작 메타데이터 저장
            savePhotoAward(item, existingSpot);
            log.info("[PhotoAwardUpsertService] 기존 스팟 보강 완료: spotId={}, name={}, awardTitle={}",
                    existingSpot.getId(), existingSpot.getName(), item.koTitle());
            return UpsertResult.ENRICHED;
        }

        // 2. 300m 이내 스팟이 없으면 신규 스팟으로 등록
        String address = kakaoAddressClient.coord2Address(coord.latitude(), coord.longitude());
        if (address == null || address.isBlank()) {
            address = item.koFilmst() != null ? item.koFilmst() : "주소 정보 없음";
        }
        address = truncate(address, 255);

        String spotName = resolveSpotName(coord.name(), item.koFilmst(), item.koTitle());
        Set<SpotCategory> categories = SpotCategoryTagger.tag(null, item.koTitle(), item.koKeyWord());

        Spot newSpot = spotRepository.save(Spot.builder()
                .name(spotName)
                .address(address)
                .latitude(coord.latitude())
                .longitude(coord.longitude())
                .categories(categories)
                .source(SpotSource.PHOTO_CONTEST)
                .badge(true)
                .status(SpotStatus.APPROVED)
                .imageUrl(item.orgImage())
                .thumbnailUrl(item.thumbImage())
                .overview(buildOverview(item))
                .photogenicScore(0)
                .build());

        eventPublisher.publishEvent(new SpotCreatedEvent(newSpot.getId()));

        if (item.orgImage() != null && !item.orgImage().isBlank()) {
            spotPhotoRepository.save(SpotPhoto.builder()
                    .spot(newSpot)
                    .photoUrl(item.orgImage())
                    .thumbnailUrl(item.thumbImage())
                    .build());
        }

        savePhotoAward(item, newSpot);
        log.info("[PhotoAwardUpsertService] 신규 공모전 스팟 등록 완료: spotId={}, name={}, coord=({}, {})",
                newSpot.getId(), newSpot.getName(), coord.latitude(), coord.longitude());
        return UpsertResult.CREATED;
    }

    private void savePhotoAward(PhotoAwardItem item, Spot spot) {
        PhotoAward award = PhotoAward.builder()
                .contentId(item.contentId())
                .title(item.koTitle() != null ? item.koTitle() : "무제")
                .photographer(item.koCmanNm())
                .awardYearMonth(item.filmDay())
                .awardName(item.koWnprzDiz())
                .locationName(item.koFilmst())
                .imageUrl(item.orgImage())
                .thumbnailUrl(item.thumbImage())
                .copyrightType(item.cpyrhtDivCd() != null ? item.cpyrhtDivCd() : "Type1")
                .lDongRegnCd(parseInteger(item.lDongRegnCd()))
                .spot(spot)
                .build();
        photoAwardRepository.save(award);
    }

    private String resolveSpotName(String kakaoPlaceName, String koFilmst, String koTitle) {
        String name;
        if (kakaoPlaceName != null && !kakaoPlaceName.isBlank()) {
            name = kakaoPlaceName.trim();
        } else if (koFilmst != null && !koFilmst.isBlank()) {
            String[] parts = koFilmst.split(",");
            if (parts.length > 1) {
                String specific = parts[parts.length - 1].trim();
                name = specific.length() >= 2 ? specific : koFilmst.trim();
            } else {
                name = koFilmst.trim();
            }
        } else {
            name = koTitle != null ? koTitle.trim() : "사진공모전 명소";
        }
        return truncate(name, 100);
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return null;
        String trimmed = str.trim();
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private String buildOverview(PhotoAwardItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("한국관광공사 대한민국 관광사진 공모전 수상작 촬영지입니다.\n");
        if (item.koWnprzDiz() != null && !item.koWnprzDiz().isBlank()) {
            sb.append("수상 내역: ").append(item.koWnprzDiz()).append("\n");
        }
        if (item.koTitle() != null && !item.koTitle().isBlank()) {
            sb.append("작품명: ").append(item.koTitle()).append("\n");
        }
        if (item.koCmanNm() != null && !item.koCmanNm().isBlank()) {
            sb.append("작가: ").append(item.koCmanNm()).append("\n");
        }
        if (item.filmDay() != null && item.filmDay().length() >= 6) {
            String y = item.filmDay().substring(0, 4);
            String m = item.filmDay().substring(4, 6);
            sb.append("촬영 시기: ").append(y).append("년 ").append(m).append("월\n");
        }
        if (item.koKeyWord() != null && !item.koKeyWord().isBlank()) {
            sb.append("키워드: ").append(item.koKeyWord());
        }
        return sb.toString().trim();
    }

    private Integer parseInteger(String val) {
        try {
            return val != null ? Integer.parseInt(val.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
