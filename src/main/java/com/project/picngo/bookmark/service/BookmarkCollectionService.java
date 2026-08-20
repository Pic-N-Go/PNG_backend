package com.project.picngo.bookmark.service;

import com.project.picngo.bookmark.domain.BookmarkCollection;
import com.project.picngo.bookmark.domain.BookmarkCollectionSpot;
import com.project.picngo.bookmark.dto.BookmarkCollectionResponse;
import com.project.picngo.bookmark.dto.CreateCollectionRequest;
import com.project.picngo.bookmark.repository.BookmarkCollectionRepository;
import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.BookmarkErrorCode;
import com.project.picngo.common.exception.code.SpotErrorCode;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.dto.SpotResponse;
import com.project.picngo.spot.repository.SpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkCollectionService {

    private static final int MAX_COLLECTIONS = 5;

    // color/icon 허용 키 — 프론트 피커와 합의된 값. 추가되면 여기에만 반영하면 됨.
    private static final Set<String> ALLOWED_COLORS = Set.of("pink", "blue", "purple", "green", "orange");
    private static final Set<String> ALLOWED_ICONS = Set.of(
            "star", "heart", "bookmark", "map-pin", "camera", "flag", "sparkles", "mountain", "clock", "archive");

    private static final String DEFAULT_NAME = "내 즐겨찾기";
    private static final String DEFAULT_COLOR = "pink";
    private static final String DEFAULT_ICON = "star";

    private final BookmarkCollectionRepository collectionRepository;
    private final BookmarkCollectionSpotRepository membershipRepository;
    private final SpotRepository spotRepository;

    // 시트 오픈용: 유저의 컬렉션 목록 + 각 컬렉션의 스팟 수 + 이 스팟 소속 여부(contains)
    @Transactional // 최초 접근 시 기본 컬렉션 자동 생성이 있어 쓰기 트랜잭션
    public List<BookmarkCollectionResponse> getCollections(Long userId, Long spotId) {
        ensureDefaultCollection(userId);

        Set<Long> containingIds = (spotId == null) ? Set.of()
                : membershipRepository.findByCollection_UserIdAndSpotId(userId, spotId).stream()
                        .map(m -> m.getCollection().getId())
                        .collect(Collectors.toSet());

        return collectionRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(c -> BookmarkCollectionResponse.of(
                        c,
                        membershipRepository.countByCollectionId(c.getId()),
                        containingIds.contains(c.getId())))
                .toList();
    }

    // 컬렉션 상세: 담긴 스팟 목록. 최근 담은 것부터.
    public List<SpotResponse> getCollectionSpots(Long userId, Long collectionId) {
        BookmarkCollection collection = collectionRepository.findById(collectionId)
                .orElseThrow(() -> new CustomException(BookmarkErrorCode.COLLECTION_NOT_FOUND));
        // 남의 컬렉션이면 존재 여부조차 흘리지 않는다 — 403이 아니라 404.
        if (!collection.getUserId().equals(userId)) {
            throw new CustomException(BookmarkErrorCode.COLLECTION_NOT_FOUND);
        }

        return toSpotResponses(membershipRepository.findSpotIdsByCollectionId(collectionId));
    }

    // MY 탭의 "북마크한 스팟": 컬렉션 구분 없이 담아둔 스팟 전부. 최근 담은 것부터.
    public List<SpotResponse> getBookmarkedSpots(Long userId) {
        return toSpotResponses(membershipRepository.findSpotIdsByUserId(userId));
    }

    // ponytail: 심사 상태·활성 여부로 걸러내지 않는다 — 담아둔 스팟을 말없이 빼면 컬렉션 카드의
    // spotCount와 목록 개수가 어긋난다. 비활성 스팟을 감춰야 하면 카운트 쿼리도 같이 맞출 것.
    // isBookmarked는 항상 true — 담겨 있다는 게 조회 조건 자체다.
    private List<SpotResponse> toSpotResponses(List<Long> spotIds) {
        if (spotIds.isEmpty()) {
            return List.of();
        }
        // findAllById는 순서를 보장하지 않아 담은 순서로 다시 세운다.
        Map<Long, Spot> byId = spotRepository.findAllById(spotIds).stream()
                .collect(Collectors.toMap(Spot::getId, Function.identity()));
        return spotIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(spot -> SpotResponse.from(spot, true))
                .toList();
    }

    @Transactional
    public BookmarkCollectionResponse createCollection(Long userId, CreateCollectionRequest request) {
        validateColorIcon(request.color(), request.icon());
        String name = request.name().trim(); // 프론트와 동일하게 trim 기준으로 비교/저장

        if (collectionRepository.countByUserId(userId) >= MAX_COLLECTIONS) {
            throw new CustomException(BookmarkErrorCode.COLLECTION_LIMIT_EXCEEDED);
        }
        // 순차 중복은 사전 체크로 깔끔하게 409
        if (collectionRepository.existsByUserIdAndName(userId, name)) {
            throw new CustomException(BookmarkErrorCode.COLLECTION_NAME_DUPLICATE);
        }

        try {
            BookmarkCollection saved = collectionRepository.saveAndFlush(BookmarkCollection.builder()
                    .userId(userId)
                    .name(name)
                    .color(request.color())
                    .icon(request.icon())
                    .build());
            return BookmarkCollectionResponse.of(saved, 0, false);
        } catch (DataIntegrityViolationException e) {
            // 동시 생성 레이스: (user_id, name) unique 제약이 최종 방어 → 예외를 던져 롤백하며 409로 변환
            throw new CustomException(BookmarkErrorCode.COLLECTION_NAME_DUPLICATE);
        }
    }

    // 체크된 collectionIds 집합으로 이 스팟의 멤버십을 통째 동기화 (추가 + 제거)
    @Transactional
    public void syncSpotCollections(Long userId, Long spotId, List<Long> collectionIds) {
        Spot spot = spotRepository.findById(spotId)
                .orElseThrow(() -> new CustomException(SpotErrorCode.SPOT_NOT_FOUND));

        Set<Long> target = Set.copyOf(collectionIds);

        // 대상 컬렉션이 모두 이 유저 소유인지 검증
        Set<Long> ownedIds = collectionRepository.findByUserId(userId).stream()
                .map(BookmarkCollection::getId)
                .collect(Collectors.toSet());
        if (!ownedIds.containsAll(target)) {
            throw new CustomException(BookmarkErrorCode.COLLECTION_NOT_FOUND);
        }

        List<BookmarkCollectionSpot> current = membershipRepository.findByCollection_UserIdAndSpotId(userId, spotId);
        Set<Long> currentIds = current.stream().map(m -> m.getCollection().getId()).collect(Collectors.toSet());

        // 제거: 현재 있으나 대상에 없는 것
        List<BookmarkCollectionSpot> toRemove = current.stream()
                .filter(m -> !target.contains(m.getCollection().getId()))
                .toList();
        membershipRepository.deleteAll(toRemove);

        // 추가: 대상에 있으나 현재 없는 것
        List<BookmarkCollectionSpot> toAdd = collectionRepository.findAllById(
                        target.stream().filter(id -> !currentIds.contains(id)).toList()).stream()
                .map(c -> BookmarkCollectionSpot.builder().collection(c).spotId(spotId).build())
                .toList();
        membershipRepository.saveAll(toAdd);

        // 스팟의 북마크 카운트 유지 (추천 스팟 정렬에 사용) — 이 유저 기준 0↔1 전이일 때만 증감
        boolean wasBookmarked = !currentIds.isEmpty();
        boolean isBookmarked = !target.isEmpty();
        if (!wasBookmarked && isBookmarked) spot.incrementBookmarkCount();
        else if (wasBookmarked && !isBookmarked) spot.decrementBookmarkCount();
    }

    // 동시 최초 접근(예: StrictMode 이중 GET) 레이스는 (user_id, name) unique 제약이 데이터 무결성을 최종 보장 —
    // 드물게 진 요청은 500 후 재요청 시 정상. 기본 컬렉션이 2개 생기는 일은 없음.
    private void ensureDefaultCollection(Long userId) {
        if (collectionRepository.countByUserId(userId) == 0) {
            collectionRepository.save(BookmarkCollection.builder()
                    .userId(userId)
                    .name(DEFAULT_NAME)
                    .color(DEFAULT_COLOR)
                    .icon(DEFAULT_ICON)
                    .build());
        }
    }

    private void validateColorIcon(String color, String icon) {
        if (!ALLOWED_COLORS.contains(color)) {
            throw new CustomException(BookmarkErrorCode.INVALID_COLLECTION_COLOR);
        }
        if (!ALLOWED_ICONS.contains(icon)) {
            throw new CustomException(BookmarkErrorCode.INVALID_COLLECTION_ICON);
        }
    }
}
