-- T06a: one row per refresh token issued (its jti), so sessions can be revoked (logout, "log out everywhere").
CREATE TABLE refresh_sessions (
    jti        varchar(36)  NOT NULL,
    user_id    bigint       NOT NULL,
    created_at timestamp(6) NOT NULL,
    expires_at timestamp(6) NOT NULL,
    revoked_at timestamp(6),
    CONSTRAINT pk_refresh_sessions PRIMARY KEY (jti),
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_sessions_user ON refresh_sessions (user_id);
CREATE INDEX idx_refresh_sessions_expires_at ON refresh_sessions (expires_at);
