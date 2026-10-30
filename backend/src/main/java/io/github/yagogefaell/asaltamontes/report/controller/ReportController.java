package io.github.yagogefaell.asaltamontes.report.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.yagogefaell.asaltamontes.report.dto.CreateReportDto;
import io.github.yagogefaell.asaltamontes.report.dto.ReportDto;
import io.github.yagogefaell.asaltamontes.report.service.ReportService;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Reports (T23b). Needs authentication like everything outside /auth; the queue and its actions are T23c. */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportDto> create(@AuthenticationPrincipal UserAccount currentUser, @Valid @RequestBody CreateReportDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.create(currentUser, dto));
    }
}
