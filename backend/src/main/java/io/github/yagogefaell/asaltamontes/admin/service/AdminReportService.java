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
        String note = note(dto);
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
        String note = note(dto);
        if (report.getTargetType() == ReportTargetType.USER) {
            throw new BadRequestException("Una denuncia a una cuenta no se resuelve borrando contenido");
        }
        if (report.getTargetType() == ReportTargetType.COMMUNITY && actor.getRole() != UserAccountRole.ADMIN) {
            throw new ForbiddenException("Solo una administradora puede borrar una comunidad");
        }
        claim(report, ReportStatus.ACTIONED, actor, note);
        reportRepository.resolveOpenOnTarget(report.getTargetType(), report.getTargetId(), ReportStatus.ACTIONED, actor.getId(), Instant.now(), note);
        Long id = report.getTargetId();
        switch (report.getTargetType()) {
            case POST -> postService.removeForModeration(id);
            case COMMENT -> postService.removeCommentForModeration(id);
            case MESSAGE -> messageService.removeForModeration(id);
            case EVENT -> eventService.cancelForModeration(id, actor.getId());
            case COMMUNITY -> communityService.removeForModeration(id);
            default -> throw new BadRequestException("Esta denuncia no se resuelve borrando contenido");
        }
        audit(actor, AdminAction.CONTENT_REMOVED, report, note);
        return detailAfter(reportId);
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

    private static String note(ResolveReportDto dto) {
        String text = dto == null || dto.resolution() == null ? null : dto.resolution().strip();
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
