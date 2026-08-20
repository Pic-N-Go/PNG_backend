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

    // 특정 사용자를 팔로우하는 사람들의 User ID 목록 조회 (탈퇴 계정 제외)
    @Query("select f.follower.id from Follow f where f.following.id = :followingId and f.follower.deletedAt is null")
    List<Long> findFollowerUserIdsByFollowingId(@Param("followingId") Long followingId);

    /*
     * 팔로워·팔로잉 수. 목록(findAllBy*)은 탈퇴 계정을 걸러내므로 수도 같이 걸러야 한다 —
     * 안 맞추면 "팔로워 12명"인데 목록에는 10명만 나온다.
     */

    // user를 팔로우하는 사람 수 (탈퇴 계정 제외)
    @Query("select count(f) from Follow f where f.following = :following and f.follower.deletedAt is null")
    long countByFollowing(@Param("following") User following);

    // user가 팔로우하는 사람 수 (탈퇴 계정 제외)
    @Query("select count(f) from Follow f where f.follower = :follower and f.following.deletedAt is null")
    long countByFollower(@Param("follower") User follower);
}
