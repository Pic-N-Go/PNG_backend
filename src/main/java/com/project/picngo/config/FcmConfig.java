package com.project.picngo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${firebase.config.path:}")
    private String firebaseConfigPath;

    private final ResourceLoader resourceLoader;

    public FcmConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public FirebaseApp firebaseApp() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        List<String> candidatePaths = List.of(
                firebaseConfigPath,
                "file:secrets/firebase-key.json",
                "file:./secrets/firebase-key.json",
                "classpath:firebase-key.json"
        );

        for (String path : candidatePaths) {
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            try {
                Resource resource = resourceLoader.getResource(path);
                if (resource.exists()) {
                    try (InputStream serviceAccount = resource.getInputStream()) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                                .build();
                        FirebaseApp app = FirebaseApp.initializeApp(options);
                        log.info("🔥 Firebase application successfully initialized using path: {}", path);
                        return app;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to load Firebase key from path candidate: {}", path, e);
            }
        }

        log.warn("⚠️ Firebase 설정 파일을 찾을 수 없거나 초기화에 실패했습니다. (시도한 경로: {})", candidatePaths);
        return null;
    }

    // firebaseApp()이 null을 반환하면 스프링은 FirebaseApp 빈이 "없는" 것으로 본다.
    // 파라미터로 바로 받으면 null 검사에 닿기 전에 기동이 실패하므로, 있으면 쓰고 없으면 건너뛰도록 ObjectProvider로 받는다.
    @Bean
    public FirebaseMessaging firebaseMessaging(ObjectProvider<FirebaseApp> firebaseAppProvider) {
        FirebaseApp firebaseApp = firebaseAppProvider.getIfAvailable();
        if (firebaseApp == null) {
            log.warn("🔥 FirebaseApp이 초기화되지 않아 FirebaseMessaging 빈 생성을 건너뜁니다.");
            return null;
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}


