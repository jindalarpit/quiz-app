package com.quizplatform.websocket.config;

import com.quizplatform.websocket.handler.QuizWebSocketHandler;
import com.quizplatform.websocket.security.WebSocketAuthInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 64 * 1024; // 64KB

    private final QuizWebSocketHandler quizWebSocketHandler;
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Value("${websocket.allowed-origins:*}")
    private String allowedOrigins;

    public WebSocketConfig(
            QuizWebSocketHandler quizWebSocketHandler,
            WebSocketAuthInterceptor webSocketAuthInterceptor) {
        this.quizWebSocketHandler = quizWebSocketHandler;
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(quizWebSocketHandler, "/ws/{pin}")
                .addInterceptors(webSocketAuthInterceptor)
                .setAllowedOrigins(allowedOrigins.split(","));
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
        return container;
    }
}
