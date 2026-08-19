package com.project.picngo.user.controller;

import com.project.picngo.auth.service.CustomUserDetails;
import com.project.picngo.user.domain.EquipmentType;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.domain.UserEquipment;
import com.project.picngo.user.repository.UserEquipmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.sql.init.mode=never")
@Transactional
class UserEquipmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserEquipmentRepository userEquipmentRepository;

    @Test
    @DisplayName("인증 없이 내 장비 API를 호출하면 접근을 거부한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/users/me/equipments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("장비를 등록하고 내 장비 목록에서 조회한다")
    void createsAndGetsEquipment() throws Exception {
        mockMvc.perform(post("/users/me/equipments")
                        .with(authentication(authenticationOf(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentType": "CAMERA",
                                  "equipmentName": "  Sony A7IV  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipmentType").value("CAMERA"))
                .andExpect(jsonPath("$.equipmentName").value("Sony A7IV"));

        mockMvc.perform(get("/users/me/equipments")
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].equipmentType").value("CAMERA"))
                .andExpect(jsonPath("$[0].equipmentName").value("Sony A7IV"));
    }

    @Test
    @DisplayName("장비 이름이 비어 있거나 지원하지 않는 종류이면 400을 반환한다")
    void validatesCreateRequest() throws Exception {
        mockMvc.perform(post("/users/me/equipments")
                        .with(authentication(authenticationOf(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentType": "CAMERA",
                                  "equipmentName": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/users/me/equipments")
                        .with(authentication(authenticationOf(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentType": "DRONE",
                                  "equipmentName": "DJI Mavic 3"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("같은 장비를 중복 등록하면 409를 반환한다")
    void rejectsDuplicateEquipment() throws Exception {
        userEquipmentRepository.saveAndFlush(
                UserEquipment.create(1L, EquipmentType.CAMERA, "Sony A7IV")
        );

        mockMvc.perform(post("/users/me/equipments")
                        .with(authentication(authenticationOf(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentType": "CAMERA",
                                  "equipmentName": "Sony A7IV"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_EQUIPMENT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("본인 장비를 수정하고 삭제한다")
    void updatesAndDeletesOwnedEquipment() throws Exception {
        UserEquipment equipment = userEquipmentRepository.saveAndFlush(
                UserEquipment.create(1L, EquipmentType.CAMERA, "Sony A7IV")
        );

        mockMvc.perform(put("/users/me/equipments/{equipmentId}", equipment.getId())
                        .with(authentication(authenticationOf(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentType": "LENS",
                                  "equipmentName": "24-70mm F2.8"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equipmentType").value("LENS"))
                .andExpect(jsonPath("$.equipmentName").value("24-70mm F2.8"));

        mockMvc.perform(delete("/users/me/equipments/{equipmentId}", equipment.getId())
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isNoContent());

        assertThat(userEquipmentRepository.findById(equipment.getId())).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 장비를 수정하거나 삭제할 수 없다")
    void rejectsUpdatingAndDeletingAnotherUsersEquipment() throws Exception {
        UserEquipment otherUsersEquipment = userEquipmentRepository.saveAndFlush(
                UserEquipment.create(2L, EquipmentType.CAMERA, "Canon R6 II")
        );

        mockMvc.perform(put("/users/me/equipments/{equipmentId}", otherUsersEquipment.getId())
                        .with(authentication(authenticationOf(1L)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentType": "LENS",
                                  "equipmentName": "수정 시도"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_EQUIPMENT_NOT_FOUND"));

        mockMvc.perform(delete("/users/me/equipments/{equipmentId}", otherUsersEquipment.getId())
                        .with(authentication(authenticationOf(1L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_EQUIPMENT_NOT_FOUND"));

        assertThat(userEquipmentRepository.findById(otherUsersEquipment.getId())).isPresent();
    }

    private UsernamePasswordAuthenticationToken authenticationOf(Long userId) {
        User user = Mockito.mock(User.class);
        Mockito.when(user.getId()).thenReturn(userId);
        Mockito.when(user.getEmail()).thenReturn("user" + userId + "@test.com");
        Mockito.when(user.getPassword()).thenReturn("password");
        Mockito.when(user.getNickname()).thenReturn("사용자" + userId);

        CustomUserDetails userDetails = CustomUserDetails.from(user, Collections.emptyList());
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }
}
