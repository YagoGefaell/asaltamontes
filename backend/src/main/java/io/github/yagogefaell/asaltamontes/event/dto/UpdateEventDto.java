package io.github.yagogefaell.asaltamontes.event.dto;

import java.math.BigDecimal;
import java.time.Instant;

import io.github.yagogefaell.asaltamontes.event.domain.EventType;
import io.github.yagogefaell.asaltamontes.user.domain.RunningLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Changes to an event (PATCH, T15a): null fields stay as they are. Visibility and community cannot change. */
public record UpdateEventDto(
    @Size(min = 1, max = 120, message = "El título debe tener entre 1 y 120 caracteres")
    String title,

    @Size(max = 4000, message = "La descripción no puede superar los 4000 caracteres")
    String description,

    EventType type,

    Instant startsAt,

    Instant endsAt,

    @Size(max = 40, message = "Zona horaria no válida")
    String timezone,

    @Size(max = 200, message = "El punto de encuentro no puede superar los 200 caracteres")
    String meetingPointName,

    @DecimalMin(value = "-90", message = "Latitud no válida") @DecimalMax(value = "90", message = "Latitud no válida")
    Double latitude,

    @DecimalMin(value = "-180", message = "Longitud no válida") @DecimalMax(value = "180", message = "Longitud no válida")
    Double longitude,

    @Size(max = 80, message = "La ciudad no puede superar los 80 caracteres")
    String city,

    @DecimalMin(value = "0.1", message = "La distancia debe ser positiva")
    @DecimalMax(value = "9999.9", message = "Distancia no válida")
    BigDecimal distanceKm,

    @Min(value = 0, message = "El desnivel no puede ser negativo") @Max(value = 20000, message = "Desnivel no válido")
    Integer elevationGainM,

    RunningLevel level,

    @Min(value = 1, message = "Debe haber al menos una plaza") @Max(value = 500, message = "Como máximo 500 plazas")
    Integer capacity
) {}
