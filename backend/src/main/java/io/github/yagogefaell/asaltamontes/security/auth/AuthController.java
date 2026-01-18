package io.github.yagogefaell.asaltamontes.security.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.security.auth.dto.LoginRequest;
import io.github.yagogefaell.asaltamontes.security.auth.dto.RegisterRequest;
import io.github.yagogefaell.asaltamontes.security.jwt.JwtUtil;
import io.github.yagogefaell.asaltamontes.user.account.UserAccount;
import io.github.yagogefaell.asaltamontes.utils.CookieUtil;
import io.jsonwebtoken.ExpiredJwtException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    

    public AuthController(JwtUtil jwtUtil, AuthService authService, AuthenticationManager authenticationManager) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
        this.authenticationManager = authenticationManager;
    }

    // ---------------- LOGIN ----------------
    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        UserAccount userDetails = (UserAccount) authentication.getPrincipal();

        String accessToken = jwtUtil.generateAccessToken(userDetails.getId().toString());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getId().toString());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(accessToken).toString());
        headers.add(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(refreshToken).toString());

        return ResponseEntity.ok()
                .headers(headers)
                .build();
    }

    // ---------------- LOGOUT ----------------
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("accessToken", "/").toString())
            .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("refreshToken", "/").toString())
            .build();
    }

    // ---------------- REFRESH TOKEN ----------------
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(@CookieValue("refreshToken") String refreshToken) {
        String idString = jwtUtil.extractId(refreshToken);

        UserAccount userDetails = authService.loadUserById(idString);

        if (!jwtUtil.isTokenValid(refreshToken, userDetails)) {
            return ResponseEntity.status(401).build();
        }

        String newAccessToken = jwtUtil.generateAccessToken(userDetails.getId().toString());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(newAccessToken).toString())
            .build();
    }

    // ---------------- REGISTRO ----------------
    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {

        UserAccount user = authService.register(request);

        String accessToken = jwtUtil.generateAccessToken(user.getId().toString());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId().toString());

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, CookieUtil.accessToken(accessToken).toString())
            .header(HttpHeaders.SET_COOKIE, CookieUtil.refreshToken(refreshToken).toString())
            .build();
    }

    // ---------------- VERIFICAR TOKEN ----------------
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyToken(
            @CookieValue(name = "accessToken", required = false) String accessToken) {

        try {
            // No hay token → no hay sesión
            if (accessToken == null || accessToken.isBlank()) {
                return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("accessToken", "/").toString())
                    .build();
            }

            // Extraer id del token
            String idString = jwtUtil.extractId(accessToken);

            // Cargar usuario (Spring Security estándar)
            UserAccount userDetails = authService.loadUserById(idString);

            // Validar token
            if (!jwtUtil.isTokenValid(accessToken, userDetails)) {
                return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("accessToken", "/").toString())
                    .build();
            }

            // Todo correcto → sesión válida
            return ResponseEntity.ok().build();

        } catch (ExpiredJwtException e) {
            // Token expirado → borrar cookie
            return ResponseEntity.status(401)
                .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("accessToken", "/").toString())
                .build();

        } catch (Exception e) {
            // Token inválido o manipulado
            return ResponseEntity.status(401)
                .header(HttpHeaders.SET_COOKIE, CookieUtil.clear("accessToken", "/").toString())
                .build();
        }
    }
}
