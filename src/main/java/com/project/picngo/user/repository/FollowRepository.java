package com.project.picngo.user.repository;

import com.project.picngo.user.domain.Follow;
import com.project.picngo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    // 이미 팔로우한 관계인지 확인
    boolean existsByFollowerAndFollowing(User follower, User following);

    // 팔로우 관계 조회
    Optional<Follow> findByFollowerAndFollowing(User follower, User following);

    // 특정 사용자를 팔로우하는 사람 목록 조회
    @Query("select f from Follow f join fetch f.follower where f.following = :following")
    List<Follow> findAllByFollowing(@Param("following") User following);

    // 특정 사용자를 팔로우 중인 사람 목록 조히
    @Query("select f from Follow f join fetch f.following where f.follower = :follower")
    List<Follow> findAllByFollower(@Param("follower") User follower);

    // 팔로워 수 조회 -> user를 팔로우하는 사람 수(팔로워 수)
    long countByFollowing(User following);

    // 팔로잉 수 조회 -> user가 팔로우하는 사람 수(팔로잉 수)
    long countByFollower(User follower);
}
