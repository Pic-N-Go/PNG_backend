package com.project.picngo.auth.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `isNewUser`의 JSON 키를 고정한다.
 *
 * 클라이언트는 이 값 하나로 신규 카카오 가입자를 온보딩으로 보낼지 정한다
 * (PNG_frontend `LoginScreen.tsx`의 `data.isNewUser`). 키가 `newUser`로 바뀌면
 * 프론트에서 undefined(falsy)가 되어 **모든 신규 가입자가 온보딩을 건너뛰고**
 * 서버가 임의로 지은 닉네임에 갇힌다. 예외도 안 나고 신규 가입자에게만 생겨서
 * 발견이 늦다.
 *
 * 지금 키가 유지되는 건 record 컴포넌트라서다 — 일반 POJO의 `isNewUser()` getter였다면
 * `newUser`로 잘린다. 즉 Jackson 버전·네이밍 전략에 달린 값이라 테스트로 못 박는다.
 */
class TokenResponseSerializationTest {

    @Test
    @DisplayName("isNewUser가 JSON에 그대로 isNewUser 키로 나간다")
    void keepsIsNewUserKey() {
        TokenResponse response = TokenResponse.bearer("tok", 60L, "refresh", 120L, null, true);

        String json = new ObjectMapper().writeValueAsString(response);

        assertTrue(json.contains("\"isNewUser\":true"),
                "클라이언트가 읽는 키가 바뀌었다. 실제 JSON: " + json);
    }
}
