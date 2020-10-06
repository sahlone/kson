package com.sahlone.kson.data.mapper

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.sahlone.kson.model.Id
import com.sahlone.kson.model.Uuid
import java.util.UUID

class IdDeser : JsonDeserializer<Id<*>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Id<*> {
        return Id<String>(p.readValueAs(String::class.java))
    }
}

class IdSer : JsonSerializer<Id<*>>() {
    override fun serialize(value: Id<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        return gen.writeString(value.value)
    }

    override fun handledType(): Class<Id<*>> {
        return Id::class.java
    }
}

class UuidDeser : JsonDeserializer<Uuid<*>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Uuid<*> {
        return Uuid<UUID>(p.readValueAs(UUID::class.java))
    }
}

class UuidSer : JsonSerializer<Uuid<*>>() {
    override fun serialize(value: Uuid<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        return gen.writeString(value.value.toString())
    }

    override fun handledType(): Class<Uuid<*>> {
        return Uuid::class.java
    }
}

class CustomModule : SimpleModule("", Version.unknownVersion()) {

    init {

        addSerializer(IdSer())
        addDeserializer(Id::class.java, IdDeser())
        addSerializer(UuidSer())
        addDeserializer(Uuid::class.java, UuidDeser())
        addSerializer(OptionSer())
        addDeserializer(Option::class.java, OptionDeser())
    }
}

class OptionDeser : JsonDeserializer<Option<*>>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Option<String> {
        return if (p.hasCurrentToken()) {
            Some(p.valueAsString)
        } else {
            None
        }
    }
}

class OptionSer : JsonSerializer<Option<*>>() {
    override fun serialize(value: Option<*>, gen: JsonGenerator, serializers: SerializerProvider) {
        return when (value) {
            is Some -> gen.writeObject(value.t)
            None -> gen.writeNull()
        }
    }

    override fun handledType(): Class<Option<*>> {
        return Option::class.java
    }
}
