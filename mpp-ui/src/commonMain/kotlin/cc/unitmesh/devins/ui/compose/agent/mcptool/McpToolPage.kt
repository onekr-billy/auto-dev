package cc.unitmesh.devins.ui.compose.agent.mcptool

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import cc.unitmesh.agent.Platform
import cc.unitmesh.devins.ui.base.ResizableSplitPane
import cc.unitmesh.devins.ui.compose.agent.AgentTopAppBar
import cc.unitmesh.devins.ui.compose.agent.mcptool.components.*
import kotlinx.coroutines.flow.collectLatest

/**
 * MCP Tool Page - Main page for MCP server and tool management
 *
 * Left side: MCP server list with status indicators (resizable)
 * Right side: Server config editor (when adding/editing) or tool list display
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpToolPage(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onNotification: (String, String) -> Unit = { _, _ -> }
) {
    val viewModel = remember { McpToolViewModel() }
    val state = viewModel.state

    // Collect notifications
    LaunchedEffect(viewModel) {
        viewModel.notificationEvent.collectLatest { (title, message) ->
            onNotification(title, message)
        }
    }

    // Cleanup on dispose
    DisposableEffect(viewModel) {
        onDispose {
            viewModel.dispose()
        }
    }

    val notMobile = (Platform.isAndroid || Platform.isIOS).not()
    Scaffold(
        modifier = modifier,
        topBar = {
            if (notMobile) {
                AgentTopAppBar(
                    title = "MCP Tools",
                    subtitle = "${state.totalToolCount} tools from ${state.servers.size} servers",
                    onBack = onBack
                )
            }
        }
    ) { paddingValues ->
        ResizableSplitPane(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            initialSplitRatio = 0.25f,
            minRatio = 0.15f,
            maxRatio = 0.4f,
            saveKey = "mcp_tool_split_ratio",
            first = {
                // Left panel - MCP Server management
                McpServerPanel(
                    servers = state.servers,
                    filteredServerNames = state.filteredServerNames,
                    selectedServerName = state.selectedServerName,
                    loadingState = state.loadingState,
                    toolCounts = state.toolsByServer.mapValues { it.value.size },
                    filterQuery = state.filterQuery,
                    onFilterChange = viewModel::setFilterQuery,
                    onSelectServer = viewModel::selectServer,
                    onAddClick = viewModel::openAddServerPane,
                    onEditClick = viewModel::openEditServerPane,
                    onDeleteClick = viewModel::deleteServer,
                    onRefreshClick = viewModel::refreshServer,
                    modifier = Modifier.fillMaxSize()
                )
            },
            second = {
                // Right panel - Config pane or Tool display
                if (state.isConfigPaneOpen) {
                    McpServerConfigPane(
                        existingName = state.configuringServerName,
                        existingConfig = state.configuringServerConfig,
                        onCancel = viewModel::closeConfigPane,
                        onSave = viewModel::saveServer,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (state.selectedServerName != null) {
                    McpToolDisplayPane(
                        serverName = state.selectedServerName!!,
                        tools = state.selectedServerTools,
                        serverState = state.loadingState.getServerState(state.selectedServerName!!),
                        onToggleTool = viewModel::toggleTool,
                        onRefreshServer = { viewModel.refreshServer(state.selectedServerName!!) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    McpToolEmptyState(modifier = Modifier.fillMaxSize())
                }
            }
        )
    }
}
