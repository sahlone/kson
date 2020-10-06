package com.sahlone.kson.data.mapper

import io.kotlintest.fail
import io.kotlintest.matchers.beOfType
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec

class JsonReaderTest : ShouldSpec({

    val complexJson = """
        {
            "menu": {
                "id": "file",
                "value": 2,
                "value1":2.123,
                "popup": {
                  "menuItems": [
                    {"value": "New", "onclick": true},
                    {"value": "Open", "onclick": false},
                    {"value": "Close", "onclick": true}
                  ],
                  "longValue":1212121211133
                }
            }
        }
    """.trimIndent()

    val erroneousJson = """
        {
            "menu": {
                "ids": "file",
                "value": 2,
                "value1":2.123,
                "popup": {
                  "menuItems": [
                    {"value": "New", "onclickc": true},
                    {"value": "Open", "onclick": false},
                    {"value": "Close", "onclick": true}
                  ],
                  "longValue":1212121211133
                }
            }
        }
    """.trimIndent()

    val missingAttributeJson = """
        {
            "menu": {
                "id": "file",
                "value": 2,
                "popup": {
                  "menuitems": [
                    {"value": "New", "onclick": true},
                    {"value": "Open", "onclick": false},
                    {"value": "Close", "onclick": true}
                  ],
                  "longValue":1212121211133
                }
            }
        }
    """.trimIndent()

    data class MenuItem(val value: String, val onClik: Boolean)
    data class Popup(val menuItems: List<MenuItem>, val value: Long)
    data class Menu(val id: String, val v1: Int, val v2: Double, val popup: Popup)
    data class AllMenu(val menu: Menu)

    val readsMenuItem: JsonReads<MenuItem> = {
        it(
            StringJsonReads at "value",
            BooleanJsonReads at "onclick"
        ) { a, b ->
            MenuItem(a, b)
        }
    }

    val menuItemArrayValuesReads: JsonReads<Sequence<MenuItem>> = JsonArrayReads(readsMenuItem)

    val popUpReads: JsonReads<Popup> = { json ->
        json(
            menuItemArrayValuesReads at "menuItems",
            LongJsonReads at "longValue"
        ) { a, b ->
            Popup(a.toList(), b)
        }
    }

    val menuReads: JsonReads<Menu> = { json ->
        json(
            StringJsonReads at "id",
            IntJsonReads at "value",
            DoubleJsonReads at "value1",
            popUpReads at "popup"
        ) { a, b, c, d ->
            Menu(a, b, c, d)
        }
    }

    val allMenuReads: JsonReads<AllMenu> = { json ->
        json<Menu, AllMenu>(
            menuReads at "menu"
        ) { a ->
            AllMenu(a)
        }
    }

    "Json mapper" {

        should("deserialize the nested data class") {

            val result = Json.parse(complexJson)
            when (result) {
                is JsonError -> fail("Json parsing failed")
                is JsonSuccess -> when (val res = allMenuReads(result.value)) {
                    is JsonSuccess -> res.value.menu.id shouldBe "file"
                    is JsonError -> fail("Json parsing failed")
                }
            }
        }

        should("return error on empty json") {
            val result = Json.parse("") as JsonError
            result.errors.size shouldBe 1
        }

        should("return JsonNull value given string with null") {
            val result = Json.parse("null")
            result should beOfType<JsonSuccess<JsonNull>>()
        }

        should("return error on missing property value") {
            val result = Json.parse("""{"property":}""")
            when (result) {
                is JsonError -> result.errors.size shouldBe 1
                is JsonSuccess -> fail("Should fail on missing property value")
            }
        }

        should("parse null property") {
            val result = Json.parse("""{"property": null}""")
            when (result) {
                is JsonError -> fail("Json parsing failed")
                is JsonSuccess -> result.value.get("property") shouldBe JsonNull
            }
        }

        should("parse empty JSON object") {
            val result = Json.parse("{}") as JsonSuccess
            (result.value as JsonObject).value.size shouldBe 0
        }

        should("return JsonValueMissing errors for invalid json") {

            val result = Json.parse(erroneousJson)
            when (result) {
                is JsonError -> fail("Json parsing failed")
                is JsonSuccess -> when (val res = allMenuReads(result.value)) {
                    is JsonError -> {
                        res.errors.size shouldBe 2
                        val error1 = res.errors.all.first {
                            it.path.value == "menu.popup.menuItems.[0].onclick"
                        }
                        val error2 = res.errors.all.first { it.path.value == "menu.id" }

                        error1.errorDetail shouldBe JsonValueMissing
                        error2.errorDetail shouldBe JsonValueMissing
                    }
                    is JsonSuccess -> fail("Json parsing should have failed")
                }
            }
        }

        should("return JsonValueMissing errors for missing json field") {

            val result = Json.parse(missingAttributeJson)
            when (result) {
                is JsonError -> fail("Json parsing failed")
                is JsonSuccess -> when (val res = allMenuReads(result.value)) {
                    is JsonError -> {
                        res.errors.size shouldBe 2
                        val error = res.errors.all.first { it.path.value == "menu.value1" }
                        error.errorDetail shouldBe JsonValueMissing
                        val error1 = res.errors.all.first { it.path.value == "menu.popup.menuItems" }
                        error1.errorDetail shouldBe JsonValueMissing
                    }
                    is JsonSuccess -> fail("Json parsing should have failed")
                }
            }
        }

        should("return only one error for multiple level errors") {

            // suppose  e is supposed to be int
            val nestedJsonObjectError = """
                {
                    "a" : {
                            "b" : {
                                    "c" : {
                                        "d" : {
                                                "e": true
                                               }
                                    }
                                }
                           }
                }
            """.trimIndent()

            data class D(val e: Int)
            data class C(val d: D)
            data class B(val c: C)
            data class A(val b: B)
            data class Root(val root: A)

            val readsD: JsonReads<D> = { json ->
                json<Int, D>(
                    (IntJsonReads at "e")
                ) { d ->
                    D(d)
                }
            }
            val readsC: JsonReads<C> = { json ->
                json<D, C>(
                    (readsD at "d")
                ) { d ->
                    C(d)
                }
            }
            val readsB: JsonReads<B> = { json ->
                json<C, B>(
                    (readsC at "c")
                ) { c ->
                    B(c)
                }
            }
            val readsA: JsonReads<A> = { json ->
                json<B, A>(
                    (readsB at "b")
                ) { b ->
                    A(b)
                }
            }
            val rootReads: JsonReads<Root> = { json ->
                json<A, Root>(
                    (readsA at "a")
                ) { a ->
                    Root(a)
                }
            }
            val result = Json.parse(nestedJsonObjectError)
            when (result) {
                is JsonError -> fail("Json parsing failed")
                is JsonSuccess -> when (val res = rootReads(result.value)) {
                    is JsonError -> {
                        res.errors.size shouldBe 1
                        val error = res.errors.all.first { it.path.value == "a.b.c.d.e" }
                        error.errorDetail shouldBe JsonValueInvalid
                    }
                    is JsonSuccess -> fail("Json parsing should have failed")
                }
            }
        }

        should("return JsonValidationFailed errors for invalid json") {
            val validationMsg = "It must be between 10 to 100"
            val menuReadsWithValidation: JsonReads<Menu> = { json ->
                json(
                    StringJsonReads at "id",
                    validatedJsonReads(IntJsonReads, { it in 10..100 }, validationMsg) at "value",
                    DoubleJsonReads at "value1",
                    popUpReads at "popup"
                ) { a, b, c, d ->
                    Menu(a, b, c, d)
                }
            }

            val allMenuReadsWithValidation: JsonReads<AllMenu> = { json ->
                json<Menu, AllMenu>(
                    menuReadsWithValidation at "menu"
                ) { a ->
                    AllMenu(a)
                }
            }

            when (val result = Json.parse(complexJson)) {
                is JsonError -> fail("Json parsing failed")
                is JsonSuccess -> when (val res = allMenuReadsWithValidation(result.value)) {
                    is JsonSuccess -> fail("Json parsing should have failed")
                    is JsonError -> {
                        res.errors.size shouldBe 1
                        val error = res.errors.all.first()
                        error.errorDetail shouldBe JsonValidationFailed(validationMsg)
                    }
                }
            }
        }
    }
})
