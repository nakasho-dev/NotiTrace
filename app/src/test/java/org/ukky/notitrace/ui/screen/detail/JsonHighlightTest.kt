package org.ukky.notitrace.ui.screen.detail

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class JsonHighlightTest {

    private val colors = JsonHighlightColors(
        key = Color.Blue,
        string = Color.Green,
        number = Color.Red,
        boolNull = Color.Magenta,
        brace = Color.Gray,
    )

    @Test
    fun `highlightJson preserves plain text content`() {
        val json = """{"name": "Alice", "age": 30}"""
        val result = highlightJson(json, colors)
        // AnnotatedString の平文テキストが元の JSON と一致すること
        assertEquals(json, result.text)
    }

    @Test
    fun `highlightJson handles pretty-printed JSON`() {
        val json = """
            {
                "title": "Hello",
                "count": 42,
                "active": true,
                "deleted": false,
                "value": null
            }
        """.trimIndent()
        val result = highlightJson(json, colors)
        assertEquals(json, result.text)
    }

    @Test
    fun `highlightJson applies key color to JSON keys`() {
        val json = """{"key": "value"}"""
        val result = highlightJson(json, colors)

        // "key" はインデックス 1..5 (引用符含む)
        val keySpans = result.spanStyles.filter {
            it.start == 1 && it.end == 6 // {"key" の " から " まで = index 1..5
        }
        // AnnotatedString のインデックスを確認
        // { = 0, " = 1, k = 2, e = 3, y = 4, " = 5, ...
        val spans = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "\"key\""
        }
        assertTrue("Key should have blue color span", spans.any { it.item.color == Color.Blue })
    }

    @Test
    fun `highlightJson applies string color to JSON string values`() {
        val json = """{"key": "value"}"""
        val result = highlightJson(json, colors)

        val spans = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "\"value\""
        }
        assertTrue("String value should have green color span", spans.any { it.item.color == Color.Green })
    }

    @Test
    fun `highlightJson applies number color to numeric values`() {
        val json = """{"count": 123}"""
        val result = highlightJson(json, colors)

        val spans = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "123"
        }
        assertTrue("Number should have red color span", spans.any { it.item.color == Color.Red })
    }

    @Test
    fun `highlightJson applies boolNull color to true, false, null`() {
        val json = """{"a": true, "b": false, "c": null}"""
        val result = highlightJson(json, colors)

        val trueSpans = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "true"
        }
        val falseSpans = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "false"
        }
        val nullSpans = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "null"
        }

        assertTrue("true should have magenta color", trueSpans.any { it.item.color == Color.Magenta })
        assertTrue("false should have magenta color", falseSpans.any { it.item.color == Color.Magenta })
        assertTrue("null should have magenta color", nullSpans.any { it.item.color == Color.Magenta })
    }

    @Test
    fun `highlightJson applies brace color to structural characters`() {
        val json = """{"a": 1}"""
        val result = highlightJson(json, colors)

        // { at index 0
        val openBrace = result.spanStyles.filter {
            it.start == 0 && it.end == 1
        }
        assertTrue("Opening brace should have gray color", openBrace.any { it.item.color == Color.Gray })
    }

    @Test
    fun `highlightJson handles escaped quotes in strings`() {
        val json = """{"msg": "say \"hello\""}"""
        val result = highlightJson(json, colors)
        assertEquals(json, result.text)
    }

    @Test
    fun `highlightJson handles nested objects`() {
        val json = """{"outer": {"inner": "val"}}"""
        val result = highlightJson(json, colors)
        assertEquals(json, result.text)
    }

    @Test
    fun `highlightJson handles arrays`() {
        val json = """{"list": [1, 2, 3]}"""
        val result = highlightJson(json, colors)
        assertEquals(json, result.text)
    }

    @Test
    fun `highlightJson handles negative numbers`() {
        val json = """{"n": -42}"""
        val result = highlightJson(json, colors)
        assertEquals(json, result.text)

        val spans = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "-42"
        }
        assertTrue("Negative number should have number color", spans.any { it.item.color == Color.Red })
    }

    @Test
    fun `highlightJson handles floating point numbers`() {
        val json = """{"pi": 3.14}"""
        val result = highlightJson(json, colors)
        assertEquals(json, result.text)

        val spans = result.spanStyles.filter {
            result.text.substring(it.start, it.end) == "3.14"
        }
        assertTrue("Float should have number color", spans.any { it.item.color == Color.Red })
    }

    @Test
    fun `highlightJson handles empty object`() {
        val json = "{}"
        val result = highlightJson(json, colors)
        assertEquals(json, result.text)
    }

    @Test
    fun `highlightJson handles empty string value`() {
        val json = """{"key": ""}"""
        val result = highlightJson(json, colors)
        assertEquals(json, result.text)
    }

    @Test
    fun `buildJsonShareFileName includes notification identity`() {
        val fileName = buildJsonShareFileName(
            packageName = "com.example.app",
            notificationId = 42L,
            lastReceivedAt = 1717088400000L,
        )

        assertEquals("notitrace_com.example.app_42_1717088400000.json", fileName)
    }

    @Test
    fun `buildJsonShareFileName sanitizes invalid characters and falls back`() {
        val fileName = buildJsonShareFileName(
            packageName = "com.example/app:beta",
            notificationId = null,
            lastReceivedAt = null,
        )

        assertEquals("notitrace_com.example_app_beta_unknown_latest.json", fileName)
    }
}
