package com.hou.exam.service;

import com.hou.exam.entity.Question;
import com.hou.exam.entity.QuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 交卷时的自动判分。大题没有作答直接 0 分，有作答的留给老师。 */
@Component
@RequiredArgsConstructor
public class AutoGrader {

    private final BlankCodec blankCodec;

    public record Outcome(Boolean correct, BigDecimal score) {
        static final Outcome WRONG = new Outcome(false, BigDecimal.ZERO);
        static final Outcome PENDING = new Outcome(null, null);
    }

    public Outcome grade(Question question, String answer) {
        if (answer == null) {
            return Outcome.WRONG;
        }
        QuestionType type = question.getType();
        return switch (type) {
            case SINGLE_CHOICE, TRUE_FALSE -> question.getAnswer().equals(answer)
                    ? new Outcome(true, question.getScore())
                    : Outcome.WRONG;
            case FILL_BLANK -> gradeBlanks(question, answer);
            case ESSAY -> Outcome.PENDING;
        };
    }

    /** 每个空单独计分，全部答对才算这题对。 */
    private Outcome gradeBlanks(Question question, String answerJson) {
        List<List<String>> correct = blankCodec.decodeCorrect(question.getAnswer());
        List<String> given = blankCodec.decodeStudent(answerJson);
        int blankCount = correct.size();
        int matched = 0;
        for (int i = 0; i < blankCount; i++) {
            String value = i < given.size() && given.get(i) != null ? given.get(i).trim() : "";
            if (!value.isEmpty() && correct.get(i).stream().anyMatch(option -> option.trim().equals(value))) {
                matched++;
            }
        }
        if (matched == blankCount) {
            return new Outcome(true, question.getScore());
        }
        // 向下取整到两位小数，部分得分不会超过应得分
        BigDecimal score = question.getScore()
                .multiply(BigDecimal.valueOf(matched))
                .divide(BigDecimal.valueOf(blankCount), 2, RoundingMode.DOWN);
        return new Outcome(false, score);
    }
}
