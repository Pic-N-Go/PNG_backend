package com.project.picngo.album.service;

import com.project.picngo.album.domain.Album;
import com.project.picngo.album.domain.AlbumPhoto;
import com.project.picngo.album.dto.AlbumCreateRequest;
import com.project.picngo.album.dto.AlbumDetailResponse;
import com.project.picngo.album.dto.AlbumPhotoAddRequest;
import com.project.picngo.album.dto.AlbumResponse;
import com.project.picngo.album.dto.AlbumUpdateRequest;
import com.project.picngo.album.repository.AlbumPhotoRepository;
import com.project.picngo.album.repository.AlbumRepository;
import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.AlbumErrorCode;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final AlbumPhotoRepository albumPhotoRepository;
    private final UserService userService;

    // 내 앨범 목록 조회
    public List<AlbumResponse> getMyAlbums(Long userId) {
        User user = userService.getById(userId);

        return albumRepository.findAllByUser(user).stream()
                .map(album -> AlbumResponse.from(
                        album,
                        albumPhotoRepository.countByAlbum(album)
                )).toList();
    }

    // 앨범 상세 조회
    public AlbumDetailResponse getAlbumDetail(Long userId, Long albumId) {
        Album album = getAlbum(albumId);
        validateOwner(userId, album);

        List<AlbumPhoto> photos = albumPhotoRepository.findAllByAlbum(album);

        return AlbumDetailResponse.from(album, photos);
    }

    // 앨범 생성
    @Transactional
    public AlbumResponse createAlbum(Long userId, AlbumCreateRequest request){
        User user = userService.getById(userId);

        Album album = Album.create(
                user,
                request.name(),
                request.category(),
                request.isPublic()
        );

        Album savedAlbum = albumRepository.save(album);

        return AlbumResponse.from(savedAlbum, 0);
    }

    // 앨범 수정
    @Transactional
    public AlbumResponse updateAlbum(Long userId, Long albumId, AlbumUpdateRequest request){
        Album album = getAlbum(albumId);
        validateOwner(userId, album);

        album.update(
                request.name(),
                request.category(),
                request.isPublic()
        );

        long photoCount = albumPhotoRepository.countByAlbum(album);

        return AlbumResponse.from(album, photoCount);
    }

    // 앨범 삭제
    @Transactional
    public void deleteAlbum(Long userId, Long albumId) {
        Album album = getAlbum(albumId);
        validateOwner(userId, album);

        albumPhotoRepository.deleteByAlbum(album);
        albumRepository.delete(album);
    }

    // 앨범 사진 추가
    @Transactional
    public AlbumDetailResponse addAlbumPhoto(Long userId, Long albumId, AlbumPhotoAddRequest request){

        Album album = getAlbum(albumId);
        validateOwner(userId, album);

        albumPhotoRepository.save(AlbumPhoto.create(album, request.imageUrl()));
        List<AlbumPhoto> photos = albumPhotoRepository.findAllByAlbum(album);

        return AlbumDetailResponse.from(album, photos);
    }

    // 앨범 사진 삭제
    @Transactional
    public void deleteAlbumPhoto(Long userId, Long albumId, Long photoId) {
        Album album = getAlbum(albumId);
        validateOwner(userId, album);

        AlbumPhoto albumPhoto = albumPhotoRepository.findById(photoId)
                .orElseThrow(() -> new CustomException(AlbumErrorCode.ALBUM_PHOTO_NOT_FOUND));

        if (!albumPhoto.getAlbum().getId().equals(album.getId())) {
            throw new CustomException(AlbumErrorCode.ALBUM_ACCESS_DENIED);
        }

        albumPhotoRepository.delete(albumPhoto);
    }

    private Album getAlbum(Long albumId) {
        return albumRepository.findById(albumId)
                .orElseThrow(() -> new CustomException(AlbumErrorCode.ALBUM_NOT_FOUND));
    }

    private void validateOwner(Long userId, Album album) {
        if(!album.getUser().getId().equals(userId)) {
            throw new CustomException(AlbumErrorCode.ALBUM_ACCESS_DENIED);
        }
    }
}
