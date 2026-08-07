package com.project.picngo.bookmark.repository;

import com.project.picngo.bookmark.domain.BookmarkCollectionSpot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookmarkCollectionSpotRepository extends JpaRepository<BookmarkCollectionSpot, Long> {

    // 이 유저가 이 스팟을 담아둔 멤버십 (contains 계산 + 스팟의 북마크 여부/카운트 판단)
    List<BookmarkCollectionSpot> findByCollection_UserIdAndSpotId(Long userId, Long spotId);

    boolean existsByCollection_UserIdAndSpotId(Long userId, Long spotId);

    long countByCollectionId(Long collectionId);
}
