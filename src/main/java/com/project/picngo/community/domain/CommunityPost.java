package com.project.picngo.community.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import com.project.picngo.spot.domain.Spot;
import com.project.picngo.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "community_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id")
    private Spot spot;

    @Column(name = "shooting_time", nullable = false)
    private LocalTime shootingTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "weather", nullable = false, length = 30)
    private CommunityWeather weather;

    @Column(name = "camera_model", length = 100)
    private String cameraModel;

    @Column(name = "lens_model", length = 150)
    private String lensModel;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @Column(name = "bookmark_count", nullable = false)
    private long bookmarkCount;

    @ElementCollection
    @CollectionTable(name = "community_post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag", nullable = false, length = 30)
    @OrderColumn(name = "tag_order")
    @BatchSize(size = 100)
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private Set<CommunityImage> images = new LinkedHashSet<>();

    private CommunityPost(
            User author,
            String content,
            Spot spot,
            LocalTime shootingTime,
            CommunityWeather weather,
            String cameraModel,
            String lensModel,
            List<String> tags
    ) {
        this.author = author;
        this.content = content;
        this.spot = spot;
        this.shootingTime = shootingTime;
        this.weather = weather;
        this.cameraModel = normalize(cameraModel);
        this.lensModel = normalize(lensModel);
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public static CommunityPost create(
            User author,
            String content,
            Spot spot,
            LocalTime shootingTime,
            CommunityWeather weather,
            String cameraModel,
            String lensModel,
            List<String> tags
    ) {
        return new CommunityPost(
                author,
                content,
                spot,
                shootingTime,
                weather,
                cameraModel,
                lensModel,
                tags
        );
    }

    public void update(
            String content,
            LocalTime shootingTime,
            CommunityWeather weather,
            String cameraModel,
            String lensModel,
            List<String> tags) {
        if(content != null){
            String normalizedContent = content.trim();

            if(normalizedContent.isBlank()){
                throw new IllegalArgumentException("게시글 내용은 공백일 수 없습니다.");
            }
            this.content = normalizedContent;
        }

        if(shootingTime != null){
            this.shootingTime = shootingTime;
        }
        if(weather != null){
            this.weather = weather;
        }
        if(cameraModel != null){
            this.cameraModel = normalize(cameraModel);
        }
        if(lensModel != null){
            this.lensModel = normalize(lensModel);
        }
        if(tags != null){
            this.tags.clear();
            this.tags.addAll(tags);
        }
    }

    public void changeSpot(Spot spot) {
        this.spot = spot;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
