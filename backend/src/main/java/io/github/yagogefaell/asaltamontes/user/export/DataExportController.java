package io.github.yagogefaell.asaltamontes.user.export;

import java.io.IOException;
import java.time.LocalDate;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.common.exceptions.TooManyRequestsException;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/** T24d: download of the caller's own data. Always the authenticated user's; there is no way to ask for someone else's. */
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class DataExportController {

    private final DataExportService exportService;
    private final UserActionLimiter limiter;
    /** Each export holds a request thread while the client reads, so only a few run at once on the whole server. */
    private final java.util.concurrent.Semaphore running = new java.util.concurrent.Semaphore(2);

    /**
     * Written straight to the servlet response on the request thread. Not a StreamingResponseBody on purpose: that
     * runs on an async dispatch, which the stateless JWT filter does not authenticate (the export came out 403 with
     * the response already committed), and D-012 says not to change the auth filters for this.
     */
    @GetMapping("/export")
    public void export(@AuthenticationPrincipal UserAccount currentUser, HttpServletResponse response) throws IOException {
        Long userId = currentUser.getId();
        limiter.dataExport(userId);
        String filename = "asaltamontes-datos-" + currentUser.getUsername() + "-" + LocalDate.now() + ".json";
        if (!running.tryAcquire()) {
            throw new TooManyRequestsException("Hay otras descargas en curso. Vuelve a probar en un minuto", 60);
        }
        try {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
            exportService.write(userId, response.getOutputStream());
        } finally {
            running.release();
        }
    }
}
