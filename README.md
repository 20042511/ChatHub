# ChatHub

A multi-agent chat application with built-in ReAct engine and MCP integration.

[![Open in Gitpod](https://gitpod.io/button/open-in-gitpod.svg)](https://gitpod.io/#https://github.com/20042511/chathub)
[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://github.com/20042511/chathub/codespaces)

## Quick Start (Cloud)

**Option 1: Gitpod** (Recommended)
1. Click the "Open in Gitpod" badge above
2. Wait ~2 minutes for services to build and start
3. Gitpod will show 4 preview URLs (port 8082 is the main API)
4. Test: `curl $(gp url 8082)/health`

**Option 2: GitHub Codespaces**
1. Click "Open in GitHub Codespaces"
2. After container starts, run: `./start-services.sh`
3. Click "Ports" tab to see forwarded URLs

## Quick Start (Local)

Requires: Go 1.22+

```bash
./start-services.sh
curl http://localhost:8082/health
```

## Architecture

```
Flutter App (port TBD)
    ↓
Chat Service :8082 ──→ Agent Gateway :8083 ──┬→ LLM Router :8085 (OpenAI/Anthropic/Gemini)
                                              └→ MCP Proxy :8084 (Tool registry)
```

- **Chat Service**: Conversation CRUD, message bridging
- **Agent Gateway**: Built-in ReAct engine, SSE streaming, task lifecycle
- **LLM Router**: Multi-provider routing (OpenAI/Anthropic/Gemini) with mock fallback
- **MCP Proxy**: MCP server registry and tool call aggregation

## API Example

```bash
# Create conversation
curl -X POST http://localhost:8082/conversations

# Send message (returns taskId + streamUrl)
curl -X POST http://localhost:8082/conversations/{id}/messages \
  -d '{"content":"summarize the repo layout"}'

# Stream agent task events (SSE)
curl -N http://localhost:8083/agents/engine/tasks/{taskId}/stream
```

SSE events: `thinking` → `tool_call` → `tool_result` → `answer` → `completed`

## Development

See [HANDOFF.md](HANDOFF.md) for:
- Complete setup guide
- API contracts
- Known issues & workarounds
- Architecture decisions

## CI Status

CI runs: gofmt check → build all services → vet → smoke test (mock mode)

Mock mode: Services run without API keys (returns `[mock:provider] ...` responses)

## License

MIT
