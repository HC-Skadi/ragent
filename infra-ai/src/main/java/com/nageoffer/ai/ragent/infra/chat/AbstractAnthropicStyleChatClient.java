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

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import com.nageoffer.ai.ragent.infra.http.HttpMediaTypes;
import com.nageoffer.ai.ragent.infra.http.ModelClientErrorType;
import com.nageoffer.ai.ragent.infra.http.ModelClientException;
import com.nageoffer.ai.ragent.infra.http.ModelUrlResolver;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Anthropic 协议风格 ChatClient 抽象基类
 * <p>
 * 支持 Anthropic Messages API 格式：
 * <ul>
 *   <li>认证：x-api-key 请求头</li>
 *   <li>版本：anthropic-version 请求头</li>
 *   <li>请求体：{@code model, max_tokens, messages, system, temperature, top_p, stream}</li>
 *   <li>响应体：{@code content[{type, text}]}</li>
 *   <li>流式：基于命名事件的 SSE（content_block_delta / message_stop）</li>
 * </ul>
 */
@Slf4j
public abstract class AbstractAnthropicStyleChatClient implements ChatClient {

    protected static final String ANTHROPIC_VERSION = "2023-06-01";

    protected final OkHttpClient syncHttpClient;
    protected final OkHttpClient streamingHttpClient;
    protected final Executor modelStreamExecutor;
    protected final Gson gson = new Gson();

    protected AbstractAnthropicStyleChatClient(OkHttpClient syncHttpClient,
                                                OkHttpClient streamingHttpClient,
                                                Executor modelStreamExecutor) {
        this.syncHttpClient = syncHttpClient;
        this.streamingHttpClient = streamingHttpClient;
        this.modelStreamExecutor = modelStreamExecutor;
    }

    // ==================== 子类钩子方法 ====================

    /**
     * 子类可覆写此方法添加提供商特有的请求体字段
     */
    protected void customizeRequestBody(JsonObject body, ChatRequest request) {
    }

    /**
     * 子类可覆写此方法添加提供商特有的请求头
     */
    protected void customizeHeaders(Request.Builder builder, ChatRequest request) {
    }

    /**
     * 是否要求提供商配置 API Key
     */
    protected boolean requiresApiKey() {
        return true;
    }

    // ==================== 模板方法：同步调用 ====================

    protected String doChat(ChatRequest request, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = requireProvider(target);
        if (requiresApiKey()) {
            requireApiKey(provider);
        }

        JsonObject reqBody = buildRequestBody(request, target, false);
        Request httpRequest = newAuthorizedRequest(provider, target, request)
                .post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
                .build();

        JsonObject respJson;
        try (Response response = syncHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String body = readBody(response.body());
                log.warn("{} 同步请求失败: status={}, body={}", provider(), response.code(), body);
                throw new ModelClientException(
                        provider() + " 同步请求失败: HTTP " + response.code(),
                        ModelClientErrorType.fromHttpStatus(response.code()),
                        response.code()
                );
            }
            respJson = parseJson(response.body());
        } catch (IOException e) {
            throw new ModelClientException(
                    provider() + " 同步请求失败: " + e.getMessage(),
                    ModelClientErrorType.NETWORK_ERROR, null, e);
        }

        return extractChatContent(respJson);
    }

    // ==================== 模板方法：流式调用 ====================

    protected StreamCancellationHandle doStreamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = requireProvider(target);
        if (requiresApiKey()) {
            requireApiKey(provider);
        }

        JsonObject reqBody = buildRequestBody(request, target, true);
        Request streamRequest = newAuthorizedRequest(provider, target, request)
                .post(RequestBody.create(reqBody.toString(), HttpMediaTypes.JSON))
                .addHeader("Accept", "text/event-stream")
                .build();

        Call call = streamingHttpClient.newCall(streamRequest);
        return StreamAsyncExecutor.submit(
                modelStreamExecutor,
                call,
                callback,
                cancelled -> doStream(call, callback, cancelled)
        );
    }

    private void doStream(Call call, StreamCallback callback, AtomicBoolean cancelled) {
        try (Response response = call.execute()) {
            if (!response.isSuccessful()) {
                String body = readBody(response.body());
                throw new ModelClientException(
                        provider() + " 流式请求失败: HTTP " + response.code() + " - " + body,
                        ModelClientErrorType.fromHttpStatus(response.code()),
                        response.code()
                );
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ModelClientException(provider() + " 流式响应为空", ModelClientErrorType.INVALID_RESPONSE, null);
            }
            BufferedSource source = body.source();
            String currentEvent = null;
            boolean completed = false;

            while (!cancelled.get()) {
                String line = source.readUtf8Line();
                if (line == null) {
                    break;
                }

                AnthropicSseParser.ParseResult result = AnthropicSseParser.parseLine(line, currentEvent, gson);

                // 更新 event 状态
                if (result.isEventUpdate()) {
                    currentEvent = result.updatedEvent();
                    continue;
                }

                if (result.hasReasoning()) {
                    callback.onThinking(result.reasoning());
                }
                if (result.hasContent()) {
                    callback.onContent(result.content());
                }
                if (result.completed()) {
                    callback.onComplete();
                    completed = true;
                    break;
                }
            }

            if (cancelled.get()) {
                log.info("{} 流式响应已被取消", provider());
                return;
            }
            if (!completed) {
                throw new ModelClientException(provider() + " 流式响应异常结束", ModelClientErrorType.INVALID_RESPONSE, null);
            }
        } catch (Exception e) {
            if (!cancelled.get()) {
                callback.onError(e);
            } else {
                log.info("{} 流式响应取消期间产生异常（可忽略）: {}", provider(), e.getMessage());
            }
        }
    }

    // ==================== 公共构建方法 ====================

    protected JsonObject buildRequestBody(ChatRequest request, ModelTarget target, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", requireModel(target));
        body.addProperty("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);

        if (stream) {
            body.addProperty("stream", true);
        }

        // system 提示词（Anthropic 格式：与 messages 分离）
        List<ChatMessage> messages = request.getMessages();
        if (CollUtil.isNotEmpty(messages)) {
            // 提取 system 消息
            StringBuilder systemBuilder = new StringBuilder();
            JsonArray msgArray = new JsonArray();
            for (ChatMessage msg : messages) {
                if (msg.getRole() == ChatMessage.Role.SYSTEM) {
                    if (systemBuilder.length() > 0) systemBuilder.append("\n");
                    systemBuilder.append(msg.getContent());
                } else {
                    JsonObject jsonMsg = new JsonObject();
                    jsonMsg.addProperty("role", toAnthropicRole(msg.getRole()));
                    jsonMsg.addProperty("content", msg.getContent());
                    msgArray.add(jsonMsg);
                }
            }
            if (systemBuilder.length() > 0) {
                body.addProperty("system", systemBuilder.toString());
            }
            body.add("messages", msgArray);
        }

        if (request.getTemperature() != null) {
            body.addProperty("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.addProperty("top_p", request.getTopP());
        }

        customizeRequestBody(body, request);
        return body;
    }

    // ==================== 认证与请求构建 ====================

    private Request.Builder newAuthorizedRequest(AIModelProperties.ProviderConfig provider,
                                                  ModelTarget target,
                                                  ChatRequest request) {
        Request.Builder builder = new Request.Builder()
                .url(ModelUrlResolver.resolveUrl(provider, target.candidate(), ModelCapability.CHAT));
        if (requiresApiKey()) {
            builder.addHeader("x-api-key", provider.getApiKey());
        }
        builder.addHeader("anthropic-version", ANTHROPIC_VERSION);
        customizeHeaders(builder, request);
        return builder;
    }

    // ==================== 响应提取 ====================

    private String extractChatContent(JsonObject respJson) {
        if (respJson == null) {
            throw new ModelClientException(provider() + " 响应为空", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        String text = AnthropicSseParser.extractTextContent(respJson);
        if (text == null) {
            throw new ModelClientException(provider() + " 响应缺少 text 内容块", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        return text;
    }

    // ==================== 工具方法 ====================

    private String toAnthropicRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private AIModelProperties.ProviderConfig requireProvider(ModelTarget target) {
        if (target == null || target.provider() == null) {
            throw new IllegalStateException(provider() + " 提供商配置缺失");
        }
        return target.provider();
    }

    private void requireApiKey(AIModelProperties.ProviderConfig provider) {
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            throw new ModelClientException(
                    provider() + " API Key 未配置", ModelClientErrorType.UNAUTHORIZED, 401);
        }
    }

    private String requireModel(ModelTarget target) {
        if (target == null || target.candidate() == null || target.candidate().getModel() == null) {
            throw new ModelClientException(
                    provider() + " 模型名称未配置", ModelClientErrorType.CLIENT_ERROR, null);
        }
        return target.candidate().getModel();
    }

    private String readBody(ResponseBody body) {
        if (body == null) return "";
        try {
            return body.string();
        } catch (IOException e) {
            return "";
        }
    }

    private JsonObject parseJson(ResponseBody body) {
        if (body == null) {
            throw new ModelClientException(provider() + " 响应体为空", ModelClientErrorType.INVALID_RESPONSE, null);
        }
        try {
            return gson.fromJson(body.string(), JsonObject.class);
        } catch (IOException e) {
            throw new ModelClientException(
                    provider() + " 响应解析失败", ModelClientErrorType.INVALID_RESPONSE, null, e);
        }
    }
}
