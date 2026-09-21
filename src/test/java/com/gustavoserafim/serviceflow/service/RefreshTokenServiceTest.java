package com.gustavoserafim.serviceflow.service;

import com.gustavoserafim.serviceflow.entity.RefreshToken;
import com.gustavoserafim.serviceflow.entity.User;
import com.gustavoserafim.serviceflow.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, Clock.fixed(NOW, ZoneOffset.UTC), 7);
        user = new User();
        user.setId(1L);
        user.setActive(true);
        user.setTokenVersion(2);
    }

    /** Emite um token pelo próprio service e devolve (valor cru, registro salvo) para os testes reutilizarem. */
    private record Issued(String raw, RefreshToken stored) {
    }

    private Issued issueOne() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        String raw = service.issue(user);
        return new Issued(raw, captor.getValue());
    }

    @Test
    void issue_storesOnlyTheHashAndSetsExpiry() {
        Issued issued = issueOne();

        assertThat(issued.raw()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(issued.stored().getTokenHash()).hasSize(64).isNotEqualTo(issued.raw()); // SHA-256 em hex
        assertThat(issued.stored().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(issued.stored().getUser()).isSameAs(user);
    }

    @Test
    void consume_validToken_revokesItAndReturnsOwner() {
        Issued issued = issueOne();
        when(repository.findByTokenHash(issued.stored().getTokenHash())).thenReturn(Optional.of(issued.stored()));

        User owner = service.consume(issued.raw());

        assertThat(owner).isSameAs(user);
        assertThat(issued.stored().getRevokedAt()).isEqualTo(NOW); // rotação: não pode ser usado de novo
    }

    @Test
    void consume_reusedToken_revokesAllSessionsAndBumpsTokenVersion() {
        Issued issued = issueOne();
        issued.stored().setRevokedAt(NOW.minusSeconds(30)); // já foi usado antes
        when(repository.findByTokenHash(issued.stored().getTokenHash())).thenReturn(Optional.of(issued.stored()));

        assertThatThrownBy(() -> service.consume(issued.raw())).isInstanceOf(BadCredentialsException.class);

        verify(repository).revokeAllActiveByUserId(1L, NOW);
        assertThat(user.getTokenVersion()).isEqualTo(3);
    }

    @Test
    void consume_expiredToken_isRejected() {
        Issued issued = issueOne();
        issued.stored().setExpiresAt(NOW.minusSeconds(1));
        when(repository.findByTokenHash(issued.stored().getTokenHash())).thenReturn(Optional.of(issued.stored()));

        assertThatThrownBy(() -> service.consume(issued.raw())).isInstanceOf(BadCredentialsException.class);
        assertThat(issued.stored().getRevokedAt()).isNull();
    }

    @Test
    void consume_tokenOfInactiveUser_isRejected() {
        Issued issued = issueOne();
        user.setActive(false);
        when(repository.findByTokenHash(issued.stored().getTokenHash())).thenReturn(Optional.of(issued.stored()));

        assertThatThrownBy(() -> service.consume(issued.raw())).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void consume_unknownToken_isRejectedWithoutTouchingAnything() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consume("qualquer-coisa")).isInstanceOf(BadCredentialsException.class);
        verify(repository, never()).revokeAllActiveByUserId(any(), any());
    }

    @Test
    void revoke_isIdempotentForUnknownAndAlreadyRevokedTokens() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        service.revoke("desconhecido"); // não lança

        Issued issued = issueOne();
        issued.stored().setRevokedAt(NOW.minusSeconds(60));
        when(repository.findByTokenHash(issued.stored().getTokenHash())).thenReturn(Optional.of(issued.stored()));
        service.revoke(issued.raw());

        assertThat(issued.stored().getRevokedAt()).isEqualTo(NOW.minusSeconds(60)); // não sobrescreve
    }
}
