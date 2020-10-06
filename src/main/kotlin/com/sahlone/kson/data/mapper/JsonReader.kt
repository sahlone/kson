package com.sahlone.kson.data.mapper

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.Try
import arrow.core.getOrElse
import arrow.data.NonEmptyList
import arrow.data.nel
import com.sahlone.kson.model.Id
import com.sahlone.kson.model.Uuid
import java.math.BigDecimal
import java.time.Month
import java.time.Year
import java.util.Base64
import java.util.UUID
import kotlin.text.Charsets.UTF_8

class PathReads<A>(val reads: JsonReads<A>, val path: JsonPath) : JsonReads<A> {
    override fun invoke(json: JsonValue): JsonResult<A> =
        when (json) {
            is JsonNull -> JsonError(NonEmptyList(JsValidationError(JsonValueMissing, path)))
            is JsonObject -> when (val result = reads(json.value[path.value] ?: JsonNull)) {
                is JsonSuccess -> result
                is JsonError -> JsonError(result.errors.map {
                    JsValidationError(it.errorDetail, path.append(it.path))
                })
            }
            else -> JsonError(NonEmptyList(JsValidationError(JsonValueInvalid, path)))
        }
}

infix fun <A> JsonReads<A>.at(path: String) = PathReads(this, JsonPath(path))

inline fun <T, reified U : JsonValue> readsGen(crossinline block: (U) -> T): JsonReads<T> = {
    when (it) {
        is JsonNull -> JsonError(NonEmptyList(JsValidationError(JsonValueMissing)))
        is U -> JsonSuccess(block(it))
        else -> JsonError(NonEmptyList(JsValidationError(JsonValueInvalid)))
    }
}

inline fun <T, reified U : JsonValue> readsGenFlatmap(crossinline block: (U) -> JsonResult<T>): JsonReads<T> = {
    when (it) {
        is JsonNull -> JsonError(NonEmptyList(JsValidationError(JsonValueMissing)))
        is U -> block(it)
        else -> JsonError(NonEmptyList(JsValidationError(JsonValueInvalid)))
    }
}

// TODO should fail if an int value has decimals
val IntJsonReads: JsonReads<Int> = readsGen<Int, JsonNumber> { it.value.intValueExact() }

val DoubleJsonReads: JsonReads<Double> = readsGen<Double, JsonNumber> { it.value.toDouble() }

val LongJsonReads: JsonReads<Long> = readsGen<Long, JsonNumber> { it.value.longValueExact() }

val StringJsonReads: JsonReads<String> = readsGen<String, JsonString> { it.value }

val MonthJsonReads: JsonReads<Month> = readsGen<Month, JsonNumber> { Month.of(it.value.intValueExact()) }

val YearJsonReads: JsonReads<Year> = readsGen<Year, JsonNumber> { Year.of(it.value.intValueExact()) }

val BigDecimalJsonReads: JsonReads<BigDecimal> = readsGen<BigDecimal, JsonNumber> { it.value }

inline fun <reified T> IdJsonReads(): JsonReads<Id<T>> = { jsonValue ->
    StringJsonReads(jsonValue).map { Id<T>(it) }
}

inline fun <reified T> UuidTypeJsonReads(): JsonReads<Uuid<T>> = { jsonValue ->
    UUIDJsonReads(jsonValue).map { Uuid<T>(it) }
}

val UUIDJsonReads: JsonReads<UUID> = {
    when (it) {
        is JsonString -> {
            Try {
                JsonSuccess(UUID.fromString(it.value))
            }.getOrElse {
                JsonError(JsValidationError(JsonValueInvalid).nel())
            }
        }
        else -> JsonError(JsValidationError(JsonValueInvalid).nel())
    }
}

val BooleanJsonReads: JsonReads<Boolean> = readsGen<Boolean, JsonBoolean> { it.value }

fun <T> OptionalJsonReads(reads: JsonReads<T>): JsonReads<Option<T>> = {
    when (it) {
        is JsonNull -> JsonSuccess(None)
        else -> {
            when (val result = reads(it)) {
                is JsonSuccess -> JsonSuccess(Some(result.value))
                is JsonError -> result
            }
        }
    }
}

fun <T> validatedJsonReads(
    reads: JsonReads<T>,
    validation: (T) -> Boolean,
    validationMsg: String
): JsonReads<T> = {
    when (val result = reads(it)) {
        is JsonSuccess -> {
            val value = result.value
            when (validation(value)) {
                true -> JsonSuccess(value)
                false -> JsonError(JsValidationError(JsonValidationFailed(validationMsg)).nel())
            }
        }
        is JsonError -> result
    }
}

val JsonObjectReads: JsonReads<JsonObject> = {
    when (it) {
        is JsonObject -> JsonSuccess(it)
        else -> JsonError(JsValidationError(JsonValueInvalid).nel())
    }
}

fun <T> optionalJsonWrites(writes: JsonWrites<T>): JsonWrites<Option<T>> = {
    when (it) {
        is Some -> writes(it.t)
        is None -> JsonNull
    }
}

fun <T> optionalJsonObjectWrites(writes: JsonWrites<T>): JsonWrites<Option<T>> = {
    when (it) {
        is Some -> writes(it.t)
        is None -> JsonObject(mapOf())
    }
}

object JsonArrayReads {

    operator fun <T> invoke(reads: JsonReads<T>): JsonReads<Sequence<T>> = { jsonValue ->
        when (jsonValue) {
            is JsonArray -> jsonValue.value.map {
                val res = reads(it)
                when (res) {
                    is JsonSuccess -> res
                    is JsonError -> {
                        val indexedRes =
                            res.errors.foldLeft(
                                0 to listOf<JsValidationError>()
                            ) { acc, elem ->
                                acc.first + 1 to
                                    acc.second.plus(
                                        elem.copy(path = JsonPath("[${acc.first}].${elem.path.value}"))
                                    )
                            }.second
                        JsonError(NonEmptyList.fromListUnsafe(indexedRes))
                    }
                }
            }.sequence()
            JsonNull -> JsonError(NonEmptyList(JsValidationError(JsonValueMissing)))
            else -> JsonError(NonEmptyList(JsValidationError(JsonValueInvalid)))
        }
    }
}

object JsonBase64EncodedRead {

    operator fun <T> invoke(reads: JsonReads<T>): JsonReads<T> = { jsonValue ->
        StringJsonReads(jsonValue).map {
            String(Base64.getDecoder().decode(it.toByteArray(UTF_8)))
        }.flatMap {
            Json.parse(it, reads)
        }
    }
}

fun <T> Sequence<JsonResult<T>>.sequence(): JsonResult<Sequence<T>> {
    val errorsAndValues = this.fold(listOf<JsValidationError>() to listOf<T>()) { acc, elem: JsonResult<T> ->
        when (elem) {
            is JsonSuccess -> acc.first to acc.second.plus(elem.value)
            is JsonError -> acc.first.plus(elem.errors.all) to acc.second
        }
    }
    return if (errorsAndValues.first.isNotEmpty()) {
        JsonError(NonEmptyList.fromListUnsafe(errorsAndValues.first))
    } else {
        JsonSuccess(errorsAndValues.second.asSequence())
    }
}
