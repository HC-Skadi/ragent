/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.infra.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.NoArgsConstructor;

/**
 * Anthropic 协议风格 SSE 解析器
 * <p>
 * Anthropic 流式 SSE 格式：
 * <pre>
 * event: content_block_delta
 * data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
 *
 * event: content_block_stop
 * data: {"type":"content_block_stop","index":0}
 *
 * event: message_stop
 * data: {"type":"message_stop"}
 * </pre>
 * 解析策略：读取每行，缓存 event 行，在 data 行到达时根据 event 类型做对应解析。
 */
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class AnthropicSseParser {

    private static final String DATA_PREFIX = "data:";
    private static final String EVENT_PREFIX = "event:";

    /**
     * 解析一行 SSE 数据。需要维护 eventType 状态（上次的 event: 行值）。
     *
     * @param line          当前行
     * @param currentEvent  当前 event 类型（从上一次 event: 行继承），可为 null
     * @param gson          Gson 实例
     * @return 解析结果
     */
    static ParseResult parseLine(String line, String currentEvent, Gson gson) {
        if (line == null || line.isBlank()) {
            return ParseResult.skip(currentEvent);
        }

        String trimmed = line.trim();

        // 缓存 event: 行，不产生输出
        if (trimmed.startsWith(EVENT_PREFIX)) {
            String event = trimmed.substring(EVENT_PREFIX.length()).trim();
            return ParseResult.updateEvent(event);
        }

        // 处理 data: 行
        if (trimmed.startsWith(DATA_PREFIX)) {
            String payload = trimmed.substring(DATA_PREFIX.length()).trim();
            if (payload.isEmpty()) {
                return ParseResult.skip(currentEvent);
            }

            JsonObject obj = gson.fromJson(payload, JsonObject.class);
            if (obj == null) {
                return ParseResult.skip(currentEvent);
            }

            String type = obj.has("type") && !obj.get("type").isJsonNull()
                    ? obj.get("type").getAsString() : null;

            // 流式内容增量
            if ("content_block_delta".equals(type)) {
                return parseContentBlockDelta(obj, currentEvent);
            }

            // 消息完成
            if ("message_stop".equals(type)) {
                return ParseResult.completed(null, null);
            }

            // message_delta 可能包含 stop_reason
            if ("message_delta".equals(type)) {
                JsonObject delta = obj.getAsJsonObject("delta");
                if (delta != null && delta.has("stop_reason")
                        && !delta.get("stop_reason").isJsonNull()) {
                    return ParseResult.completed(null, null);
                }
                return ParseResult.skip(currentEvent);
            }

            // message_start / content_block_start / content_block_stop — 忽略
            return ParseResult.skip(currentEvent);
        }

        return ParseResult.skip(currentEvent);
    }

    private static ParseResult parseContentBlockDelta(JsonObject obj, String currentEvent) {
        JsonObject delta = obj.getAsJsonObject("delta");
        if (delta == null) {
            return ParseResult.skip(currentEvent);
        }
        String deltaType = delta.has("type") && !delta.get("type").isJsonNull()
                ? delta.get("type").getAsString() : null;

        // 文本内容
        if ("text_delta".equals(deltaType)) {
            String text = delta.has("text") && !delta.get("text").isJsonNull()
                    ? delta.get("text").getAsString() : null;
            return ParseResult.content(text, null);
        }

        // 思考/推理内容
        if ("thinking_delta".equals(deltaType)) {
            String thinking = delta.has("thinking") && !delta.get("thinking").isJsonNull()
                    ? delta.get("thinking").getAsString() : null;
            return ParseResult.content(null, thinking);
        }

        return ParseResult.skip(currentEvent);
    }

    /**
     * 同步响应的 content 提取 — 从 content 数组中找出 type=text 的块
     */
    static String extractTextContent(JsonObject response) {
        if (response == null || !response.has("content")) {
            return null;
        }
        var contentArray = response.getAsJsonArray("content");
        if (contentArray == null || contentArray.isEmpty()) {
            return null;
        }
        for (var element : contentArray) {
            if (element != null && element.isJsonObject()) {
                JsonObject block = element.getAsJsonObject();
                var typeEl = block.get("type");
                if (typeEl != null && "text".equals(typeEl.getAsString())) {
                    return block.has("text") ? block.get("text").getAsString() : null;
                }
            }
        }
        return null;
    }

    /**
     * 解析结果，包含更新的 event 状态、内容、推理内容和完成标志
     */
    record ParseResult(String updatedEvent, String content, String reasoning, boolean completed) {

        static ParseResult skip(String currentEvent) {
            return new ParseResult(currentEvent, null, null, false);
        }

        static ParseResult updateEvent(String event) {
            return new ParseResult(event, null, null, false);
        }

        static ParseResult content(String content, String reasoning) {
            return new ParseResult(null, content, reasoning, false);
        }

        static ParseResult completed(String content, String reasoning) {
            return new ParseResult(null, content, reasoning, true);
        }

        boolean hasContent() {
            return content != null && !content.isEmpty();
        }

        boolean hasReasoning() {
            return reasoning != null && !reasoning.isEmpty();
        }

        boolean isEventUpdate() {
            return updatedEvent != null && content == null && reasoning == null && !completed;
        }
    }
}
