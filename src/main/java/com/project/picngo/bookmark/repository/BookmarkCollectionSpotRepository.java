package com.project.picngo.bookmark.repository;

import com.project.picngo.bookmark.domain.BookmarkCollectionSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookmarkCollectionSpotRepository extends JpaRepository<BookmarkCollectionSpot, Long> {

    // 이 유저가 이 스팟을 담아둔 멤버십 (contains 계산 + 스팟의 북마크 여부/카운트 판단)
    List<BookmarkCollectionSpot> findByCollection_UserIdAndSpotId(Long userId, Long spotId);

    boolean existsByCollection_UserIdAndSpotId(Long userId, Long spotId);

    long countByCollectionId(Long collectionId);

    // 목록 응답의 isBookmarked 채우기용. 스팟마다 exists를 도는 N+1 대신 한 방에 가져온다.
    // 같은 스팟이 여러 컬렉션에 담겨 있으면 중복 행이 나오므로 distinct 필수.
    @Query("select distinct bcs.spotId from BookmarkCollectionSpot bcs "
            + "where bcs.collection.userId = :userId and bcs.spotId in :spotIds")
    List<Long> findBookmarkedSpotIds(@Param("userId") Long userId, @Param("spotIds") Collection<Long> spotIds);
}
