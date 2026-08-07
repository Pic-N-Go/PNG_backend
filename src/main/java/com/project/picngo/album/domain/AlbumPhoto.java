package com.project.picngo.album.domain;

import com.project.picngo.common.domain.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "album_photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlbumPhoto extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 사진이 포함된 앨범
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    // 앨범 사진 URL
    @Column(nullable = false, length = 500)
    private String imageUrl;

    private AlbumPhoto(Album album, String imageUrl) {
        this.album = album;
        this.imageUrl = imageUrl;
    }

    // 앨범 사진 추가
    public static AlbumPhoto create(Album album, String imageUrl) {
        return new AlbumPhoto(album, imageUrl);
    }
}
