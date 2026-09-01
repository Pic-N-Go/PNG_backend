package com.project.picngo.community.repository;

import com.project.picngo.common.image.domain.ExifConsentStatus;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostBookmark;
import com.project.picngo.community.domain.PostWeather;
import com.project.picngo.user.domain.Role;
import com.project.picngo.user.domain.SocialProvider;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * searchBookmarked는 "저장한 글" 목록의 유일한 경로다. EXISTS가 틀리면 조용히
 * 전부 반환하거나(유저 격리 실패) 아무것도 반환하지 않는데, 서비스 단위 테스트는
 * 이 리포지토리를 목으로 막아 조인이 틀려도 통과한다. 실제 쿼리로 고정한다.
 */
@DataJpaTest
@ActiveProfiles("test")
class SearchBookmarkedQueryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostBookmarkRepository bookmarkRepository;

    @Autowired
    private UserRepository userRepository;

    private User user(String email, String nickname) {
        return userRepository.save(User.builder()
                .email(email)
                .nickname(nickname)
                .role(Role.USER)
                .provider(SocialProvider.LOCAL)
                .build());
    }

    // shootingTime·weather는 NOT NULL이라 값을 채워야 저장된다.
    private Post post(User author, String content) {
        return postRepository.save(Post.create(
                author, content, null, LocalTime.of(5, 30), PostWeather.CLEAR, null, null, List.of(),
                ExifConsentStatus.UNKNOWN, ExifConsentStatus.UNKNOWN));
    }

    private void bookmark(Post post, Long userId) {
        bookmarkRepository.save(new PostBookmark(post, userId));
    }

    private List<String> contentsOf(Page<Post> page) {
        return page.getContent().stream().map(Post::getContent).toList();
    }

    @Test
    @DisplayName("내가 저장한 글만 나온다 — 남이 저장한 글은 섞이지 않는다")
    void returnsOnlyMyBookmarks() {
        User me = user("me@test.com", "나");
        User other = user("other@test.com", "남");
        Post mine = post(other, "내가 저장한 글");
        Post theirs = post(other, "남이 저장한 글");
        post(other, "아무도 저장하지 않은 글");

        bookmark(mine, me.getId());
        bookmark(theirs, other.getId());

        Page<Post> result = postRepository.searchBookmarked(null, me.getId(), PageRequest.of(0, 20));

        // EXISTS의 userId 조건이 빠지면 3건 또는 2건이 나온다.
        assertThat(contentsOf(result)).containsExactly("내가 저장한 글");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("내 글도 저장했으면 나온다 — 작성자 여부와 무관하다")
    void includesMyOwnBookmarkedPost() {
        User me = user("me@test.com", "나");
        Post ownPost = post(me, "내가 쓰고 저장한 글");
        bookmark(ownPost, me.getId());

        Page<Post> result = postRepository.searchBookmarked(null, me.getId(), PageRequest.of(0, 20));

        // searchFollowing을 복사해 만든 쿼리라 author.id <> :userId 조건이 남아 있으면 0건이 된다.
        assertThat(contentsOf(result)).containsExactly("내가 쓰고 저장한 글");
    }

    @Test
    @DisplayName("저장한 글이 없으면 빈 페이지")
    void emptyWhenNoBookmarks() {
        User me = user("me@test.com", "나");
        post(user("other@test.com", "남"), "저장 안 한 글");

        Page<Post> result = postRepository.searchBookmarked(null, me.getId(), PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("검색어를 주면 저장한 글 안에서 다시 거른다")
    void keywordNarrowsWithinBookmarks() {
        User me = user("me@test.com", "나");
        User author = user("other@test.com", "남");
        Post sunset = post(author, "노을이 예쁜 곳");
        Post night = post(author, "야경 촬영지");
        bookmark(sunset, me.getId());
        bookmark(night, me.getId());

        Page<Post> result = postRepository.searchBookmarked("야경", me.getId(), PageRequest.of(0, 20));

        assertThat(contentsOf(result)).containsExactly("야경 촬영지");
    }

    @Test
    @DisplayName("같은 글을 여러 사람이 저장해도 중복으로 나오지 않는다")
    void noDuplicateWhenBookmarkedByMany() {
        User me = user("me@test.com", "나");
        User other = user("other@test.com", "남");
        Post popular = post(other, "인기 있는 글");
        bookmark(popular, me.getId());
        bookmark(popular, other.getId());

        Page<Post> result = postRepository.searchBookmarked(null, me.getId(), PageRequest.of(0, 20));

        // 조인으로 짰으면 행이 두 번 나온다. EXISTS를 쓴 이유가 이것이다.
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
