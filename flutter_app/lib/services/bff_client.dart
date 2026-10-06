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

  // Creates a new conversation.
  Future<Map<String, dynamic>> createConversation() async {
    final r = await _dio.post("$baseUrl/conversations", options: _jsonOpts());
    return r.data as Map<String, dynamic>;
  }

  // Sends a message; returns the accepted payload (taskId + streamUrl).
  Future<Map<String, dynamic>> sendMessage(String conversationId, String content) async {
    final r = await _dio.post(
      "$baseUrl/conversations/$conversationId/messages",
      data: {"role": "user", "content": content},
      options: _jsonOpts(),
    );
    return r.data as Map<String, dynamic>;
  }

  // Streams agent task events via SSE, emitting decoded event payloads.
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

  Options _jsonOpts() =>
      Options(headers: _headers(), sendTimeout: 30 * 1000);
