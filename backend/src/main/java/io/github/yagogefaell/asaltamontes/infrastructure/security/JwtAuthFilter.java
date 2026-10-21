package io.github.yagogefaell.asaltamontes.infrastructure.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Request attribute with the reason a bearer token was rejected; read by {@link SecurityProblemHandler}. */
    public static final String AUTH_ERROR_ATTRIBUTE = "asaltamontes.authError";

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        
        if (path.startsWith("/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = header.substring(7);

        if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                if (!jwtUtil.isTokenValid(jwt)) {
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Token inválido o expirado");
                    filterChain.doFilter(request, response);
                    return;
                }

                Claims claims = jwtUtil.extractAllClaims(jwt);

                if (jwtUtil.isExpired(claims)) {
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Token expirado");
                    filterChain.doFilter(request, response);
                    return;
                }

                // A refresh token must never authenticate an API request
                if (!jwtUtil.isType(claims, JwtUtil.ACCESS_TYPE)) {
                    request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Token inválido");
                    filterChain.doFilter(request, response);
                    return;
                }

                Long id = jwtUtil.extractId(claims);

                UserAccount userDetails = userService.loadUserById(id);

                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authToken);

            } catch (Exception e) {
                // Continue as anonymous: protected routes answer 401 through SecurityProblemHandler
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "Token inválido");
            }
        }

        filterChain.doFilter(request, response);
    }

}