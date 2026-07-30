package com.project.picngo.album.repository;

import com.project.picngo.album.domain.Album;
import com.project.picngo.album.domain.AlbumPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlbumPhotoRepository extends JpaRepository<AlbumPhoto, Long> {

    // 특정 앨범에 포함된 사진 목록 조회
    List<AlbumPhoto> findAllByAlbum(Album album);

    // 특정 앨범에 포함된 사진 개수 조회
    long countByAlbum(Album album);

    // 특정 앨범에 포함된 사진 전체 삭제
    void deleteByAlbum(Album album);
}
