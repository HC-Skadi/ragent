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

package com.nageoffer.ai.ragent.rag.mq;

import com.nageoffer.ai.ragent.framework.mq.MessageWrapper;
import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import com.nageoffer.ai.ragent.rag.mq.event.MessageFeedbackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 消息反馈 MQ 生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageFeedbackProducer {

    public static final String TOPIC = "mq:message-feedback:topic";

    private final MessageQueueProducer messageQueueProducer;

    /**
     * 发送点赞/点踩反馈事件
     */
    public void sendFeedbackEvent(MessageFeedbackEvent event) {
        MessageWrapper<MessageFeedbackEvent> wrapper = MessageWrapper.<MessageFeedbackEvent>builder()
                .topic(TOPIC)
                .keys(event.getUserId() + ":" + event.getMessageId())
                .body(event)
                .build();
        messageQueueProducer.send(wrapper);
        log.info("点赞/点踩事件已发送，messageId: {}, userId: {}, vote: {}",
                event.getMessageId(), event.getUserId(), event.getVote());
    }
}
