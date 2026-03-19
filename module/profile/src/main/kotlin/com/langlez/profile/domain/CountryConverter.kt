package com.langlez.profile.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.util.Locale

@Converter
class CountryConverter : AttributeConverter<Locale, String> {

    override fun convertToDatabaseColumn(attribute: Locale?): String? =
        attribute?.toLanguageTag()

    override fun convertToEntityAttribute(dbData: String?): Locale? =
        if (dbData.isNullOrBlank()) null
        else Locale.forLanguageTag(dbData)

}