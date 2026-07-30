package com.project.picngo.album.repository;

import com.project.picngo.album.domain.Album;
import com.project.picngo.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    // 내 앨범 목록 조회
    List<Album> findAllByUser(User user);

    // 다른 사용자에게 공개할 앨범 목록 조회
    List<Album> findAllByUserAndIsPublicTrue(User user);
}
