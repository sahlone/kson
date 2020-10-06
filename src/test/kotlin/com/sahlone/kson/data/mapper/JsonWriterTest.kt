package com.sahlone.kson.data.mapper

import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec

class JsonWriterTest : ShouldSpec({

    data class MenuItem(val value: String, val onClick: Boolean)
    data class Popup(val menuItems: List<MenuItem>, val value: Long)
    data class Menu(val id: String, val v1: Int, val v2: Double, val popup: Popup)
    data class AllMenu(val menu: Menu)

    val menuItemWrites: JsonWrites<MenuItem> = {
        (StringJsonWrites(it.value) at "value") and
            (BooleanJsonWrites(it.onClick) at "onClick")
    }

    val menuItemArrayWrites: JsonWrites<Iterable<MenuItem>> = JsonArrayWrites(menuItemWrites)

    val popupJsonWriter: JsonWrites<Popup> = {
        Json(
            menuItemArrayWrites(it.menuItems) at "menuItems",
            LongJsonWrites(it.value) at "longValue"
        )
    }
    val menuJsonWrites: JsonWrites<Menu> = {
        Json(
            StringJsonWrites(it.id) at "id",
            IntJsonWrites(it.v1) at "value",
            DoubleJsonWrites(it.v2) at "value1",
            popupJsonWriter(it.popup) at "popup"
        )
    }
    val allMenuJsonWrites: JsonWrites<AllMenu> = {
        Json(
            menuJsonWrites(it.menu) at "menu"
        )
    }

    val menuItemList = listOf(
        MenuItem("New", true),
        MenuItem("Open", false),
        MenuItem("Close", true)
    )

    val allMenu = AllMenu(
        Menu(
            "file", 2, 2.123, Popup(
                menuItemList,
                1212121211133
            )
        )
    )
    val mapper = ObjectMapperFactory.createDefaultObjectMapper()

    "Json writer " {
        should("produce write a nested json object") {
            val expected = """
                {
                    "menu": {
                        "id": "file",
                        "value": 2,
                        "value1":2.12300000000000022026824808563105762004852294921875,
                        "popup": {
                          "menuItems": [
                            {"value": "New", "onClick": true},
                            {"value": "Open", "onClick": false},
                            {"value": "Close", "onClick": true}
                          ],
                          "longValue":1212121211133
                        }
                    }
                }""".trimIndent()
            val json = allMenuJsonWrites(allMenu)
            val result = Json.stringify(json).unsafeFix()
            mapper.readTree(result) shouldBe mapper.readTree(expected)
        }

        should("write a json array") {
            val expected = """
                [
                    {"value": "New", "onClick": true},
                    {"value": "Open", "onClick": false},
                    {"value": "Close", "onClick": true}
                ]
            """.trimIndent()
            val json = menuItemArrayWrites(menuItemList)
            val result = Json.stringify(json).unsafeFix()
            mapper.readTree(result) shouldBe mapper.readTree(expected)
        }

        should("write a json string") {
            val expected = """
                "some json string"
            """.trimIndent()
            val json = StringJsonWrites("some json string")
            val result = Json.stringify(json).unsafeFix()
            mapper.readTree(result) shouldBe mapper.readTree(expected)
        }

        should("write a json double with precision") {
            val json = DoubleJsonWrites(2332.12322323211)
            val result = Json.stringify(json).unsafeFix()
            result.toDouble() shouldBe 2332.12322323211
        }

        should("not include null values in json output") {
            data class TestNull(val some: String? = null, val more: String = "more value")

            val writes = Json.jsonWrites<TestNull>()
            val result = writes.invoke(TestNull())
            val jsonString = Json.stringify(result).unsafeFix()
            jsonString shouldBe """{"more":"more value"}"""
        }

        should(" include empty braces for object with only one null value") {
            data class TestNull(val some: String? = null)

            val writes = Json.jsonWrites<TestNull>()
            val result = writes.invoke(TestNull())
            val jsonString = Json.stringify(result).unsafeFix()
            jsonString shouldBe """{}"""
        }
        should("not fail if the stringify is called multiple times on json value") {
            data class TestData(val some: String = "someValue")

            val writes = Json.jsonWrites<TestData>()
            val result = writes.invoke(TestData())
            val jsonString = Json.stringify(result).unsafeFix()
            jsonString shouldBe """{"some":"someValue"}"""
            val jsonString1 = Json.stringify(result).unsafeFix()
            jsonString1 shouldBe """{"some":"someValue"}"""
        }
    }
})
