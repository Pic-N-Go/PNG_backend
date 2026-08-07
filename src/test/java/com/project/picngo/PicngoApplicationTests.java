package com.project.picngo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class PicngoApplicationTests {

	// 테스트 환경엔 유효한 firebase-key.json이 없어 실제 FirebaseMessaging 빈 생성이 실패한다.
	// 컨텍스트 로딩 확인이 목적이므로 목으로 대체한다.
	@MockitoBean
	private com.google.firebase.messaging.FirebaseMessaging firebaseMessaging;

	@Test
	void contextLoads() {
	}

}
