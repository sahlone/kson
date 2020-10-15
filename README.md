# kson
Functional Json Library based on [Arrow-kt](https://arrow-kt.io/)  [Jackson](https://github.com/FasterXML/jackson)  and Inspired by [Play-framework Json packages](https://www.playframework.com/documentation/2.8.x/ScalaJson) 

[![](https://jitpack.io/v/sahlone/kson.svg?label=Release)](https://jitpack.io/#sahlone/kson)

Kson is a functional approach to dealing with Json serializing and deserializing objects in Kotlin.
The library is inspired by [Play Framework Json](https://www.playframework.com/documentation/2.8.x/ScalaJson), a Json library for play framework in Scala language. Underneath the library itself is backed by [Jackson](https://github.com/FasterXML/jackson) one of the oldest and stronger libraries in Java for serializing and deserializing. Kson uses the capabilities of Jackson for Json tree parsing.
Kson adds a functional wrapper over whats already provided by Jackson giving user  the benifits of weiting type safe code. In addition the library provides a programmer a holistic approach at dealing with the issue where we have to change a domain model just because of json serializing and deserializing capabilities of a library. The issue can be easily solved by writing custom serializers and deserializers in Jackson, but that renders the whole codebase in clumsy state.

The library developed as an experiment in on of the projects and have been in use on production systems. Library needs adapting as the new version of Kotlin are being added which can make library better like usage of extractors.
So contributions are welcome
### Installation
The library can be obtained from [Jitpack](https://jitpack.io/#sahlone/kson).
```Gradle
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    implementation 'com.github.sahlone:kson:$version'
}
```
### Basic Concepts
The library consists of data types to support primitive data types as listed on [Json.org](https://www.json.org/json-en.html)
```
JsonNumber
JsonNull
JsonBoolean
JsonString
JsonBinary
JsonArray
JsonObject
```
All of the above type are subcatogories of a `Jsonvalue` type
All it boils down is two simple concepts
`JsonReads` A function that reads `Jsonvalue` and return a type class `T`
`JsonWrites` A function that takes a type `T` as an argument and return `JsonResult`
So `JsonReads<T>` can be represented as 
```
(JsonValue) -> JsonResult<T>
```
and `JsonWrites<T>` as 
```
(T) -> JsonValue
```
The result of invoking `JsonReads` function is a type `JsonResult` which is nothing but an encapsulation which summarises the result of deserializing the `Json`.
The result itself can be of two types `JsonSuccess` and `JsonFailure` based on if the computation was a success or a failure

### Basic usage
*Kson* provides two basic methods for serializing and deserializing objects to `JsonValue`
```
Json.parse(jsonInput) -> to parse the json string input to a JsonValue
Json.stringify(jsonValue) -> to serialize  the  JsonValue to a string output
```
Using the building blocks above we can serialize and deserilize the `User` data class
```
data class User(val name: String, val dob: Instant)
```
Now, lets write functions describing how to serialize the `User` object
```
val userWrites: JsonWrites<User> = Json.jsonWrites<User>()
```
Now, we use the function above to produce json
```
Json.stringify(userWrites(User("test user", Instant.now())))
```
The above code should produce result as a string respresnting the json 
```
{"name":"test user","dob":1602097286.063}
```
Lets  take this as an input and create a `User` Object out of it
Input
```
{"name":"test user","dob":1602097286.063}
```
We can parse the  above json like this
```
Json.parse(jsonInput).map(userReads)
```
The above code should gives us a user object back.
Note , that above we produced the reads and writes automatically which is easiest way to get around the library working.
We are not yet done, Kson is a type safe library os we should start handling the results in a type safe manner.  The Json stringfy and parse methods above give results as `JsonResult` which needs handling for example like this
```
when(result){
    is JsonSuccess -> print(user.value)
    is JsonError -> print("failed to produce user object from string ${user.errors}")
}
```
### A step up        
Kson utilizes the concepts of [ADT](https://en.wikipedia.org/wiki/Abstract_data_type) where we can form bigger building blocks from smaller unit.
If you dont want to use above approach and want to take control of Json  by writing functions then we can serialize and deserialize objects using small building blocks
Lets say we have the following data structure
```
data class MenuItem(val value: String, val onClick: Boolean)
data class Popup(val menuItems: List<MenuItem>, val value: Long)
data class Menu(val id: String, val v1: Int, val v2: Double, val popup: Popup)
data class AllMenu(val menu: Menu)
```
Divinding the whole problem into smaller functions
```
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
```

So using the  above functions we can serialize the `AllMenu` object like so
```
val json = allMenuJsonWrites(allMenu)
val result:String = Json.stringify(json).unsafeFix()
```
Note the `unsafeFix` is used to directly get the string value back without checking the types safely if it is a `JsonSuccess`  or a `JsonError`.
Also note  the different syntaxes of writing the  serializers.

For deserializing the above data classes from json string follows the same approach
```
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
```
Using the above `JsonReads` function we can do as follows to deserialize the data class

Input json
```
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
```
Running the below instructions on above input
```
val result = Json.parse(inputJson)
when (result) {
    is JsonError -> "Json parsing failed"
    is JsonSuccess -> result.value
}
```
Using  the kotlin smart casting we can obtain the `AllMenu`  object  back.
###  Reporting issues
Don't shy away from creating  a github issue, A  PR for the fix is always  welcome as well.

