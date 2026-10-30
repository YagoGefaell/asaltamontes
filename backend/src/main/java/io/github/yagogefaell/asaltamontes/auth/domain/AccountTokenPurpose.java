package io.github.yagogefaell.asaltamontes.auth.domain;

/** What a one-time account token is for. RESET_PASSWORD and CHANGE_EMAIL are used from T24b. */
public enum AccountTokenPurpose {
    VERIFY_EMAIL,
    RESET_PASSWORD,
    CHANGE_EMAIL
}
