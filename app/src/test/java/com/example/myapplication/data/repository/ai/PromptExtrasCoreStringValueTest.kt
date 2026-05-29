package com.example.myapplication.data.repository.ai

import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptExtrasCoreStringValueTest {
    private fun parse(json: String): JsonObject =
        AppJson.gson.fromJson(json, JsonObject::class.java)

    @Test
    fun stringValue_returnsEmptyForJsonObjectField() {
        val obj = parse("""{"k":{"a":1}}""")
        assertEquals("", obj.stringValue("k"))
    }

    @Test
    fun stringValue_returnsEmptyForJsonArrayField() {
        val obj = parse("""{"k":[1,2]}""")
        assertEquals("", obj.stringValue("k"))
    }

    @Test
    fun stringValue_returnsEmptyForJsonNullField() {
        val obj = parse("""{"k":null}""")
        assertEquals("", obj.stringValue("k"))
    }

    @Test
    fun stringValue_returnsEmptyForMissingField() {
        val obj = parse("""{"other":"x"}""")
        assertEquals("", obj.stringValue("k"))
    }

    @Test
    fun stringValue_returnsTrimmedStringForPrimitive() {
        val obj = parse("""{"k":" hi "}""")
        assertEquals("hi", obj.stringValue("k"))
    }

    @Test
    fun stringValue_returnsStringFormForNumberPrimitive() {
        val obj = parse("""{"k":42}""")
        assertEquals("42", obj.stringValue("k"))
    }

    @Test
    fun stringValue_returnsStringFormForBooleanPrimitive() {
        val obj = parse("""{"k":true}""")
        assertEquals("true", obj.stringValue("k"))
    }

    @Test
    fun stringValue_returnsEmptyForNullReceiver() {
        val obj: JsonObject? = null
        assertEquals("", obj.stringValue("k"))
    }
}
