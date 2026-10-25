package com.project.picngo.user.repository;

import com.project.picngo.user.domain.User;
import com.project.picngo.user.domain.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    Optional<UserBlock> findByBlockerAndBlocked(User blocker, User blocked);

    /** 차단 목록 화면. 팔로워/팔로잉 목록과 같이 페이징 없이 돌려준다. */
    @Query("""
            select b from UserBlock b
            join fetch b.blocked blocked
            where b.blocker.id = :blockerId and blocked.deletedAt is null
            order by b.createdAt desc
            """)
    List<UserBlock> findBlockedUsers(@Param("blockerId") Long blockerId);
}
