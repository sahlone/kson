package com.sahlone.kson.data.mapper

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

object ObjectMapperFactory {
    fun createDefaultObjectMapper(): ObjectMapper =
        jacksonObjectMapper()
            .registerModule(KotlinModule())
            .registerModule(JavaTimeModule())
            .deactivateDefaultTyping()
            .apply {
                propertyNamingStrategy = PropertyNamingStrategy.LOWER_CAMEL_CASE
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
                configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
                configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true)
                configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                configure(WRITE_DATES_AS_TIMESTAMPS, false)
            }
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
}
