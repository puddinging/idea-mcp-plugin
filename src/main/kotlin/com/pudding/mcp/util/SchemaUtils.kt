package com.pudding.mcp.util

import com.google.gson.JsonObject

object SchemaUtils {

    fun schema(block: JsonObject.() -> Unit): JsonObject {
        return JsonObject().apply(block)
    }

    fun JsonObject.properties(block: JsonObject.() -> Unit) {
        val props = JsonObject().apply(block)
        add("properties", props)
    }

    fun JsonObject.required(vararg fields: String) {
        val arr = com.google.gson.JsonArray()
        fields.forEach { arr.add(it) }
        add("required", arr)
    }

    fun JsonObject.stringProp(name: String, description: String) {
        val prop = JsonObject().apply {
            addProperty("type", "string")
            addProperty("description", description)
        }
        (getAsJsonObject("properties") ?: JsonObject().also { add("properties", it) })
            .add(name, prop)
    }

    fun JsonObject.numberProp(name: String, description: String) {
        val prop = JsonObject().apply {
            addProperty("type", "number")
            addProperty("description", description)
        }
        (getAsJsonObject("properties") ?: JsonObject().also { add("properties", it) })
            .add(name, prop)
    }

    fun JsonObject.boolProp(name: String, description: String) {
        val prop = JsonObject().apply {
            addProperty("type", "boolean")
            addProperty("description", description)
        }
        (getAsJsonObject("properties") ?: JsonObject().also { add("properties", it) })
            .add(name, prop)
    }

    fun result(block: JsonObject.() -> Unit): JsonObject = JsonObject().apply(block)

    fun error(message: String): JsonObject = JsonObject().apply {
        addProperty("error", message)
    }

    fun JsonObject.string(key: String): String? = get(key)?.takeIf { !it.isJsonNull }?.asString
    fun JsonObject.int(key: String): Int? = get(key)?.takeIf { !it.isJsonNull }?.asInt
    fun JsonObject.bool(key: String): Boolean? = get(key)?.takeIf { !it.isJsonNull }?.asBoolean
    fun JsonObject.stringOrDefault(key: String, default: String): String = string(key) ?: default
    fun JsonObject.intOrDefault(key: String, default: Int): Int = int(key) ?: default
    fun JsonObject.boolOrDefault(key: String, default: Boolean): Boolean = bool(key) ?: default
}
