package com.quizplatform.quiz.service;

import com.quizplatform.common.exception.ForbiddenException;
import com.quizplatform.common.exception.ResourceNotFoundException;
import com.quizplatform.common.exception.ValidationException;
import com.quizplatform.quiz.dto.*;
import com.quizplatform.quiz.model.Question;
import com.quizplatform.quiz.model.QuestionType;
import com.quizplatform.quiz.model.Quiz;
import com.quizplatform.quiz.repository.QuestionRepository;
import com.quizplatform.quiz.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;

    @Transactional
    public QuizResponse createQuiz(UUID ownerId, CreateQuizRequest request) {
        Quiz quiz = Quiz.builder()
                .ownerId(ownerId)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverImageUrl(request.getCoverImageUrl())
                .isPublished(false)
                .build();

        Quiz saved = quizRepository.save(quiz);
        log.info("Quiz created: id={}, owner={}", saved.getId(), ownerId);
        return toQuizResponse(saved, 0);
    }

    @Transactional(readOnly = true)
    public Page<QuizResponse> listQuizzes(UUID ownerId, Pageable pageable) {
        return quizRepository.findByOwnerId(ownerId, pageable)
                .map(quiz -> toQuizResponse(quiz, questionRepository.countByQuizId(quiz.getId())));
    }

    @Transactional(readOnly = true)
    public QuizDetailResponse getQuiz(UUID quizId, UUID ownerId) {
        Quiz quiz = findQuizAndVerifyOwnership(quizId, ownerId);
        List<Question> questions = questionRepository.findByQuizIdOrderByPositionAsc(quizId);

        return QuizDetailResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .coverImageUrl(quiz.getCoverImageUrl())
                .isPublished(quiz.getIsPublished())
                .scoringMode(quiz.getScoringMode())
                .settings(quiz.getSettings())
                .questionCount(questions.size())
                .createdAt(quiz.getCreatedAt())
                .updatedAt(quiz.getUpdatedAt())
                .questions(questions.stream().map(this::toQuestionResponse).collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public QuizResponse updateQuiz(UUID quizId, UUID ownerId, UpdateQuizRequest request) {
        Quiz quiz = findQuizAndVerifyOwnership(quizId, ownerId);

        if (request.getTitle() != null) {
            quiz.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            quiz.setDescription(request.getDescription());
        }
        if (request.getCoverImageUrl() != null) {
            quiz.setCoverImageUrl(request.getCoverImageUrl());
        }
        if (request.getScoringMode() != null) {
            quiz.setScoringMode(request.getScoringMode());
        }

        Quiz saved = quizRepository.save(quiz);
        int questionCount = questionRepository.countByQuizId(quizId);
        log.info("Quiz updated: id={}", quizId);
        return toQuizResponse(saved, questionCount);
    }

    @Transactional
    public void deleteQuiz(UUID quizId, UUID ownerId) {
        Quiz quiz = findQuizAndVerifyOwnership(quizId, ownerId);
        quizRepository.delete(quiz);
        log.info("Quiz deleted: id={}", quizId);
    }

    @Transactional
    public QuizResponse duplicateQuiz(UUID quizId, UUID ownerId) {
        Quiz original = findQuizAndVerifyOwnership(quizId, ownerId);
        List<Question> originalQuestions = questionRepository.findByQuizIdOrderByPositionAsc(quizId);

        Quiz duplicate = Quiz.builder()
                .ownerId(ownerId)
                .title(original.getTitle() + " (Copy)")
                .description(original.getDescription())
                .coverImageUrl(original.getCoverImageUrl())
                .isPublished(false)
                .settings(original.getSettings())
                .build();

        Quiz savedDuplicate = quizRepository.save(duplicate);

        List<Question> duplicatedQuestions = new ArrayList<>();
        for (Question q : originalQuestions) {
            Question copy = Question.builder()
                    .quiz(savedDuplicate)
                    .type(q.getType())
                    .text(q.getText())
                    .options(q.getOptions() != null ? new ArrayList<>(q.getOptions()) : null)
                    .correctAnswer(q.getCorrectAnswer())
                    .timeLimitSeconds(q.getTimeLimitSeconds())
                    .points(q.getPoints())
                    .position(q.getPosition())
                    .mediaUrl(q.getMediaUrl())
                    .build();
            duplicatedQuestions.add(copy);
        }
        questionRepository.saveAll(duplicatedQuestions);

        log.info("Quiz duplicated: original={}, duplicate={}", quizId, savedDuplicate.getId());
        return toQuizResponse(savedDuplicate, duplicatedQuestions.size());
    }

    @Transactional
    public QuestionResponse addQuestion(UUID quizId, UUID ownerId, CreateQuestionRequest request) {
        Quiz quiz = findQuizAndVerifyOwnership(quizId, ownerId);
        int currentCount = questionRepository.countByQuizId(quizId);

        Question question = Question.builder()
                .quiz(quiz)
                .type(request.getType())
                .text(request.getText())
                .options(request.getOptions())
                .correctAnswer(request.getCorrectAnswer())
                .timeLimitSeconds(request.getTimeLimitSeconds() != null ? request.getTimeLimitSeconds() : 20)
                .points(request.getPoints() != null ? request.getPoints() : 1000)
                .position(currentCount)
                .mediaUrl(request.getMediaUrl())
                .build();

        Question saved = questionRepository.save(question);
        log.info("Question added: quizId={}, questionId={}, position={}", quizId, saved.getId(), saved.getPosition());
        return toQuestionResponse(saved);
    }

    @Transactional
    public void reorderQuestions(UUID quizId, UUID ownerId, ReorderQuestionsRequest request) {
        findQuizAndVerifyOwnership(quizId, ownerId);
        List<Question> questions = questionRepository.findByQuizIdOrderByPositionAsc(quizId);

        List<UUID> requestedIds = request.getQuestionIds();
        if (requestedIds.size() != questions.size()) {
            throw new ValidationException("Question IDs count does not match existing questions count");
        }

        for (int i = 0; i < requestedIds.size(); i++) {
            UUID questionId = requestedIds.get(i);
            Question question = questions.stream()
                    .filter(q -> q.getId().equals(questionId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Question", questionId.toString()));
            question.setPosition(i);
        }

        questionRepository.saveAll(questions);
        log.info("Questions reordered: quizId={}", quizId);
    }

    @Transactional
    public QuestionResponse updateQuestion(UUID quizId, UUID questionId, UUID ownerId, UpdateQuestionRequest request) {
        findQuizAndVerifyOwnership(quizId, ownerId);
        Question question = questionRepository.findByIdAndQuizId(questionId, quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Question", questionId.toString()));

        if (request.getType() != null) {
            question.setType(request.getType());
        }
        if (request.getText() != null) {
            question.setText(request.getText());
        }
        if (request.getOptions() != null) {
            question.setOptions(request.getOptions());
        }
        if (request.getCorrectAnswer() != null) {
            question.setCorrectAnswer(request.getCorrectAnswer());
        }
        if (request.getTimeLimitSeconds() != null) {
            question.setTimeLimitSeconds(request.getTimeLimitSeconds());
        }
        if (request.getPoints() != null) {
            question.setPoints(request.getPoints());
        }
        if (request.getMediaUrl() != null) {
            question.setMediaUrl(request.getMediaUrl());
        }

        Question saved = questionRepository.save(question);
        log.info("Question updated: quizId={}, questionId={}", quizId, questionId);
        return toQuestionResponse(saved);
    }

    @Transactional
    public void deleteQuestion(UUID quizId, UUID questionId, UUID ownerId) {
        findQuizAndVerifyOwnership(quizId, ownerId);
        Question question = questionRepository.findByIdAndQuizId(questionId, quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Question", questionId.toString()));

        int deletedPosition = question.getPosition();
        questionRepository.delete(question);
        questionRepository.decrementPositionsAfter(quizId, deletedPosition);
        log.info("Question deleted: quizId={}, questionId={}, position={}", quizId, questionId, deletedPosition);
    }

    @Transactional
    public QuizResponse updateScoringMode(UUID quizId, UUID ownerId, UpdateScoringModeRequest request) {
        Quiz quiz = findQuizAndVerifyOwnership(quizId, ownerId);
        quiz.setScoringMode(request.getScoringMode());
        Quiz saved = quizRepository.save(quiz);
        int questionCount = questionRepository.countByQuizId(quizId);
        log.info("Quiz scoring mode updated: quizId={}, scoringMode={}", quizId, request.getScoringMode());
        return toQuizResponse(saved, questionCount);
    }

    public void validateQuiz(UUID quizId) {
        List<Question> questions = questionRepository.findByQuizIdOrderByPositionAsc(quizId);

        if (questions.isEmpty()) {
            throw new ValidationException("Quiz must have at least 1 question to be published");
        }

        List<String> errors = new ArrayList<>();
        for (Question question : questions) {
            if ((question.getType() == QuestionType.MCQ || question.getType() == QuestionType.TRUE_FALSE)
                    && (question.getCorrectAnswer() == null || question.getCorrectAnswer().isBlank())) {
                errors.add("Question at position " + question.getPosition() + " (" + question.getType()
                        + ") must have a correct answer set");
            }
            if (question.getType() == QuestionType.POLL
                    && question.getCorrectAnswer() != null && !question.getCorrectAnswer().isBlank()) {
                errors.add("Poll question at position " + question.getPosition()
                        + " should not have a correct answer set");
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException("Quiz validation failed", errors);
        }
    }

    private Quiz findQuizAndVerifyOwnership(UUID quizId, UUID ownerId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId.toString()));

        if (!quiz.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException("You do not have permission to access this quiz");
        }

        return quiz;
    }

    private QuizResponse toQuizResponse(Quiz quiz, int questionCount) {
        return QuizResponse.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .description(quiz.getDescription())
                .coverImageUrl(quiz.getCoverImageUrl())
                .isPublished(quiz.getIsPublished())
                .scoringMode(quiz.getScoringMode())
                .settings(quiz.getSettings())
                .questionCount(questionCount)
                .createdAt(quiz.getCreatedAt())
                .updatedAt(quiz.getUpdatedAt())
                .build();
    }

    private QuestionResponse toQuestionResponse(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .type(question.getType())
                .text(question.getText())
                .options(question.getOptions())
                .correctAnswer(question.getCorrectAnswer())
                .timeLimitSeconds(question.getTimeLimitSeconds())
                .points(question.getPoints())
                .position(question.getPosition())
                .mediaUrl(question.getMediaUrl())
                .build();
    }
}
