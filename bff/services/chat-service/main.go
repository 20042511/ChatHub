// ChatHub BFF - Chat Service
// Conversation CRUD + messages + search + export + sync + feedback.
// P0: sending a message bridges to the Agent Gateway to launch a ReAct task.

package main

import (
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"os"
	"time"
)

type Conversation struct {
	ID        string    `json:"id"`
	Title     string    `json:"title"`
	Status    string    `json:"status"`
	CreatedAt time.Time `json:"createdAt"`
}

type Message struct {
	ID        string    `json:"id"`
	ConvID    string    `json:"convId"`
	Role      string    `json:"role"`
	Content   string    `json:"content"`
	Feedback  *string   `json:"feedback,omitempty"`
	CreatedAt time.Time `json:"createdAt"`
}

var (
	conversations = map[string]Conversation{}
	messages      = map[string][]Message{}
)

func main() {
	port := env("PORT", "8082")
	mux := http.NewServeMux()

	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, map[string]string{"status": "healthy", "service": "chat-service"})
	})

	// conversations list / create
	mux.HandleFunc("/conversations", func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			var list []Conversation
			for _, c := range conversations {
				list = append(list, c)
			}
			writeJSON(w, map[string]interface{}{"conversations": list, "total": len(list)})
		case http.MethodPost:
			convID := generateID("conv")
			conversations[convID] = Conversation{
				ID: convID, Title: "New Conversation", Status: "active",
				CreatedAt: time.Now(),
			}
			w.WriteHeader(http.StatusCreated)
			writeJSON(w, conversations[convID])
		}
	})

	// conversation detail / delete
	mux.HandleFunc("/conversations/", func(w http.ResponseWriter, r *http.Request) {
		convID := r.URL.Path[len("/conversations/"):]
		switch {
		case r.Method == http.MethodGet:
			conv, ok := conversations[convID]
			if !ok {
				writeErr(w, "CONV_NOT_FOUND", "conversation not found", http.StatusNotFound)
				return
			}
			writeJSON(w, map[string]interface{}{"conversation": conv, "messages": messages[convID]})
		case r.Method == http.MethodDelete:
			delete(conversations, convID)
			delete(messages, convID)
			writeJSON(w, map[string]string{"message": "deleted"})
		}
	})

	// send message -> bridge to agent gateway
	mux.HandleFunc("/conversations/{id}/messages", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		convID := conversationIDFromPath(r.URL.Path)
		existing, ok := conversations[convID]
		if !ok {
			writeErr(w, "CONV_NOT_FOUND", "conversation not found", http.StatusNotFound)
			return
		}
		var msg Message
		json.NewDecoder(r.Body).Decode(&msg)
		msg.ID = generateID("msg")
		msg.ConvID = convID
		if msg.Role == "" {
			msg.Role = "user"
		}
		msg.CreatedAt = time.Now()
		messages[convID] = append(messages[convID], msg)
		if existing.Title == "New Conversation" {
			conversations[convID] = withTitle(conversations[convID], summarize(msg.Content))
		}
		log.Printf("[CHAT] message sent: conv=%s msg=%s role=%s (traceId=%s)", convID, msg.ID, msg.Role, r.Header.Get("X-Trace-Id"))
		task := createAgentTask(convID, msg.Content, r)
		w.WriteHeader(http.StatusAccepted)
		resp := map[string]interface{}{"messageId": msg.ID, "status": "accepted"}
		for k, v := range task {
			resp[k] = v
		}
		writeJSON(w, resp)
	})

	// feedback
	mux.HandleFunc("/conversations/{id}/feedback", func(w http.ResponseWriter, r *http.Request) {
		var req struct {
			MessageID string   `json:"messageId"`
			Positive  bool     `json:"positive"`
			Tags      []string `json:"tags"`
			Text      string   `json:"text"`
		}
		json.NewDecoder(r.Body).Decode(&req)
		log.Printf("[CHAT] feedback: msg=%s positive=%v tags=%v", req.MessageID, req.Positive, req.Tags)
		writeJSON(w, map[string]string{"message": "feedback recorded"})
	})

	// sync
	mux.HandleFunc("/sync/push", func(w http.ResponseWriter, r *http.Request) {
		log.Printf("[SYNC] push received (traceId=%s)", r.Header.Get("X-Trace-Id"))
		writeJSON(w, map[string]string{"message": "sync pushed"})
	})

	mux.HandleFunc("/sync/pull", func(w http.ResponseWriter, r *http.Request) {
		since := r.URL.Query().Get("since")
		log.Printf("[SYNC] pull since=%s", since)
		writeJSON(w, map[string]interface{}{"updates": []interface{}{}, "since": since})
	})

	log.Printf("Chat Service listening on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, withCORS(mux)))
}

// createAgentTask POSTs the message to the Agent Gateway to launch a ReAct task.
// On failure it still returns a graceful fallback so the chat flow never hard-fails.
func createAgentTask(convID, content string, r *http.Request) map[string]interface{} {
	body, _ := json.Marshal(map[string]string{"instruction": content, "strategy": "react"})
	url := agentGatewayURL() + "/agents/engine/tasks"
	resp, err := http.Post(url, "application/json", bytes.NewReader(body))
	if err != nil {
		log.Printf("[CHAT] agent gateway unreachable: %v", err)
		return map[string]interface{}{"taskId": "", "status": "agent_unavailable"}
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	var out map[string]interface{}
	json.Unmarshal(b, &out)
	if out == nil {
		out = map[string]interface{}{}
	}
	out["convId"] = convID
	return out
}

func agentGatewayURL() string { return env("AGENT_GATEWAY_URL", "http://agent-gateway:8083") }

func withTitle(c Conversation, title string) Conversation {
	c.Title = title
	return c
}

func summarize(s string) string {
	if len(s) > 40 {
		return s[:40] + "..."
	}
	return s
}

func generateID(prefix string) string {
	return prefix + "-" + time.Now().Format("20060102150405")
}

func conversationIDFromPath(p string) string {
	rest := p[len("/conversations/"):]
	i := 0
	for i < len(rest) && rest[i] != 47 {
		i++
	}
	return rest[:i]
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
