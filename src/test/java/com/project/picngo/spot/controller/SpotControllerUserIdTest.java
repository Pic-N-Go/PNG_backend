package com.project.picngo.spot.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.spot.service.PhotogenicService;
import com.project.picngo.spot.service.ReviewService;
import com.project.picngo.spot.service.SpotService;
import com.project.picngo.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 컨트롤러가 토큰의 userId를 서비스까지 실제로 넘기는지 검증한다.
 * 서비스/리포지토리 테스트는 userId를 직접 주입받으므로 이 배선이 끊겨도 안 깨진다 —
 * 그러면 응답은 200 OK에 isBookmarked가 전부 false인 채로 조용히 나간다.
 */
@ExtendWith(MockitoExtension.class)
class SpotControllerUserIdTest {

    @Mock
    private SpotService spotService;

    @Mock
    private ReviewService reviewService;

    @Mock
    private PhotogenicService photogenicService;

    @InjectMocks
    private SpotController spotController;

    private CustomUserDetails userDetails(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return CustomUserDetails.from(user, Collections.emptyList());
    }

    @Test
    @DisplayName("스팟 목록 - 로그인 사용자의 id가 서비스로 전달된다")
    void 스팟_목록_로그인_userId_전달() {
        spotController.getSpots(userDetails(7L), null, "latest", 0, 20);

        verify(spotService).getSpots(any(), any(), anyInt(), anyInt(), eq(7L));
    }

    @Test
    @DisplayName("인기 스팟 - 로그인 사용자의 id가 서비스로 전달된다")
    void 인기_스팟_로그인_userId_전달() {
        spotController.getPopularSpots(userDetails(7L), null, 10);

        verify(spotService).getPopularSpots(any(), anyInt(), eq(7L));
    }

    @Test
    @DisplayName("스팟 검색 - 로그인 사용자의 id가 서비스로 전달된다")
    void 스팟_검색_로그인_userId_전달() {
        spotController.searchSpots(userDetails(7L), "공원", null, 0, 20);

        verify(spotService).searchSpots(any(), any(), anyInt(), anyInt(), eq(7L));
    }

    @Test
    @DisplayName("비로그인 요청은 userId 없이(null) 서비스를 호출한다")
    void 비로그인은_userId_null() {
        spotController.getSpots(null, null, "latest", 0, 20);
        spotController.getPopularSpots(null, null, 10);
        spotController.searchSpots(null, "공원", null, 0, 20);

        verify(spotService).getSpots(any(), any(), anyInt(), anyInt(), eq(null));
        verify(spotService).getPopularSpots(any(), anyInt(), eq(null));
        verify(spotService).searchSpots(any(), any(), anyInt(), anyInt(), eq(null));
    }
}
