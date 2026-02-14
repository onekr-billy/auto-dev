package cc.unitmesh.devins.ui.compose.agent.mcptool

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.unitmesh.agent.config.*
import cc.unitmesh.agent.mcp.McpServerConfig
import cc.unitmesh.agent.mcp.McpServerStatus
import cc.unitmesh.config.ConfigManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * State for the MCP Tool management page
 */
data class McpToolState(
    val servers: Map<String, McpServerConfig> = emptyMap(),
    val serverStatuses: Map<String, McpServerStatus> = emptyMap(),
    val toolsByServer: Map<String, List<ToolItem>> = emptyMap(),
    val selectedServerName: String? = null,
    val isConfigPaneOpen: Boolean = false,
    val configuringServerName: String? = null,
    val configuringServerConfig: McpServerConfig? = null,
    val filterQuery: String = "",
    val loadingState: McpLoadingState = McpLoadingState()
) {
    val serverNames: List<String>
        get() = servers.keys.toList()

    val filteredServerNames: List<String>
        get() = if (filterQuery.isBlank()) serverNames
        else serverNames.filter { it.contains(filterQuery, ignoreCase = true) }

    val selectedServerTools: List<ToolItem>
        get() = selectedServerName?.let { toolsByServer[it] } ?: emptyList()

    val totalToolCount: Int
        get() = toolsByServer.values.sumOf { it.size }

    val enabledToolCount: Int
        get() = toolsByServer.values.flatten().count { it.enabled }
}

/**
 * ViewModel for MCP Tool management page
 */
class McpToolViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var state by mutableStateOf(McpToolState())
        private set

    private val _notificationEvent = MutableSharedFlow<Pair<String, String>>()
    val notificationEvent = _notificationEvent.asSharedFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        scope.launch {
            try {
                val toolConfig = ConfigManager.loadToolConfig()
                state = state.copy(servers = toolConfig.mcpServers)

                if (toolConfig.mcpServers.isNotEmpty()) {
                    discoverAllTools(toolConfig)
                }
            } catch (e: Exception) {
                println("[McpTool] Failed to load config: ${e.message}")
            }
        }
    }

    private suspend fun discoverAllTools(toolConfig: ToolConfigFile) {
        val callback = object : McpLoadingStateCallback {
            override fun onServerStateChanged(serverName: String, serverState: McpServerState) {
                state = state.copy(
                    loadingState = state.loadingState.updateServerState(serverName, serverState)
                )
                if (serverState.isLoaded) {
                    state = state.copy(
                        toolsByServer = state.toolsByServer + (serverName to serverState.tools)
                    )
                }
            }

            override fun onLoadingStateChanged(loadingState: McpLoadingState) {
                state = state.copy(loadingState = loadingState)
            }

            override fun onBuiltinToolsLoaded(tools: List<ToolItem>) {}
        }

        try {
            McpToolConfigManager.discoverMcpToolsIncremental(
                toolConfig.mcpServers,
                toolConfig.enabledMcpTools.toSet(),
                callback
            )
        } catch (e: Exception) {
            println("[McpTool] Failed to discover tools: ${e.message}")
        }
    }

    fun selectServer(serverName: String) {
        state = state.copy(
            selectedServerName = serverName,
            isConfigPaneOpen = false,
            configuringServerName = null,
            configuringServerConfig = null
        )
    }

    fun setFilterQuery(query: String) {
        state = state.copy(filterQuery = query)
    }

    fun openAddServerPane() {
        state = state.copy(
            isConfigPaneOpen = true,
            configuringServerName = null,
            configuringServerConfig = null
        )
    }

    fun openEditServerPane(serverName: String) {
        val config = state.servers[serverName]
        state = state.copy(
            isConfigPaneOpen = true,
            configuringServerName = serverName,
            configuringServerConfig = config
        )
    }

    fun closeConfigPane() {
        state = state.copy(
            isConfigPaneOpen = false,
            configuringServerName = null,
            configuringServerConfig = null
        )
    }

    fun saveServer(name: String, config: McpServerConfig) {
        scope.launch {
            try {
                val toolConfig = ConfigManager.loadToolConfig()
                val updatedServers = toolConfig.mcpServers.toMutableMap()
                // If editing and name changed, remove old entry
                val oldName = state.configuringServerName
                if (oldName != null && oldName != name) {
                    updatedServers.remove(oldName)
                }
                updatedServers[name] = config
                val updatedConfig = toolConfig.copy(mcpServers = updatedServers)
                ConfigManager.saveToolConfig(updatedConfig)

                state = state.copy(
                    servers = updatedConfig.mcpServers,
                    isConfigPaneOpen = false,
                    configuringServerName = null,
                    configuringServerConfig = null,
                    selectedServerName = name
                )

                _notificationEvent.emit("Saved" to "MCP server '$name' saved successfully")

                // Discover tools for the new/updated server
                discoverAllTools(updatedConfig)
            } catch (e: Exception) {
                _notificationEvent.emit("Error" to "Failed to save server: ${e.message}")
            }
        }
    }

    fun deleteServer(serverName: String) {
        scope.launch {
            try {
                val toolConfig = ConfigManager.loadToolConfig()
                val updatedServers = toolConfig.mcpServers.toMutableMap()
                updatedServers.remove(serverName)
                val updatedConfig = toolConfig.copy(mcpServers = updatedServers)
                ConfigManager.saveToolConfig(updatedConfig)

                state = state.copy(
                    servers = updatedConfig.mcpServers,
                    toolsByServer = state.toolsByServer - serverName,
                    selectedServerName = if (state.selectedServerName == serverName) null else state.selectedServerName
                )

                _notificationEvent.emit("Deleted" to "MCP server '$serverName' deleted")
            } catch (e: Exception) {
                _notificationEvent.emit("Error" to "Failed to delete server: ${e.message}")
            }
        }
    }

    fun toggleTool(toolName: String) {
        scope.launch {
            try {
                val toolConfig = ConfigManager.loadToolConfig()
                val enabledTools = toolConfig.enabledMcpTools.toMutableList()
                if (toolName in enabledTools) {
                    enabledTools.remove(toolName)
                } else {
                    enabledTools.add(toolName)
                }
                val updatedConfig = toolConfig.copy(enabledMcpTools = enabledTools)
                ConfigManager.saveToolConfig(updatedConfig)

                // Update local state
                val updatedToolsByServer = state.toolsByServer.mapValues { (_, tools) ->
                    tools.map { tool ->
                        if (tool.name == toolName) tool.copy(enabled = !tool.enabled) else tool
                    }
                }
                state = state.copy(toolsByServer = updatedToolsByServer)
            } catch (e: Exception) {
                _notificationEvent.emit("Error" to "Failed to toggle tool: ${e.message}")
            }
        }
    }

    fun refreshServer(serverName: String) {
        scope.launch {
            try {
                val toolConfig = ConfigManager.loadToolConfig()
                val serverConfig = toolConfig.mcpServers[serverName] ?: return@launch

                val callback = object : McpLoadingStateCallback {
                    override fun onServerStateChanged(name: String, serverState: McpServerState) {
                        state = state.copy(
                            loadingState = state.loadingState.updateServerState(name, serverState)
                        )
                        if (serverState.isLoaded) {
                            state = state.copy(
                                toolsByServer = state.toolsByServer + (name to serverState.tools)
                            )
                        }
                    }

                    override fun onLoadingStateChanged(loadingState: McpLoadingState) {
                        state = state.copy(loadingState = loadingState)
                    }

                    override fun onBuiltinToolsLoaded(tools: List<ToolItem>) {}
                }

                McpToolConfigManager.discoverMcpToolsIncremental(
                    mapOf(serverName to serverConfig),
                    toolConfig.enabledMcpTools.toSet(),
                    callback
                )
            } catch (e: Exception) {
                _notificationEvent.emit("Error" to "Failed to refresh server: ${e.message}")
            }
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
