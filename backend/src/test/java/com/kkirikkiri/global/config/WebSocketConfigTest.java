package com.kkirikkiri.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WebSocketConfigTest {

	@Test
	void applicationSettingsRegisterNativeChatEndpoint() throws Exception {
		try (var context = new AnnotationConfigWebApplicationContext()) {
			context.setServletContext(new MockServletContext());
			context.getEnvironment().getPropertySources().addLast(
					new ResourcePropertySource("classpath:application.properties"));
			context.register(WebSocketConfig.class, TestConfig.class);
			context.refresh();
			assertEquals("/ws/chat", context.getEnvironment().getProperty("app.chat.websocket.endpoint"));
			var mapping = context.getBean("stompWebSocketHandlerMapping", SimpleUrlHandlerMapping.class);
			assertInstanceOf(WebSocketHttpRequestHandler.class, mapping.getUrlMap().get("/ws/chat"));
			assertEquals(1, mapping.getUrlMap().size());
		}
	}

	@Configuration
	static class TestConfig {
		@Bean
		ChatStompInterceptor chatStompInterceptor() {
			return mock(ChatStompInterceptor.class);
		}
	}
}
