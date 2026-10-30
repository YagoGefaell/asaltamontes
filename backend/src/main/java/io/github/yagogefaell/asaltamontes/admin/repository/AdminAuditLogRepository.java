package io.github.yagogefaell.asaltamontes.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.yagogefaell.asaltamontes.admin.domain.AdminAuditLog;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
