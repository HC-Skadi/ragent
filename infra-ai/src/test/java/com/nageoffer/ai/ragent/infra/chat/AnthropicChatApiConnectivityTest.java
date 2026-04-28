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

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Anthropic Messages API 格式连通性测试。
 * <p>
 * 需要配置有效的 MiniMax API Key，通过环境变量 {@code MINIMAX_API_KEY} 提供，
 * 且网络能连通 api.minimaxi.com。未满足条件时跳过测试。
 * 使用 MiniMax 的 Anthropic 兼容端点 {@code /anthropic/v1/messages}。
 */
class AnthropicChatApiConnectivityTest {

    private static final String ANTHROPIC_BASE_URL = "https://api.minimaxi.com";
    private static final String ANTHROPIC_MODEL = "MiniMax-M2.7";

    private static final OkHttpClient syncHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Test
    @DisplayName("Anthropic 格式 sync chat API 调用成功（MiniMax Anthropic 端点）")
    void anthropicSyncChat_success() {
        String apiKey = System.getenv("MINIMAX_API_KEY");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "MINIMAX_API_KEY 环境变量未配置，跳过测试");

        var providerConfig = new AIModelProperties.ProviderConfig();
        providerConfig.setUrl(ANTHROPIC_BASE_URL);
        providerConfig.setApiKey(apiKey);
        providerConfig.setEndpoints(Map.of("chat", "/anthropic/v1/messages"));

        var candidate = new AIModelProperties.ModelCandidate();
        candidate.setModel(ANTHROPIC_MODEL);
        candidate.setProvider("anthropic");
        candidate.setEnabled(true);

        var target = new ModelTarget("minimax-anthropic", candidate, providerConfig);

        var request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("请用一句话介绍你自己")))
                .temperature(0.7)
                .maxTokens(200)
                .build();

        var client = new TestableAnthropicChatClient(syncHttpClient, syncHttpClient);

        String response = client.chat(request, target);

        assertThat(response).isNotBlank();
        System.out.println("Anthropic（MiniMax）响应: " + response);
    }

    /**
     * 测试用子类，不加载 Spring 上下文，直接调用父类模板方法。
     */
    private static class TestableAnthropicChatClient extends AnthropicChatClient {
        public TestableAnthropicChatClient(OkHttpClient sync, OkHttpClient streaming) {
            super(sync, streaming, Executors.newSingleThreadExecutor());
        }

        @Override
        public String chat(ChatRequest request, ModelTarget target) {
            return doChat(request, target);
        }
    }
}
