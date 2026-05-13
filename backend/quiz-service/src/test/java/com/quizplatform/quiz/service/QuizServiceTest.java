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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuestionRepository questionRepository;

    private QuizService quizService;

    private UUID ownerId;
    private UUID quizId;
    private Quiz sampleQuiz;

    @BeforeEach
    void setUp() {
        quizService = new QuizService(quizRepository, questionRepository);
        ownerId = UUID.randomUUID();
        quizId = UUID.randomUUID();
        sampleQuiz = Quiz.builder()
                .id(quizId)
                .ownerId(ownerId)
                .title("Sample Quiz")
                .description("A sample quiz")
                .isPublished(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createQuiz_shouldCreateAndReturnQuizResponse() {
        CreateQuizRequest request = CreateQuizRequest.builder()
                .title("New Quiz")
                .description("Description")
                .coverImageUrl("https://example.com/image.png")
                .build();

        when(quizRepository.save(any(Quiz.class))).thenReturn(
                Quiz.builder()
                        .id(quizId)
                        .ownerId(ownerId)
                        .title("New Quiz")
                        .description("Description")
                        .coverImageUrl("https://example.com/image.png")
                        .isPublished(false)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build()
        );

        QuizResponse response = quizService.createQuiz(ownerId, request);

        assertThat(response.getId()).isEqualTo(quizId);
        assertThat(response.getTitle()).isEqualTo("New Quiz");
        assertThat(response.getDescription()).isEqualTo("Description");
        assertThat(response.getIsPublished()).isFalse();
        assertThat(response.getQuestionCount()).isZero();

        ArgumentCaptor<Quiz> captor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(ownerId);
        assertThat(captor.getValue().getTitle()).isEqualTo("New Quiz");
    }

    @Test
    void listQuizzes_shouldReturnPaginatedResults() {
        Pageable pageable = PageRequest.of(0, 20);
        List<Quiz> quizzes = List.of(sampleQuiz);
        Page<Quiz> page = new PageImpl<>(quizzes, pageable, 1);

        when(quizRepository.findByOwnerId(ownerId, pageable)).thenReturn(page);
        when(questionRepository.countByQuizId(quizId)).thenReturn(5);

        Page<QuizResponse> result = quizService.listQuizzes(ownerId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Sample Quiz");
        assertThat(result.getContent().get(0).getQuestionCount()).isEqualTo(5);
    }

    @Test
    void getQuiz_shouldReturnQuizWithQuestions() {
        Question question = Question.builder()
                .id(UUID.randomUUID())
                .quiz(sampleQuiz)
                .type(QuestionType.MCQ)
                .text("What is 2+2?")
                .options(List.of(Map.of("text", "3"), Map.of("text", "4")))
                .correctAnswer("1")
                .timeLimitSeconds(20)
                .points(1000)
                .position(0)
                .build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));
        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(List.of(question));

        QuizDetailResponse response = quizService.getQuiz(quizId, ownerId);

        assertThat(response.getTitle()).isEqualTo("Sample Quiz");
        assertThat(response.getQuestions()).hasSize(1);
        assertThat(response.getQuestions().get(0).getText()).isEqualTo("What is 2+2?");
    }

    @Test
    void getQuiz_shouldThrowForbiddenWhenNotOwner() {
        UUID otherOwnerId = UUID.randomUUID();
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));

        assertThatThrownBy(() -> quizService.getQuiz(quizId, otherOwnerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getQuiz_shouldThrowNotFoundWhenQuizDoesNotExist() {
        UUID nonExistentId = UUID.randomUUID();
        when(quizRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quizService.getQuiz(nonExistentId, ownerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateQuiz_shouldUpdateOnlyProvidedFields() {
        UpdateQuizRequest request = UpdateQuizRequest.builder()
                .title("Updated Title")
                .build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));
        when(quizRepository.save(any(Quiz.class))).thenReturn(sampleQuiz);
        when(questionRepository.countByQuizId(quizId)).thenReturn(3);

        quizService.updateQuiz(quizId, ownerId, request);

        assertThat(sampleQuiz.getTitle()).isEqualTo("Updated Title");
        assertThat(sampleQuiz.getDescription()).isEqualTo("A sample quiz");
    }

    @Test
    void deleteQuiz_shouldDeleteQuiz() {
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));

        quizService.deleteQuiz(quizId, ownerId);

        verify(quizRepository).delete(sampleQuiz);
    }

    @Test
    void deleteQuiz_shouldThrowForbiddenWhenNotOwner() {
        UUID otherOwnerId = UUID.randomUUID();
        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));

        assertThatThrownBy(() -> quizService.deleteQuiz(quizId, otherOwnerId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void duplicateQuiz_shouldDeepCopyQuizWithQuestions() {
        Question originalQuestion = Question.builder()
                .id(UUID.randomUUID())
                .quiz(sampleQuiz)
                .type(QuestionType.MCQ)
                .text("Question 1")
                .options(List.of(Map.of("text", "A"), Map.of("text", "B")))
                .correctAnswer("0")
                .timeLimitSeconds(20)
                .points(1000)
                .position(0)
                .build();

        Quiz duplicatedQuiz = Quiz.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .title("Sample Quiz (Copy)")
                .description("A sample quiz")
                .isPublished(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));
        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(List.of(originalQuestion));
        when(quizRepository.save(any(Quiz.class))).thenReturn(duplicatedQuiz);
        when(questionRepository.saveAll(anyList())).thenReturn(List.of(originalQuestion));

        QuizResponse response = quizService.duplicateQuiz(quizId, ownerId);

        assertThat(response.getTitle()).isEqualTo("Sample Quiz (Copy)");
        assertThat(response.getIsPublished()).isFalse();

        ArgumentCaptor<Quiz> quizCaptor = ArgumentCaptor.forClass(Quiz.class);
        verify(quizRepository).save(quizCaptor.capture());
        assertThat(quizCaptor.getValue().getTitle()).isEqualTo("Sample Quiz (Copy)");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Question>> questionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(questionRepository).saveAll(questionsCaptor.capture());
        assertThat(questionsCaptor.getValue()).hasSize(1);
        assertThat(questionsCaptor.getValue().get(0).getQuiz()).isEqualTo(duplicatedQuiz);
    }

    @Test
    void addQuestion_shouldAddAtEnd() {
        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .type(QuestionType.MCQ)
                .text("New question")
                .options(List.of(Map.of("text", "A"), Map.of("text", "B")))
                .correctAnswer("0")
                .timeLimitSeconds(30)
                .points(2000)
                .build();

        Question savedQuestion = Question.builder()
                .id(UUID.randomUUID())
                .quiz(sampleQuiz)
                .type(QuestionType.MCQ)
                .text("New question")
                .options(List.of(Map.of("text", "A"), Map.of("text", "B")))
                .correctAnswer("0")
                .timeLimitSeconds(30)
                .points(2000)
                .position(3)
                .build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));
        when(questionRepository.countByQuizId(quizId)).thenReturn(3);
        when(questionRepository.save(any(Question.class))).thenReturn(savedQuestion);

        QuestionResponse response = quizService.addQuestion(quizId, ownerId, request);

        assertThat(response.getText()).isEqualTo("New question");
        assertThat(response.getPosition()).isEqualTo(3);
        assertThat(response.getTimeLimitSeconds()).isEqualTo(30);
        assertThat(response.getPoints()).isEqualTo(2000);
    }

    @Test
    void reorderQuestions_shouldUpdatePositions() {
        UUID q1Id = UUID.randomUUID();
        UUID q2Id = UUID.randomUUID();

        Question q1 = Question.builder().id(q1Id).quiz(sampleQuiz).position(0).build();
        Question q2 = Question.builder().id(q2Id).quiz(sampleQuiz).position(1).build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));
        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(List.of(q1, q2));

        ReorderQuestionsRequest request = ReorderQuestionsRequest.builder()
                .questionIds(List.of(q2Id, q1Id))
                .build();

        quizService.reorderQuestions(quizId, ownerId, request);

        assertThat(q2.getPosition()).isEqualTo(0);
        assertThat(q1.getPosition()).isEqualTo(1);
        verify(questionRepository).saveAll(anyList());
    }

    @Test
    void reorderQuestions_shouldThrowWhenCountMismatch() {
        Question q1 = Question.builder().id(UUID.randomUUID()).quiz(sampleQuiz).position(0).build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));
        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(List.of(q1));

        ReorderQuestionsRequest request = ReorderQuestionsRequest.builder()
                .questionIds(List.of(UUID.randomUUID(), UUID.randomUUID()))
                .build();

        assertThatThrownBy(() -> quizService.reorderQuestions(quizId, ownerId, request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void deleteQuestion_shouldDeleteAndReorderRemaining() {
        UUID questionId = UUID.randomUUID();
        Question question = Question.builder()
                .id(questionId)
                .quiz(sampleQuiz)
                .position(1)
                .build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));
        when(questionRepository.findByIdAndQuizId(questionId, quizId)).thenReturn(Optional.of(question));

        quizService.deleteQuestion(quizId, questionId, ownerId);

        verify(questionRepository).delete(question);
        verify(questionRepository).decrementPositionsAfter(quizId, 1);
    }

    @Test
    void validateQuiz_shouldThrowWhenNoQuestions() {
        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> quizService.validateQuiz(quizId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least 1 question");
    }

    @Test
    void validateQuiz_shouldThrowWhenMCQMissingCorrectAnswer() {
        Question mcq = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.MCQ)
                .text("MCQ without answer")
                .options(List.of(Map.of("text", "A")))
                .correctAnswer(null)
                .position(0)
                .build();

        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(List.of(mcq));

        assertThatThrownBy(() -> quizService.validateQuiz(quizId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("validation failed");
    }

    @Test
    void validateQuiz_shouldThrowWhenTrueFalseMissingCorrectAnswer() {
        Question tf = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.TRUE_FALSE)
                .text("True or false?")
                .options(List.of(Map.of("text", "True"), Map.of("text", "False")))
                .correctAnswer("")
                .position(0)
                .build();

        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(List.of(tf));

        assertThatThrownBy(() -> quizService.validateQuiz(quizId))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void validateQuiz_shouldThrowWhenPollHasCorrectAnswer() {
        Question poll = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.POLL)
                .text("Favorite color?")
                .options(List.of(Map.of("text", "Red"), Map.of("text", "Blue")))
                .correctAnswer("0")
                .position(0)
                .build();

        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(List.of(poll));

        assertThatThrownBy(() -> quizService.validateQuiz(quizId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Poll question");
    }

    @Test
    void validateQuiz_shouldPassForValidQuiz() {
        Question mcq = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.MCQ)
                .text("Valid MCQ")
                .options(List.of(Map.of("text", "A"), Map.of("text", "B")))
                .correctAnswer("0")
                .position(0)
                .build();

        Question poll = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.POLL)
                .text("Valid Poll")
                .options(List.of(Map.of("text", "X"), Map.of("text", "Y")))
                .correctAnswer(null)
                .position(1)
                .build();

        when(questionRepository.findByQuizIdOrderByPositionAsc(quizId)).thenReturn(List.of(mcq, poll));

        // Should not throw
        quizService.validateQuiz(quizId);
    }

    @Test
    void updateQuestion_shouldUpdateOnlyProvidedFields() {
        UUID questionId = UUID.randomUUID();
        Question existing = Question.builder()
                .id(questionId)
                .quiz(sampleQuiz)
                .type(QuestionType.MCQ)
                .text("Original text")
                .options(List.of(Map.of("text", "A")))
                .correctAnswer("0")
                .timeLimitSeconds(20)
                .points(1000)
                .position(0)
                .build();

        UpdateQuestionRequest request = UpdateQuestionRequest.builder()
                .text("Updated text")
                .points(2000)
                .build();

        when(quizRepository.findById(quizId)).thenReturn(Optional.of(sampleQuiz));
        when(questionRepository.findByIdAndQuizId(questionId, quizId)).thenReturn(Optional.of(existing));
        when(questionRepository.save(any(Question.class))).thenReturn(existing);

        QuestionResponse response = quizService.updateQuestion(quizId, questionId, ownerId, request);

        assertThat(existing.getText()).isEqualTo("Updated text");
        assertThat(existing.getPoints()).isEqualTo(2000);
        assertThat(existing.getTimeLimitSeconds()).isEqualTo(20); // unchanged
        assertThat(existing.getCorrectAnswer()).isEqualTo("0"); // unchanged
    }
}
