package com.sahlone.kson.data.mapper

import com.sahlone.kson.model.Id
import com.sahlone.kson.model.Uuid
import java.math.BigDecimal
import java.time.Month
import java.time.Year
import java.util.UUID

infix fun JsonValue.at(path: String): JsonObject =
    JsonObject(mapOf(path to this))

infix fun JsonObject.and(jsonObject: JsonObject): JsonObject =
    JsonObject(this.value.plus(jsonObject.value))

val StringJsonWrites: JsonWrites<String> = {
    JsonString(it)
}

val BooleanJsonWrites: JsonWrites<Boolean> = {
    when (it) {
        true -> JsonTrue
        false -> JsonFalse
    }
}
val IntJsonWrites: JsonWrites<Int> = {
    JsonNumber(BigDecimal(it))
}
val LongJsonWrites: JsonWrites<Long> = {
    JsonNumber(BigDecimal(it))
}

val BigDecimalJsonWrites: JsonWrites<BigDecimal> = {
    JsonNumber(it)
}

val DoubleJsonWrites: JsonWrites<Double> = {
    JsonNumber(BigDecimal(it))
}

val UuidTypeJsonWrites: JsonWrites<Uuid<*>> = {
    JsonString(it.value.toString())
}

val IdJsonWrites: JsonWrites<Id<*>> = {
    JsonString(it.value)
}

val UUIDJsonWrites: JsonWrites<UUID> = {
    JsonString(it.toString())
}

val MonthJsonWrites: JsonWrites<Month> = {
    JsonNumber(BigDecimal(it.value))
}

val YearJsonWrites: JsonWrites<Year> = {
    JsonNumber(BigDecimal(it.value))
}

val JsonObjectWrites: JsonWrites<JsonObject> = { it }

val JsonNullWrites: JsonWrites<JsonNull> = { it }

object JsonArrayWrites {

    operator fun <T> invoke(writes: JsonWrites<T>): JsonWrites<Iterable<T>> = {
        JsonArray(
            it.map { tee ->
                writes(tee)
            }.asSequence()
        )
    }
}
