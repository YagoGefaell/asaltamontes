package io.github.yagogefaell.asaltamontes.infrastructure.security;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import io.github.yagogefaell.asaltamontes.common.exceptions.InvalidTokenException;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

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

        Cookie cookie = WebUtils.getCookie(request, "accessToken");
        String jwt = (cookie != null) ? cookie.getValue() : null;

        if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                if (!jwtUtil.isTokenValid(jwt)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Claims claims = jwtUtil.extractAllClaims(jwt);

                if (jwtUtil.isExpired(claims)) {
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
                logger.warn(e.getMessage());
                throw new InvalidTokenException(e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

}