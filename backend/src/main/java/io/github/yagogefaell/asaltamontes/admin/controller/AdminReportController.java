package io.github.yagogefaell.asaltamontes.admin.controller;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.admin.dto.AdminReportDto;
import io.github.yagogefaell.asaltamontes.admin.dto.ResolveReportDto;
import io.github.yagogefaell.asaltamontes.admin.service.AdminReportService;
import io.github.yagogefaell.asaltamontes.community.dto.CursorPageDto;
import io.github.yagogefaell.asaltamontes.report.domain.ReportStatus;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Moderation panel (T23c). Staff only: a USER gets 403. The role of an account is never changed through the API. */
@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService service;

    @GetMapping
    public CursorPageDto<AdminReportDto> list(@AuthenticationPrincipal UserAccount actor,
                                              @RequestParam(required = false) ReportStatus status,
                                              @RequestParam(required = false) ReportTargetType type,
                                              @RequestParam(required = false) String before,
                                              @RequestParam(required = false) Integer limit) {
        return service.list(actor, status, type, before, limit);
    }

    @GetMapping("/{id}")
    public AdminReportDto detail(@AuthenticationPrincipal UserAccount actor, @PathVariable UUID id) {
        return service.detail(actor, id);
    }

    @PostMapping("/{id}/dismiss")
    public AdminReportDto dismiss(@AuthenticationPrincipal UserAccount actor, @PathVariable UUID id,
                                  @Valid @RequestBody(required = false) ResolveReportDto body) {
        return service.dismiss(actor, id, body);
    }

    @PostMapping("/{id}/remove-content")
    public AdminReportDto removeContent(@AuthenticationPrincipal UserAccount actor, @PathVariable UUID id,
                                        @Valid @RequestBody(required = false) ResolveReportDto body) {
        return service.removeContent(actor, id, body);
    }
}
