package io.github.yagogefaell.asaltamontes.admin.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.yagogefaell.asaltamontes.admin.domain.AdminAction;
import io.github.yagogefaell.asaltamontes.admin.domain.AdminAuditLog;
import io.github.yagogefaell.asaltamontes.admin.dto.AdminReportDto;
import io.github.yagogefaell.asaltamontes.admin.dto.ResolveReportDto;
import io.github.yagogefaell.asaltamontes.admin.dto.SuspendUserDto;
import io.github.yagogefaell.asaltamontes.auth.service.AuthService;
import io.github.yagogefaell.asaltamontes.user.repository.UserAccountRepository;
import io.github.yagogefaell.asaltamontes.admin.repository.AdminAuditLogRepository;
import io.github.yagogefaell.asaltamontes.admin.repository.AdminReportQueries;
import io.github.yagogefaell.asaltamontes.admin.repository.AdminReportQueries.Row;
import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ConflictException;
import io.github.yagogefaell.asaltamontes.common.exceptions.ForbiddenException;
import io.github.yagogefaell.asaltamontes.common.exceptions.NotFoundException;
import io.github.yagogefaell.asaltamontes.common.pagination.OpaqueCursor;
import io.github.yagogefaell.asaltamontes.community.dto.CursorPageDto;
import io.github.yagogefaell.asaltamontes.community.service.CommunityService;
import io.github.yagogefaell.asaltamontes.event.service.EventService;
import io.github.yagogefaell.asaltamontes.message.service.MessageService;
import io.github.yagogefaell.asaltamontes.post.service.PostService;
import io.github.yagogefaell.asaltamontes.report.domain.Report;
import io.github.yagogefaell.asaltamontes.report.domain.ReportStatus;
import io.github.yagogefaell.asaltamontes.report.domain.ReportTargetType;
import io.github.yagogefaell.asaltamontes.report.repository.ReportRepository;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccountRole;
import lombok.RequiredArgsConstructor;

/**
 * The moderation panel (T23c): queue, detail and resolution of reports. Every method re-checks the role of the caller
 * (the controller already requires it) and every resolution writes the audit log in the same transaction. Nothing here
 * ever reaches the reporter or the reported account.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String NOT_FOUND = "Denuncia no encontrada";

    private final AdminReportQueries queries;
    private final ReportRepository reportRepository;
    private final AdminAuditLogRepository auditLog;
    private final PostService postService;
    private final MessageService messageService;
    private final EventService eventService;
    private final CommunityService communityService;
    private final JdbcTemplate jdbc;
    private final UserAccountRepository userRepository;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public CursorPageDto<AdminReportDto> list(UserAccount actor, ReportStatus status, ReportTargetType type, String before, Integer limit) {
        requireStaff(actor);
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        // Open reports: oldest first; history (resolved) and the mixed view: newest first
        boolean oldestFirst = status == ReportStatus.OPEN;
        OpaqueCursor cursor = OpaqueCursor.decode(before, OpaqueCursor.REPORT);
        Instant at = cursor == null ? null : cursor.order().toInstant(ZoneOffset.UTC);
        List<Row> rows = queries.page(status, type, oldestFirst, at, cursor == null ? null : cursor.publicId(), size + 1);
        boolean more = rows.size() > size;
        List<Row> page = more ? rows.subList(0, size) : rows;
        String next = null;
        if (more) {
            Report last = page.get(page.size() - 1).report();
            next = OpaqueCursor.encode(OpaqueCursor.REPORT, LocalDateTime.ofInstant(last.getCreatedAt(), ZoneOffset.UTC), last.getPublicId());
        }
        return new CursorPageDto<>(page.stream().map(r -> toDto(r, false)).toList(), next);
    }

    @Transactional(readOnly = true)
    public AdminReportDto detail(UserAccount actor, UUID reportId) {
        requireStaff(actor);
        return toDto(queries.byPublicId(reportId).orElseThrow(() -> new NotFoundException(NOT_FOUND)), true);
    }

    /** Closes this report without acting. Other reports about the same target stay open: they may say something different. */
    @Transactional
    public AdminReportDto dismiss(UserAccount actor, UUID reportId, ResolveReportDto dto) {
        requireStaff(actor);
        Report report = load(reportId);
        String note = note(dto == null ? null : dto.resolution());
        requireNotInvolved(actor, report);
        claim(report, ReportStatus.DISMISSED, actor, note);
        audit(actor, AdminAction.REPORT_DISMISSED, report, note);
        return detailAfter(reportId);
    }

    /**
     * Removes the reported content (post, comment, message; cancels an event; deletes a community, ADMIN only) and
     * closes every open report about it. Accounts are not touched here: a USER report is handled with the suspension.
     */
    @Transactional
    public AdminReportDto removeContent(UserAccount actor, UUID reportId, ResolveReportDto dto) {
        requireStaff(actor);
        Report report = load(reportId);
        String note = note(dto == null ? null : dto.resolution());
        if (report.getTargetType() == ReportTargetType.USER) {
            throw new BadRequestException("Una denuncia a una cuenta no se resuelve borrando contenido");
        }
        if (report.getTargetType() == ReportTargetType.COMMUNITY && actor.getRole() != UserAccountRole.ADMIN) {
            throw new ForbiddenException("Solo una administradora puede borrar una comunidad");
        }
        requireNotInvolved(actor, report);
        claim(report, ReportStatus.ACTIONED, actor, note);
        reportRepository.resolveOpenOnTarget(report.getTargetType(), report.getTargetId(), ReportStatus.ACTIONED, actor.getId(), Instant.now(), note);
        Long id = report.getTargetId();
        boolean removed = switch (report.getTargetType()) {
            case POST -> postService.removeForModeration(id);
            case COMMENT -> postService.removeCommentForModeration(id);
            case MESSAGE -> messageService.removeForModeration(id);
            case EVENT -> eventService.cancelForModeration(id);
            case COMMUNITY -> communityService.removeForModeration(id);
            default -> throw new BadRequestException("Esto no se resuelve borrando contenido");
        };
        if (!removed) {
            // Rolls back the claim: the audit log never says something was removed when it was not
            throw new ConflictException("El contenido ya no existe. Descarta la denuncia");
        }
        audit(actor, AdminAction.CONTENT_REMOVED, report, note);
        return detailAfter(reportId);
    }

    /**
     * Suspends the account the report is about (the reported account, or the author of the reported content), closes
     * this report as ACTIONED (and, for a USER report, every open report about that account) and revokes her sessions.
     * Nobody suspends an administrator; only an administrator suspends a moderator.
     */
    @Transactional
    public AdminReportDto suspendUser(UserAccount actor, UUID reportId, SuspendUserDto dto) {
        requireStaff(actor);
        if (dto == null || dto.permanent() == (dto.days() != null)) {
            throw new BadRequestException("Indica los días de suspensión o que sea permanente");
        }
        Report report = load(reportId);
        if (report.getReportedUserId() == null) {
            throw new BadRequestException("Esta denuncia no tiene una cuenta a la que suspender");
        }
        requireNotInvolved(actor, report);
        UserAccount target = userRepository.findById(report.getReportedUserId())
                .orElseThrow(() -> new BadRequestException("Esta denuncia no tiene una cuenta a la que suspender"));
        if (target.getRole() == UserAccountRole.ADMIN
                || (target.getRole() == UserAccountRole.MODERATOR && actor.getRole() != UserAccountRole.ADMIN)) {
            throw new ForbiddenException("No puedes suspender a esta cuenta");
        }
        if (target.isSuspended()) {
            // Suspending again would overwrite (and could shorten) the one in force
            throw new ConflictException("Esta cuenta ya está suspendida");
        }
        String note = note(dto.resolution());
        claim(report, ReportStatus.ACTIONED, actor, note);
        if (report.getTargetType() == ReportTargetType.USER) {
            reportRepository.resolveOpenOnTarget(ReportTargetType.USER, report.getTargetId(), ReportStatus.ACTIONED, actor.getId(), Instant.now(), note);
        }
        if (dto.permanent()) {
            target.setSuspendedPermanently(true);
            target.setSuspendedUntil(null);
        } else {
            target.setSuspendedPermanently(false);
            target.setSuspendedUntil(LocalDateTime.now().plusDays(dto.days()));
        }
        userRepository.save(target);
        authService.revokeAllSessions(target.getId());
        audit(actor, AdminAction.USER_SUSPENDED, report, (dto.permanent() ? "permanent" : dto.days() + " days") + (note == null ? "" : ": " + note));
        return detailAfter(reportId);
    }

    /** Lifts the suspension of an account. Audited; 404 if she is not suspended. */
    @Transactional
    public void unsuspend(UserAccount actor, String username) {
        requireStaff(actor);
        UserAccount target = userRepository.findByUsernameIgnoreCase(username)
                .filter(UserAccount::isSuspended).orElseThrow(() -> new NotFoundException("Cuenta no suspendida"));
        if (target.getRole() == UserAccountRole.MODERATOR && actor.getRole() != UserAccountRole.ADMIN) {
            throw new ForbiddenException("No puedes actuar sobre esta cuenta");
        }
        target.setSuspendedPermanently(false);
        target.setSuspendedUntil(null);
        userRepository.save(target);
        // Whatever session slipped in while she was being suspended does not come back to life
        authService.revokeAllSessions(target.getId());
        AdminAuditLog entry = new AdminAuditLog();
        entry.setActorId(actor.getId());
        entry.setAction(AdminAction.USER_UNSUSPENDED);
        entry.setTargetUserId(target.getId());
        auditLog.save(entry);
    }

    // ---------------------------------------------------------------- internals

    private Report load(UUID reportId) {
        return queries.byPublicId(reportId).orElseThrow(() -> new NotFoundException(NOT_FOUND)).report();
    }

    /** Only the request that flips the report from OPEN handles it: two moderators at once get 200 and 409. */
    private void claim(Report report, ReportStatus status, UserAccount actor, String note) {
        if (reportRepository.resolve(report.getId(), status, actor.getId(), Instant.now(), note) == 0) {
            throw new ConflictException("Esta denuncia ya está resuelta");
        }
    }

    /** Nobody resolves a report about herself or her own content: another member of the staff has to. */
    private static void requireNotInvolved(UserAccount actor, Report report) {
        if (actor.getId().equals(report.getReportedUserId())) {
            throw new ForbiddenException("No puedes resolver una denuncia sobre ti");
        }
    }

    private void audit(UserAccount actor, AdminAction action, Report report, String note) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setActorId(actor.getId());
        entry.setAction(action);
        entry.setReportId(report.getId());
        entry.setTargetType(report.getTargetType());
        entry.setTargetPublicId(report.getTargetPublicId());
        entry.setTargetUserId(report.getReportedUserId());
        entry.setNote(note);
        auditLog.save(entry);
    }

    private AdminReportDto detailAfter(UUID reportId) {
        return toDto(queries.byPublicId(reportId).orElseThrow(() -> new NotFoundException(NOT_FOUND)), true);
    }

    private static void requireStaff(UserAccount actor) {
        if (actor == null || (actor.getRole() != UserAccountRole.ADMIN && actor.getRole() != UserAccountRole.MODERATOR)) {
            throw new ForbiddenException("No tienes permiso para esto");
        }
    }

    private static String note(String resolution) {
        String text = resolution == null ? null : resolution.strip();
        return text == null || text.isEmpty() ? null : text;
    }

    private AdminReportDto toDto(Row row, boolean withAvailability) {
        Report r = row.report();
        String target = r.getTargetType() == ReportTargetType.USER ? row.reportedUsername()
                : r.getTargetPublicId() == null ? null : r.getTargetPublicId().toString();
        return new AdminReportDto(r.getPublicId(), r.getTargetType(), target, r.getReason(), r.getDetails(), r.getContentSnapshot(),
                r.getStatus(), r.getCreatedAt(), row.reporterUsername(), row.reportedUsername(), row.openOnTarget(),
                withAvailability ? available(r) : null, row.handledByUsername(), r.getHandledAt(), r.getResolution());
    }

    /** Whether the reported thing is still there (a deleted post, a cancelled event or a gone message are not). */
    private Boolean available(Report r) {
        String sql = switch (r.getTargetType()) {
            case USER -> "SELECT EXISTS (SELECT 1 FROM users WHERE id = ?)";
            case POST -> "SELECT EXISTS (SELECT 1 FROM posts WHERE id = ? AND deleted_at IS NULL)";
            case COMMENT -> "SELECT EXISTS (SELECT 1 FROM post_comments WHERE id = ? AND deleted_at IS NULL)";
            case MESSAGE -> "SELECT EXISTS (SELECT 1 FROM messages WHERE id = ?)";
            case EVENT -> "SELECT EXISTS (SELECT 1 FROM events WHERE id = ? AND status <> 'CANCELLED')";
            case COMMUNITY -> "SELECT EXISTS (SELECT 1 FROM communities WHERE id = ?)";
        };
        return jdbc.queryForObject(sql, Boolean.class, r.getTargetId());
    }
}
