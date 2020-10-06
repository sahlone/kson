package com.sahlone.kson.model

import java.util.UUID

data class Id<T>(val value: String) {

    companion object {
        inline fun <reified DataType> randomId(): Id<DataType> = Id(UUID.randomUUID().toString())
    }
}

data class Uuid<T>(val value: UUID) {

    companion object {
        inline fun <reified DataType> random(): Uuid<DataType> = Uuid(UUID.randomUUID())
        fun randomString(): String = UUID.randomUUID().toString()
    }
}
inline fun <reified T> String.uuid() = Uuid<T>(UUID.fromString(this@uuid))
