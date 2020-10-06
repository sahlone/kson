package com.sahlone.kson.data.mapper

import io.kotlintest.fail
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec

data class TransactionRequest(
    val paymentMethod: String,
    val amount: Long
)

class JsonAutoConversionTest : ShouldSpec({

    val missingAttributeJson = """
        {
            "amount": 1000
        }
    """.trimIndent()

    "Json to Kotlin object mapping" {

        "should return JsonValueMissing error for not nullable field" {
            val jsonReads = Json.jsonReads<TransactionRequest>()
            val result = jsonReads(Json.parse(missingAttributeJson).unsafeFix())

            if (result is JsonError) {
                result.errors.size shouldBe 1
                result.errors.head.errorDetail shouldBe JsonValueMissing
                result.errors.head.path shouldBe JsonPath("paymentMethod")
            } else {
                fail("JsonError is expected")
            }
        }
    }
})
