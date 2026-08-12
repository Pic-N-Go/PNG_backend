package com.project.picngo.bookmark.service;

import com.project.picngo.bookmark.domain.BookmarkCollection;
import com.project.picngo.bookmark.dto.CreateCollectionRequest;
import com.project.picngo.bookmark.repository.BookmarkCollectionRepository;
import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.spot.domain.enums.SpotStatus;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookmarkCollectionServiceTest {

    @Mock
    private BookmarkCollectionRepository collectionRepository;
    @Mock
    private BookmarkCollectionSpotRepository membershipRepository;
    @Mock
    private SpotRepository spotRepository;

    @InjectMocks
    private BookmarkCollectionService bookmarkCollectionService;

    @Captor
    private ArgumentCaptor<Long> userIdCaptor;

    @Test
    @DisplayName("getCollections는 요청한 userId를 그대로 리포지토리에 전달한다")
    void getCollections_passesRequestedUserIdToRepository() {
        Long userA = 7L;
        given(collectionRepository.countByUserId(userA)).willReturn(1L);
        given(collectionRepository.findByUserIdOrderByCreatedAtAsc(any())).willReturn(List.of());

        bookmarkCollectionService.getCollections(userA, null);

        verify(collectionRepository).findByUserIdOrderByCreatedAtAsc(userIdCaptor.capture());
        assertThat(userIdCaptor.getValue()).isEqualTo(userA);
    }

    @Test
    @DisplayName("createCollection은 요청한 userId로 컬렉션을 저장한다")
    void createCollection_savesWithRequestedUserId() {
        Long userId = 7L;
        CreateCollectionRequest request = new CreateCollectionRequest("여행", "pink", "star");
        given(collectionRepository.countByUserId(userId)).willReturn(0L);
        given(collectionRepository.existsByUserIdAndName(userId, "여행")).willReturn(false);
        given(collectionRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<BookmarkCollection> savedCaptor = ArgumentCaptor.forClass(BookmarkCollection.class);
        bookmarkCollectionService.createCollection(userId, request);

        verify(collectionRepository).saveAndFlush(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("syncSpotCollections는 요청한 userId 소유 컬렉션 기준으로 멤버십을 조회한다")
    void syncSpotCollections_looksUpMembershipForRequestedUserId() {
        Long userId = 7L;
        given(spotRepository.findById(100L)).willReturn(Optional.of(
                Spot.builder()
                        .name("스팟").address("주소").latitude(37.5).longitude(127.0)
                        .categories(Set.of())
                        .status(SpotStatus.APPROVED)
                        .build()));
        given(collectionRepository.findByUserId(userId)).willReturn(List.of());
        given(membershipRepository.findByCollection_UserIdAndSpotId(any(), any())).willReturn(List.of());
        given(collectionRepository.findAllById(any())).willReturn(List.of());

        bookmarkCollectionService.syncSpotCollections(userId, 100L, List.of());

        verify(membershipRepository).findByCollection_UserIdAndSpotId(userIdCaptor.capture(), any());
        assertThat(userIdCaptor.getValue()).isEqualTo(userId);
    }
}
