# Boris CLI

> **Autonomous Terminal AI Agent Exclusively for Local LLMs**

**Boris** is an autonomous terminal-based AI development assistant built with Java 21, Spring AI, Lanterna (TUI), and Picocli. 

**Boris is designed exclusively for running local AI models** (such as Ollama, llama.cpp, or local OpenAI-compatible inference servers running on your machine or private network). It does **not** use or rely on third-party cloud AI APIs, ensuring complete data privacy, offline capability, zero subscription fees, and total ownership over your code and prompts.

Boris connects your local models with a rich set of developer tools to inspect codebases, execute shell commands, edit files, generate documents, and perform complex automation workflows directly from your terminal.

---

## Key Features

- 🏠 **100% Local AI Execution**: Exclusively engineered for local model runtimes (Ollama / local inference endpoints). Your code and data never leave your machine or local network.
- 🖥️ **Terminal User Interface (TUI)**: Rich interactive full-screen terminal interface powered by Lanterna, featuring Markdown rendering, scrolling, mouse capture, command menu, and live token usage tracking.
- 🧠 **Native Reasoning & `<think>` Blocks**: Real-time streaming and inspection of reasoning traces with configurable reasoning effort (`high`, `medium`, `low`, `none`) for local models with thinking capabilities (e.g., Granite 4.2, Qwen, DeepSeek).
- 🛠️ **Autonomous Tool Execution**: Built-in set of developer and system tools with sequential execution and automatic result feedback:
  - **Filesystem & Code**: Read, write, surgically edit, list, and delete files with precision.
  - **Shell & Execution**: Run shell commands, inspect output, and capture execution results.
  - **Documents**: Generate PDFs and create/inspect Office documents (Word, Excel).
  - **Web & Information**: Web search integration and system resource monitoring.
- ⚡ **Task Abort & Control**: Ability to abort running tasks, commands, and generations on demand.
- 🔒 **Zero Cloud Subscriptions**: Full autonomy and control running on your own hardware.
- ⚙️ **Automatic Initialization**: Automatically bootstraps configurations and agent rules in `~/.boris/` upon first run.

---

## Built-in Agent Tools

| Tool | Description |
| :--- | :--- |
| `read_file` | Reads contents of files in the workspace with pagination and line ranges. |
| `write_file` | Creates or replaces files with provided content. |
| `edit_file` | Surgically edits files using string replacements or line-targeted substitutions. |
| `list_files` | Explores workspace directories, file sizes, and children counts. |
| `delete_file` | Deletes files or directories safely. |
| `execute_command` | Executes shell commands in the background or synchronously with abort support. |
| `pdf_generation` | Generates formatted PDF documents from text/markdown. |
| `office_document` | Reads and writes Office documents (`.docx`, `.xlsx`). |
| `web_search` | Performs web queries and extracts relevant search results. |
| `system_info` | Retrieves OS details, CPU/memory stats, and environment variables. |

---

## Prerequisites

- **Java 21+** (JDK 21 or higher)
- **Maven 3.6+**
- **Ollama** (or any OpenAI-compatible local model server) running locally or over the network.

---

## Quick Start

### 1. Build and Run with `run.sh` (Recommended)

```bash
# Compile (skipping tests) and launch Boris CLI
./run.sh
```

**Additional execution options:**
- `./run.sh` — Compiles and launches the CLI.
- `./run.sh --run-tests` — Runs test suite before packaging and launching.
- `./run.sh -- <args>` — Passes command line arguments to Boris.

### 2. Manual Build and Run

```bash
mvn clean package
java -jar target/boris-cli-1.0.0.jar
```

---

## Configuration

On its first launch, Boris automatically creates the configuration directory `~/.boris/` along with generic default templates if they do not exist:

- **`~/.boris/settings.json`**: Primary configuration file for model endpoints, parameters, and environment.
- **`~/.boris/AGENTS.md`**: Persona instructions, execution rules, and system prompt constraints.

### Default `settings.json`

```json
{
  "model": {
    "baseUrl": "http://localhost:8080",
    "name": "granite4.2:8b",
    "options": {
      "think": "high"
    }
  },
  "env": {
    "OLLAMA_API_KEY": "ollama"
  },
  "maxHistorySize": 20,
  "enableHistory": true,
  "enforceSequentialExecution": true,
  "temperature": 0.7,
  "contextWindow": 10000,
  "thinkingEnabled": true,
  "thinkingMode": "think"
}
```

### Configuration Parameters:

- **`model.baseUrl`**: Endpoint URL for Ollama / LLM server (e.g. `http://localhost:11434` or `http://localhost:8080`).
- **`model.name`**: Name of the model (e.g. `granite4.2:8b`, `qwen3.6-35b-64k`, `deepseek-r1`, etc.).
- **`model.options`**: Model-specific options (e.g. `"think": "high"`, `"medium"`, `"low"`).
- **`env`**: Environment key-value pairs (e.g. API keys).
- **`maxHistorySize`**: Maximum number of conversation turns preserved in history.
- **`enableHistory`**: Boolean flag to enable or disable conversation history.
- **`enforceSequentialExecution`**: Forces sequential execution of tools.
- **`temperature`**: Sampling temperature (0.0 - 1.0).
- **`contextWindow`**: Token limit for the context window.
- **`thinkingEnabled`**: Toggles extraction and display of `<think>` reasoning traces.
- **`thinkingMode`**: Mode of reasoning (`think`, `low`, `medium`, `high`).

---

## Running Tests

```bash
# Run unit tests
mvn test

# Run e2e tests
mvn test -Pe2e

# Run integration tests
mvn integration-test
```

---

## Author

Created by **Anibal Gomez** — anibal@librixsoft.com
