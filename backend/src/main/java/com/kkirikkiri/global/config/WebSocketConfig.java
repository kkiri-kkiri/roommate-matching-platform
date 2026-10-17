package com.kkirikkiri.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final ChatStompInterceptor chatStompInterceptor;
	private final String[] allowedOrigins;
	private final String endpoint;

	public WebSocketConfig(ChatStompInterceptor chatStompInterceptor,
			@Value("${app.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins,
			@Value("${app.chat.websocket.endpoint:}") String endpoint) {
		this.chatStompInterceptor = chatStompInterceptor;
		this.allowedOrigins = allowedOrigins;
		this.endpoint = endpoint;
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes("/pub");
		registry.enableSimpleBroker("/sub");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// application.properties의 연결 경로와 기존 CORS 정책을 사용한다.
		if (!endpoint.isBlank()) {
			registry.addEndpoint(endpoint).setAllowedOrigins(allowedOrigins);
		}
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(chatStompInterceptor);
	}

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new org.springframework.messaging.support.ExecutorChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> beforeHandle(
                    org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel,
                    org.springframework.messaging.MessageHandler handler) {
                return chatStompInterceptor.beforeHandle(message, channel, handler);
            }
        });
    }
}
