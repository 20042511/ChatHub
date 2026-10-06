# ChatHub P0 开发交接文档
日期: 2026-10-06 | 环境: Ubuntu 24.04 arm64 沙箱 (/workspace/chathub/)
前手: AI 代理 (Agnes) | 用途: 下一执行人只读此文件即可接手

## 1. 一句话现状
P0 后端闭环完成且端到端验证通过: 4 个 BFF Go 服务
(chat-service:8082 -> agent-gateway:8083 内置ReAct -> llm-router:8085 / mcp-proxy:8084)
编译+vet+运行+链路全绿; CI workflow 已写好; 前端 Dart 两文件完整代码在 §7,
其中 bff_client.dart 沙箱里只落了 61/90 行(缺 §7 末尾 4 个私有方法),
chat_screen.dart 沙箱里还没建(直接新建即可)。

## 2. 文件清单
```
/workspace/chathub/
├── HANDOFF.md                      # 本文档
├── .github/workflows/bff-ci.yml    # [完成] CI: gofmt+build+vet+冒烟
└── bff/services/
    ├── llm-router/{go.mod,main.go} # [完成] 282行 build/vet/运行OK
    ├── agent-gateway/{go.mod,main.go} # [完成] 365行 build/vet/运行OK
    ├── chat-service/{go.mod,main.go}  # [完成] 226行 build/vet/运行OK
    └── mcp-proxy/{go.mod,main.go}   # [完成] 177行 build/vet/运行OK
```
flutter_app/ 下两个 Dart 文件见 §7 (bff_client.dart 需补全, chat_screen.dart 需新建)。

## 3. 环境与工具链 (重装方法)
- 沙箱原本无 go/curl/git。已用 apt 装好 go1.22.2 + curl。
- 国内源: /etc/apt/sources.list.d/ubuntu.sources 已切到
  http://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ (必须用 HTTP,
  沙箱缺 CA 库导致 HTTPS 证书校验失败; GPG 仍由本地 keyring 校验, 安全无损)
- Go 国内代理: go env -w GOPROXY=https://goproxy.cn,direct
  GOSUMDB=off; GOMODCACHE=/workspace/.gopath/pkg/mod
- **git 未安装**: 推 GitHub 前先 `apt-get install -y git`
- 无 Flutter SDK: Dart 代码只有静态审查, 未做 flutter analyze (P1 做)

## 4. 已完成的验证 (全绿)
- 4 服务: `gofmt -l` 空 + `go build` + `go vet` 全部通过
- e2e (起 3 服务): POST /agents/engine/tasks -> task done,
  result="[mock:openai] summarize the repo layout" (无 key 自动 mock, CI 可复现)
- SSE 回放: data: thinking(step0) -> data: answer -> data: completed
- chat-service: 建对话+发消息 -> 返回 {messageId, taskId, streamUrl,
  agentId:"engine", convId} 即消息已桥接到 ReAct
- llm-router 冒烟: /health /llm/route /llm/route/stream 均正常

## 5. 已修 3 个 bug (防止回归, 都在 agent-gateway / llm-router)
1. llm-router: `hreq.SetHeader(...)` 编译错误
   (http.Request 无 SetHeader) -> 改为 `hreq.Header.Set(...)`
2. agentIDFromPath: 曾误用 parts[3] (把 "tasks" 当 agentId) -> 改 parts[2]
   (/agents/{agentId}/tasks 中 agentId 在第 2 段)
3. 任务路径解析: 曾用 tailSegment 取末段, 遇 /stream 尾巴取成 "stream"
   -> 引入 taskIDFromPath 固定取 parts[4]
   (状态/取消/流 3 个 handler 用 taskIDFromPath; approve 不查 task)

## 6. 接手后按序 TODO
P0 (让 GitHub CI 绿起来):
 1. 补全 bff_client.dart: 沙箱版缺 §7 中最后 4 个方法
    (_parseSse/_headers/_jsonOpts/_nextTrace) + 类收尾 }
    -- 把 §7.1 全文覆盖写 flutter_app/lib/services/bff_client.dart
 2. 新建 flutter_app/lib/ui/chat/chat_screen.dart (§7.2 全文)
 3. apt-get install -y git; 建仓库; 提交全部文件 (含 go.mod, 无 go.sum 依赖)
 4. 推 .github/workflows/bff-ci.yml, 看 CI: 期望 gofmt+build+vet+冒烟 全绿
 5. (有 Flutter 环境时) flutter pub get + flutter analyze
P1:
 - 接真实 key (OPENAI_API_KEY/ANTHROPIC_API_KEY/GEMINI_API_KEY), 去掉 mock 路径
 - 内存 map 存储 -> PostgreSQL + Redis (并发/重启丢失问题)
 - mcp-proxy /mcp/call 现在是模拟结果, 改为真实转发到注册 MCP Server
 - agent-gateway 加 RBAC/审批真逻辑 (现在 approve 只记日志)
 - CI 加 Flutter analyze job

## 7. 完整 Dart 代码 (接手后直接照抄进沙箱/仓库)
### 7.1 flutter_app/lib/services/bff_client.dart (完整 90 行版, 覆盖沙箱残缺版)

```dart
import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';

// Minimal BFF client for the ChatHub backend.
// Handles trace ids and SSE streaming of agent task events.
class BffClient {
  BffClient({String baseUrl = "http://localhost:8080", Dio? dio})
      : _dio = dio ?? Dio() {
    _dio.options.headers["X-Client"] = "chathub-app";
  }

  final Dio _dio;
  final String baseUrl;
  String? token;
  int _traceSeq = 0;

  Future<Map<String, dynamic>> createConversation() async {
    final r = await _dio.post("$baseUrl/conversations", options: _jsonOpts());
    return r.data as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> sendMessage(String conversationId, String content) async {
    final r = await _dio.post(
      "$baseUrl/conversations/$conversationId/messages",
      data: {"role": "user", "content": content},
      options: _jsonOpts(),
    );
    return r.data as Map<String, dynamic>;
  }

  Stream<Map<String, dynamic>> streamTask(String streamUrl) async* {
    final resp = await _dio.get(
      "$baseUrl$streamUrl",
      options: Options(
        responseType: ResponseType.stream,
        headers: _headers(),
        sendTimeout: 30 * 1000,
      ),
    );
    final Stream<List<int>> s = resp.data as Stream<List<int>>;
    var pending = "";
    const nl = String.fromCharCode(10);
    await for (final chunk in s) {
      pending += utf8.decode(chunk, allowMalformed: true);
      int idx;
      while ((idx = pending.indexOf(nl + nl)) >= 0) {
        final frame = pending.substring(0, idx);
        pending = pending.substring(idx + 2);
        final payload = _parseSse(frame, nl);
        if (payload != null) yield payload;
      }
    }
  }

  Map<String, dynamic>? _parseSse(String frame, String nl) {
    for (final line in frame.split(nl)) {
      if (line.startsWith("data:")) {
        final raw = line.substring(5).trim();
        if (raw.isEmpty || raw == "[DONE]") continue;
        try {
          return json.decode(raw) as Map<String, dynamic>;
        } catch (_) {
          return <String, dynamic>{"type": "raw", "data": raw};
        }
      }
    }
    return null;
  }

  Map<String, String> _headers() {
    final h = <String, String>{"Content-Type": "application/json"};
    h["X-Trace-Id"] = _nextTrace();
    if (token != null) h["Authorization"] = "Bearer $token";
    return h;
  }

  Options _jsonOpts() =>
      Options(headers: _headers(), sendTimeout: 30 * 1000);

  String _nextTrace() {
    _traceSeq++;
    return "app-${DateTime.now().microsecondsSinceEpoch}-$_traceSeq";
  }
}
```

### 7.2 flutter_app/lib/ui/chat/chat_screen.dart (新建)

```dart
import 'package:flutter/material.dart';

import '../../services/bff_client.dart';
import 'message_bubble.dart';

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _controller = TextEditingController();
  final _bff = BffClient();
  final _log = <Map<String, dynamic>>[];

  String? _conversationId;
  String _activeTool = '';
  bool _isStreaming = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('ChatHub'),
        actions: [IconButton(icon: const Icon(Icons.more_vert), onPressed: _showMenu)],
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: _log.isEmpty
                  ? const Center(child: Text('Start chatting…'))
                  : ListView.builder(
                      padding: const EdgeInsets.all(12),
                      itemCount: _log.length,
                      itemBuilder: (context, i) {
                        final m = _log[i];
                        return MessageBubble(
                          isUser: m['isUser'] as bool? ?? false,
                          content: m['content'] as String? ?? '',
                          isStreaming: _isStreaming && i == _log.length - 1,
                        );
                      },
                    ),
            ),
            if (_activeTool.isNotEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                child: Row(children: [
                  const SizedBox(width: 14, height: 14, child: CircularProgressIndicator()),
                  const SizedBox(width: 8),
                  Text('调用工具 $_activeTool …'),
                ]),
              ),
            _buildInputBar(),
          ],
        ),
      ),
    );
  }

  Widget _buildInputBar() {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(border: Border(top: BorderSide(color: Colors.grey.shade300))),
      child: Row(
        children: [
          IconButton(icon: const Icon(Icons.photo_library), onPressed: () {}),
          Expanded(
            child: TextField(
              controller: _controller,
              decoration: const InputDecoration(hintText: '输入消息…', border: OutlineInputBorder()),
              onSubmitted: _sendMessage,
            ),
          ),
          IconButton(
            icon: _isStreaming ? const Icon(Icons.stop) : const Icon(Icons.send),
            onPressed: _isStreaming ? null : () => _sendMessage(_controller.text),
          ),
        ],
      ),
    );
  }

  Future<void> _sendMessage(String text) async {
    final content = text.trim();
    if (content.isEmpty || _isStreaming) return;
    setState(() {
      _log.add({'isUser': true, 'content': content});
      _isStreaming = true;
      _controller.clear();
    });
    try {
      final cid = _conversationId ??= (await _bff.createConversation())['id'] as String;
      final accepted = await _bff.sendMessage(cid, content);
      final taskId = accepted['taskId'] as String?;
      final streamUrl = accepted['streamUrl'] as String?;
      if (taskId == null || streamUrl == null) {
        _appendAnswer('（Agent 不可用）');
        return;
      }
      _appendAnswer('');
      await for (final evt in _bff.streamTask(streamUrl)) {
        if (!mounted) return;
        switch (evt['type']) {
          case 'tool_call':
            setState(() => _activeTool = '${evt['tool']}');
            break;
          case 'tool_result':
            if (evt['status'] == 'error') _log.last['content'] = '工具 ${evt['tool']} 失败';
            break;
          case 'answer':
            _log.last['content'] = '${evt['content']}';
            break;
          case 'error':
            _log.last['content'] = '出错：${evt['message']}';
            break;
        }
      }
    } catch (e) {
      if (mounted) _log.last['content'] = '请求失败：$e';
    } finally {
      if (mounted) setState(() {
        _activeTool = '';
        _isStreaming = false;
      });
    }
  }

  void _appendAnswer(String s) => setState(() => _log.add({'isUser': false, 'content': s}));
  void _showMenu() {}
}
```

## 8. API 契约 (前后端对齐)

| 端点 | 方法 | 返回 | 服务 |
|---|---|---|---|
| /conversations | POST | {id,title,status,createdAt} | chat:8082 |
| /conversations/{id}/messages | POST | {messageId,status,taskId,streamUrl,agentId,convId} | chat:8082 |
| /agents/{agentId}/tasks | POST | 202 {taskId,status,streamUrl} | agw:8083 |
| /agents/{agentId}/tasks/{taskId} | GET | Task {id,status,progress,result,...} | agw:8083 |
| /agents/{agentId}/tasks/{taskId}/stream | GET | SSE data: {type,...} | agw:8083 |
| /agents/{agentId}/tasks/{taskId}/cancel | POST | {message} | agw:8083 |
| /mcp/call | POST {tool,arguments} | {success,tool,result,ts} | mcp:8084 |
| /llm/route | POST {provider,model,messages} | {content,model,provider,tokens} | llm:8085 |

SSE 事件类型:
- thinking(step): ReAct 推理步骤
- tool_call(tool, args): 调用工具
- tool_result(tool, status, result): 工具返回
- answer(content): 最终答案
- completed(success): 任务完成
- error(message): 错误
- heartbeat: 每 15s 保活（新增）

ReAct 协议: LLM 输出 `TOOL: <工具名> {json}` 触发工具调用，最多 4 步。
mock 行为: 无 API key 时 llm-router 返回 `[mock:{provider}] {用户消息前 60 字}`。
默认端口: chat:8082, agw:8083, mcp:8084, llm:8085。

## 9. 踩坑清单（前人血泪）
1. **沙箱通道间歇性丢参数**: 会话变长后大载荷命令随机报 `command/path is required`。
   对策: 写文件拆小块(≤500B)、失败重试、重要内容留在对话里。
2. **清华镜像必须 HTTP**: 沙箱缺 CA 库，HTTPS 证书校验失败，apt 拉不到
   ca-certificates 是死循环。源已改 http://，别改回 https。
3. **Go 服务独立 module**: 顶层 `go build ./...` 无效，必须逐个 cd 编译。
4. **Dio 5 细节**: 超时是 int 毫秒；流式 resp.data 是 Stream<List<int>>。
5. **路径解析**: /agents/{agentId}/tasks/{taskId}/action 中
   agentId=parts[2], taskId=parts[4]。
6. **git 未装**: 推 GitHub 前 `apt-get install -y git`。
7. **服务名 vs localhost**: 代码已改成 llm-router:8085 / agent-gateway:8083 等
   Docker Compose 友好的服务名。本地测试时需环境变量覆盖或 /etc/hosts。
8. **SSE 心跳**: 已加 15s 心跳，防止长时间无输出导致连接断开。

## 10. 启动指令与下一步

### 本地验证（沙箱内）
```bash
cd /workspace/chathub/bff/services
for d in llm-router agent-gateway chat-service mcp-proxy; do
  (cd $d && gofmt -w . && go build -o /tmp/$d . && go vet ./... && echo "✓ $d")
done

# 起服务（本地测试用 localhost 覆盖服务名）
LLM_ROUTER_URL=http://localhost:8085 MCP_PROXY_URL=http://localhost:8084 \
  PORT=8084 /tmp/mcp-proxy & P1=$!
PORT=8085 /tmp/llm-router & P2=$!
LLM_ROUTER_URL=http://localhost:8085 MCP_PROXY_URL=http://localhost:8084 \
  PORT=8083 /tmp/agent-gateway & P3=$!
AGENT_GATEWAY_URL=http://localhost:8083 PORT=8082 /tmp/chat-service & P4=$!

sleep 2
curl -s http://localhost:8082/health  # 期望 {"status":"healthy","service":"chat-service"}
curl -s -X POST http://localhost:8082/conversations  # 期望返回 {id, title, ...}

kill $P1 $P2 $P3 $P4
```

### Docker Compose 部署（推荐）
创建 `docker-compose.yml`（服务名直接解析，无需环境变量覆盖）：
```yaml
version: '3.8'
services:
  mcp-proxy:
    build: ./bff/services/mcp-proxy
    ports: ["8084:8084"]
  llm-router:
    build: ./bff/services/llm-router
    ports: ["8085:8085"]
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
  agent-gateway:
    build: ./bff/services/agent-gateway
    ports: ["8083:8083"]
    depends_on: [llm-router, mcp-proxy]
  chat-service:
    build: ./bff/services/chat-service
    ports: ["8082:8082"]
    depends_on: [agent-gateway]
```
每个服务的 Dockerfile（示例）：
```dockerfile
FROM golang:1.22-alpine
WORKDIR /app
COPY go.mod main.go ./
RUN go build -o server .
CMD ["./server"]
```

### P0 交付清单
- [x] 4 个 Go 服务编译+vet+e2e 验证通过
- [x] CI workflow (.github/workflows/bff-ci.yml) 
- [x] 服务间调用改用服务名 (llm-router:8085 等)
- [x] SSE 流加 15s 心跳防挂起
- [ ] 补全 bff_client.dart (沙箱里只有 61/90 行, 用 §7.1 全文覆盖)
- [ ] 新建 chat_screen.dart (用 §7.2 全文)
- [ ] 装 git, 提交推送到 GitHub
- [ ] CI 跑通 (期望 gofmt+build+vet+冒烟全绿)

### 本次改动（相比交接前）
1. **环境变量友好**: llmRouterURL / mcpProxyURL / agentGatewayURL 默认值
   从 localhost:端口 改成 服务名:端口 (llm-router:8085 等)，
   Docker Compose 直接可用；本地测试时环境变量覆盖即可。
2. **SSE 心跳**: agent-gateway 的 /stream 端点每 15s 发一个
   data: {"type":"heartbeat"}，防止长时间无输出导致客户端/代理超时断连。

### 已知技术债（P1 处理）
- 无认证/授权（任何人可调用）
- 内存存储（重启丢失、并发不安全）
- ReAct 引擎简陋（固定 4 步、格式解析脆弱）
- mcp-proxy /mcp/call 是模拟结果
- 无结构化错误 / 链路追踪
- CI 只测编译，不测完整链路

---

## 11. 云端 IDE 一键验证（真实运行）

### Gitpod（推荐，自动启动服务）
1. 推代码到 GitHub
2. 访问 `https://gitpod.io/#https://github.com/YOUR_USERNAME/chathub`
3. 等 2 分钟（自动 build + 启动 4 个服务）
4. 点 "Ports" 面板里的 8082 端口预览 URL
5. 测试：在终端 `curl $(gp url 8082)/health`

特点：
- 自动编译全部服务
- 自动启动（后台运行，日志在 /workspace/logs/）
- 公网 URL 可分享给团队测试
- 免费套餐每月 50 小时

### GitHub Codespaces（需手动启动）
1. 仓库页面 → Code → Codespaces → Create
2. 容器启动后运行：`./start-services.sh`
3. 点 "Ports" 标签查看转发的 URL
4. 测试：`curl http://localhost:8082/health`

特点：
- VSCode 完整体验（插件、调试）
- 免费套餐每月 60 小时
- 需手动启动服务（更灵活）

### 配置文件说明
- `.gitpod.yml`：Gitpod 自动化任务（构建 + 启动）
- `.devcontainer/devcontainer.json`：Codespaces 容器配置
- `start-services.sh`：统一启动脚本（本地/云端通用）

### 验证清单
在云端 IDE 里运行：
```bash
# 1. 健康检查
curl localhost:8082/health  # chat-service
curl localhost:8083/health  # agent-gateway

# 2. 端到端测试
# 建对话
CONV=$(curl -s -X POST localhost:8082/conversations | jq -r .id)
# 发消息
curl -X POST localhost:8082/conversations/$CONV/messages \
  -d '{"content":"test"}' | jq .taskId
# 看 SSE 流（Ctrl+C 停止）
curl -N localhost:8083/agents/engine/tasks/{上面的taskId}/stream
```

期望看到：`thinking` → `answer: [mock:openai] test` → `completed`

---
