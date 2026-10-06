// ChatHub BFF - Agent Gateway
// A2A agent registry + task CRUD + SSE streaming + approval + webhook.
// P0: built-in ReAct engine - an LLM drives tool calls through the MCP
// proxy and every step is streamed back as an SSE event.

package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
)

// nl is a byte-slice newline (avoids literal escapes in streaming writes).
var nl = string([]byte{10})

type AgentCard struct {
	ID           string   `json:"id"`
	Name         string   `json:"name"`
	Description  string   `json:"description"`
	BaseURL      string   `json:"baseUrl"`
	Capabilities []string `json:"capabilities"`
	Status       string   `json:"status"`
}

type Task struct {
	ID          string      `json:"id"`
	AgentID     string      `json:"agentId"`
	Instruction string      `json:"instruction"`
	Status      string      `json:"status"`
	Progress    float64     `json:"progress"`
	Result      interface{} `json:"result,omitempty"`
	Error       string      `json:"error,omitempty"`
	TraceID     string      `json:"traceId"`
	CreatedAt   time.Time   `json:"createdAt"`
	CompletedAt *time.Time  `json:"completedAt,omitempty"`
}

// toolCall is a parsed ReAct tool invocation: "TOOL: name {json}".
type toolCall struct {
	name string
	args string
}

var (
	agents     = map[string]AgentCard{}
	tasks      = map[string]*Task{}
	taskEvents = map[string][]string{}
	mu         sync.RWMutex
)

func main() {
	port := env("PORT", "8083")
	mux := http.NewServeMux()

	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, map[string]string{"status": "healthy", "service": "agent-gateway"})
	})

	// Agent registry
	mux.HandleFunc("/agents", func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			var card AgentCard
			json.NewDecoder(r.Body).Decode(&card)
			mu.Lock()
			agents[card.ID] = card
			mu.Unlock()
			log.Printf("[AGW] agent registered: %s (%s)", card.ID, card.Name)
			w.WriteHeader(http.StatusCreated)
			writeJSON(w, card)
			return
		}
		mu.RLock()
		var list []AgentCard
		for _, a := range agents {
			list = append(list, a)
		}
		mu.RUnlock()
		writeJSON(w, map[string][]AgentCard{"agents": list})
	})

	// Task creation (starts the ReAct loop asynchronously)
	mux.HandleFunc("/agents/{agentId}/tasks", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var req struct {
			Instruction string `json:"instruction"`
			Strategy    string `json:"strategy"`
		}
		json.NewDecoder(r.Body).Decode(&req)
		agentID := agentIDFromPath(r.URL.Path)
		taskID := fmt.Sprintf("task-%d", time.Now().UnixNano())
		t := &Task{
			ID: taskID, AgentID: agentID, Instruction: req.Instruction,
			Status: "pending", TraceID: r.Header.Get("X-Trace-Id"), CreatedAt: time.Now(),
		}
		mu.Lock()
		tasks[taskID] = t
		taskEvents[taskID] = nil
		mu.Unlock()
		log.Printf("[AGW] task created: %s agent=%s strategy=%s (traceId=%s)", taskID, agentID, req.Strategy, t.TraceID)
		go runReAct(t)
		w.WriteHeader(http.StatusAccepted)
		writeJSON(w, map[string]interface{}{
			"taskId":    taskID,
			"status":    "accepted",
			"agentId":   agentID,
			"streamUrl": "/agents/" + agentID + "/tasks/" + taskID + "/stream",
		})
	})

	// Task status
	mux.HandleFunc("/agents/{agentId}/tasks/{taskId}", func(w http.ResponseWriter, r *http.Request) {
		taskID := taskIDFromPath(r.URL.Path)
		mu.RLock()
		t := tasks[taskID]
		mu.RUnlock()
		if t == nil {
			writeErr(w, "TASK_NOT_FOUND", "task not found", http.StatusNotFound)
			return
		}
		writeJSON(w, t)
	})

	// Cancel
	mux.HandleFunc("/agents/{agentId}/tasks/{taskId}/cancel", func(w http.ResponseWriter, r *http.Request) {
		taskID := taskIDFromPath(r.URL.Path)
		mu.Lock()
		if t := tasks[taskID]; t != nil && (t.Status == "pending" || t.Status == "executing") {
			t.Status = "cancelled"
		}
		mu.Unlock()
		writeJSON(w, map[string]string{"message": "task cancelled"})
	})

	// SSE stream with replay from the event buffer
	mux.HandleFunc("/agents/{agentId}/tasks/{taskId}/stream", func(w http.ResponseWriter, r *http.Request) {
		taskID := taskIDFromPath(r.URL.Path)
		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache")
		w.Header().Set("Connection", "keep-alive")
		lastHB := time.Now()
		flusher, ok := w.(http.Flusher)
		if !ok {
			http.Error(w, "streaming unsupported", http.StatusInternalServerError)
			return
		}
		mu.RLock()
		evts := append([]string(nil), taskEvents[taskID]...)
		known := len(evts)
		mu.RUnlock()
		for _, e := range evts {
			fmt.Fprintf(w, "data: %s"+nl+nl, e)
		}
		flusher.Flush()
		for deadline := time.Now().Add(60 * time.Second); time.Now().Before(deadline); {
			time.Sleep(250 * time.Millisecond)
			if time.Since(lastHB) > 15*time.Second {
				fmt.Fprintf(w, "data: %s"+nl+nl, `{"type":"heartbeat"}`)
				flusher.Flush()
				lastHB = time.Now()
			}
			mu.RLock()
			all := taskEvents[taskID]
			state := ""
			if t := tasks[taskID]; t != nil {
				state = t.Status
			}
			for i := known; i < len(all); i++ {
				fmt.Fprintf(w, "data: %s"+nl+nl, all[i])
			}
			known = len(all)
			done := state == "done" || state == "error" || state == "cancelled"
			mu.RUnlock()
			flusher.Flush()
			if done {
				break
			}
		}
	})

	// Approval
	mux.HandleFunc("/agents/{agentId}/tasks/{taskId}/approve", func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			Approved bool   `json:"approved"`
			Reason   string `json:"reason"`
		}
		json.NewDecoder(r.Body).Decode(&req)
		log.Printf("[AGW] approval: approved=%v reason=%s (traceId=%s)", req.Approved, req.Reason, r.Header.Get("X-Trace-Id"))
		writeJSON(w, map[string]string{"message": "approval recorded", "approved": fmt.Sprint(req.Approved)})
	})

	// Webhook
	mux.HandleFunc("/webhooks/task-completed", func(w http.ResponseWriter, r *http.Request) {
		log.Printf("[AGW] webhook: task completed received")
		writeJSON(w, map[string]string{"message": "webhook received"})
	})

	log.Printf("Agent Gateway listening on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, withCORS(mux)))
}

// Built-in ReAct engine: LLM -> parse TOOL: lines -> MCP proxy -> inject -> answer.
func runReAct(task *Task) {
	msgs := []map[string]string{
		{"role": "system", "content": "You are ChatHub. To use a tool, output a line exactly: TOOL: <tool> {json}. Otherwise answer directly."},
		{"role": "user", "content": task.Instruction},
	}
	answer := ""
	for step := 0; step < 4; step++ {
		setState(task, "executing")
		emit(task, map[string]interface{}{"type": "thinking", "step": step})
		out, err := llmChat(msgs)
		if err != nil {
			emit(task, map[string]interface{}{"type": "error", "message": err.Error()})
			task.Error = err.Error()
			setState(task, "error")
			return
		}
		calls := parseTools(out)
		if len(calls) == 0 {
			answer = out
			break
		}
		for _, c := range calls {
			emit(task, map[string]interface{}{"type": "tool_call", "tool": c.name, "args": c.args})
			res, cerr := mcpCall(c.name, c.args)
			status := "completed"
			if cerr != nil {
				status = "error"
				res = cerr.Error()
			}
			emit(task, map[string]interface{}{"type": "tool_result", "tool": c.name, "status": status, "result": res})
			msgs = append(msgs,
				map[string]string{"role": "assistant", "content": out},
				map[string]string{"role": "user", "content": "TOOL RESULT (" + c.name + "): " + res},
			)
		}
	}
	setState(task, "streaming")
	emit(task, map[string]interface{}{"type": "answer", "content": answer})
	task.Result = answer
	now := time.Now()
	task.CompletedAt = &now
	setState(task, "done")
	emit(task, map[string]interface{}{"type": "completed", "success": true})
}

func llmChat(msgs []map[string]string) (string, error) {
	body, _ := json.Marshal(map[string]interface{}{"provider": "openai", "model": env("AGENT_MODEL", "gpt-4o-mini"), "messages": msgs})
	resp, err := http.Post(llmRouterURL()+"/llm/route", "application/json", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	var out struct {
		Content string `json:"content"`
	}
	json.Unmarshal(b, &out)
	return out.Content, nil
}

func mcpCall(name, args string) (string, error) {
	body, _ := json.Marshal(map[string]string{"tool": name, "arguments": args})
	resp, err := http.Post(mcpProxyURL()+"/mcp/call", "application/json", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	var out struct {
		Result string `json:"result"`
	}
	json.Unmarshal(b, &out)
	if out.Result == "" {
		return string(b), nil
	}
	return out.Result, nil
}

func parseTools(text string) []toolCall {
	var out []toolCall
	for _, line := range strings.Split(text, nl) {
		line = strings.TrimSpace(line)
		if !strings.HasPrefix(line, "TOOL:") {
			continue
		}
		rest := strings.TrimSpace(line[len("TOOL:"):])
		if i := strings.IndexByte(rest, 32); i > 0 {
			out = append(out, toolCall{name: rest[:i], args: strings.TrimSpace(rest[i:])})
		} else {
			out = append(out, toolCall{name: rest, args: "{}"})
		}
	}
	return out
}

func setState(task *Task, s string) {
	mu.Lock()
	task.Status = s
	mu.Unlock()
}

func emit(task *Task, evt interface{}) {
	data, _ := json.Marshal(evt)
	mu.Lock()
	taskEvents[task.ID] = append(taskEvents[task.ID], string(data))
	mu.Unlock()
}

func llmRouterURL() string { return env("LLM_ROUTER_URL", "http://llm-router:8085") }
func mcpProxyURL() string  { return env("MCP_PROXY_URL", "http://mcp-proxy:8084") }

func agentIDFromPath(p string) string {
	parts := strings.Split(p, "/")
	if len(parts) >= 3 && parts[2] != "" {
		return parts[2]
	}
	return "default"
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

func writeErr(w http.ResponseWriter, code, msg string, status int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]interface{}{
		"error": map[string]string{"code": code, "message": msg},
	})
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

// taskIDFromPath extracts the task id from /agents/{agentId}/tasks/{taskId}[/action].
func taskIDFromPath(p string) string {
	parts := strings.Split(p, "/")
	if len(parts) >= 5 && parts[4] != "" {
		return parts[4]
	}
	return "default"
}
