package com.project.picngo.bookmark.repository;

import com.project.picngo.bookmark.domain.BookmarkCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookmarkCollectionRepository extends JpaRepository<BookmarkCollection, Long> {

    List<BookmarkCollection> findByUserIdOrderByCreatedAtAsc(Long userId);
    List<BookmarkCollection> findByUserId(Long userId);
    long countByUserId(Long userId);
}
