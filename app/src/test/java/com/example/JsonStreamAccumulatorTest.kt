package com.example

import com.example.core.data.service.JsonStreamAccumulator
import com.example.core.data.service.McpStreamException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class JsonStreamAccumulatorTest {

    @Test
    fun testPerfectJsonStream() {
        val accumulator = JsonStreamAccumulator()
        accumulator.append("{\"tool\": ")
        accumulator.append("\"web_search\", ")
        accumulator.append("\"query\": ")
        accumulator.append("\"Jetpack Compose\"}")

        val json = accumulator.getValidatedJson()
        assertEquals("web_search", json.getString("tool"))
        assertEquals("Jetpack Compose", json.getString("query"))
        assertTrue(accumulator.telemetry.parseSuccess)
        assertFalse(accumulator.telemetry.repairAttempted)
    }

    @Test
    fun testBoundarySafeChunkSplitting() {
        val accumulator = JsonStreamAccumulator()
        // Split precisely inside escape sequences and properties
        accumulator.append("{\"description\": \"line1\\nli")
        accumulator.append("ne2\", \"escape\": \"foo\\\"ba")
        accumulator.append("r\", \"unicode\": \"\\u00")
        accumulator.append("41\"}")

        val json = accumulator.getValidatedJson()
        assertEquals("line1\nline2", json.getString("description"))
        assertEquals("foo\"bar", json.getString("escape"))
        assertEquals("A", json.getString("unicode"))
    }

    @Test
    fun testLenientRepairTrailingCommas() {
        val accumulator = JsonStreamAccumulator()
        accumulator.append("{\"item_list\": [1, 2, 3,], \"nested\": {\"foo\": \"bar\",},}")

        val json = accumulator.getValidatedJson()
        val list = json.getJSONArray("item_list")
        assertEquals(3, list.length())
        assertEquals(1, list.getInt(0))
        assertEquals("bar", json.getJSONObject("nested").getString("foo"))
        assertTrue(accumulator.telemetry.repairAttempted)
        assertTrue(accumulator.telemetry.repairSuccessful)
    }

    @Test
    fun testLenientRepairTruncatedBraces() {
        val accumulator = JsonStreamAccumulator()
        // Truncated during streaming
        accumulator.append("{\"status\": \"ongoing\", \"progress\": {\"attempts\": 3")

        val json = accumulator.getValidatedJson()
        assertEquals("ongoing", json.getString("status"))
        val progress = json.getJSONObject("progress")
        assertEquals(3, progress.getInt("attempts"))
        assertTrue(accumulator.telemetry.repairSuccessful)
    }

    @Test
    fun testLenientRepairTruncatedArrayBrackets() {
        val accumulator = JsonStreamAccumulator()
        accumulator.append("{\"data\": [100, 201, 303")

        val json = accumulator.getValidatedJson()
        val data = json.getJSONArray("data")
        assertEquals(3, data.length())
        assertEquals(303, data.getInt(2))
    }

    @Test
    fun testEarlyFailOnMalformedStructure() {
        val accumulator = JsonStreamAccumulator()
        accumulator.append("{\"data\":}") // Start invalid, but wait for append close error check
        
        try {
            accumulator.append("}}") // Throws immediately on unmatched brace
            fail("Expected unmatched closing brace error")
        } catch (e: McpStreamException.StreamingParseFailure) {
            assertEquals("braceDepth became negative", e.unmatchedStructureInfo)
            assertTrue(accumulator.telemetry.malformedPayloadStatsCount >= 1)
        }
    }

    @Test
    fun testBufferSafetyLimits() {
        val maxLen = 100
        val accumulator = JsonStreamAccumulator(maxBufferSize = maxLen)
        accumulator.append("{\"key\": \"")
        
        try {
            accumulator.append("A".repeat(110))
            fail("Expected size limit exception")
        } catch (e: McpStreamException.BufferExceeded) {
            assertTrue(e.message!!.contains("Buffer size limit"))
        }
    }

    @Test
    fun testIsolatedConcurrentStreams() {
        val executor = Executors.newFixedThreadPool(8)
        val futures = mutableListOf<Future<JSONObject>>()

        for (i in 1..40) {
            futures.add(executor.submit<JSONObject> {
                val acc = JsonStreamAccumulator()
                acc.append("{\"id\": $i, \"payload\": \"temp")
                acc.append("\", \"active\": true}")
                acc.getValidatedJson()
            })
        }

        executor.shutdown()
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS))

        for (i in 0 until 40) {
            val json = futures[i].get()
            assertEquals(i + 1, json.getInt("id"))
            assertEquals("temp", json.getString("payload"))
            assertTrue(json.getBoolean("active"))
        }
    }
}
