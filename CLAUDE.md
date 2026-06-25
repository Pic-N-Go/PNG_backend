# picngo 백엔드

출사 여행자를 위한 포토스팟 정보 제공 및 커뮤니티 서비스. 공모전 프로젝트.

## 기술 스택

- Spring Boot 4.0.6 / Java 21
- MySQL + Spring Data JPA (Hibernate 7)
- Spring Security + OAuth2
- Lombok, Validation, Swagger (springdoc 2.5)
- dotenv-java (.env 파일로 환경변수 로딩)

## 로컬 실행

1. 프로젝트 루트에 `.env` 파일 생성
2. MySQL 실행: `brew services start mysql`
3. DB 생성: `CREATE DATABASE picngo;`
4. `./gradlew bootRun`
5. Swagger: `http://localhost:8080/swagger-ui/index.html`

### .env 필수 항목

```
DB_URL=jdbc:mysql://localhost:3306/picngo?useSSL=false&serverTimezone=Asia/Seoul
DB_USERNAME=root
DB_PASSWORD=
WEATHER_SERVICE_KEY=
KAKAO_REST_API_KEY=
```

## 패키지 구조

도메인별로 분리. 각 도메인은 아래 구조를 따른다.

```
{domain}/
├── controller/
│   ├── {Domain}Controller.java        # 엔드포인트
│   └── {Domain}ControllerApiSpec.java # Swagger 어노테이션 분리
├── domain/
│   └── {Domain}.java                  # 엔티티
├── dto/                               # record 타입 사용
├── repository/
└── service/
    └── {Domain}Service.java
```

공통 코드는 `common/` 패키지 사용.
- `common/domain/BaseTimeEntity` — `createdAt`, `updatedAt` 자동 관리
- `common/exception/` — 전역 예외 처리 및 도메인별 ErrorCode

## 코드 패턴

### 엔티티
```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Spot extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder
    public Spot(...) { ... }
}
```

### DTO (record + 정적 팩토리)
```java
public record SpotResponse(Long id, String name) {
    public static SpotResponse from(Spot spot) {
        return new SpotResponse(spot.getId(), spot.getName());
    }
}
```

### Service
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 기본값
public class SpotService {
    // 변경 메서드만 @Transactional 추가
}
```

### 예외 처리
```java
// 도메인별 ErrorCode enum 생성 (common/exception/code/)
throw new CustomException(SpotErrorCode.SPOT_NOT_FOUND);
```

### 인증 미구현 시 임시 처리
```java
private final Long TEMP_USER_ID = 1L; // Spring Security 연동 전까지 하드코딩
```

## 깃 컨벤션

### 브랜치
- `main` — 배포
- `develop` — 개발 기반
- `feature/기능명` — 소문자 + `-` (예: `feature/spot-detail`)

### 커밋 메시지
```
✨ feat: 변경사항 요약
- 상세 내용
- issue : #번호
```

| 이모지 | 타입 | 설명 |
|--------|------|------|
| ✨ | feat | 새로운 기능 |
| 🐛 | fix | 버그 수정 |
| 📝 | docs | 문서 수정 |
| 💄 | style | 포맷팅 |
| ♻️ | refactor | 리팩토링 |
| ✅ | test | 테스트 |
| 🔧 | chore | 빌드/패키지 |

### PR
```
## 🔗 반영 브랜치
(#이슈번호) feature/xxx -> develop

## 📝 작업 내용

## 💬 리뷰 요구사항(선택)
```

## 담당자별 API 영역

| 담당자 | 영역 |
|--------|------|
| 박예은 | 스팟 상세, 리뷰, 북마크, 포토제닉 지수, 커뮤니티 피드 |
| 이예인 | 스팟 목록/검색, 마이페이지, 콘테스트 |
| 소영재 | 인증, 스팟 등록, 사진, 채팅 |
| 모정민 | 날씨/골든아워, 여행 계획, 위시리스트, 알림 |

## 외부 API

| 용도 | API | 비고 |
|------|-----|------|
| 스팟 초기 데이터 | 한국관광공사 TourAPI 4.0 | 관광공사 인증 뱃지 출처 |
| 날씨 | 기상청 단기예보 API | 공공데이터포털 |
| 미세먼지 | 에어코리아 대기오염정보 API | 한국환경공단 |
| 주변 주차장 | 전국주차장정보표준데이터 API | 공공데이터포털. 노출 항목: 주차장명, 거리, 무료/유료 |
| 길찾기 | 카카오맵/네이버 지도/Apple 지도 딥링크 | 백엔드 구현 없음. 프론트에서 스팟 좌표로 앱 딥링크 호출. |
