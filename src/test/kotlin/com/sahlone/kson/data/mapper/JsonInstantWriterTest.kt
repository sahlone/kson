package com.sahlone.kson.data.mapper

import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec
import java.time.Instant

class JsonInstantWriterTest : ShouldSpec({

    data class TestInstant(val time: Instant)
    "Json writer " {
        should("write java instant as string") {
            val stringTime = "2020-11-12T19:04:14.316Z"
            val expected = """{"time":"2020-11-12T19:04:14.316Z"}"""
            val input = TestInstant(Instant.parse(stringTime))
            val output = Json.stringify(Json.jsonWrites<TestInstant>().invoke(input)).unsafeFix()
            output shouldBe expected
        }

    }
})
