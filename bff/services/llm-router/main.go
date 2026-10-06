// ChatHub BFF - LLM Router
// Routes LLM requests to OpenAI-compatible / Anthropic / Gemini backends.
// P0: stdlib-only. When a provider key is absent it falls back to a mock
// response so the service (and CI) run without credentials.

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
	"time"
)

// Msg is a single chat message.
type Msg struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// Req is an LLM routing request.
type Req struct {
	Provider string `json:"provider"`
	Model    string `json:"model"`
	Messages []Msg  `json:"messages"`
	Stream   bool   `json:"stream"`
}

// Res is an LLM routing response.
type Res struct {
	Content   string  `json:"content"`
	Model     string  `json:"model"`
	Provider  string  `json:"provider"`
	Tokens    int     `json:"tokens"`
	CostUSD   float64 `json:"costUSD"`
	LatencyMs int     `json:"latencyMs"`
}

// keys loads provider API keys from the environment.
var keys = map[string]string{
	"openai":    os.Getenv("OPENAI_API_KEY"),
	"anthropic": os.Getenv("ANTHROPIC_API_KEY"),
	"gemini":    os.Getenv("GEMINI_API_KEY"),
}

func main() {
	port := env("PORT", "8085")
	mux := http.NewServeMux()

	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, map[string]string{"status": "healthy", "service": "llm-router"})
	})

	mux.HandleFunc("/llm/models", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, map[string][]string{"providers": []string{"openai", "anthropic", "gemini"}})
	})

	mux.HandleFunc("/llm/route", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		var req Req
		_ = json.NewDecoder(r.Body).Decode(&req)
		if req.Provider == "" {
			req.Provider = "openai"
		}
		t0 := time.Now()
		res, err := real(req)
		if err != nil {
			log.Printf("[LLM] %s call failed: %v -> mock", req.Provider, err)
			res = mock(req)
		}
		res.LatencyMs = int(time.Since(t0).Milliseconds())
		writeJSON(w, res)
	})

	mux.HandleFunc("/llm/route/stream", func(w http.ResponseWriter, r *http.Request) {
		var req Req
		_ = json.NewDecoder(r.Body).Decode(&req)
		if req.Provider == "" {
			req.Provider = "openai"
		}
		res, err := real(req)
		if err != nil {
			res = mock(req)
		}
		nl := string([]byte{10})
		w.Header().Set("Content-Type", "text/event-stream")
		w.Header().Set("Cache-Control", "no-cache")
		fl, _ := w.(http.Flusher)
		for _, tok := range strings.Fields(res.Content) {
			data, _ := json.Marshal(map[string]string{"token": tok + " "})
			fmt.Fprintf(w, "data: %s"+nl+nl, data)
			if fl != nil {
				fl.Flush()
			}
		}
		done, _ := json.Marshal(map[string]string{"type": "completed", "model": req.Model})
		fmt.Fprintf(w, "data: %s"+nl+nl, done)
		if fl != nil {
			fl.Flush()
		}
	})

	log.Printf("LLM Router on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, withCORS(mux)))
}

// mock returns a deterministic response so CI runs without API keys.
func mock(req Req) Res {
	last := ""
	for _, m := range req.Messages {
		last = m.Content
	}
	return Res{
		Content:  "[mock:" + req.Provider + "] " + truncate(last, 60),
		Model:    req.Model,
		Provider: req.Provider,
		Tokens:   16,
	}
}

// real dispatches to the provider API, or mock when no key is present.
func real(req Req) (Res, error) {
	if keys[req.Provider] == "" {
		return mock(req), nil
	}
	switch req.Provider {
	case "anthropic":
		return callAnthropic(req)
	case "gemini":
		return callGemini(req)
	default:
		return callOpenAI(req)
	}
}

func callOpenAI(req Req) (Res, error) {
	base := os.Getenv("OPENAI_BASE_URL")
	if base == "" {
		base = "https://api.openai.com/v1"
	}
	body, _ := json.Marshal(map[string]interface{}{
		"model":    orModel(req.Model, "gpt-4o-mini"),
		"messages": req.Messages,
	})
	resp, err := (&http.Client{Timeout: 60 * time.Second}).Post(base+"/chat/completions", "application/json", bytes.NewReader(body))
	if err != nil {
		return Res{}, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	var out struct {
		Choices []struct {
			Message Msg `json:"message"`
		} `json:"choices"`
		Usage struct {
			TotalTokens int `json:"total_tokens"`
		} `json:"usage"`
	}
	_ = json.Unmarshal(b, &out)
	c := ""
	if len(out.Choices) > 0 {
		c = out.Choices[0].Message.Content
	}
	return Res{Content: c, Model: req.Model, Provider: "openai", Tokens: out.Usage.TotalTokens}, nil
}

func callAnthropic(req Req) (Res, error) {
	body, _ := json.Marshal(map[string]interface{}{
		"model":      orModel(req.Model, "claude-3-5-sonnet"),
		"max_tokens": 2048,
		"messages":   req.Messages,
	})
	hreq, _ := http.NewRequest(http.MethodPost, "https://api.anthropic.com/v1/messages", bytes.NewReader(body))
	hreq.Header.Set("x-api-key", keys["anthropic"])
	hreq.Header.Set("anthropic-version", "2023-06-01")
	hreq.Header.Set("Content-Type", "application/json")
	resp, err := (&http.Client{Timeout: 60 * time.Second}).Do(hreq)
	if err != nil {
		return Res{}, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	var out struct {
		Content []struct {
			Text string `json:"text"`
		} `json:"content"`
		Usage struct {
			OutputTokens int `json:"output_tokens"`
		} `json:"usage"`
	}
	_ = json.Unmarshal(b, &out)
	c := ""
	if len(out.Content) > 0 {
		c = out.Content[0].Text
	}
	return Res{Content: c, Model: req.Model, Provider: "anthropic", Tokens: out.Usage.OutputTokens}, nil
}

func callGemini(req Req) (Res, error) {
	model := orModel(req.Model, "gemini-1.5-flash")
	contents := make([]map[string]interface{}, 0, len(req.Messages))
	for _, m := range req.Messages {
		p := "user"
		if m.Role == "assistant" {
			p = "model"
		}
		contents = append(contents, map[string]interface{}{
			"role":  p,
			"parts": []map[string]string{{"text": m.Content}},
		})
	}
	body, _ := json.Marshal(map[string]interface{}{"contents": contents})
	url := "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + keys["gemini"]
	resp, err := (&http.Client{Timeout: 60 * time.Second}).Post(url, "application/json", bytes.NewReader(body))
	if err != nil {
		return Res{}, err
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	var out struct {
		Candidates []struct {
			Content struct {
				Parts []struct {
					Text string `json:"text"`
				} `json:"parts"`
			} `json:"content"`
		} `json:"candidates"`
	}
	_ = json.Unmarshal(b, &out)
	c := ""
	if len(out.Candidates) > 0 && len(out.Candidates[0].Content.Parts) > 0 {
		c = out.Candidates[0].Content.Parts[0].Text
	}
	return Res{Content: c, Model: model, Provider: "gemini"}, nil
}

func orModel(m, d string) string {
	if m == "" {
		return d
	}
	return m
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "..."
}

func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}

func writeJSON(w http.ResponseWriter, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}

func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Trace-Id")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}
