package com.project.picngo.community.repository;

import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostImage;
import com.project.picngo.community.domain.PostWeather;
import com.project.picngo.common.image.dto.PhotoExifInfo;
import com.project.picngo.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
        entityManager.persist(post(me, "my post"));
        entityManager.persist(post(other, "other post"));
        entityManager.flush();
        entityManager.clear();

        var result = postRepository.search(null, me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent())
                .extracting(post -> post.getAuthor().getId())
                .containsOnly(me.getId());
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
                List.of()
        );
    }
}
