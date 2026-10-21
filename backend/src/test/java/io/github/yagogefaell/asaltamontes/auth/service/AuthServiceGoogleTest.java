package io.github.yagogefaell.asaltamontes.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;
import io.github.yagogefaell.asaltamontes.infrastructure.google.GoogleAuthService;
import io.github.yagogefaell.asaltamontes.infrastructure.google.GoogleTokenVerifier;
import io.github.yagogefaell.asaltamontes.infrastructure.security.JwtUtil;
import io.github.yagogefaell.asaltamontes.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;
    @Mock
    private GoogleAuthService googleAuthService;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    void googleLogin_withUnverifiedEmail_isRejected() {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail("ana@gmail.com");
        payload.setEmailVerified(false);
        when(googleAuthService.exchangeCodeForIdToken("code")).thenReturn("id-token");
        when(googleTokenVerifier.verify("id-token")).thenReturn(payload);

        assertThatThrownBy(() -> authService.googleLogin("code"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessage("El email de Google no está verificado");
        verify(userService, never()).findOrCreateGoogleUser(any(), any(), any(), any());
    }
}
