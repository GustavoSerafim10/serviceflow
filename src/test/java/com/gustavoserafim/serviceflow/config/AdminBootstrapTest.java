package com.gustavoserafim.serviceflow.config;

import com.gustavoserafim.serviceflow.entity.Role;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = new AdminBootstrap(userRepository, passwordEncoder, "Administrador", "Admin@Empresa.com", "Senha@1234");
    }

    @Test
    void createsTheFirstAdminWhenNoAdminExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("Admin@Empresa.com")).thenReturn(false);
        when(passwordEncoder.encode("Senha@1234")).thenReturn("HASH");

        bootstrap.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@empresa.com"); // normalizado
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASH");        // nunca a senha em texto
    }

    // Regressão: a regra antiga era "tabela de usuários vazia". Com usuários comuns já cadastrados
    // (ex: dados de demonstração), o ADMIN nunca era criado e ninguém conseguia administrar o sistema.
    @Test
    void createsTheAdminEvenWhenOrdinaryUsersAlreadyExist() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("HASH");

        bootstrap.run(null);

        verify(userRepository).save(any(User.class));
        verify(userRepository, never()).count();
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenTheConfiguredEmailIsAlreadyTaken() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("Admin@Empresa.com")).thenReturn(true);

        bootstrap.run(null);

        verify(userRepository, never()).save(any());
    }
}
