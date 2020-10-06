package com.sahlone.kson.data.mapper

import arrow.core.Left
import arrow.core.Right
import arrow.core.Try
import arrow.core.Tuple2
import arrow.core.getOrElse
import arrow.data.NonEmptyList
import arrow.data.nel
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import java.math.BigDecimal

typealias JsonWrites<T> = (T) -> JsonValue
typealias JsonReads<T> = (JsonValue) -> JsonResult<T>

fun <A, B> JsonReads<A>.map(block: (A) -> B): JsonReads<B> = {
    when (val result = this@map(it)) {
        is JsonSuccess -> JsonSuccess(block(result.value))
        is JsonError -> result
    }
}

sealed class ErrorDetail {
    fun show(): String = when (this) {
        is JsonValueInvalid -> "Invalid attribute"
        is JsonValidationFailed -> "Invalid value attribute"
        is JsonValueMissing -> "Missing attribute"
        is NotAJsonValue -> "Invalid json content"
        is JsonAutoConversionFailed -> {
            "Invalid json content"
        }
    }
}

object JsonValueInvalid : ErrorDetail()
data class JsonValidationFailed(val validationMsg: String) : ErrorDetail()
object JsonValueMissing : ErrorDetail()
object NotAJsonValue : ErrorDetail()
data class JsonAutoConversionFailed(val err: Throwable) : ErrorDetail()
data class JsValidationError(val errorDetail: ErrorDetail, val path: JsonPath = JsonPath(""))

sealed class JsonResult<out A> {

    abstract fun isSuccess(): Boolean
    abstract fun isError(): Boolean

    infix fun <B> combine(b: JsonResult<B>): JsonResult<Tuple2<A, B>> =
        when (this) {
            is JsonError -> when (b) {
                is JsonError -> JsonError(b.errors.plus(this.errors))
                is JsonSuccess -> JsonError(this.errors)
            }
            is JsonSuccess -> when (b) {
                is JsonError -> JsonError(b.errors)
                is JsonSuccess -> JsonSuccess(Tuple2(this.value, b.value))
            }
        }

    infix fun <B> and(b: JsonResult<B>): JsonResult<Tuple2<A, B>> =
        when (this) {
            is JsonSuccess -> when (b) {
                is JsonError -> b
                is JsonSuccess -> JsonSuccess(Tuple2(this.value, b.value))
            }
            is JsonError -> this
        }

    fun toEither() = when (this) {
        is JsonSuccess -> Right(this.value)
        is JsonError -> Left(this.errors)
    }

    fun <B> map(block: (A) -> B): JsonResult<B> = when (this) {
        is JsonSuccess -> JsonSuccess(block(this.value))
        is JsonError -> this
    }

    fun <B> flatMap(block: (A) -> JsonResult<B>): JsonResult<B> = when (this) {
        is JsonSuccess -> block(this.value)
        is JsonError -> this
    }

    @Suppress("UNCHECKED_CAST")
    fun <B> flatten(): JsonResult<B> = when (this) {
        is JsonSuccess -> {
            when (val result = this.value) {
                is JsonResult<*> -> (result) as JsonResult<B>
                else -> this as JsonResult<B>
            }
        }
        is JsonError -> this
    }
}

data class JsonSuccess<A>(val value: A) : JsonResult<A>() {
    override fun isSuccess(): Boolean = true
    override fun isError(): Boolean = false
}

data class JsonError(val errors: NonEmptyList<JsValidationError>) : JsonResult<Nothing>() {

    override fun isSuccess(): Boolean = false
    override fun isError(): Boolean = true
}

object Json {

    fun <T> handle(block: () -> T) = Try {
        val jsonResult = block()
        if (jsonResult == JsonMissing) {
            JsonError(JsValidationError(JsonValueMissing).nel())
        } else {
            JsonSuccess(jsonResult)
        }
    }.getOrElse {
        JsonError(when (it) {
            is MissingKotlinParameterException -> JsValidationError(JsonValueMissing, JsonPath(it.parameter.name ?: ""))
            else -> JsValidationError(JsonAutoConversionFailed(it))
        }.nel())
    }

    fun parse(input: String): JsonResult<JsonValue> = handle { StaticBinding.parse(input) }
    fun parse(input: ByteArray): JsonResult<JsonValue> = parse(String(input))

    fun <T> parse(input: String, reads: JsonReads<T>): JsonResult<T> = parse(input).flatMap { reads(it) }
    inline fun <reified T> extract(input: ByteArray): JsonResult<T> = extract(String(input))
    inline fun <reified T> extract(input: String): JsonResult<T> = handle { StaticBinding.extract<T>(input) }
    inline fun <reified T> extract(input: JsonValue): JsonResult<T> = handle { StaticBinding.extract<T>(input) }

    fun stringify(jsonValue: JsonValue): JsonResult<String> = handle { StaticBinding.stringify(jsonValue) }

    fun <T> stringify(data: T): JsonResult<String> = handle { StaticBinding.stringify(data) }
    fun <T> unsafeStringify(data: T): String = stringify(data).unsafeFix()
    fun <T> jsonValue(data: T): JsonResult<JsonValue> = handle { StaticBinding.jsonValue(data) }
    fun <T> unsafeJsonValue(data: T): JsonValue = jsonValue(data).unsafeFix()

    operator fun invoke(vararg objects: JsonObject): JsonObject =
        JsonObject(
            objects.fold(mapOf()) { acc, item ->
                acc.plus(item.value)
            }
        )

    inline fun <reified T> jsonReads(): JsonReads<T> = {
        extract(it)
    }

    fun <T> jsonWrites(): JsonWrites<T> = {
        unsafeJsonValue(it)
    }
}

fun <T> JsonResult<T>.unsafeFix(): T = when (this) {
    is JsonSuccess -> this.value
    is JsonError -> throw JsonException(errors)
}

data class JsonPath(val value: String) {

    fun append(b: JsonPath): JsonPath = if (b.value.isBlank()) {
        this
    } else {
        JsonPath("$value.${b.value}")
    }
}

// TODO the builder can throw error if developer is smart enough to throw exceptions in type safe code
@Suppress("LongParameterList")
sealed class JsonValue {

    operator fun <A, OUT> invoke(a: PathReads<A>, builder: (A) -> OUT): JsonResult<OUT> =
        when (val result = a(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(builder(result.value))
        }

    operator fun <A, B, OUT> invoke(a: PathReads<A>, b: PathReads<B>, builder: (A, B) -> OUT): JsonResult<OUT> =
        when (val result = a(this) combine b(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(builder(result.value.a, result.value.b))
        }

    operator fun invoke(vararg reads: PathReads<*>) =
        reads.map {
            it(this)
        }.reduce { acc, jsonResult ->
            acc and jsonResult
        }

    operator fun <A, B, C, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        builder: (A, B, C) -> OUT
    ): JsonResult<OUT> =
        when (val result = a(this) combine b(this) combine c(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        builder: (A, B, C, D) -> OUT
    ): JsonResult<OUT> =
        when (val result = a(this) combine b(this) combine c(this) combine d(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        builder: (A, B, C, D, E) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, F, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        f: PathReads<F>,
        builder: (A, B, C, D, E, F) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this) combine f(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a.a,
                    result.value.a.a.a.a.b,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, F, G, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        f: PathReads<F>,
        g: PathReads<G>,
        builder: (A, B, C, D, E, F, G) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this) combine f(this) combine g(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a.a.a,
                    result.value.a.a.a.a.a.b,
                    result.value.a.a.a.a.b,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, F, G, H, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        f: PathReads<F>,
        g: PathReads<G>,
        h: PathReads<H>,
        builder: (A, B, C, D, E, F, G, H) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this)
                    combine f(this) combine g(this) combine h(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a.a.a.a,
                    result.value.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.b,
                    result.value.a.a.a.a.b,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, F, G, H, I, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        f: PathReads<F>,
        g: PathReads<G>,
        h: PathReads<H>,
        i: PathReads<I>,
        builder: (A, B, C, D, E, F, G, H, I) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this)
                combine f(this) combine g(this) combine h(this) combine i(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a.a.a.a.a,
                    result.value.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.b,
                    result.value.a.a.a.a.b,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, F, G, H, I, J, K, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        f: PathReads<F>,
        g: PathReads<G>,
        h: PathReads<H>,
        i: PathReads<I>,
        j: PathReads<J>,
        k: PathReads<K>,
        builder: (A, B, C, D, E, F, G, H, I, J, K) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this)
                combine f(this) combine g(this) combine h(this) combine i(this) combine j(this) combine k(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a.a.a.a.a.a.a,
                    result.value.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.b,
                    result.value.a.a.a.a.b,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, F, G, H, I, J, K, L, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        f: PathReads<F>,
        g: PathReads<G>,
        h: PathReads<H>,
        i: PathReads<I>,
        j: PathReads<J>,
        k: PathReads<K>,
        l: PathReads<L>,
        builder: (A, B, C, D, E, F, G, H, I, J, K, L) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this)
                combine f(this) combine g(this) combine h(this) combine i(this)
                combine j(this) combine k(this) combine l(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a.a.a.a.a.a.a.a,
                    result.value.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.b,
                    result.value.a.a.a.a.b,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        f: PathReads<F>,
        g: PathReads<G>,
        h: PathReads<H>,
        i: PathReads<I>,
        j: PathReads<J>,
        k: PathReads<K>,
        l: PathReads<L>,
        m: PathReads<M>,
        n: PathReads<N>,
        o: PathReads<O>,
        p: PathReads<P>,
        q: PathReads<Q>,
        builder: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this)
                combine f(this) combine g(this) combine h(this) combine i(this)
                combine j(this) combine k(this) combine l(this) combine m(this)
                combine n(this) combine o(this) combine p(this) combine q(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.b,
                    result.value.a.a.a.a.b,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    operator fun <A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, OUT> invoke(
        a: PathReads<A>,
        b: PathReads<B>,
        c: PathReads<C>,
        d: PathReads<D>,
        e: PathReads<E>,
        f: PathReads<F>,
        g: PathReads<G>,
        h: PathReads<H>,
        i: PathReads<I>,
        j: PathReads<J>,
        k: PathReads<K>,
        l: PathReads<L>,
        m: PathReads<M>,
        n: PathReads<N>,
        o: PathReads<O>,
        p: PathReads<P>,
        q: PathReads<Q>,
        r: PathReads<R>,
        s: PathReads<S>,
        builder: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) -> OUT
    ): JsonResult<OUT> =
        when (val result =
            a(this) combine b(this) combine c(this) combine d(this) combine e(this)
                    combine f(this) combine g(this) combine h(this) combine i(this)
                    combine j(this) combine k(this) combine l(this) combine m(this)
                    combine n(this) combine o(this) combine p(this) combine q(this)
                    combine r(this) combine s(this)) {
            is JsonError -> result
            is JsonSuccess -> JsonSuccess(
                builder(
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.a.b,
                    result.value.a.a.a.a.a.b,
                    result.value.a.a.a.a.b,
                    result.value.a.a.a.b,
                    result.value.a.a.b,
                    result.value.a.b,
                    result.value.b
                )
            )
        }

    fun put(key: String, value: JsonValue) = when (this) {
        is JsonObject -> JsonObject(this.value.plus(key to value))
        else -> this
    }

    fun get(key: String) = when (this) {
        is JsonObject -> this.value[key] ?: JsonNull
        else -> JsonNull
    }

    fun path(path: String) = path.split("/").fold(this) { value, key -> value.get(key) }
}

object JsonNull : JsonValue()
object JsonMissing : JsonValue()

sealed class JsonBoolean : JsonValue() {
    abstract val value: Boolean
}

object JsonTrue : JsonBoolean() {
    override val value = true
}

object JsonFalse : JsonBoolean() {
    override val value = false
}

data class JsonNumber(val value: BigDecimal) : JsonValue()

data class JsonString(val value: String) : JsonValue()
data class JsonBinary(val value: ByteArray) : JsonValue() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        // TODO use kotlin contracts
        if (javaClass != other?.javaClass) return false
        other as JsonBinary
        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

data class JsonArray(val value: Sequence<JsonValue>) : JsonValue()
data class JsonObject(val value: Map<String, JsonValue>) : JsonValue()

data class JsonException(val err: NonEmptyList<JsValidationError>) :
    RuntimeException(err.all.joinToString { it.errorDetail.show() })
