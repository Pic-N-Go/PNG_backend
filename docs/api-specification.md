| 화면 | Method | Endpoint | 설명 | 담당자 | 상태 |
| --- | --- | --- | --- | --- | --- |
| 스플래시 | GET | /version/check | 앱 버전 확인 (강제 업데이트) | 소영재 | 미시작 |
| 로그인 | POST | /auth/login | 이메일/비밀번호 로그인 | 소영재 | 미시작 |
| 로그인 | POST | /auth/login/social | 소셜 로그인 (카카오/애플) | 소영재 | 미시작 |
| 로그인 | POST | /auth/refresh | Access Token 갱신 | 소영재 | 미시작 |
| 로그인 | POST | /auth/logout | 로그아웃 | 소영재 | 미시작 |
| 회원가입 | POST | /auth/register | 회원가입 | 소영재 | 미시작 |
| 회원가입 | POST | /auth/email/verify | 이메일 인증 코드 발송 | 소영재 | 미시작 |
| 회원가입 | POST | /auth/email/confirm | 이메일 인증 코드 확인 | 소영재 | 미시작 |
| 회원가입 | GET | /auth/nickname/check | 닉네임 중복 확인 | 소영재 | 미시작 |
| 회원가입 | GET | /categories | 관심 카테고리 목록 조회 | 소영재 | 미시작 |
| 회원가입 | PUT | /users/me/preferences | 관심 카테고리 저장 | 소영재 | 미시작 |
| 홈/지도 | GET | /spots/nearby | 내 위치 기반 주변 스팟 조회 | 박예은 | 미시작 |
| 홈/지도 | GET | /spots/recommended | 관심 테마 기반 추천 스팟 | 박예은 | 완료 |
| 홈/지도 | GET | /weather/current | 현재 위치 날씨 조회 | 박예은 | 미시작 |
| 홈/지도 | GET | /weather/golden-hour | 골든아워 시간 계산 | 모정민 | 미시작 |
| 홈/지도 | GET | /spots | 카테고리 필터 스팟 목록 | 이예인 | 미시작 |
| 홈/지도 | GET | /spots/popular | 이번 주 인기 스팟 | 이예인 | 미시작 |
| 홈/지도 | GET | /calendar/shoots | 이달의 출사 캘린더 | 박예은 | 미시작 |
| 홈/지도 | GET | /spots/search | 스팟 검색 | 이예인 | 미시작 |
| 지도 확장형 | GET | /spots/map | 지도 영역 내 스팟 핀 조회 | 이예인 | 미시작 |
| 지도 확장형 | GET | /spots/{id}/summary | 스팟 요약 카드 (핀 탭 시) | 이예인 | 미시작 |
| 지도 확장형 | GET | /chats/{spotId}/preview | 스팟 채팅 미리보기 (최근 3줄) | 소영재 | 미시작 |
| 지도 확장형 | GET | /chats/{spotId}/participants/count | 현재 채팅 참여 인원 | 소영재 | 미시작 |
| 검색 결과 | GET | /spots/search | 통합 검색 (키워드/카테고리) | 이예인 | 미시작 |
| 스팟 상세 | GET | /spots/{id} | 스팟 상세 정보 조회 | 박예은 | 완료 |
| 스팟 상세 | GET | /spots/{id}/photogenic-score | 포토제닉 지수 계산 (시즌 반영, 날씨/미세먼지 대기) | 박예은 | 진행중 |
| 스팟 상세 | GET | /spots/{id}/photos | 스팟 사진 목록 | 소영재 | 미시작 |
| 스팟 상세 | GET | /spots/{id}/reviews | 스팟 리뷰 목록 | 박예은 | 완료 |
| 스팟 상세 | POST | /spots/{id}/bookmark | 북마크 토글 (추가/해제 통합) | 박예은 | 완료 |
| 스팟 상세 | GET | /spots/{id}/nearby-parking | 주변 주차장 정보 | 박예은 | 미시작 |
| 채팅 탭 | GET | /chats/{spotId}/messages | 채팅 메시지 조회 | 소영재 | 미시작 |
| 채팅 탭 | POST | /chats/{spotId}/messages | 채팅 메시지 전송 | 소영재 | 미시작 |
| 채팅 탭 | GET | /chats/{spotId}/participants | 채팅 참여자 목록 | 소영재 | 미시작 |
| 코스에 저장 | GET | /courses | 내 여행 계획 목록 조회 | 모정민 | 미시작 |
| 코스에 저장 | POST | /courses/{id}/spots | 코스에 스팟 추가 | 모정민 | 미시작 |
| 코스에 저장 | POST | /courses | 새 여행 계획 생성 | 모정민 | 미시작 |
| 바로 출발 | GET | /directions | 출발지→목적지 이동 시간/거리 | 모정민 | 완료 |
| 스팟 등록 | POST | /upload/image | 사진 업로드 (S3 presigned URL) | 소영재 | 미시작 |
| 스팟 등록 | POST | /spots/geocode | GPS 좌표→주소 변환 | 소영재 | 미시작 |
| 스팟 등록 | GET | /spots/categories | 카테고리 목록 조회 | 소영재 | 미시작 |
| 스팟 등록 | POST | /spots | 스팟 등록 최종 제출 | 소영재 | 미시작 |
| 스팟 등록 | GET | /spots/{id}/status | 등록 검토 상태 확인 | 소영재 | 미시작 |
| 리뷰 작성 | POST | /spots/{id}/reviews | 리뷰 작성 | 박예은 | 완료 |
| 리뷰 작성 | PUT | /reviews/{id} | 리뷰 수정 | 박예은 | 완료 |
| 리뷰 작성 | DELETE | /reviews/{id} | 리뷰 삭제 | 박예은 | 완료 |
| 리뷰 작성 | POST | /reviews/{id}/photos | 리뷰 사진 추가 | 박예은 | 완료 |
| 리뷰 작성 | DELETE | /reviews/{id}/photos/{photoId} | 리뷰 사진 삭제 | 박예은 | 완료 |
| 코스 계획 | GET | /courses | 내 여행 계획 목록 | 모정민 | 미시작 |
| 코스 계획 | POST | /courses | 새 여행 계획 생성 | 모정민 | 미시작 |
| 코스 계획 | GET | /courses/{id} | 여행 계획 상세 조회 | 모정민 | 미시작 |
| 코스 계획 | PUT | /courses/{id} | 여행 계획 수정 | 모정민 | 미시작 |
| 코스 계획 | DELETE | /courses/{id} | 여행 계획 삭제 | 모정민 | 미시작 |
| 코스 계획 | POST | /courses/{id}/spots | 코스에 스팟 추가 | 모정민 | 미시작 |
| 코스 계획 | DELETE | /courses/{id}/spots/{spotId} | 코스에서 스팟 제거 | 모정민 | 미시작 |
| 코스 계획 | PUT | /courses/{id}/spots/order | 스팟 순서 변경 | 모정민 | 미시작 |
| 코스 계획 | GET | /weather/forecast | DAY별 날씨 예보 | 모정민 | 미시작 |
| 코스 계획 | GET | /spots/{id}/golden-hour | 스팟별 골든아워 시간 | 모정민 | 완료 |
| 위시리스트 설정 | GET | /wishlist | 위시리스트 목록 조회 | 모정민 | 미시작 |
| 위시리스트 설정 | POST | /wishlist | 위시리스트 추가 | 모정민 | 미시작 |
| 위시리스트 설정 | GET | /wishlist/{id} | 위시리스트 스팟 설정 조회 | 모정민 | 미시작 |
| 위시리스트 설정 | PUT | /wishlist/{id} | 날씨/시간대/알림 조건 저장 | 모정민 | 미시작 |
| 위시리스트 설정 | DELETE | /wishlist/{id} | 위시리스트 스팟 삭제 | 모정민 | 미시작 |
| 위시리스트 설정 | GET | /weather/forecast | 7일 예보 (조건 충족일 계산) | 모정민 | 미시작 |
| 위시리스트 설정 | PUT | /notifications/settings | 알림 시점/방해금지 시간 설정 | 모정민 | 미시작 |
| 알림 센터 | GET | /notifications | 알림 목록 조회 | 모정민 | 미시작 |
| 알림 센터 | PUT | /notifications/{id}/read | 알림 읽음 처리 | 모정민 | 미시작 |
| 알림 센터 | PUT | /notifications/read-all | 전체 알림 읽음 | 모정민 | 미시작 |
| 커뮤니티 피드 | GET | /posts | 피드 게시물 목록 | 박예은 | 미시작 |
| 커뮤니티 피드 | POST | /posts | 새 게시물 작성 | 박예은 | 미시작 |
| 커뮤니티 피드 | GET | /posts/{id} | 게시물 상세 조회 | 박예은 | 미시작 |
| 커뮤니티 피드 | POST | /posts/{id}/like | 좋아요 | 박예은 | 미시작 |
| 커뮤니티 피드 | DELETE | /posts/{id}/like | 좋아요 취소 | 박예은 | 미시작 |
| 커뮤니티 피드 | GET | /posts/{id}/comments | 댓글 목록 | 박예은 | 미시작 |
| 커뮤니티 피드 | POST | /posts/{id}/comments | 댓글 작성 | 박예은 | 미시작 |
| 커뮤니티 피드 | POST | /posts/{id}/bookmark | 게시물 북마크 | 박예은 | 미시작 |
| 커뮤니티 피드 | POST | /upload/image | 이미지 업로드 | 이예인 | 미시작 |
| 커뮤니티 피드 | GET | /posts/{id}/exif | EXIF 메타데이터 조회 | 소영재 | 미시작 |
| 콘테스트 | GET | /contests/current | 현재 진행 중인 콘테스트 | 이예인 | 미시작 |
| 콘테스트 | GET | /contests/{id}/entries | 출품작 목록 | 이예인 | 미시작 |
| 콘테스트 | POST | /contests/{id}/entries | 콘테스트 출품 | 이예인 | 미시작 |
| 콘테스트 | POST | /contests/{id}/entries/{entryId}/vote | 투표 (하루 3회 제한) | 이예인 | 미시작 |
| 콘테스트 | GET | /contests/{id}/my-entry | 내 출품 현황 | 이예인 | 미시작 |
| 콘테스트 | GET | /contests | 지난 콘테스트 목록 | 이예인 | 미시작 |
| 콘테스트 | GET | /contests/{id}/result | 콘테스트 결과 조회 | 이예인 | 미시작 |
| 마이페이지 | GET | /users/me | 내 프로필 정보 조회 | 이예인 | 미시작 |
| 마이페이지 | PUT | /users/me | 프로필 수정 | 이예인 | 미시작 |
| 마이페이지 | GET | /users/me/stats | 활동 통계 | 이예인 | 미시작 |
| 마이페이지 | GET | /users/me/reviews | 내가 쓴 리뷰 목록 | 박예은 | 완료 |
| 마이페이지 | GET | /users/me/visited-spots | 방문한 스팟 목록 (지도용) | 이예인 | 미시작 |
| 마이페이지 | GET | /users/me/albums | 지난 촬영 앨범 목록 | 이예인 | 미시작 |
| 마이페이지 | GET | /users/me/photogenic-report | 월간 포토제닉 리포트 | 이예인 | 미시작 |
| 마이페이지 | GET | /users/{id}/followers | 팔로워 목록 | 이예인 | 미시작 |
| 마이페이지 | GET | /users/{id}/following | 팔로잉 목록 | 이예인 | 미시작 |
| 마이페이지 | POST | /users/{id}/follow | 팔로우 | 이예인 | 미시작 |
| 마이페이지 | DELETE | /users/{id}/follow | 언팔로우 | 이예인 | 미시작 |
| 마이페이지 | GET | /users/me/bookmarked-spots | 북마크한 스팟 전체 목록 | 박예은 | 완료 |
| 마이페이지 | GET | /bookmark-collections/{id}/spots | 컬렉션에 담긴 스팟 목록 | 박예은 | 완료 |
| 타 유저 프로필 | GET | /users/{id}/profile | 타 유저 프로필 조회 | 이예인 | 미시작 |
| 설정 | GET | /settings | 앱 설정 조회 | 소영재 | 미시작 |
| 설정 | PUT | /settings | 앱 설정 저장 | 소영재 | 미시작 |
| 공통 | POST | /report | 스팟/게시물/유저 신고 | 소영재 | 미시작 |