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
     *
     * @param quizId the quiz UUID
     * @return list of QuestionDTOs, or empty list if the circuit is open
     */
    @CircuitBreaker(name = "quizService", fallbackMethod = "getQuestionsFallback")
    public List<QuestionDTO> getQuestions(UUID quizId) {
        String url = quizServiceUrl + "/api/quizzes/" + quizId + "/questions";
        log.debug("Fetching questions from Quiz Service: {}", url);

        ResponseEntity<List<QuestionDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<QuestionDTO>>() {}
        );
        return response.getBody();
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
    private List<QuestionDTO> getQuestionsFallback(UUID quizId, Throwable throwable) {
        log.error("Circuit breaker fallback: failed to fetch questions for quiz {}: {}",
                quizId, throwable.getMessage());
        return Collections.emptyList();
    }
}
