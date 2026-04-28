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

import com.nageoffer.ai.ragent.infra.enums.ModelProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekChatClientTest {

    @Test
    @DisplayName("provider() 应返回 deepseek")
    void provider_returnsDeepSeek() {
        var client = new TestableDeepSeekChatClient();

        String result = client.provider();

        assertThat(result).isEqualTo("deepseek");
    }

    @Test
    @DisplayName("DeepSeekChatClient 应正确实现 ChatClient 接口")
    void implementsChatClient() {
        DeepSeekChatClient client = new TestableDeepSeekChatClient();

        assertThat(client.provider()).isEqualTo(ModelProvider.DEEPSEEK.getId());
        assertThat(client.provider()).isEqualTo("deepseek");
    }

    @Test
    @DisplayName("ModelProvider.DEEPSEEK 应正确匹配字符串")
    void modelProviderMatchesString() {
        assertThat(ModelProvider.DEEPSEEK.matches("deepseek")).isTrue();
        assertThat(ModelProvider.DEEPSEEK.matches("DeepSeek")).isTrue();
        assertThat(ModelProvider.DEEPSEEK.matches("DEEPSEEK")).isTrue();
        assertThat(ModelProvider.DEEPSEEK.matches("other")).isFalse();
        assertThat(ModelProvider.DEEPSEEK.matches(null)).isFalse();
    }

    @Test
    @DisplayName("ModelProvider 枚举应包含 DEEPSEEK")
    void modelProviderEnumHasDeepSeek() {
        assertThat(ModelProvider.values())
                .extracting(ModelProvider::getId)
                .contains("deepseek");
    }

    private static class TestableDeepSeekChatClient extends DeepSeekChatClient {
        public TestableDeepSeekChatClient() {
            super(null, null, Runnable::run);
        }
    }
}
