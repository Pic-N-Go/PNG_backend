package com.project.picngo.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 탈퇴 후 복구 기간이 지난 계정의 개인정보를 파기한다.
 *
 * <p>users row는 지우지 않는다 — 게시글·댓글이 "탈퇴한 사용자"로 계속 보이려면 작성자 row가
 * 있어야 하고, row를 지우면 FK 때문에 남이 쓴 글에 달린 대화까지 끊긴다.
 * 지우는 것은 개인정보 컬럼뿐이다(User.purgePersonalData).
 *
 * <p>다중 인스턴스에서 같은 시각에 함께 돌아도 안전하다 — 이미 파기된 계정은 건너뛰므로
 * 두 번 실행돼도 결과가 같다. 별도 분산 락을 두지 않은 이유가 이것이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPurgeScheduler {

    private final UserService userService;

    /**
     * 매일 새벽 4시. 트래픽이 가장 적은 시각이고, 파기가 하루 늦어도 문제되지 않는다
     * (복구 기간이 지난 계정은 이미 로그인·조회에서 제외된 상태다).
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void purgeExpiredAccounts() {
        try {
            int purged = userService.purgeExpiredAccounts();
            if (purged > 0) {
                log.info("탈퇴 계정 개인정보 파기 완료: {}건", purged);
            }
        } catch (Exception e) {
            // 예외를 삼키지 않으면 스케줄러가 죽어 다음 실행이 오지 않는다.
            log.error("탈퇴 계정 파기 실패 — 다음 실행에서 다시 시도한다", e);
        }
    }
}
