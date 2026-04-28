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

class MinimaxChatClientTest {

    @Test
    @DisplayName("provider() 应返回 minimax")
    void provider_returnsMinimax() {
        // Given
        var client = new TestableMinimaxChatClient();

        // When
        String result = client.provider();

        // Then
        assertThat(result).isEqualTo("minimax");
    }

    @Test
    @DisplayName("MinimaxChatClient 应正确实现 ChatClient 接口")
    void implementsChatClient() {
        // Given
        MinimaxChatClient client = new TestableMinimaxChatClient();

        // When & Then
        assertThat(client.provider()).isEqualTo(ModelProvider.MINIMAX.getId());
        assertThat(client.provider()).isEqualTo("minimax");
    }

    @Test
    @DisplayName("ModelProvider.MINIMAX 应正确匹配字符串")
    void modelProviderMatchesString() {
        // Given
        String providerStr = "minimax";

        // When & Then
        assertThat(ModelProvider.MINIMAX.matches(providerStr)).isTrue();
        assertThat(ModelProvider.MINIMAX.matches("Minimax")).isTrue();
        assertThat(ModelProvider.MINIMAX.matches("MINIMAX")).isTrue();
        assertThat(ModelProvider.MINIMAX.matches("other")).isFalse();
        assertThat(ModelProvider.MINIMAX.matches(null)).isFalse();
    }

    @Test
    @DisplayName("ModelProvider 枚举应包含 MINIMAX")
    void modelProviderEnumHasMinimax() {
        // When & Then
        assertThat(ModelProvider.values())
                .extracting(ModelProvider::getId)
                .contains("minimax");
    }

    /**
     * 测试用子类，绕过构造函数依赖注入
     */
    private static class TestableMinimaxChatClient extends MinimaxChatClient {
        public TestableMinimaxChatClient() {
            super(null, null, Runnable::run);
        }
    }
}