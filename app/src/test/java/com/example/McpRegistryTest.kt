package com.example

import com.example.core.data.service.*
import com.example.core.domain.models.McpServerModel
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class McpRegistryTest {

    private val server1 = McpServerModel(
        id = "srv_fs",
        name = "Filesystem",
        endpoint = "http://localhost:8080",
        transport = "HTTP"
    )

    private val server2 = McpServerModel(
        id = "srv_db",
        name = "Database",
        endpoint = "http://localhost:8081",
        transport = "HTTP"
    )

    @Test
    fun testDuplicateToolNamesAcrossServers() {
        val toolA = McpTool("read_file", "Reads a file", "{}")
        val toolB = McpTool("read_file", "Reads another file", "{}")

        val registry = McpRegistry.Builder()
            .register(server1, toolA)
            .register(server2, toolB)
            .build()

        assertEquals(2, registry.items.size)

        val tool1 = registry.getTool(ToolIdentifier("srv_fs", "read_file"))
        val tool2 = registry.getTool(ToolIdentifier("srv_db", "read_file"))

        assertNotNull(tool1)
        assertNotNull(tool2)
        assertEquals("srv_fs", tool1!!.server.id)
        assertEquals("srv_db", tool2!!.server.id)
    }

    @Test
    fun testRoutingCorrectnessWithNamespacedIds() {
        val toolA = McpTool("read_file", "Reads a file", "{}")
        val toolB = McpTool("write_file", "Writes a file", "{}")

        val registry = McpRegistry.Builder()
            .register(server1, toolA)
            .register(server1, toolB)
            .build()

        val result = registry.dispatch("srv_fs::write_file")
        assertTrue(result is DispatchResult.Success)
        val success = result as DispatchResult.Success
        assertEquals("write_file", success.registeredTool.tool.name)
        assertEquals("srv_fs", success.registeredTool.server.id)
    }

    @Test
    fun testAmbiguityRejectionForBareNames() {
        val toolA = McpTool("query_table", "Run SQL on database", "{}")
        val toolB = McpTool("query_table", "Run SQL on client", "{}")

        val registry = McpRegistry.Builder()
            .register(server1, toolA)
            .register(server2, toolB)
            .build()

        val result = registry.dispatch("query_table")
        assertTrue(result is DispatchResult.AmbiguityError)
        val ambiguity = result as DispatchResult.AmbiguityError
        assertEquals("query_table", ambiguity.toolName)
        assertEquals(2, ambiguity.matchingServers.size)
    }

    @Test
    fun testSingleServerCompatibilityForBareNames() {
        val toolA = McpTool("query_table", "Run SQL", "{}")
        val toolB = McpTool("fetch_web", "Get URL", "{}")

        val registry = McpRegistry.Builder()
            .register(server1, toolA)
            .register(server2, toolB)
            .build()

        val res1 = registry.dispatch("query_table")
        assertTrue(res1 is DispatchResult.Success)
        assertEquals("srv_fs", (res1 as DispatchResult.Success).registeredTool.server.id)

        val res2 = registry.dispatch("fetch_web")
        assertTrue(res2 is DispatchResult.Success)
        assertEquals("srv_db", (res2 as DispatchResult.Success).registeredTool.server.id)
    }

    @Test
    fun testMalformedOrCreateEmptyNamespaces() {
        val toolA = McpTool("read_file", "Reads a file", "{}")
        val registry = McpRegistry.Builder()
            .register(server1, toolA)
            .build()

        val res1 = registry.dispatch("srv_fs::")
        assertTrue(res1 is DispatchResult.InvalidNamespace)

        val res2 = registry.dispatch("::read_file")
        assertTrue(res2 is DispatchResult.InvalidNamespace)

        val res3 = registry.dispatch("  ::  ")
        assertTrue(res3 is DispatchResult.InvalidNamespace)
    }

    @Test
    fun testRegistryImmutabilityAfterBuild() {
        val toolA = McpTool("read_file", "Reads a file", "{}")
        val builder = McpRegistry.Builder()
        builder.register(server1, toolA)

        val registry = builder.build()
        
        val toolB = McpTool("write_file", "Writes a file", "{}")
        builder.register(server1, toolB)

        assertNull(registry.getTool(ToolIdentifier("srv_fs", "write_file")))
        assertEquals(1, registry.items.size)
    }

    @Test
    fun testConcurrentAccessSafety() {
        val builder = McpRegistry.Builder()
        val executor = Executors.newFixedThreadPool(10)
        val futures = mutableListOf<Future<*>>()

        for (i in 1..100) {
            futures.add(executor.submit {
                val srv = McpServerModel(id = "srv_con_$i", name = "Srv $i", endpoint = "", transport = "HTTP")
                val tool = McpTool("con_tool_$i", "Description $i", "{}")
                builder.register(srv, tool)
            })
        }

        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)

        for (future in futures) {
            future.get()
        }

        val registry = builder.build()
        assertEquals(100, registry.items.size)
    }

    @Test
    fun testSchemaCorrectnessNamespacing() {
        val tool = McpTool("read_file", "Reads a file", "{}")
        val registry = McpRegistry.Builder()
            .register(server1, tool)
            .build()

        val registered = registry.getTool(ToolIdentifier("srv_fs", "read_file"))
        assertNotNull(registered)
        assertEquals("srv_fs::read_file", registered!!.identifier.toNamespacedId())
        assertEquals("read_file", registered.tool.name)
        assertEquals("srv_fs", registered.tool.serverId)
        assertEquals("Filesystem", registered.tool.serverName)
    }
}
