/**
 * MCP Configuration Panel Component
 *
 * A panel with left sidebar (server list) and right detail view (server config + tools).
 * Communicates with extension via postMessage for reading/writing MCP configuration.
 */

import React, { useState, useEffect, useCallback } from 'react';
import { useVSCode, ExtensionMessage } from '../hooks/useVSCode';
import './McpConfigPanel.css';

/** Represents a single MCP server configuration */
export interface McpServerEntry {
  name: string;
  command?: string;
  url?: string;
  args?: string[];
  disabled?: boolean;
  env?: Record<string, string>;
  autoApprove?: string[];
  timeout?: number;
  trust?: boolean;
  headers?: Record<string, string>;
  cwd?: string;
}

/** Tool info discovered from an MCP server */
interface McpToolEntry {
  name: string;
  description: string;
}

interface McpConfigPanelProps {
  isOpen: boolean;
  onClose: () => void;
}

export const McpConfigPanel: React.FC<McpConfigPanelProps> = ({ isOpen, onClose }) => {
  const [servers, setServers] = useState<McpServerEntry[]>([]);
  const [selectedServerName, setSelectedServerName] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [tools, setTools] = useState<McpToolEntry[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);

  // Edit form state
  const [editName, setEditName] = useState('');
  const [editTransport, setEditTransport] = useState<'stdio' | 'network'>('stdio');
  const [editCommand, setEditCommand] = useState('');
  const [editUrl, setEditUrl] = useState('');
  const [editArgs, setEditArgs] = useState('');
  const [editEnv, setEditEnv] = useState('');
  const [editDisabled, setEditDisabled] = useState(false);
  const [editCwd, setEditCwd] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isNewServer, setIsNewServer] = useState(false);

  const { postMessage, onMessage } = useVSCode();

  // Request MCP config when panel opens
  useEffect(() => {
    if (isOpen) {
      postMessage({ type: 'action', action: 'getMcpConfig' });
    }
  }, [isOpen, postMessage]);

  // Listen for messages from extension
  useEffect(() => {
    return onMessage((msg: ExtensionMessage) => {
      if (msg.type === 'mcpConfigUpdate' && msg.data) {
        const serverList = msg.data.servers as McpServerEntry[] || [];
        setServers(serverList);
        // If selected server no longer exists, clear selection
        if (selectedServerName && !serverList.find(s => s.name === selectedServerName)) {
          setSelectedServerName(null);
          setIsEditing(false);
        }
      }
      if (msg.type === 'mcpToolsResult' && msg.data) {
        setTools(msg.data.tools as McpToolEntry[] || []);
        setToolsLoading(false);
      }
    });
  }, [onMessage, selectedServerName]);

  const selectedServer = servers.find(s => s.name === selectedServerName) || null;

  // Load tools when a server is selected
  useEffect(() => {
    if (selectedServerName && !isEditing) {
      const server = servers.find(s => s.name === selectedServerName);
      if (server && !server.disabled) {
        setToolsLoading(true);
        setTools([]);
        postMessage({ type: 'action', action: 'getMcpServerTools', data: { serverName: selectedServerName } });
      } else {
        setTools([]);
      }
    }
  }, [selectedServerName, isEditing, servers, postMessage]);

  const handleSelectServer = useCallback((name: string) => {
    setSelectedServerName(name);
    setIsEditing(false);
    setErrors({});
  }, []);

  const handleAddServer = useCallback(() => {
    setIsNewServer(true);
    setIsEditing(true);
    setSelectedServerName(null);
    setEditName('');
    setEditTransport('stdio');
    setEditCommand('');
    setEditUrl('');
    setEditArgs('');
    setEditEnv('');
    setEditDisabled(false);
    setEditCwd('');
    setErrors({});
    setTools([]);
  }, []);

  const handleEditServer = useCallback(() => {
    if (!selectedServer) return;
    setIsNewServer(false);
    setIsEditing(true);
    setEditName(selectedServer.name);
    setEditTransport(selectedServer.command ? 'stdio' : 'network');
    setEditCommand(selectedServer.command || '');
    setEditUrl(selectedServer.url || '');
    setEditArgs((selectedServer.args || []).join(' '));
    setEditEnv(selectedServer.env ? Object.entries(selectedServer.env).map(([k, v]) => `${k}=${v}`).join('\n') : '');
    setEditDisabled(selectedServer.disabled || false);
    setEditCwd(selectedServer.cwd || '');
    setErrors({});
  }, [selectedServer]);

  const handleDeleteServer = useCallback(() => {
    if (!selectedServerName) return;
    postMessage({ type: 'action', action: 'deleteMcpServer', data: { serverName: selectedServerName } });
    setSelectedServerName(null);
    setIsEditing(false);
  }, [selectedServerName, postMessage]);

  const handleToggleServer = useCallback(() => {
    if (!selectedServer) return;
    postMessage({
      type: 'action',
      action: 'toggleMcpServer',
      data: { serverName: selectedServer.name, disabled: !selectedServer.disabled }
    });
  }, [selectedServer, postMessage]);

  const handleSave = useCallback(() => {
    const newErrors: Record<string, string> = {};
    if (!editName.trim()) {
      newErrors.name = 'Server name is required';
    }
    if (editTransport === 'stdio' && !editCommand.trim()) {
      newErrors.command = 'Command is required for stdio transport';
    }
    if (editTransport === 'network' && !editUrl.trim()) {
      newErrors.url = 'URL is required for network transport';
    }
    // Check for duplicate name on new server
    if (isNewServer && servers.some(s => s.name === editName.trim())) {
      newErrors.name = 'A server with this name already exists';
    }

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    // Parse env vars
    const envMap: Record<string, string> = {};
    if (editEnv.trim()) {
      editEnv.trim().split('\n').forEach(line => {
        const eqIdx = line.indexOf('=');
        if (eqIdx > 0) {
          envMap[line.substring(0, eqIdx).trim()] = line.substring(eqIdx + 1).trim();
        }
      });
    }

    // Parse args
    const argsList = editArgs.trim() ? editArgs.trim().split(/\s+/) : [];

    const serverData: Record<string, unknown> = {
      serverName: editName.trim(),
      command: editTransport === 'stdio' ? editCommand.trim() : undefined,
      url: editTransport === 'network' ? editUrl.trim() : undefined,
      args: argsList,
      disabled: editDisabled,
      env: Object.keys(envMap).length > 0 ? envMap : undefined,
      cwd: editCwd.trim() || undefined,
      isNew: isNewServer,
      originalName: isNewServer ? undefined : selectedServerName,
    };

    postMessage({ type: 'action', action: 'saveMcpServer', data: serverData });
    setIsEditing(false);
    setSelectedServerName(editName.trim());
  }, [editName, editTransport, editCommand, editUrl, editArgs, editEnv, editDisabled, editCwd, isNewServer, selectedServerName, servers, postMessage]);

  const handleCancelEdit = useCallback(() => {
    setIsEditing(false);
    if (isNewServer) {
      setSelectedServerName(null);
    }
    setErrors({});
  }, [isNewServer]);

  if (!isOpen) return null;

  return (
    <div className="mcp-panel-overlay" onClick={onClose}>
      <div className="mcp-panel" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="mcp-panel-header">
          <h2>MCP Servers</h2>
          <button className="mcp-panel-close" onClick={onClose}>x</button>
        </div>

        <div className="mcp-panel-body">
          {/* Left Panel - Server List */}
          <div className="mcp-server-list">
            <div className="mcp-server-list-header">
              <span>Servers</span>
              <button className="mcp-server-add-btn" onClick={handleAddServer} title="Add MCP Server">+</button>
            </div>
            <div className="mcp-server-items">
              {servers.length === 0 && !isEditing ? (
                <div className="mcp-server-empty">
                  <span>No MCP servers configured</span>
                  <span>Click + to add one</span>
                </div>
              ) : (
                servers.map(server => (
                  <div
                    key={server.name}
                    className={`mcp-server-item ${selectedServerName === server.name && !isNewServer ? 'selected' : ''}`}
                    onClick={() => handleSelectServer(server.name)}
                  >
                    <div className={`mcp-server-status ${server.disabled ? 'disabled' : 'enabled'}`} />
                    <div className="mcp-server-item-info">
                      <div className="mcp-server-item-name">{server.name}</div>
                      <div className="mcp-server-item-type">
                        {server.command ? 'stdio' : 'network'}
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Right Panel - Detail View */}
          <div className="mcp-detail">
            {!isEditing && !selectedServer ? (
              <div className="mcp-detail-empty">
                <span>Select a server to view details</span>
                <span>or click + to add a new one</span>
              </div>
            ) : isEditing ? (
              /* Edit/Add Form */
              <>
                <div className="mcp-detail-header">
                  <div className="mcp-detail-header-left">
                    <h3>{isNewServer ? 'Add Server' : 'Edit Server'}</h3>
                  </div>
                </div>
                <div className="mcp-detail-content">
                  <div className="mcp-form-field">
                    <label>Server Name</label>
                    <input
                      type="text"
                      value={editName}
                      onChange={e => { setEditName(e.target.value); if (errors.name) setErrors({ ...errors, name: '' }); }}
                      placeholder="e.g., filesystem, github"
                      className={errors.name ? 'error' : ''}
                      disabled={!isNewServer}
                    />
                    {errors.name && <div className="mcp-error-message">{errors.name}</div>}
                  </div>

                  <div className="mcp-form-field">
                    <label>Transport</label>
                    <select value={editTransport} onChange={e => setEditTransport(e.target.value as 'stdio' | 'network')}>
                      <option value="stdio">Stdio (Command)</option>
                      <option value="network">Network (URL)</option>
                    </select>
                  </div>

                  {editTransport === 'stdio' ? (
                    <>
                      <div className="mcp-form-field">
                        <label>Command</label>
                        <input
                          type="text"
                          value={editCommand}
                          onChange={e => { setEditCommand(e.target.value); if (errors.command) setErrors({ ...errors, command: '' }); }}
                          placeholder="e.g., npx, node, python"
                          className={errors.command ? 'error' : ''}
                        />
                        {errors.command && <div className="mcp-error-message">{errors.command}</div>}
                      </div>
                      <div className="mcp-form-field">
                        <label>Arguments</label>
                        <input
                          type="text"
                          value={editArgs}
                          onChange={e => setEditArgs(e.target.value)}
                          placeholder="e.g., -y @anthropic/mcp-server-filesystem"
                        />
                        <div className="field-hint">Space-separated arguments</div>
                      </div>
                      <div className="mcp-form-field">
                        <label>Working Directory</label>
                        <input
                          type="text"
                          value={editCwd}
                          onChange={e => setEditCwd(e.target.value)}
                          placeholder="Optional working directory"
                        />
                      </div>
                    </>
                  ) : (
                    <div className="mcp-form-field">
                      <label>URL</label>
                      <input
                        type="text"
                        value={editUrl}
                        onChange={e => { setEditUrl(e.target.value); if (errors.url) setErrors({ ...errors, url: '' }); }}
                        placeholder="e.g., http://localhost:3000/sse"
                        className={errors.url ? 'error' : ''}
                      />
                      {errors.url && <div className="mcp-error-message">{errors.url}</div>}
                    </div>
                  )}

                  <div className="mcp-form-field">
                    <label>Environment Variables</label>
                    <textarea
                      value={editEnv}
                      onChange={e => setEditEnv(e.target.value)}
                      placeholder="KEY=VALUE (one per line)"
                      rows={3}
                    />
                    <div className="field-hint">One KEY=VALUE pair per line</div>
                  </div>

                  <div className="mcp-form-toggle">
                    <input
                      type="checkbox"
                      id="mcp-disabled"
                      checked={editDisabled}
                      onChange={e => setEditDisabled(e.target.checked)}
                    />
                    <label htmlFor="mcp-disabled">Disabled</label>
                  </div>
                </div>
                <div className="mcp-detail-footer">
                  <button className="mcp-btn mcp-btn-secondary" onClick={handleCancelEdit}>Cancel</button>
                  <button className="mcp-btn mcp-btn-primary" onClick={handleSave}>Save</button>
                </div>
              </>
            ) : selectedServer ? (
              /* Detail View */
              <>
                <div className="mcp-detail-header">
                  <div className="mcp-detail-header-left">
                    <div className={`mcp-server-status ${selectedServer.disabled ? 'disabled' : 'enabled'}`} />
                    <h3>{selectedServer.name}</h3>
                  </div>
                  <div className="mcp-detail-actions">
                    <button className="mcp-detail-action-btn" onClick={handleToggleServer} title={selectedServer.disabled ? 'Enable' : 'Disable'}>
                      {selectedServer.disabled ? 'Enable' : 'Disable'}
                    </button>
                    <button className="mcp-detail-action-btn" onClick={handleEditServer} title="Edit">Edit</button>
                    <button className="mcp-detail-action-btn danger" onClick={handleDeleteServer} title="Delete">Delete</button>
                  </div>
                </div>
                <div className="mcp-detail-content">
                  {/* Server Info */}
                  <div className="mcp-form-field">
                    <label>Transport</label>
                    <div style={{ fontSize: 13, color: 'var(--vscode-foreground)' }}>
                      {selectedServer.command ? 'Stdio' : 'Network'}
                    </div>
                  </div>
                  {selectedServer.command && (
                    <div className="mcp-form-field">
                      <label>Command</label>
                      <div style={{ fontSize: 13, fontFamily: 'var(--vscode-editor-font-family, monospace)', color: 'var(--vscode-foreground)' }}>
                        {selectedServer.command} {(selectedServer.args || []).join(' ')}
                      </div>
                    </div>
                  )}
                  {selectedServer.url && (
                    <div className="mcp-form-field">
                      <label>URL</label>
                      <div style={{ fontSize: 13, fontFamily: 'var(--vscode-editor-font-family, monospace)', color: 'var(--vscode-foreground)' }}>
                        {selectedServer.url}
                      </div>
                    </div>
                  )}
                  {selectedServer.cwd && (
                    <div className="mcp-form-field">
                      <label>Working Directory</label>
                      <div style={{ fontSize: 13, color: 'var(--vscode-foreground)' }}>
                        {selectedServer.cwd}
                      </div>
                    </div>
                  )}
                  {selectedServer.env && Object.keys(selectedServer.env).length > 0 && (
                    <div className="mcp-form-field">
                      <label>Environment Variables</label>
                      <div style={{ fontSize: 12, fontFamily: 'var(--vscode-editor-font-family, monospace)', color: 'var(--vscode-descriptionForeground)' }}>
                        {Object.entries(selectedServer.env).map(([k, v]) => (
                          <div key={k}>{k}={v.length > 8 ? v.substring(0, 4) + '****' : '****'}</div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Tools Section */}
                  <div className="mcp-tools-section">
                    <div className="mcp-tools-header">
                      <span>Tools</span>
                      {tools.length > 0 && <span className="mcp-tools-badge">{tools.length}</span>}
                    </div>
                    {selectedServer.disabled ? (
                      <div className="mcp-tools-empty">Server is disabled. Enable it to discover tools.</div>
                    ) : toolsLoading ? (
                      <div className="mcp-tools-loading">
                        <div className="spinner" />
                        <span>Discovering tools...</span>
                      </div>
                    ) : tools.length === 0 ? (
                      <div className="mcp-tools-empty">No tools discovered</div>
                    ) : (
                      <div className="mcp-tool-list">
                        {tools.map(tool => (
                          <div key={tool.name} className="mcp-tool-item">
                            <div className="mcp-tool-item-info">
                              <div className="mcp-tool-name">{tool.name}</div>
                              {tool.description && <div className="mcp-tool-desc">{tool.description}</div>}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
};
