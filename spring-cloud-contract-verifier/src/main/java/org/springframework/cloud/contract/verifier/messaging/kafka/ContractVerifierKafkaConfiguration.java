/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.contract.verifier.messaging.kafka;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifier;
import org.springframework.cloud.contract.verifier.messaging.integration.ContractVerifierIntegrationConfiguration;
import org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessage;
import org.springframework.cloud.contract.verifier.messaging.internal.ContractVerifierMessaging;
import org.springframework.cloud.contract.verifier.messaging.noop.NoOpContractVerifierAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

/**
 * @author Marcin Grzejszczak
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ KafkaTemplate.class, EmbeddedKafkaBroker.class })
@ConditionalOnProperty(name = "stubrunner.kafka.enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureBefore({ ContractVerifierIntegrationConfiguration.class, NoOpContractVerifierAutoConfiguration.class })
@ConditionalOnBean(EmbeddedKafkaBroker.class)
public class ContractVerifierKafkaConfiguration {

	private static final Log log = LogFactory.getLog(ContractVerifierKafkaConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	MessageVerifier<Message<?>> contractVerifierKafkaMessageExchange(KafkaTemplate kafkaTemplate,
			EmbeddedKafkaBroker broker, KafkaProperties kafkaProperties, KafkaStubMessagesInitializer initializer) {
		return new KafkaStubMessages(kafkaTemplate, broker, kafkaProperties, initializer);
	}

	@Bean
	@ConditionalOnMissingBean
	KafkaStubMessagesInitializer contractVerifierKafkaStubMessagesInitializer() {
		if (log.isDebugEnabled()) {
			log.debug("Registering contract verifier stub messages initializer");
		}
		return new ContractVerifierKafkaStubMessagesInitializer();
	}

	@Bean
	@ConditionalOnMissingBean
	ContractVerifierMessaging<Message<?>> contractVerifierKafkaMessaging(MessageVerifier<Message<?>> exchange) {
		return new ContractVerifierKafkaHelper(exchange);
	}

}

class ContractVerifierKafkaHelper extends ContractVerifierMessaging<Message<?>> {

	ContractVerifierKafkaHelper(MessageVerifier<Message<?>> exchange) {
		super(exchange);
	}

	@Override
	protected ContractVerifierMessage convert(Message<?> message) {
		return new ContractVerifierMessage(message.getPayload(), convertHeaders(message.getHeaders()));
	}

	private MessageHeaders convertHeaders(Map<String, Object> headers) {
		final Map<String, Object> headersMap = new HashMap<>();
		if (headers != null) {
			headers.forEach((k, v) -> headersMap.put(k, maybeConvertValue(v)));
		}
		return new MessageHeaders(headersMap);
	}

	private Object maybeConvertValue(Object value) {
		if (value == null) {
			return value;
		}
		if (!(value instanceof byte[])) {
			return value;
		}
		return new String((byte[]) value, StandardCharsets.UTF_8);
	}

}
