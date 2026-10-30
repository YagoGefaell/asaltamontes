package io.github.yagogefaell.asaltamontes.report.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import io.github.yagogefaell.asaltamontes.auth.service.VerifiedGuard;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.infrastructure.ratelimit.UserActionLimiter;
import io.github.yagogefaell.asaltamontes.report.domain.Report;
import io.github.yagogefaell.asaltamontes.report.domain.ReportReason;
import io.github.yagogefaell.asaltamontes.report.dto.CreateReportDto;
import io.github.yagogefaell.asaltamontes.report.dto.ReportDto;
import io.github.yagogefaell.asaltamontes.report.repository.ReportRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/**
 * Reporting (T23b). The reported account is never told. Deliberately not {@code @Transactional}: the insert runs in
 * its own transaction so that the unique index of open reports (a double click, two tabs) can be answered with a
 * clean 409 instead of poisoning an outer transaction.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** After a dismissal, the same reporter cannot report the same target again for this long (T23c). */
    static final Duration DISMISSED_COOLDOWN = Duration.ofDays(7);

    private final ReportRepository reportRepository;
    private final ReportTargetResolver resolver;
    private final UserActionLimiter limiter;
    private final VerifiedGuard verifiedGuard;

    public ReportDto create(UserAccount reporter, CreateReportDto dto) {
        verifiedGuard.require(reporter);
        // Every attempt counts, also the ones that end in an error
        limiter.reportCreate(reporter.getId());
        String details = dto.details() == null ? null : dto.details().strip();
        if (details != null && details.isEmpty()) {
            details = null;
        }
        if (dto.reason() == ReportReason.OTHER && details == null) {
            throw new BadRequestException("Cuéntanos qué ocurre para poder revisarlo");
        }

        ResolvedTarget target = resolver.resolve(reporter, dto.targetType(), dto.target());
        if (reportRepository.existsOpen(reporter.getId(), dto.targetType(), target.targetId())) {
            throw alreadyReported();
        }

        // Once the moderation has dismissed her report she cannot insist on the same target for a while
        if (reportRepository.existsDismissedSince(reporter.getId(), dto.targetType(), target.targetId(), Instant.now().minus(DISMISSED_COOLDOWN))) {
            throw new ConflictException("Ya revisamos tu denuncia sobre esto y no encontramos motivo para actuar");
        }

        Report report = new Report();
        report.setReporterId(reporter.getId());
        report.setTargetType(dto.targetType());
        report.setTargetId(target.targetId());
        report.setTargetPublicId(target.targetPublicId());
        report.setReportedUserId(target.reportedUserId());
        report.setReason(dto.reason());
        report.setDetails(details);
        report.setContentSnapshot(target.snapshot());
        try {
            report = reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            // Lost the race against her own other request (the partial unique index let only one through); anything else is not a duplicate
            if (!reportRepository.existsOpen(reporter.getId(), dto.targetType(), target.targetId())) {
                throw e;
            }
            throw alreadyReported();
        }
        return new ReportDto(report.getPublicId(), report.getTargetType(), report.getReason(), report.getStatus(), report.getCreatedAt());
    }

    private static ConflictException alreadyReported() {
        return new ConflictException("Ya has denunciado esto. Lo estamos revisando");
    }
}
