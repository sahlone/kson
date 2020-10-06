package com.sahlone.kson.data.mapper

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.BinaryNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.readValue

object StaticBinding {
    val jacksonMapper: ObjectMapper = ObjectMapperFactory.createDefaultObjectMapper()

    private val writer = jacksonMapper.writer().with(
        JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN
    )
    private val jnf = JsonNodeFactory(true)
    private fun to(value: JsonValue): JsonNode =
        when (value) {
            is JsonNull -> NullNode.instance
            is JsonMissing -> MissingNode.getInstance()
            is JsonTrue -> BooleanNode.TRUE
            is JsonFalse -> BooleanNode.FALSE
            is JsonString -> TextNode(value.value)
            is JsonArray -> ArrayNode(jnf, value.value.map { to(it) }.toMutableList())
            is JsonObject -> ObjectNode(jnf, value.value.filter {
                when (it.value) {
                    is JsonNull -> false
                    else -> true
                }
            }.map { it.key to to(it.value) }.toMap())
            is JsonNumber -> DecimalNode(value.value)
            is JsonBinary -> BinaryNode(value.value)
        }

    private fun from(value: JsonNode): JsonValue =
        when (value.nodeType) {
            JsonNodeType.ARRAY -> JsonArray(Sequence { value.elements() }.map { from(it) })
            JsonNodeType.BINARY -> JsonBinary(value.binaryValue())
            JsonNodeType.BOOLEAN -> when (value.asBoolean()) {
                true -> JsonTrue
                false -> JsonFalse
            }
            JsonNodeType.MISSING -> JsonMissing
            JsonNodeType.NULL -> JsonNull
            JsonNodeType.NUMBER -> JsonNumber(value.decimalValue())
            JsonNodeType.OBJECT -> {
                val map = value.fieldNames().asSequence().map {
                    it to from(value.get(it))
                }.toMap()
                JsonObject(map)
            }
            JsonNodeType.STRING -> if (null == value.textValue()) JsonNull else JsonString(value.textValue())
            JsonNodeType.POJO -> JsonNull
            null -> JsonNull
        }

    fun parse(input: String): JsonValue = from(jacksonMapper.readTree(input))

    fun parse(input: ByteArray): JsonValue = parse(String(input))

    inline fun <reified T> extract(t: String): T = jacksonMapper.readValue(t)

    inline fun <reified T> extract(json: JsonValue): T = jacksonMapper.readValue(stringify(json))

    fun stringify(jsonValue: JsonValue): String = writer.writeValueAsString(to(jsonValue))

    fun <T> stringify(t: T): String = jacksonMapper.writeValueAsString(t)

    fun <T> jsonValue(t: T): JsonValue = from(jacksonMapper.readTree(jacksonMapper.writeValueAsString(t)))
}
