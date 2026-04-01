package io.github.yagogefaell.asaltamontes.message.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ChatTypeBooleanConverter implements AttributeConverter<ChatType, Boolean> {

    @Override
    public Boolean convertToDatabaseColumn(ChatType attribute) {
        if (attribute == null) {
            return Boolean.FALSE;
        }
        return attribute == ChatType.GROUP;
    }

    @Override
    public ChatType convertToEntityAttribute(Boolean dbData) {
        return Boolean.TRUE.equals(dbData) ? ChatType.GROUP : ChatType.INDIVIDUAL;
    }
}
