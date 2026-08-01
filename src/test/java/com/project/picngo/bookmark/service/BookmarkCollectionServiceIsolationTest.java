package com.project.picngo.bookmark.service;

import com.project.picngo.bookmark.dto.BookmarkCollectionResponse;
import com.project.picngo.bookmark.dto.CreateCollectionRequest;
import com.project.picngo.bookmark.repository.BookmarkCollectionRepository;
import com.project.picngo.bookmark.repository.BookmarkCollectionSpotRepository;
import com.project.picngo.spot.repository.SpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BookmarkCollectionServiceIsolationTest {

    @Autowired
    private BookmarkCollectionRepository collectionRepository;
    @Autowired
    private BookmarkCollectionSpotRepository membershipRepository;
    @Autowired
    private SpotRepository spotRepository;

    private BookmarkCollectionService service;

    @BeforeEach
    void setUp() {
        service = new BookmarkCollectionService(collectionRepository, membershipRepository, spotRepository);
    }

    @Test
    @DisplayName("getCollections는 실제 저장소를 사용해도 다른 유저의 컬렉션을 노출하지 않는다")
    void getCollections_realRepository_doesNotLeakOtherUsersCollections() {
        Long userA = 7L;
        Long userB = 8L;

        service.createCollection(userA, new CreateCollectionRequest("A의 컬렉션", "blue", "heart"));
        service.createCollection(userB, new CreateCollectionRequest("B의 컬렉션", "green", "flag"));

        List<String> userACollectionNames = service.getCollections(userA, null).stream()
                .map(BookmarkCollectionResponse::name)
                .toList();

        assertThat(userACollectionNames).contains("A의 컬렉션");
        assertThat(userACollectionNames).doesNotContain("B의 컬렉션");
    }
}
