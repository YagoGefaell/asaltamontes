package io.github.yagogefaell.asaltamontes.event.service;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.yagogefaell.asaltamontes.common.exceptions.BadRequestException;
import io.github.yagogefaell.asaltamontes.event.dto.UpdateEventDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

/**
 * Reads a PATCH body telling an absent field from an explicit null (D-020), without extra dependencies: the DTO is
 * built from the JSON tree and the names of the fields present are kept next to it.
 */
@Component
@RequiredArgsConstructor
public class UpdateEventReader {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public UpdateEventDto read(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BadRequestException("La petición no es válida");
        }
        UpdateEventDto dto;
        try {
            dto = objectMapper.treeToValue(body, UpdateEventDto.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BadRequestException("La petición no es válida");
        }
        Set<String> present = new HashSet<>();
        body.fieldNames().forEachRemaining(present::add);
        dto = dto.withPresent(present);

        Set<ConstraintViolation<UpdateEventDto>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            throw new BadRequestException(violations.stream()
                    .map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining(". ")));
        }
        return dto;
    }
}
