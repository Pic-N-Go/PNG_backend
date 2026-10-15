package com.project.picngo.user.repository;

import com.project.picngo.common.image.domain.ExifConsentStatus;
import com.project.picngo.community.domain.Post;
import com.project.picngo.community.domain.PostWeather;
import com.project.picngo.community.repository.PostRepository;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.domain.UserBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 차단 필터는 JPQL의 not exists 서브쿼리로만 성립한다. 서비스 단위 테스트는 리포지토리를
 * 목으로 막아 조인이 틀려도 통과하므로, 실제 쿼리로 고정한다.
 *
 * 특히 viewerId가 null일 때(비로그인) 아무것도 걸러지지 않아야 한다 — 서브쿼리가 한 건도
 * 못 찾는 것에 기대고 있어서, 이게 깨지면 비로그인 피드가 통째로 빈다.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserBlockFilterQueryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserBlockRepository userBlockRepository;

    @Autowired
    private PostRepository postRepository;

    private User me;
    private User troll;

    @BeforeEach
    void setUp() {
        me = userRepository.save(User.createLocalUser("me@test.local", "pw", "나", Set.of()));
        troll = userRepository.save(User.createLocalUser("troll@test.local", "pw", "악성유저", Set.of()));
        post(me, "내 글");
        post(troll, "악성 유저 글");
    }

    @Test
    @DisplayName("차단하면 그 사용자의 글이 피드에서 빠진다")
    void hidesBlockedAuthorPosts() {
        userBlockRepository.save(UserBlock.create(me, troll));

        List<String> contents = contentsSeenBy(me.getId());

        assertThat(contents).containsExactly("내 글");
    }

    @Test
    @DisplayName("차단하기 전에는 모두 보인다")
    void showsEveryoneBeforeBlocking() {
        assertThat(contentsSeenBy(me.getId())).hasSize(2);
    }

    @Test
    @DisplayName("차단은 단방향이다 — 차단당한 쪽 피드는 그대로다")
    void blockIsOneDirectional() {
        userBlockRepository.save(UserBlock.create(me, troll));

        // troll은 나를 차단한 적이 없으므로 내 글이 계속 보여야 한다.
        assertThat(contentsSeenBy(troll.getId())).hasSize(2);
    }

    @Test
    @DisplayName("비로그인(viewerId가 null)이면 아무것도 걸러지지 않는다")
    void filtersNothingForAnonymousViewer() {
        userBlockRepository.save(UserBlock.create(me, troll));

        assertThat(contentsSeenBy(null)).hasSize(2);
    }

    @Test
    @DisplayName("차단을 해제하면 다시 보인다")
    void showsAgainAfterUnblock() {
        UserBlock block = userBlockRepository.save(UserBlock.create(me, troll));
        userBlockRepository.delete(block);

        assertThat(contentsSeenBy(me.getId())).hasSize(2);
    }

    private List<String> contentsSeenBy(Long viewerId) {
        return postRepository.search(null, null, viewerId, PageRequest.of(0, 20))
                .getContent().stream()
                .map(Post::getContent)
                .toList();
    }

    private void post(User author, String content) {
        postRepository.save(Post.create(
                author, content, null, LocalTime.NOON, PostWeather.CLEAR, null, null, List.of(),
                ExifConsentStatus.UNKNOWN, ExifConsentStatus.UNKNOWN
        ));
    }
}
