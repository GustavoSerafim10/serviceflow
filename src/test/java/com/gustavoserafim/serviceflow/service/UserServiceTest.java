package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.dto.UserCreateRequest;
import com.gustavoserafim.serviceflow.dto.UserResponse;
import com.gustavoserafim.serviceflow.entity.Role;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.exception.ConflictException;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @Test
    void create_withDuplicatedEmail_throwsConflictAndDoesNotSave() {
        when(userRepository.existsByEmailIgnoreCase("ana@empresa.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new UserCreateRequest("Ana", "ana@empresa.com", "senha1234", Role.SOLICITANTE)))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }
}
