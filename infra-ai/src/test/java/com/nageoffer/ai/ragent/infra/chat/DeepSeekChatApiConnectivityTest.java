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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeepSeek 模型 API 连通性测试。
 * <p>
 * 需要配置有效的 DeepSeek API Key，通过环境变量 {@code DEEPSEEK_API_KEY} 提供。
 * 未配置时跳过测试。
 */
class DeepSeekChatApiConnectivityTest {

    private static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    private static final String DEEPSEEK_MODEL = "deepseek-chat";

    private static final OkHttpClient syncHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final OkHttpClient streamingHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Test
    @DisplayName("DeepSeek 同步 chat API 调用成功")
    void deepseekSyncChat_success() {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "DEEPSEEK_API_KEY 环境变量未配置，跳过测试");

        var providerConfig = new AIModelProperties.ProviderConfig();
        providerConfig.setUrl(DEEPSEEK_BASE_URL);
        providerConfig.setApiKey(apiKey);
        providerConfig.setEndpoints(Map.of("chat", "/chat/completions"));

        var candidate = new AIModelProperties.ModelCandidate();
        candidate.setModel(DEEPSEEK_MODEL);
        candidate.setProvider("deepseek");
        candidate.setEnabled(true);

        var target = new ModelTarget("deepseek-chat", candidate, providerConfig);

        var request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("请用一句话介绍你自己")))
                .temperature(0.7)
                .maxTokens(200)
                .build();

        var client = new TestableDeepSeekChatClient(syncHttpClient, streamingHttpClient);

        String response = client.chat(request, target);

        assertThat(response).isNotBlank();
        System.out.println("DeepSeek 响应: " + response);
    }

    /**
     * 测试用子类，不加载 Spring 上下文，直接调用父类模板方法。
     */
    private static class TestableDeepSeekChatClient extends DeepSeekChatClient {
        public TestableDeepSeekChatClient(OkHttpClient sync, OkHttpClient streaming) {
            super(sync, streaming, Executors.newSingleThreadExecutor());
        }

        @Override
        public String chat(ChatRequest request, ModelTarget target) {
            return doChat(request, target);
        }
    }
}
