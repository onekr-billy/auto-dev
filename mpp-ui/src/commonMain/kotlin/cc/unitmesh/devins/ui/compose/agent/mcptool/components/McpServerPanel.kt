package cc.unitmesh.devins.ui.compose.agent.mcptool.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.config.McpServerLoadingStatus
import cc.unitmesh.agent.config.McpLoadingState
import cc.unitmesh.agent.mcp.McpServerConfig
import cc.unitmesh.devins.ui.compose.icons.AutoDevComposeIcons

/**
 * MCP Server Panel - Left side panel for managing MCP servers
 *
 * Displays list of MCP servers with connection status indicators,
 * search/filter, and add/edit/delete controls.
 */
@Composable
fun McpServerPanel(
    servers: Map<String, McpServerConfig>,
    filteredServerNames: List<String>,
    selectedServerName: String?,
    loadingState: McpLoadingState,
    toolCounts: Map<String, Int>,
    filterQuery: String,
    onFilterChange: (String) -> Unit,
    onSelectServer: (String) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onRefreshClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        // Header
        McpServerHeader(
            serverCount = servers.size,
            onAddClick = onAddClick
        )

        HorizontalDivider()

        // Search/Filter
        McpSearchField(
            query = filterQuery,
            onQueryChange = onFilterChange,
            modifier = Modifier.padding(8.dp)
        )

        // Server list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(filteredServerNames, key = { it }) { serverName ->
                val serverConfig = servers[serverName] ?: return@items
                val serverLoadingState = loadingState.getServerState(serverName)
                val toolCount = toolCounts[serverName] ?: 0

                McpServerItem(
                    serverName = serverName,
                    serverConfig = serverConfig,
                    isSelected = serverName == selectedServerName,
                    loadingStatus = serverLoadingState?.status,
                    toolCount = toolCount,
                    onSelect = { onSelectServer(serverName) },
                    onEditClick = { onEditClick(serverName) },
                    onDeleteClick = { onDeleteClick(serverName) },
                    onRefreshClick = { onRefreshClick(serverName) }
                )
            }
        }

        // Summary footer
        if (servers.isNotEmpty()) {
            HorizontalDivider()
            McpServerSummary(
                totalServers = servers.size,
                loadingState = loadingState,
                totalTools = toolCounts.values.sum()
            )
        }
    }
}

@Composable
private fun McpServerHeader(
    serverCount: Int,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "MCP Servers",
                style = MaterialTheme.typography.titleMedium
            )
            if (serverCount > 0) {
                Text(
                    text = "$serverCount server(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = onAddClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                AutoDevComposeIcons.Add,
                contentDescription = "Add MCP server",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun McpSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search servers...", style = MaterialTheme.typography.bodySmall) },
        leadingIcon = {
            Icon(
                AutoDevComposeIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(AutoDevComposeIcons.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.bodySmall,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun McpServerItem(
    serverName: String,
    serverConfig: McpServerConfig,
    isSelected: Boolean,
    loadingStatus: McpServerLoadingStatus?,
    toolCount: Int,
    onSelect: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (loadingStatus) {
                            McpServerLoadingStatus.LOADED -> MaterialTheme.colorScheme.primary
                            McpServerLoadingStatus.LOADING -> MaterialTheme.colorScheme.tertiary
                            McpServerLoadingStatus.ERROR -> MaterialTheme.colorScheme.error
                            McpServerLoadingStatus.DISABLED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        }
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Transport type
                    Text(
                        text = when {
                            serverConfig.isStdioTransport() -> serverConfig.command ?: "stdio"
                            serverConfig.isNetworkTransport() -> serverConfig.url ?: "network"
                            else -> "unknown"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Status text
                    when (loadingStatus) {
                        McpServerLoadingStatus.LOADED -> {
                            Text(
                                text = "$toolCount tools",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        McpServerLoadingStatus.LOADING -> {
                            Text(
                                text = "Loading...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        McpServerLoadingStatus.ERROR -> {
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        McpServerLoadingStatus.DISABLED -> {
                            Text(
                                text = "Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        else -> {}
                    }
                }
            }

            // Loading indicator
            if (loadingStatus == McpServerLoadingStatus.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        AutoDevComposeIcons.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEditClick()
                        },
                        leadingIcon = {
                            Icon(AutoDevComposeIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        onClick = {
                            showMenu = false
                            onRefreshClick()
                        },
                        leadingIcon = {
                            Icon(AutoDevComposeIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDeleteClick()
                        },
                        leadingIcon = {
                            Icon(
                                AutoDevComposeIcons.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun McpServerSummary(
    totalServers: Int,
    loadingState: McpLoadingState,
    totalTools: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$totalServers servers, $totalTools tools",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (loadingState.loadingServers.isNotEmpty()) {
            Text(
                text = "Loading: ${loadingState.loadingServers.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
