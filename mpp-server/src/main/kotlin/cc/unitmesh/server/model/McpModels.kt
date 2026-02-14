package cc.unitmesh.server.model

import cc.unitmesh.agent.config.ToolItem
import cc.unitmesh.agent.mcp.McpServerConfig
import cc.unitmesh.agent.mcp.McpServerStatus
import kotlinx.serialization.Serializable

@Serializable
data class McpServerListResponse(
    val servers: Map<String, McpServerConfig>
)

@Serializable
data class McpServerSaveRequest(
    val name: String,
    val config: McpServerConfig
)

@Serializable
data class McpToolListResponse(
    val tools: Map<String, List<ToolItem>>
)

@Serializable
data class McpToolToggleRequest(
    val toolName: String,
    val enabled: Boolean
)

@Serializable
data class McpToolExecuteRequest(
    val serverName: String,
    val toolName: String,
    val arguments: String = "{}"
)

@Serializable
data class McpToolExecuteResponse(
    val result: String,
    val success: Boolean = true,
    val error: String? = null
)

@Serializable
data class McpServerStatusResponse(
    val statuses: Map<String, McpServerStatus>
)
