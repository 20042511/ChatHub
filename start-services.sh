#!/bin/bash
set -e
mkdir -p /tmp/chathub-logs

echo "Building services..."
cd bff/services
for d in llm-router agent-gateway chat-service mcp-proxy; do
  (cd $d && go build -o /tmp/chathub-$d . && echo "✓ $d")
done

echo "Starting services..."
cd /tmp
PORT=8084 ./chathub-mcp-proxy > /tmp/chathub-logs/mcp.log 2>&1 &
PORT=8085 ./chathub-llm-router > /tmp/chathub-logs/llm.log 2>&1 &
sleep 1
PORT=8083 ./chathub-agent-gateway > /tmp/chathub-logs/agw.log 2>&1 &
PORT=8082 ./chathub-chat-service > /tmp/chathub-logs/chat.log 2>&1 &

sleep 2
echo ""
echo "✓ Services running:"
echo "  Chat Service:    http://localhost:8082"
echo "  Agent Gateway:   http://localhost:8083"
echo "  MCP Proxy:       http://localhost:8084"
echo "  LLM Router:      http://localhost:8085"
echo ""
echo "Health check:"
curl -s http://localhost:8082/health && echo ""
echo ""
echo "Logs: /tmp/chathub-logs/*.log"
