package com.project.picngo.community.repository;

import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostImage;
import com.project.picngo.community.domain.PostWeather;
import com.project.picngo.common.image.domain.ExifConsentStatus;
import com.project.picngo.common.image.dto.PhotoExifInfo;
import com.project.picngo.user.domain.Follow;
import com.project.picngo.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostImageRepository imageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void myPostsSearchFiltersByAuthorId() {
        User me = persistUser("me@example.com", "me");
        User other = persistUser("another@example.com", "another");
        Post olderMine = post(me, "older my post");
        Post newerMine = post(me, "newer my post");
        Post otherPost = post(other, "other post");
        entityManager.persist(olderMine);
        entityManager.persist(newerMine);
        entityManager.persist(otherPost);
        entityManager.flush();
        updateCreatedAt(olderMine, LocalDateTime.of(2026, 1, 1, 10, 0));
        updateCreatedAt(newerMine, LocalDateTime.of(2026, 1, 2, 10, 0));
        updateCreatedAt(otherPost, LocalDateTime.of(2026, 1, 3, 10, 0));
        entityManager.clear();

        var result = postRepository.search(null, me.getId(), PageRequest.of(0, 20, latestSort()));

        assertThat(result.getContent())
                .extracting(Post::getContent)
                .containsExactly("newer my post", "older my post");
    }

    @Test
    void followingSearchReturnsOnlyPostsWrittenByFollowedUsers() {
        User me = persistUser("following-me@example.com", "following-me");
        User followed = persistUser("followed@example.com", "followed");
        User notFollowed = persistUser("not-followed@example.com", "not-followed");
        entityManager.persist(Follow.create(me, followed));
        Post myPost = post(me, "my post");
        Post olderFollowedPost = post(followed, "older followed post");
        Post newerFollowedPost = post(followed, "newer followed post");
        Post notFollowedPost = post(notFollowed, "not followed post");
        entityManager.persist(myPost);
        entityManager.persist(olderFollowedPost);
        entityManager.persist(newerFollowedPost);
        entityManager.persist(notFollowedPost);
        entityManager.flush();
        updateCreatedAt(olderFollowedPost, LocalDateTime.of(2026, 1, 1, 10, 0));
        updateCreatedAt(newerFollowedPost, LocalDateTime.of(2026, 1, 2, 10, 0));
        updateCreatedAt(myPost, LocalDateTime.of(2026, 1, 3, 10, 0));
        updateCreatedAt(notFollowedPost, LocalDateTime.of(2026, 1, 4, 10, 0));
        entityManager.clear();

        var result = postRepository.searchFollowing(
                null,
                me.getId(),
                PageRequest.of(0, 20, latestSort())
        );

        assertThat(result.getContent())
                .extracting(Post::getContent)
                .containsExactly("newer followed post", "older followed post");
    }

    @Test
    void latestSortReturnsNewestPostsFirst() {
        User author = persistUser("latest@example.com", "latest-author");
        Post oldest = post(author, "oldest");
        Post middle = post(author, "middle");
        Post newest = post(author, "newest");
        entityManager.persist(oldest);
        entityManager.persist(middle);
        entityManager.persist(newest);
        entityManager.flush();
        updateCreatedAt(oldest, LocalDateTime.of(2026, 1, 1, 10, 0));
        updateCreatedAt(middle, LocalDateTime.of(2026, 1, 2, 10, 0));
        updateCreatedAt(newest, LocalDateTime.of(2026, 1, 3, 10, 0));
        entityManager.clear();

        var result = postRepository.search(null, null, PageRequest.of(0, 20, latestSort()));

        assertThat(result.getContent())
                .extracting(Post::getContent)
                .containsExactly("newest", "middle", "oldest");
    }

    @Test
    void popularSortReturnsMoreLikedPostsFirstAndUsesLatestAsTieBreaker() {
        User author = persistUser("popular@example.com", "popular-author");
        Post fewLikes = post(author, "few likes");
        Post olderPopular = post(author, "older popular");
        Post newerPopular = post(author, "newer popular");
        entityManager.persist(fewLikes);
        entityManager.persist(olderPopular);
        entityManager.persist(newerPopular);
        entityManager.flush();
        updateCreatedAt(fewLikes, LocalDateTime.of(2026, 1, 3, 10, 0));
        updateCreatedAt(olderPopular, LocalDateTime.of(2026, 1, 1, 10, 0));
        updateCreatedAt(newerPopular, LocalDateTime.of(2026, 1, 2, 10, 0));
        updateLikeCount(fewLikes, 3);
        updateLikeCount(olderPopular, 10);
        updateLikeCount(newerPopular, 10);
        entityManager.clear();

        var result = postRepository.search(null, null, PageRequest.of(0, 20, popularSort()));

        assertThat(result.getContent())
                .extracting(Post::getContent)
                .containsExactly("newer popular", "older popular", "few likes");
    }

    @Test
    void postAndImagesCanBeBulkDeletedWithoutTransientReferenceError() {
        User author = persistUser("delete@example.com", "delete-user");
        Post post = post(author, "delete target");
        entityManager.persist(post);

        PostImage image = PostImage.uploaded(
                author.getId(),
                "community/" + author.getId() + "/delete.jpg",
                org.mockito.Mockito.mock(PhotoExifInfo.class)
        );
        image.attachTo(post, 0);
        entityManager.persist(image);
        entityManager.flush();
        entityManager.clear();

        Post managedPost = postRepository.findById(post.getId()).orElseThrow();
        List<String> objectKeys = imageRepository.findObjectKeysByPostId(post.getId());

        assertThat(objectKeys).containsExactly("community/" + author.getId() + "/delete.jpg");

        imageRepository.deleteAllByPostId(post.getId());
        postRepository.delete(managedPost);
        postRepository.flush();

        assertThat(postRepository.existsById(post.getId())).isFalse();
        assertThat(imageRepository.findObjectKeysByPostId(post.getId())).isEmpty();
    }

    @Test
    void commentCountIsDecreasedWithoutBecomingNegative() {
        User author = persistUser("comment-count@example.com", "comment-count-user");
        Post post = post(author, "comment count");
        entityManager.persist(post);
        entityManager.flush();
        entityManager.clear();

        postRepository.incrementCommentCount(post.getId());
        postRepository.incrementCommentCount(post.getId());
        postRepository.decrementCommentCount(post.getId());

        Post decreasedPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(decreasedPost.getCommentCount()).isEqualTo(1);

        entityManager.clear();
        postRepository.decrementCommentCount(post.getId());
        postRepository.decrementCommentCount(post.getId());

        Post zeroPost = postRepository.findById(post.getId()).orElseThrow();
        assertThat(zeroPost.getCommentCount()).isZero();
    }

    private User persistUser(String email, String nickname) {
        User user = User.createLocalUser(email, "password", nickname, Set.of());
        entityManager.persist(user);
        return user;
    }

    private Post post(User author, String content) {
        return Post.create(
                author,
                content,
                null,
                LocalTime.NOON,
                PostWeather.CLEAR,
                null,
                null,
                List.of(),
                ExifConsentStatus.UNKNOWN,
                ExifConsentStatus.UNKNOWN
        );
    }

    private Sort latestSort() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
    }

    private Sort popularSort() {
        return Sort.by(
                Sort.Order.desc("likeCount"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
    }

    private void updateCreatedAt(Post post, LocalDateTime createdAt) {
        entityManager.createNativeQuery("""
                        update community_posts
                        set created_at = :createdAt
                        where id = :postId
                        """)
                .setParameter("createdAt", createdAt)
                .setParameter("postId", post.getId())
                .executeUpdate();
    }

    private void updateLikeCount(Post post, long likeCount) {
        entityManager.createNativeQuery("""
                        update community_posts
                        set like_count = :likeCount
                        where id = :postId
                        """)
                .setParameter("likeCount", likeCount)
                .setParameter("postId", post.getId())
                .executeUpdate();
    }
}
