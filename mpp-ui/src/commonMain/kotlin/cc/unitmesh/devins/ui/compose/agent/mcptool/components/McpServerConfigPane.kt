package cc.unitmesh.devins.ui.compose.agent.mcptool.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.mcp.McpServerConfig

/**
 * MCP Server Config Pane - Right side panel for adding/editing MCP server configurations
 */
@Composable
fun McpServerConfigPane(
    existingName: String?,
    existingConfig: McpServerConfig?,
    onCancel: () -> Unit,
    onSave: (String, McpServerConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditing = existingName != null

    var name by remember(existingName) { mutableStateOf(existingName ?: "") }
    var transportType by remember(existingConfig) {
        mutableStateOf(
            when {
                existingConfig?.isNetworkTransport() == true -> "network"
                else -> "stdio"
            }
        )
    }
    var command by remember(existingConfig) { mutableStateOf(existingConfig?.command ?: "") }
    var args by remember(existingConfig) { mutableStateOf(existingConfig?.args?.joinToString("\n") ?: "") }
    var url by remember(existingConfig) { mutableStateOf(existingConfig?.url ?: "") }
    var envText by remember(existingConfig) {
        mutableStateOf(
            existingConfig?.env?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: ""
        )
    }
    var cwd by remember(existingConfig) { mutableStateOf(existingConfig?.cwd ?: "") }
    var disabled by remember(existingConfig) { mutableStateOf(existingConfig?.disabled ?: false) }
    var trust by remember(existingConfig) { mutableStateOf(existingConfig?.trust ?: false) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            text = if (isEditing) "Edit MCP Server" else "Add MCP Server",
            style = MaterialTheme.typography.titleMedium
        )

        // Server name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Server Name") },
            placeholder = { Text("e.g., filesystem, github") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )

        // Transport type selector
        Text(
            text = "Transport Type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = transportType == "stdio",
                onClick = { transportType = "stdio" },
                label = { Text("Stdio (Command)") }
            )
            FilterChip(
                selected = transportType == "network",
                onClick = { transportType = "network" },
                label = { Text("Network (URL)") }
            )
        }

        // Transport-specific fields
        if (transportType == "stdio") {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("Command") },
                placeholder = { Text("e.g., npx, node, python") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
            OutlinedTextField(
                value = args,
                onValueChange = { args = it },
                label = { Text("Arguments (one per line)") },
                placeholder = { Text("e.g., -y\n@modelcontextprotocol/server-filesystem\n/tmp") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                shape = RoundedCornerShape(8.dp),
                minLines = 3
            )
        } else {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("e.g., http://localhost:3000/sse") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )
        }

        // Environment variables
        OutlinedTextField(
            value = envText,
            onValueChange = { envText = it },
            label = { Text("Environment Variables (KEY=VALUE, one per line)") },
            placeholder = { Text("e.g., GITHUB_TOKEN=your-token") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
            shape = RoundedCornerShape(8.dp),
            minLines = 2
        )

        // Advanced settings
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide Advanced Settings" else "Show Advanced Settings")
        }

        if (showAdvanced) {
            if (transportType == "stdio") {
                OutlinedTextField(
                    value = cwd,
                    onValueChange = { cwd = it },
                    label = { Text("Working Directory") },
                    placeholder = { Text("e.g., /home/user/project") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = disabled,
                    onCheckedChange = { disabled = it }
                )
                Text("Disabled", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = trust,
                    onCheckedChange = { trust = it }
                )
                Text("Trust (skip confirmation for all tools)", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val envMap = envText.lines()
                        .filter { it.contains("=") }
                        .associate {
                            val parts = it.split("=", limit = 2)
                            parts[0].trim() to parts.getOrElse(1) { "" }.trim()
                        }
                        .ifEmpty { null }

                    val argsList = args.lines().filter { it.isNotBlank() }

                    val config = if (transportType == "stdio") {
                        McpServerConfig(
                            command = command.ifBlank { null },
                            args = argsList,
                            disabled = disabled,
                            env = envMap,
                            trust = trust,
                            cwd = cwd.ifBlank { null }
                        )
                    } else {
                        McpServerConfig(
                            url = url.ifBlank { null },
                            disabled = disabled,
                            env = envMap,
                            trust = trust
                        )
                    }

                    onSave(name, config)
                },
                enabled = name.isNotBlank() && (
                    (transportType == "stdio" && command.isNotBlank()) ||
                    (transportType == "network" && url.isNotBlank())
                )
            ) {
                Text("Save")
            }
        }
    }
}
