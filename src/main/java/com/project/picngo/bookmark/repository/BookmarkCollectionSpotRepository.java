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

    // 컬렉션 상세용. 멤버십 id가 담은 순서라 desc면 최근 담은 것부터가 된다
    // (BookmarkCollectionSpot은 BaseTimeEntity를 상속하지 않아 createdAt이 없다).
    @Query("select bcs.spotId from BookmarkCollectionSpot bcs "
            + "where bcs.collection.id = :collectionId order by bcs.id desc")
    List<Long> findSpotIdsByCollectionId(@Param("collectionId") Long collectionId);

    // MY 탭의 "북마크한 스팟" — 컬렉션 구분 없이 전부. 같은 스팟이 여러 컬렉션에 있으면 한 번만 센다.
    // 정렬 기준이 max(id)라 여러 컬렉션에 담긴 스팟은 가장 최근에 담은 시점으로 줄을 선다.
    @Query("select bcs.spotId from BookmarkCollectionSpot bcs "
            + "where bcs.collection.userId = :userId "
            + "group by bcs.spotId order by max(bcs.id) desc")
    List<Long> findSpotIdsByUserId(@Param("userId") Long userId);
}
