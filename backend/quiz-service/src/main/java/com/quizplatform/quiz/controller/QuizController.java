package com.quizplatform.quiz.controller;

import com.quizplatform.quiz.dto.*;
import com.quizplatform.quiz.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping
    public ResponseEntity<QuizResponse> createQuiz(
            @RequestHeader("X-User-Id") UUID ownerId,
            @Valid @RequestBody CreateQuizRequest request) {
        QuizResponse response = quizService.createQuiz(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<QuizResponse>> listQuizzes(
            @RequestHeader("X-User-Id") UUID ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<QuizResponse> quizzes = quizService.listQuizzes(ownerId, pageable);
        return ResponseEntity.ok(quizzes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuizDetailResponse> getQuiz(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id) {
        QuizDetailResponse response = quizService.getQuiz(id, ownerId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuizResponse> updateQuiz(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuizRequest request) {
        QuizResponse response = quizService.updateQuiz(id, ownerId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuiz(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id) {
        quizService.deleteQuiz(id, ownerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<QuizResponse> duplicateQuiz(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id) {
        QuizResponse response = quizService.duplicateQuiz(id, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/questions")
    public ResponseEntity<QuestionResponse> addQuestion(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateQuestionRequest request) {
        QuestionResponse response = quizService.addQuestion(id, ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/questions/reorder")
    public ResponseEntity<Void> reorderQuestions(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id,
            @Valid @RequestBody ReorderQuestionsRequest request) {
        quizService.reorderQuestions(id, ownerId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/questions/{qId}")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id,
            @PathVariable UUID qId,
            @Valid @RequestBody UpdateQuestionRequest request) {
        QuestionResponse response = quizService.updateQuestion(id, qId, ownerId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/questions/{qId}")
    public ResponseEntity<Void> deleteQuestion(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id,
            @PathVariable UUID qId) {
        quizService.deleteQuestion(id, qId, ownerId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<QuizResponse> updateScoringMode(
            @RequestHeader("X-User-Id") UUID ownerId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScoringModeRequest request) {
        QuizResponse response = quizService.updateScoringMode(id, ownerId, request);
        return ResponseEntity.ok(response);
    }
}
