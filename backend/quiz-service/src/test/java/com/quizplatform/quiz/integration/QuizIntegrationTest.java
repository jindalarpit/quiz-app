package com.quizplatform.quiz.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizplatform.quiz.dto.*;
import com.quizplatform.quiz.model.QuestionType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QuizIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quizplatform")
            .withUsername("quiz")
            .withPassword("quiz_test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    // Shared state across ordered tests
    private static UUID createdQuizId;
    private static UUID createdQuestionId;
    private static UUID secondQuestionId;

    // ==================== Quiz CRUD Tests ====================

    @Test
    @Order(1)
    void createQuiz_shouldReturn201WithQuizResponse() throws Exception {
        CreateQuizRequest request = CreateQuizRequest.builder()
                .title("Integration Test Quiz")
                .description("A quiz for integration testing")
                .coverImageUrl("https://example.com/cover.png")
                .build();

        MvcResult result = mockMvc.perform(post("/api/quizzes")
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Integration Test Quiz"))
                .andExpect(jsonPath("$.description").value("A quiz for integration testing"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://example.com/cover.png"))
                .andExpect(jsonPath("$.isPublished").value(false))
                .andExpect(jsonPath("$.questionCount").value(0))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        QuizResponse response = objectMapper.readValue(responseBody, QuizResponse.class);
        createdQuizId = response.getId();
    }

    @Test
    @Order(2)
    void listQuizzes_shouldReturn200WithPaginatedResults() throws Exception {
        mockMvc.perform(get("/api/quizzes")
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].title").value("Integration Test Quiz"))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.pageable").exists());
    }

    @Test
    @Order(3)
    void getQuiz_shouldReturn200WithQuizDetails() throws Exception {
        mockMvc.perform(get("/api/quizzes/{id}", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdQuizId.toString()))
                .andExpect(jsonPath("$.title").value("Integration Test Quiz"))
                .andExpect(jsonPath("$.description").value("A quiz for integration testing"))
                .andExpect(jsonPath("$.questions").isArray())
                .andExpect(jsonPath("$.questions", hasSize(0)));
    }

    @Test
    @Order(4)
    void updateQuiz_shouldReturn200WithUpdatedQuiz() throws Exception {
        UpdateQuizRequest request = UpdateQuizRequest.builder()
                .title("Updated Quiz Title")
                .description("Updated description")
                .build();

        mockMvc.perform(put("/api/quizzes/{id}", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdQuizId.toString()))
                .andExpect(jsonPath("$.title").value("Updated Quiz Title"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.coverImageUrl").value("https://example.com/cover.png"));
    }

    // ==================== Question CRUD Tests ====================

    @Test
    @Order(5)
    void addQuestion_shouldReturn201WithQuestionResponse() throws Exception {
        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .type(QuestionType.MCQ)
                .text("What is 2 + 2?")
                .options(List.of(
                        Map.of("id", "A", "text", "3"),
                        Map.of("id", "B", "text", "4"),
                        Map.of("id", "C", "text", "5"),
                        Map.of("id", "D", "text", "6")))
                .correctAnswer("B")
                .timeLimitSeconds(30)
                .points(1000)
                .build();

        MvcResult result = mockMvc.perform(post("/api/quizzes/{id}/questions", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("MCQ"))
                .andExpect(jsonPath("$.text").value("What is 2 + 2?"))
                .andExpect(jsonPath("$.correctAnswer").value("B"))
                .andExpect(jsonPath("$.timeLimitSeconds").value(30))
                .andExpect(jsonPath("$.points").value(1000))
                .andExpect(jsonPath("$.position").value(0))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        QuestionResponse response = objectMapper.readValue(responseBody, QuestionResponse.class);
        createdQuestionId = response.getId();
    }

    @Test
    @Order(6)
    void addQuestion_secondQuestion_shouldHavePosition1() throws Exception {
        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .type(QuestionType.TRUE_FALSE)
                .text("The sky is blue")
                .options(List.of(
                        Map.of("id", "A", "text", "True"),
                        Map.of("id", "B", "text", "False")))
                .correctAnswer("A")
                .timeLimitSeconds(15)
                .points(500)
                .build();

        MvcResult result = mockMvc.perform(post("/api/quizzes/{id}/questions", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.type").value("TRUE_FALSE"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        QuestionResponse response = objectMapper.readValue(responseBody, QuestionResponse.class);
        secondQuestionId = response.getId();
    }

    @Test
    @Order(7)
    void reorderQuestions_shouldReturn200() throws Exception {
        // Swap the order: second question first, first question second
        ReorderQuestionsRequest request = ReorderQuestionsRequest.builder()
                .questionIds(List.of(secondQuestionId, createdQuestionId))
                .build();

        mockMvc.perform(put("/api/quizzes/{id}/questions/reorder", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify the reorder by fetching the quiz
        mockMvc.perform(get("/api/quizzes/{id}", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].id").value(secondQuestionId.toString()))
                .andExpect(jsonPath("$.questions[1].id").value(createdQuestionId.toString()));
    }

    @Test
    @Order(8)
    void updateQuestion_shouldReturn200WithUpdatedQuestion() throws Exception {
        UpdateQuestionRequest request = UpdateQuestionRequest.builder()
                .text("What is 3 + 3?")
                .points(2000)
                .build();

        mockMvc.perform(put("/api/quizzes/{id}/questions/{qId}", createdQuizId, createdQuestionId)
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdQuestionId.toString()))
                .andExpect(jsonPath("$.text").value("What is 3 + 3?"))
                .andExpect(jsonPath("$.points").value(2000))
                .andExpect(jsonPath("$.type").value("MCQ"))
                .andExpect(jsonPath("$.correctAnswer").value("B"));
    }

    @Test
    @Order(9)
    void deleteQuestion_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/quizzes/{id}/questions/{qId}", createdQuizId, secondQuestionId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isNoContent());

        // Verify the question is gone
        mockMvc.perform(get("/api/quizzes/{id}", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions", hasSize(1)))
                .andExpect(jsonPath("$.questions[0].id").value(createdQuestionId.toString()));
    }

    // ==================== Duplicate Quiz Test ====================

    @Test
    @Order(10)
    void duplicateQuiz_shouldReturn201WithCopiedQuiz() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/quizzes/{id}/duplicate", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Updated Quiz Title (Copy)"))
                .andExpect(jsonPath("$.isPublished").value(false))
                .andExpect(jsonPath("$.questionCount").value(1))
                .andReturn();

        // Verify the duplicate has a different ID
        String responseBody = result.getResponse().getContentAsString();
        QuizResponse response = objectMapper.readValue(responseBody, QuizResponse.class);
        Assertions.assertNotEquals(createdQuizId, response.getId());
    }

    // ==================== Delete Quiz Test ====================

    @Test
    @Order(11)
    void deleteQuiz_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/quizzes/{id}", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isNoContent());

        // Verify the quiz is gone
        mockMvc.perform(get("/api/quizzes/{id}", createdQuizId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isNotFound());
    }

    // ==================== Authorization Tests ====================

    @Test
    @Order(12)
    void getQuiz_withDifferentUser_shouldReturn403() throws Exception {
        // Create a quiz as OWNER_ID
        CreateQuizRequest request = CreateQuizRequest.builder()
                .title("Private Quiz")
                .description("Only owner can access")
                .build();

        MvcResult result = mockMvc.perform(post("/api/quizzes")
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        QuizResponse response = objectMapper.readValue(responseBody, QuizResponse.class);
        UUID privateQuizId = response.getId();

        // Try to access with a different user
        mockMvc.perform(get("/api/quizzes/{id}", privateQuizId)
                        .header(USER_ID_HEADER, OTHER_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @Order(13)
    void updateQuiz_withDifferentUser_shouldReturn403() throws Exception {
        // Create a quiz as OWNER_ID
        CreateQuizRequest createRequest = CreateQuizRequest.builder()
                .title("Another Private Quiz")
                .build();

        MvcResult result = mockMvc.perform(post("/api/quizzes")
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        QuizResponse response = objectMapper.readValue(responseBody, QuizResponse.class);
        UUID quizId = response.getId();

        // Try to update with a different user
        UpdateQuizRequest updateRequest = UpdateQuizRequest.builder()
                .title("Hacked Title")
                .build();

        mockMvc.perform(put("/api/quizzes/{id}", quizId)
                        .header(USER_ID_HEADER, OTHER_USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @Order(14)
    void deleteQuiz_withDifferentUser_shouldReturn403() throws Exception {
        // Create a quiz as OWNER_ID
        CreateQuizRequest request = CreateQuizRequest.builder()
                .title("Quiz to Protect")
                .build();

        MvcResult result = mockMvc.perform(post("/api/quizzes")
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        QuizResponse response = objectMapper.readValue(responseBody, QuizResponse.class);
        UUID quizId = response.getId();

        // Try to delete with a different user
        mockMvc.perform(delete("/api/quizzes/{id}", quizId)
                        .header(USER_ID_HEADER, OTHER_USER_ID.toString()))
                .andExpect(status().isForbidden());
    }

    // ==================== Not Found Tests ====================

    @Test
    @Order(15)
    void getQuiz_nonExistent_shouldReturn404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/quizzes/{id}", nonExistentId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @Order(16)
    void updateQuiz_nonExistent_shouldReturn404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        UpdateQuizRequest request = UpdateQuizRequest.builder()
                .title("Ghost Quiz")
                .build();

        mockMvc.perform(put("/api/quizzes/{id}", nonExistentId)
                        .header(USER_ID_HEADER, OWNER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @Order(17)
    void deleteQuiz_nonExistent_shouldReturn404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(delete("/api/quizzes/{id}", nonExistentId)
                        .header(USER_ID_HEADER, OWNER_ID.toString()))
                .andExpect(status().isNotFound());
    }

    // ==================== List Isolation Test ====================

    @Test
    @Order(18)
    void listQuizzes_shouldOnlyReturnOwnQuizzes() throws Exception {
        // OTHER_USER_ID should see no quizzes (they haven't created any)
        mockMvc.perform(get("/api/quizzes")
                        .header(USER_ID_HEADER, OTHER_USER_ID.toString())
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
