package io.github.yagogefaell.asaltamontes.infrastructure.ratelimit;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.yagogefaell.asaltamontes.infrastructure.security.ProblemDetailWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Brute-force protection for the authentication endpoints (T06b): login per IP and per username,
 * register and refresh per IP. Answers 429 ProblemDetail with Retry-After.
 *
 * The client IP is request.getRemoteAddr(): in the prod profile Spring resolves it from X-Forwarded-For
 * (server.forward-headers-strategy=framework), which is only trustworthy once the backend is reachable
 * exclusively through the nginx proxy (T07).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Duration PERIOD = Duration.ofMinutes(1);
    private static final int MAX_LOGIN_BODY_BYTES = 4096;

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ProblemDetailWriter problemDetailWriter;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties,
                               ProblemDetailWriter problemDetailWriter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.problemDetailWriter = problemDetailWriter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !properties.isEnabled() || !"POST".equals(request.getMethod())
                || !pathOf(request).startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        HttpServletRequest forwarded = request;
        long retryAfter;

        switch (pathOf(request)) {
            case "/auth/login" -> {
                CachedBodyRequest cached = new CachedBodyRequest(request);
                forwarded = cached;
                retryAfter = rateLimiter.tryAcquire("login:ip:" + ip, properties.getLoginPerMinute(), PERIOD);
                String username = usernameOf(cached.body);
                if (retryAfter == 0 && username != null) {
                    retryAfter = rateLimiter.tryAcquire("login:user:" + username, properties.getLoginPerMinute(), PERIOD);
                }
            }
            case "/auth/register" -> retryAfter = rateLimiter.tryAcquire("register:ip:" + ip, properties.getRegisterPerMinute(), PERIOD);
            case "/auth/verify-email" -> retryAfter = rateLimiter.tryAcquire("verify-email:ip:" + ip, properties.getVerifyEmailPerMinute(), PERIOD);
            case "/auth/refresh" -> retryAfter = rateLimiter.tryAcquire("refresh:ip:" + ip, properties.getRefreshPerMinute(), PERIOD);
            default -> retryAfter = 0;
        }

        if (retryAfter > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            problemDetailWriter.write(response, request, HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    "Demasiados intentos. Vuelve a probar en " + retryAfter + " segundos");
            return;
        }

        filterChain.doFilter(forwarded, response);
    }

    private static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private String usernameOf(byte[] body) {
        if (body.length == 0 || body.length > MAX_LOGIN_BODY_BYTES) {
            return null;
        }
        try {
            JsonNode username = objectMapper.readTree(body).get("username");
            return username != null && username.isTextual() ? username.asText().toLowerCase(Locale.ROOT) : null;
        } catch (IOException e) {
            return null; // Malformed JSON: the controller answers 400
        }
    }

    /** Reads the body once so the filter can inspect it and the controller can still read it. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readNBytes(MAX_LOGIN_BODY_BYTES + 1);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(@NonNull byte[] buffer, int offset, int length) {
                    return input.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
