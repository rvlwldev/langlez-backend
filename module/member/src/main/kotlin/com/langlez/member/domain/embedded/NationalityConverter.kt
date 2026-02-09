package com.langlez.member.domain.embedded

import com.langlez.member.domain.embedded.MemberPersonality.Nationality
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Nationality를 JPA에서 사용하기 위한 AttributeConverter */
@Converter(autoApply = true)
class NationalityConverter : AttributeConverter<Nationality?, String?> {

    override fun convertToDatabaseColumn(attribute: Nationality?): String? = attribute?.code

    override fun convertToEntityAttribute(dbData: String?): Nationality? =
            dbData?.let { Nationality.of(it) }
}
