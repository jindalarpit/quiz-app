package com.quizplatform.session.client;

import com.quizplatform.common.dto.QuestionDTO;
import com.quizplatform.common.dto.QuizDTO;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Client for communicating with the Quiz Service.
 * Uses Resilience4j circuit breaker to handle failures gracefully.
 */
@Slf4j
@Component
public class QuizServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.quiz-service.url:http://localhost:8081}")
    private String quizServiceUrl;

    public QuizServiceClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Fetch quiz details by ID from the Quiz Service.
     *
     * @param quizId the quiz UUID
     * @return the QuizDTO, or null if the circuit is open or the call fails
     */
    @CircuitBreaker(name = "quizService", fallbackMethod = "getQuizFallback")
    public QuizDTO getQuiz(UUID quizId) {
        String url = quizServiceUrl + "/api/quizzes/" + quizId;
        log.debug("Fetching quiz from Quiz Service: {}", url);

        ResponseEntity<QuizDTO> response =
                restTemplate.getForEntity(url, QuizDTO.class);
        return response.getBody();
    }

    /**
     * Fetch all questions for a quiz from the Quiz Service.
     * Uses the quiz detail endpoint which includes questions in the response.
     *
     * @param quizId the quiz UUID
     * @param hostId the host user ID (required for quiz access)
     * @return list of QuestionDTOs, or empty list if the circuit is open
     */
    @CircuitBreaker(name = "quizService", fallbackMethod = "getQuestionsFallback")
    public List<QuestionDTO> getQuestions(UUID quizId, UUID hostId) {
        String url = quizServiceUrl + "/api/quizzes/" + quizId;
        log.debug("Fetching quiz with questions from Quiz Service: {}", url);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-User-Id", hostId.toString());
        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

        ResponseEntity<QuizDTO> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                QuizDTO.class
        );

        QuizDTO quiz = response.getBody();
        if (quiz != null && quiz.getQuestions() != null) {
            return quiz.getQuestions();
        }
        return Collections.emptyList();
    }

    /**
     * Fallback for getQuiz when circuit breaker is open or call fails.
     */
    @SuppressWarnings("unused")
    private QuizDTO getQuizFallback(UUID quizId, Throwable throwable) {
        log.error("Circuit breaker fallback: failed to fetch quiz {}: {}",
                quizId, throwable.getMessage());
        return null;
    }

    /**
     * Fallback for getQuestions when circuit breaker is open or call fails.
     */
    @SuppressWarnings("unused")
    private List<QuestionDTO> getQuestionsFallback(UUID quizId, UUID hostId, Throwable throwable) {
        log.error("Circuit breaker fallback: failed to fetch questions for quiz {}: {}",
                quizId, throwable.getMessage());
        return Collections.emptyList();
    }
}
