package com.hou.exam.dto;

import com.hou.exam.entity.QuestionType;
import com.hou.exam.entity.SubmissionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 学生端返回的数据。这里的类型都不能包含正确答案字段。 */
public final class StudentDtos {

    private StudentDtos() {
    }

    /** answer 用于选择、判断、大题；blanks 用于填空题，按空的顺序传；images 只用于大题，不传表示不改动。 */
    public record SaveAnswerRequest(String answer, List<String> blanks, List<String> images) {
    }

    public record SectionVO(QuestionType type, int count, int answered) {
    }

    public record PaperOverviewVO(Long paperId, String title, SubmissionStatus status, BigDecimal maxScore,
                                  int questionCount, int answeredCount, List<SectionVO> sections) {
    }

    public record StudentQuestionVO(Long id, QuestionType type, Integer sortOrder, String content,
                                    String optionA, String optionB, String optionC, String optionD,
                                    Integer blankCount, BigDecimal score,
                                    String myAnswer, List<String> myBlanks,
                                    List<String> images, List<String> myImages) {
    }

    public record ResultItemVO(Long questionId, QuestionType type, Integer sortOrder, String content,
                               String optionA, String optionB, String optionC, String optionD,
                               String myAnswer, List<String> myBlanks,
                               Boolean correct, BigDecimal score, BigDecimal maxScore, String comment,
                               List<String> images, List<String> myImages) {
    }

    /** 未判完时 totalScore 为 null，items 为空。 */
    public record ResultVO(Long paperId, String title, SubmissionStatus status, BigDecimal totalScore,
                           BigDecimal maxScore, LocalDateTime submittedAt, LocalDateTime gradedAt,
                           List<ResultItemVO> items) {
    }
}
