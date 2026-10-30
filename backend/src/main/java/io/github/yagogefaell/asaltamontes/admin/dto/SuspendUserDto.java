package io.github.yagogefaell.asaltamontes.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Either {@code days} (1 to 3650) or {@code permanent = true}, never both and never neither. */
public record SuspendUserDto(@Min(1) @Max(3650) Integer days, boolean permanent, @Size(max = 1000) String resolution) {
}
