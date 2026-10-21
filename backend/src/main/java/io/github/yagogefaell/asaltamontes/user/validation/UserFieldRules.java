package io.github.yagogefaell.asaltamontes.user.validation;

/**
 * Single source of truth for user field rules (T06b). Used by the Bean Validation annotations of the
 * request DTOs and by UserValidator. The frontend mirrors them in src/config/constants.js (VALIDATION).
 */
public final class UserFieldRules {

    private UserFieldRules() {}

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 30;
    /** Letters, digits, dots and underscores; no dot at the start or at the end. */
    public static final String USERNAME_PATTERN = "^(?![.])[a-zA-Z0-9._]+(?<![.])$";
    public static final String USERNAME_SIZE_MESSAGE = "Debe tener entre 3 y 30 caracteres";
    public static final String USERNAME_PATTERN_MESSAGE =
            "Solo letras, números, puntos y guiones bajos, sin punto al principio ni al final";

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 100;
    public static final String PASSWORD_SIZE_MESSAGE = "Debe tener entre 8 y 100 caracteres";

    public static final int FULL_NAME_MIN = 2;
    public static final int FULL_NAME_MAX = 60;
    /** Letters of any language, spaces, hyphens and apostrophes, starting with a letter. */
    public static final String FULL_NAME_PATTERN = "^\\p{L}[\\p{L} '\\-]*$";
    public static final String FULL_NAME_SIZE_MESSAGE = "Debe tener entre 2 y 60 caracteres";
    public static final String FULL_NAME_PATTERN_MESSAGE = "Solo letras, espacios, guiones y apóstrofos";

    public static final int EMAIL_MAX = 255;
    public static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    public static final String EMAIL_PATTERN_MESSAGE = "Formato de email inválido";
}
