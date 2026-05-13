package com.quizplatform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.main.web-application-type=reactive"
})
class GatewayApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the application context loads successfully
    }
}
