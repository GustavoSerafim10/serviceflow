package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.ChangePasswordRequest;
import com.gustavoserafim.serviceflow.dto.ResetPasswordRequest;
import com.gustavoserafim.serviceflow.dto.UserCreateRequest;
import com.gustavoserafim.serviceflow.dto.UserResponse;
import com.gustavoserafim.serviceflow.entity.Role;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.exception.BusinessRuleException;
import com.gustavoserafim.serviceflow.exception.ConflictException;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void create_hashesPasswordAndNormalizesEmail() {
        when(userRepository.existsByEmailIgnoreCase("ana@empresa.com")).thenReturn(false);
        when(passwordEncoder.encode("senha1234")).thenReturn("HASH_BCRYPT");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.create(
                new UserCreateRequest("Ana", "  Ana@Empresa.com ", "senha1234", Role.TECNICO));

        // Captura o que foi realmente enviado ao repository para inspecionar.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPasswordHash()).isEqualTo("HASH_BCRYPT").isNotEqualTo("senha1234");
        assertThat(saved.getEmail()).isEqualTo("ana@empresa.com");
        assertThat(response.role()).isEqualTo(Role.TECNICO);
    }

    private User existingUser() {
        User user = new User();
        user.setEmail("ana@empresa.com");
        user.setPasswordHash("HASH_ATUAL");
        user.setTokenVersion(4);
        return user;
    }

    @Test
    void changeOwnPassword_withCorrectCurrentPassword_updatesHashAndBumpsTokenVersion() {
        User user = existingUser();
        when(userRepository.findByEmailIgnoreCase("ana@empresa.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("atual1234", "HASH_ATUAL")).thenReturn(true);
        when(passwordEncoder.matches("nova12345", "HASH_ATUAL")).thenReturn(false);
        when(passwordEncoder.encode("nova12345")).thenReturn("HASH_NOVO");

        userService.changeOwnPassword("ana@empresa.com", new ChangePasswordRequest("atual1234", "nova12345"));

        assertThat(user.getPasswordHash()).isEqualTo("HASH_NOVO");
        assertThat(user.getTokenVersion()).isEqualTo(5); // sessões antigas invalidadas
    }

    @Test
    void changeOwnPassword_withWrongCurrentPassword_throwsBusinessRuleAndChangesNothing() {
        User user = existingUser();
        when(userRepository.findByEmailIgnoreCase("ana@empresa.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("errada123", "HASH_ATUAL")).thenReturn(false);

        assertThatThrownBy(() -> userService.changeOwnPassword("ana@empresa.com",
                new ChangePasswordRequest("errada123", "nova12345")))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(user.getPasswordHash()).isEqualTo("HASH_ATUAL");
        assertThat(user.getTokenVersion()).isEqualTo(4);
    }

    @Test
    void changeOwnPassword_withSameAsCurrent_throwsBusinessRule() {
        User user = existingUser();
        when(userRepository.findByEmailIgnoreCase("ana@empresa.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("atual1234", "HASH_ATUAL")).thenReturn(true);

        assertThatThrownBy(() -> userService.changeOwnPassword("ana@empresa.com",
                new ChangePasswordRequest("atual1234", "atual1234")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resetPassword_setsNewHashAndBumpsTokenVersion() {
        User user = existingUser();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("redefinida1")).thenReturn("HASH_RESET");

        userService.resetPassword(7L, new ResetPasswordRequest("redefinida1"));

        assertThat(user.getPasswordHash()).isEqualTo("HASH_RESET");
        assertThat(user.getTokenVersion()).isEqualTo(5);
    }

    @Test
    void create_withDuplicatedEmail_throwsConflictAndDoesNotSave() {
        when(userRepository.existsByEmailIgnoreCase("ana@empresa.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new UserCreateRequest("Ana", "ana@empresa.com", "senha1234", Role.SOLICITANTE)))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }
}
