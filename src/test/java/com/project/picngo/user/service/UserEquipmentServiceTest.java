package com.project.picngo.user.service;

import com.project.picngo.common.exception.CustomException;
import com.project.picngo.common.exception.code.UserEquipmentErrorCode;
import com.project.picngo.user.domain.EquipmentType;
import com.project.picngo.user.domain.User;
import com.project.picngo.user.domain.UserEquipment;
import com.project.picngo.user.dto.UserEquipmentCreateRequest;
import com.project.picngo.user.dto.UserEquipmentResponse;
import com.project.picngo.user.dto.UserEquipmentUpdateRequest;
import com.project.picngo.user.repository.UserEquipmentRepository;
import com.project.picngo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEquipmentServiceTest {

    @Mock
    private UserEquipmentRepository userEquipmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserEquipmentService userEquipmentService;

    @Test
    @DisplayName("로그인한 사용자의 장비 목록만 조회한다")
    void getsCurrentUsersEquipments() {
        UserEquipment camera = UserEquipment.create(1L, EquipmentType.CAMERA, "Sony A7IV");
        UserEquipment lens = UserEquipment.create(1L, EquipmentType.LENS, "24-70mm F2.8");
        when(userEquipmentRepository.findAllByUserIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(camera, lens));

        List<UserEquipmentResponse> responses = userEquipmentService.getMyEquipments(1L);

        assertThat(responses)
                .extracting(UserEquipmentResponse::equipmentName)
                .containsExactly("Sony A7IV", "24-70mm F2.8");
        verify(userEquipmentRepository).findAllByUserIdOrderByCreatedAtAsc(1L);
    }

    @Test
    @DisplayName("장비를 등록할 때 이름의 앞뒤 공백을 제거하고 인증 사용자 ID를 저장한다")
    void createsEquipmentForCurrentUserWithTrimmedName() {
        UserEquipmentCreateRequest request =
                new UserEquipmentCreateRequest(EquipmentType.CAMERA, "  Sony A7IV  ");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        when(userEquipmentRepository.existsByUserIdAndEquipmentTypeAndEquipmentName(
                1L, EquipmentType.CAMERA, "Sony A7IV"
        )).thenReturn(false);
        when(userEquipmentRepository.countByUserId(1L)).thenReturn(0L);
        when(userEquipmentRepository.saveAndFlush(any(UserEquipment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userEquipmentService.createUserEquipment(1L, request);

        ArgumentCaptor<UserEquipment> captor = ArgumentCaptor.forClass(UserEquipment.class);
        verify(userEquipmentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getEquipmentType()).isEqualTo(EquipmentType.CAMERA);
        assertThat(captor.getValue().getEquipmentName()).isEqualTo("Sony A7IV");
    }

    @Test
    @DisplayName("같은 종류와 이름의 장비를 중복 등록할 수 없다")
    void rejectsDuplicateEquipment() {
        UserEquipmentCreateRequest request =
                new UserEquipmentCreateRequest(EquipmentType.CAMERA, "Sony A7IV");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        when(userEquipmentRepository.existsByUserIdAndEquipmentTypeAndEquipmentName(
                1L, EquipmentType.CAMERA, "Sony A7IV"
        )).thenReturn(true);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> userEquipmentService.createUserEquipment(1L, request)
        );

        assertSame(UserEquipmentErrorCode.USER_EQUIPMENT_ALREADY_EXISTS, exception.getErrorCode());
        verify(userEquipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("장비가 20개이면 추가 등록할 수 없다")
    void rejectsEquipmentOverLimit() {
        UserEquipmentCreateRequest request =
                new UserEquipmentCreateRequest(EquipmentType.LENS, "새 렌즈");
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mock(User.class)));
        when(userEquipmentRepository.existsByUserIdAndEquipmentTypeAndEquipmentName(
                1L, EquipmentType.LENS, "새 렌즈"
        )).thenReturn(false);
        when(userEquipmentRepository.countByUserId(1L)).thenReturn(20L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> userEquipmentService.createUserEquipment(1L, request)
        );

        assertSame(UserEquipmentErrorCode.USER_EQUIPMENT_LIMIT_EXCEEDED, exception.getErrorCode());
        verify(userEquipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("본인 장비의 종류와 이름을 수정한다")
    void updatesOwnedEquipment() {
        UserEquipment equipment = UserEquipment.create(1L, EquipmentType.CAMERA, "기존 장비");
        UserEquipmentUpdateRequest request =
                new UserEquipmentUpdateRequest(EquipmentType.LENS, "  수정 렌즈  ");
        when(userEquipmentRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(equipment));
        when(userEquipmentRepository.existsByUserIdAndEquipmentTypeAndEquipmentNameAndIdNot(
                1L, EquipmentType.LENS, "수정 렌즈", 10L
        )).thenReturn(false);

        UserEquipmentResponse response =
                userEquipmentService.updateUserEquipment(1L, 10L, request);

        assertThat(response.equipmentType()).isEqualTo(EquipmentType.LENS);
        assertThat(response.equipmentName()).isEqualTo("수정 렌즈");
        verify(userEquipmentRepository).flush();
    }

    @Test
    @DisplayName("다른 사용자의 장비를 수정할 수 없다")
    void rejectsUpdatingAnotherUsersEquipment() {
        when(userEquipmentRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty());
        UserEquipmentUpdateRequest request =
                new UserEquipmentUpdateRequest(EquipmentType.CAMERA, "수정 시도");

        CustomException exception = assertThrows(
                CustomException.class,
                () -> userEquipmentService.updateUserEquipment(1L, 10L, request)
        );

        assertSame(UserEquipmentErrorCode.USER_EQUIPMENT_NOT_FOUND, exception.getErrorCode());
        verify(userEquipmentRepository, never()).flush();
    }

    @Test
    @DisplayName("본인 장비를 삭제한다")
    void deletesOwnedEquipment() {
        UserEquipment equipment = UserEquipment.create(1L, EquipmentType.CAMERA, "Sony A7IV");
        when(userEquipmentRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(equipment));

        userEquipmentService.deleteUserEquipment(1L, 10L);

        verify(userEquipmentRepository).delete(equipment);
    }

    @Test
    @DisplayName("다른 사용자의 장비를 삭제할 수 없다")
    void rejectsDeletingAnotherUsersEquipment() {
        when(userEquipmentRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.empty());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> userEquipmentService.deleteUserEquipment(1L, 10L)
        );

        assertSame(UserEquipmentErrorCode.USER_EQUIPMENT_NOT_FOUND, exception.getErrorCode());
        verify(userEquipmentRepository, never()).delete(any());
    }
}
