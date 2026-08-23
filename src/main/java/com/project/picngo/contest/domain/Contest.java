package com.project.picngo.contest.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String themeImageUrl;

    @Column(nullable = false)
    private LocalDateTime submitStartAt; // 출품 시작일

    @Column(nullable = false)
    private LocalDateTime submitEndAt; // 출품 종료일

    @Column(nullable = false)
    private LocalDateTime voteStartAt; // 투표 시작일

    @Column(nullable = false)
    private LocalDateTime voteEndAt; // 투표 종료일

    @Column(nullable = false)
    private LocalDateTime resultOpenAt; // 결과 발표일

    @Column(nullable = false)
    private int maxEntriesPerUser; // 1인 최대 출품 수

    @Column(nullable = false)
    private int voteLimit; // 콘테스트 기간 내 최대 투표 수

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // ── 주기 규칙 ──────────────────────────────────────────────
    // 출품 2주 → 투표 2주 → 투표 종료 다음 날 오전 9시 결과 발표.
    // 발표까지의 사이(집계 중)에는 결과를 공개하지 않는다(ContestService.canShowRanking).
    //
    // 회차를 손으로 INSERT하면 이 규칙이 아무 데도 강제되지 않아 3일짜리 출품 기간도 통과한다.
    // 그래서 생성 경로를 create() 하나로 모으고 날짜 5개를 전부 여기서 파생시킨다.
    private static final int SUBMIT_WEEKS = 2;
    private static final int VOTE_WEEKS = 2;
    private static final LocalTime RESULT_ANNOUNCE_TIME = LocalTime.of(9, 0);

    /**
     * "오전 9시 발표"가 가리키는 시간대. 컨테이너(alpine)에 TZ가 없으면 systemDefault()가 UTC라
     * 발표가 서울 기준 18시에 열린다. 배포 설정에 기대지 않도록 도메인에 못박는다.
     * 레포의 다른 모듈(Course·NotificationScheduler·WeatherForecastService 등)과 같은 방식이다.
     */
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public static Contest create(
            String title,
            String description,
            String themeImageUrl,
            LocalDateTime submitStartAt,
            int maxEntriesPerUser,
            int voteLimit
    ) {
        Contest contest = new Contest();
        contest.title = title;
        contest.description = description;
        contest.themeImageUrl = themeImageUrl;
        contest.submitStartAt = submitStartAt;
        contest.submitEndAt = submitStartAt.plusWeeks(SUBMIT_WEEKS);
        // getPhase가 투표 시작을 submitEndAt으로 본다. voteStartAt을 그보다 뒤로 두면
        // 투표는 받는데 순위 스냅샷만 늦게 시작하는 구간이 생기므로 둘을 항상 붙여 둔다.
        contest.voteStartAt = contest.submitEndAt;
        contest.voteEndAt = contest.voteStartAt.plusWeeks(VOTE_WEEKS);
        contest.resultOpenAt = contest.voteEndAt
                .atZone(ZONE)
                .toLocalDate()
                .plusDays(1)
                .atTime(RESULT_ANNOUNCE_TIME);
        contest.maxEntriesPerUser = maxEntriesPerUser;
        contest.voteLimit = voteLimit;
        contest.active = true;
        return contest;
    }

    public ContestPhase getPhase(LocalDateTime now) {
        // submitStartAt을 보지 않으면 다음 달 회차도 SUBMITTING으로 잡힌다.
        // GET /contests/upcoming이 그 id를 내주므로 출품 API가 몇 주 일찍 열린다.
        if (now.isBefore(submitStartAt)) {
            return ContestPhase.UPCOMING;
        }

        if (now.isBefore(submitEndAt)) {
            return ContestPhase.SUBMITTING;
        }

        if (now.isBefore(voteEndAt)) {
            return ContestPhase.VOTING;
        }

        if (now.isBefore(resultOpenAt)) {
            return ContestPhase.RESULT;
        }

        return ContestPhase.ENDED;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
