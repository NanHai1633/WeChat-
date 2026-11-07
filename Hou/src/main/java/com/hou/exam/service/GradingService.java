package com.hou.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hou.exam.common.BizException;
import com.hou.exam.common.PageResult;
import com.hou.exam.dto.GradingDtos;
import com.hou.exam.dto.SubmissionRow;
import com.hou.exam.entity.Question;
import com.hou.exam.entity.QuestionType;
import com.hou.exam.entity.Submission;
import com.hou.exam.entity.SubmissionAnswer;
import com.hou.exam.entity.SubmissionStatus;
import com.hou.exam.mapper.SubmissionAnswerMapper;
import com.hou.exam.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GradingService {

    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper answerMapper;
    private final PaperService paperService;
    private final AccountService accountService;
    private final BlankCodec blankCodec;
    private final FileService fileService;

    public PageResult<SubmissionRow> listByPaper(Long paperId, String status, int page, int size) {
        paperService.requirePaper(paperId);
        PageResult<SubmissionRow> result =
                PageResult.of(submissionMapper.selectByPaper(PageResult.pageOf(page, size), paperId, status));
        result.records().forEach(row -> row.setPaperId(paperId));
        return result;
    }

    public PageResult<SubmissionRow> listByStudent(Long studentId, int page, int size) {
        accountService.requireStudent(studentId);
        return PageResult.of(submissionMapper.selectByStudent(PageResult.pageOf(page, size), studentId));
    }

    public PageResult<SubmissionRow> listPending(int page, int size) {
        return PageResult.of(submissionMapper.selectPending(PageResult.pageOf(page, size)));
    }

    public GradingDtos.SubmissionDetailVO detail(Long submissionId) {
        SubmissionRow row = submissionMapper.selectRowById(submissionId);
        if (row == null) {
            throw BizException.notFound("答卷不存在");
        }
        List<Question> questions = paperService.questionsOf(row.getPaperId());
        Map<Long, SubmissionAnswer> answers = answersOf(submissionId);
        List<GradingDtos.AnswerDetailVO> items = questions.stream().map(q -> {
            SubmissionAnswer a = answers.get(q.getId());
            boolean fill = q.getType() == QuestionType.FILL_BLANK;
            String given = a == null ? null : a.getAnswer();
            return new GradingDtos.AnswerDetailVO(q.getId(), q.getType(), q.getSortOrder(), q.getContent(),
                    q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(),
                    fill ? null : q.getAnswer(), fill ? blankCodec.decodeCorrect(q.getAnswer()) : null,
                    fill ? null : given, fill ? blankCodec.decodeStudent(given) : null,
                    a == null ? null : a.getCorrect(), a == null ? null : a.getScore(), q.getScore(),
                    a == null ? null : a.getTeacherComment(),
                    fileService.decodeImages(q.getImages()),
                    fileService.decodeImages(a == null ? null : a.getImages()));
        }).toList();
        return new GradingDtos.SubmissionDetailVO(row.getSubmissionId(), row.getPaperId(), row.getTitle(),
                row.getStudentId(), row.getUsername(), row.getStatus(), row.getAutoScore(), row.getTotalScore(),
                PaperService.sumScore(questions), row.getSubmittedAt(), row.getGradedAt(), items);
    }

    /** 保存大题分数，可以分多次。判卷完成后再改分，总分同步更新。 */
    @Transactional
    public void saveGrades(Long submissionId, List<GradingDtos.GradeItem> grades) {
        Submission submission = requireSubmitted(submissionId);
        Map<Long, Question> questions = paperService.questionsOf(submission.getPaperId()).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, SubmissionAnswer> answers = answersOf(submissionId);
        LocalDateTime now = LocalDateTime.now();

        for (GradingDtos.GradeItem grade : grades) {
            Question question = questions.get(grade.questionId());
            if (question == null) {
                throw BizException.notFound("题目不存在");
            }
            if (question.getType() != QuestionType.ESSAY) {
                throw BizException.badRequest("只有大题需要老师判分");
            }
            if (grade.score().compareTo(question.getScore()) > 0) {
                throw BizException.badRequest("分数不能超过这题的满分 " + question.getScore().stripTrailingZeros().toPlainString());
            }
            SubmissionAnswer answer = answers.get(grade.questionId());
            answer.setScore(grade.score());
            answer.setCorrect(grade.score().compareTo(question.getScore()) == 0);
            answer.setTeacherComment(grade.comment());
            answer.setUpdatedAt(now);
            answerMapper.updateById(answer);
        }
        if (submission.getStatus() == SubmissionStatus.GRADED) {
            submission.setTotalScore(sumAnswers(submissionId));
            submissionMapper.updateById(submission);
        }
    }

    /** 完成判卷：所有大题都有分数才能完成，之后学生可以看到成绩。 */
    @Transactional
    public GradingDtos.SubmissionDetailVO finish(Long submissionId, Long adminId) {
        Submission submission = requireSubmitted(submissionId);
        long pending = answersOf(submissionId).values().stream().filter(a -> a.getScore() == null).count();
        if (pending > 0) {
            throw BizException.badRequest("还有 " + pending + " 道大题没有打分");
        }
        submission.setTotalScore(sumAnswers(submissionId));
        submission.setStatus(SubmissionStatus.GRADED);
        submission.setGradedAt(LocalDateTime.now());
        submission.setGradedBy(adminId);
        submissionMapper.updateById(submission);
        return detail(submissionId);
    }

    private Submission requireSubmitted(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw BizException.notFound("答卷不存在");
        }
        if (submission.getStatus() == SubmissionStatus.IN_PROGRESS) {
            throw BizException.conflict("学生还没有交卷");
        }
        return submission;
    }

    private BigDecimal sumAnswers(Long submissionId) {
        return answersOf(submissionId).values().stream()
                .map(SubmissionAnswer::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, SubmissionAnswer> answersOf(Long submissionId) {
        return answerMapper.selectList(Wrappers.<SubmissionAnswer>lambdaQuery()
                        .eq(SubmissionAnswer::getSubmissionId, submissionId))
                .stream().collect(Collectors.toMap(SubmissionAnswer::getQuestionId, Function.identity()));
    }
}
