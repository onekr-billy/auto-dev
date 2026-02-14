package cc.unitmesh.server.service

import cc.unitmesh.agent.config.*
import cc.unitmesh.agent.mcp.McpConfig
import cc.unitmesh.agent.mcp.McpServerConfig
import cc.unitmesh.agent.mcp.McpServerStatus
import cc.unitmesh.config.ConfigManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * MCP Tool Service - Manages MCP server configurations and tool discovery for the server side.
 *
 * Provides CRUD operations for MCP server configurations and tool discovery/execution.
 */
class McpToolService {
    private val mutex = Mutex()

    /**
     * Get all MCP server configurations
     */
    suspend fun getServers(): Map<String, McpServerConfig> = mutex.withLock {
        val toolConfig = ConfigManager.loadToolConfig()
        toolConfig.mcpServers
    }

    /**
     * Add or update an MCP server configuration
     */
    suspend fun saveServer(name: String, config: McpServerConfig): Map<String, McpServerConfig> = mutex.withLock {
        val toolConfig = ConfigManager.loadToolConfig()
        val updatedServers = toolConfig.mcpServers.toMutableMap()
        updatedServers[name] = config
        val updatedConfig = toolConfig.copy(mcpServers = updatedServers)
        ConfigManager.saveToolConfig(updatedConfig)
        updatedConfig.mcpServers
    }

    /**
     * Delete an MCP server configuration
     */
    suspend fun deleteServer(name: String): Map<String, McpServerConfig> = mutex.withLock {
        val toolConfig = ConfigManager.loadToolConfig()
        val updatedServers = toolConfig.mcpServers.toMutableMap()
        updatedServers.remove(name)
        val updatedConfig = toolConfig.copy(mcpServers = updatedServers)
        ConfigManager.saveToolConfig(updatedConfig)
        updatedConfig.mcpServers
    }

    /**
     * Discover tools from all configured MCP servers
     */
    suspend fun discoverTools(): Map<String, List<ToolItem>> {
        val toolConfig = ConfigManager.loadToolConfig()
        if (toolConfig.mcpServers.isEmpty()) return emptyMap()

        return McpToolConfigManager.discoverMcpTools(
            toolConfig.mcpServers,
            toolConfig.enabledMcpTools.toSet()
        )
    }

    /**
     * Discover tools from a specific MCP server
     */
    suspend fun discoverServerTools(serverName: String): List<ToolItem> {
        val toolConfig = ConfigManager.loadToolConfig()
        val enabledMcpTools = toolConfig.enabledMcpTools.toSet()

        val callback = object : McpLoadingStateCallback {
            override fun onServerStateChanged(serverName: String, state: McpServerState) {}
            override fun onLoadingStateChanged(loadingState: McpLoadingState) {}
            override fun onBuiltinToolsLoaded(tools: List<ToolItem>) {}
        }

        val loadingState = McpToolConfigManager.discoverMcpToolsIncremental(
            toolConfig.mcpServers.filter { it.key == serverName },
            enabledMcpTools,
            callback
        )

        return loadingState.servers[serverName]?.tools ?: emptyList()
    }

    /**
     * Toggle a tool's enabled state
     */
    suspend fun toggleTool(toolName: String, enabled: Boolean) = mutex.withLock {
        val toolConfig = ConfigManager.loadToolConfig()
        val enabledTools = toolConfig.enabledMcpTools.toMutableList()
        if (enabled && toolName !in enabledTools) {
            enabledTools.add(toolName)
        } else if (!enabled) {
            enabledTools.remove(toolName)
        }
        val updatedConfig = toolConfig.copy(enabledMcpTools = enabledTools)
        ConfigManager.saveToolConfig(updatedConfig)
    }

    /**
     * Execute a tool on a specific MCP server
     */
    suspend fun executeTool(serverName: String, toolName: String, arguments: String): String {
        return McpToolConfigManager.executeTool(serverName, toolName, arguments)
    }

    /**
     * Get server statuses
     */
    fun getServerStatuses(): Map<String, McpServerStatus> {
        return McpToolConfigManager.getServerStatuses()
    }
}
