# SAP AI Assistant — Eclipse ADT Plugin

An AI-powered assistant for SAP ABAP development inside Eclipse ADT. Think "Cursor for SAP" — a ChatGPT-style chat panel that understands your open ABAP code, can search objects, read/write source, run syntax checks and ATC, all from within Eclipse.

## Features

- **Context-aware** — Automatically knows which ABAP object is open, cursor position, selection, and current errors
- **Full SAP access** — 17 ADT tools for search, read, write, lock, activate, syntax check, ATC, create objects
- **Multi-provider LLM** — Anthropic Claude, OpenAI GPT, Google Gemini, Mistral
- **Zero infrastructure** — Direct HTTP calls from the plugin, no backend server needed
- **ADT integration** — Reuses your existing Eclipse ADT connections

## Architecture

```
Eclipse IDE
├── ADT Plugin (existing) — ABAP editors, project explorer
└── AI Assistant Plugin (this)
    ├── Chat View (SWT panel)
    ├── Context Collector (active editor, cursor, errors)
    ├── LLM Client (direct HTTP to Claude/GPT/Gemini/Mistral)
    ├── SAP Tool Executor (direct HTTP to ADT REST endpoints)
    └── Agent Loop (send → tool calls → execute → repeat)
```

## SAP Tools

| Tool | Description |
|------|-------------|
| `sap_search_object` | Search ABAP objects by name pattern |
| `sap_get_source` | Read source code |
| `sap_set_source` | Write source code |
| `sap_object_structure` | Get object metadata and includes |
| `sap_node_contents` | Browse package contents |
| `sap_lock` / `sap_unlock` | Lock/unlock objects for editing |
| `sap_activate` | Activate objects after changes |
| `sap_syntax_check` | Check ABAP syntax |
| `sap_atc_run` | Run ATC/Clean Core checks |
| `sap_create_object` | Create new ABAP objects |
| `sap_write_and_check` | Composite: create → lock → write → syntax check |
| `sap_transport_info` | Get transport information |
| `sap_find_definition` | Find definition location |
| `sap_usage_references` | Where-used list |

## Prerequisites

- Eclipse 2024-03 or later
- SAP ADT (ABAP Development Tools) installed
- Java 17+
- Maven 3.9+ (for building)
- An API key for your preferred LLM provider

## Build

```bash
mvn clean verify
```

The update site is produced at `com.sap.ai.assistant.site/target/repository/`.

## Install

1. In Eclipse: **Help > Install New Software...**
2. Click **Add > Local...** and browse to `com.sap.ai.assistant.site/target/repository/`
3. Select **SAP AI Assistant** and complete the wizard
4. Restart Eclipse

## Setup

1. **Window > Preferences > SAP AI Assistant**
2. Select your LLM provider (Anthropic, OpenAI, Google, Mistral)
3. Enter your API key
4. Optionally change the model name

## Usage

1. **Window > Show View > Other... > SAP AI Assistant**
2. Select a SAP system from the dropdown (auto-discovered from ADT projects)
3. Start chatting — the AI knows your currently open ABAP object

### Example prompts:
- "Explain this code"
- "Find all programs starting with Z_TEST"
- "Show me the source of ZMY_REPORT"
- "Create a new report ZAI_TEST that reads from SFLIGHT"
- "Run ATC checks on this object and fix the findings"
- "Refactor this method to use clean ABAP"

## Technology

- **Java 17** — Plugin language
- **Maven Tycho** — Eclipse plugin build system
- **java.net.http.HttpClient** — Built-in HTTP (no extra dependencies)
- **Gson** — JSON parsing (embedded in bundle)
- **SWT** — Native Eclipse UI toolkit

## License

MIT
