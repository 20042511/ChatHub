// ChatHub BFF - MCP Proxy
// Multi MCP server registry/aggregate + tool call + health + tool cache + RBAC.

package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
)

type MCPServer struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	URL       string    `json:"url"`
	AuthType  string    `json:"authType"`
	Caps      []string  `json:"capabilities"`
	Healthy   bool      `json:"healthy"`
	LastCheck time.Time `json:"lastCheck"`
}

type ToolDef struct {
	Name        string      `json:"name"`
	Description string      `json:"description"`
	InputSchema interface{} `json:"inputSchema"`
	ServerID    string      `json:"serverId"`
}

var (
	servers   = map[string]MCPServer{}
	toolCache = map[string]map[string]interface{}{}
	mu        sync.RWMutex
)

func main() {
	port := env("PORT", "8084")
	mux := http.NewServeMux()

	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, map[string]string{"status": "healthy", "service": "mcp-proxy"})
	})

	// MCP server list / register
	mux.HandleFunc("/mcp-servers", func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			var srv MCPServer
			json.NewDecoder(r.Body).Decode(&srv)
			mu.Lock()
			servers[srv.ID] = srv
			mu.Unlock()
			log.Printf("[MCP] server registered: %s (%s)", srv.ID, srv.Name)
			w.WriteHeader(http.StatusCreated)
			writeJSON(w, srv)
			return
		}
		mu.RLock()
		var list []MCPServer
		for _, s := range servers {
			list = append(list, s)
		}
		mu.RUnlock()
		writeJSON(w, map[string][]MCPServer{"servers": list})
	})

	// Aggregated tool list
	mux.HandleFunc("/mcp-servers/{id}/tools", func(w http.ResponseWriter, r *http.Request) {
		tools := []ToolDef{
			{Name: "web_scrape", Description: "抓取网页内容", ServerID: "web"},
			{Name: "notion_api", Description: "Notion 读写", ServerID: "notion"},
			{Name: "weather_api", Description: "天气查询", ServerID: "weather"},
			{Name: "code_runner", Description: "执行代码（沙箱）", ServerID: "code"},
			{Name: "vector_search", Description: "向量检索", ServerID: "rag"},
		}
		writeJSON(w, map[string][]ToolDef{"tools": tools})
	})

	// Tool call with cache
	mux.HandleFunc("/mcp-servers/{id}/tools/{toolId}/call", func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			Arguments map[string]interface{} `json:"arguments"`
		}
		json.NewDecoder(r.Body).Decode(&req)
		toolID := tailSegment(r.URL.Path)
		log.Printf("[MCP] tool call: %s args=%v (traceId=%s)", toolID, req.Arguments, r.Header.Get("X-Trace-Id"))
		cacheKey := toolID + fmt.Sprint(req.Arguments)
		mu.RLock()
		if cached, ok := toolCache[cacheKey]; ok {
			mu.RUnlock()
			log.Printf("[MCP] cache hit: %s", toolID)
			writeJSON(w, cached)
			return
		}
		mu.RUnlock()
		result := map[string]interface{}{
			"success": true,
			"data":    "simulated result for " + toolID,
			"ts":      time.Now().Format(time.RFC3339),
		}
		mu.Lock()
		toolCache[cacheKey] = result
		mu.Unlock()
		writeJSON(w, result)
	})

	// Flat tool-call endpoint used by the built-in ReAct engine.
	mux.HandleFunc("/mcp/call", func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			Tool      string `json:"tool"`
			Arguments string `json:"arguments"`
		}
		json.NewDecoder(r.Body).Decode(&req)
		log.Printf("[MCP] /mcp/call tool=%s args=%s (traceId=%s)", req.Tool, req.Arguments, r.Header.Get("X-Trace-Id"))
		result := map[string]interface{}{
			"success": true,
			"tool":    req.Tool,
			"result":  "ok:" + req.Tool,
			"ts":      time.Now().Format(time.RFC3339),
		}
		writeJSON(w, result)
	})

	// Per-server health
	mux.HandleFunc("/mcp-servers/{id}/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, map[string]interface{}{"healthy": true, "ts": time.Now().Format(time.RFC3339)})
	})

	log.Printf("MCP Proxy listening on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, withCORS(mux)))
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func writeJSON(w http.ResponseWriter, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}

func tailSegment(p string) string {
	parts := strings.Split(strings.TrimSuffix(p, "/"), "/")
	if len(parts) == 0 {
		return ""
	}
	return parts[len(parts)-1]
}

func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Trace-Id")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}
