package com.hou.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hou.exam.common.BizException;
import com.hou.exam.dto.StudentDtos;
import com.hou.exam.dto.StudentPaperRow;
import com.hou.exam.entity.Paper;
import com.hou.exam.entity.PaperStatus;
import com.hou.exam.entity.Question;
import com.hou.exam.entity.QuestionType;
import com.hou.exam.entity.Submission;
import com.hou.exam.entity.SubmissionAnswer;
import com.hou.exam.entity.SubmissionStatus;
import com.hou.exam.mapper.PaperMapper;
import com.hou.exam.mapper.QuestionMapper;
import com.hou.exam.mapper.SubmissionAnswerMapper;
import com.hou.exam.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentExamService {

    private static final Set<String> CHOICES = Set.of("A", "B", "C", "D");
    private static final Set<String> TRUE_FALSE = Set.of("TRUE", "FALSE");
    private static final int MAX_ANSWER_LENGTH = 10000;

    private final PaperMapper paperMapper;
    private final QuestionMapper questionMapper;
    private final SubmissionMapper submissionMapper;
    private final SubmissionAnswerMapper answerMapper;
    private final PaperService paperService;
    private final BlankCodec blankCodec;
    private final AutoGrader autoGrader;
    private final FileService fileService;

    public List<StudentPaperRow> listPapers(Long studentId) {
        return paperMapper.selectForStudent(studentId);
    }

    public StudentDtos.PaperOverviewVO overview(Long paperId, Long studentId) {
        Submission submission = findSubmission(paperId, studentId);
        Paper paper = requireVisiblePaper(paperId, submission);
        List<Question> questions = paperService.questionsOf(paperId);
        Map<Long, SubmissionAnswer> answers = answersOf(submission);

        // 按固定顺序返回题型，这篇没有的题型不出现
        List<StudentDtos.SectionVO> sections = new ArrayList<>();
        for (QuestionType type : QuestionType.values()) {
            List<Question> ofType = questions.stream().filter(q -> q.getType() == type).toList();
            if (!ofType.isEmpty()) {
                int answered = (int) ofType.stream().filter(q -> isAnswered(answers.get(q.getId()))).count();
                sections.add(new StudentDtos.SectionVO(type, ofType.size(), answered));
            }
        }
        int answeredCount = sections.stream().mapToInt(StudentDtos.SectionVO::answered).sum();
        return new StudentDtos.PaperOverviewVO(paperId, paper.getTitle(), submission == null ? null : submission.getStatus(),
                PaperService.sumScore(questions), questions.size(), answeredCount, sections);
    }

    public List<StudentDtos.StudentQuestionVO> questions(Long paperId, QuestionType type, Long studentId) {
        Submission submission = findSubmission(paperId, studentId);
        requireVisiblePaper(paperId, submission);
        Map<Long, SubmissionAnswer> answers = answersOf(submission);
        return paperService.questionsOf(paperId).stream()
                .filter(q -> type == null || q.getType() == type)
                .map(q -> toStudentVO(q, answers.get(q.getId())))
                .toList();
    }

    @Transactional
    public void saveAnswer(Long paperId, Long questionId, StudentDtos.SaveAnswerRequest request, Long studentId) {
        Submission submission = lockOrCreate(paperId, studentId);
        requireInProgress(submission);
        Question question = questionMapper.selectById(questionId);
        if (question == null || !question.getPaperId().equals(paperId)) {
            throw BizException.notFound("题目不存在");
        }
        if (request.images() != null && !request.images().isEmpty() && question.getType() != QuestionType.ESSAY) {
            throw BizException.badRequest("只有大题可以上传图片");
        }
        String value = normalizeAnswer(question, request);
        SubmissionAnswer existing = answerMapper.selectOne(Wrappers.<SubmissionAnswer>lambdaQuery()
                .eq(SubmissionAnswer::getSubmissionId, submission.getId())
                .eq(SubmissionAnswer::getQuestionId, questionId));
        String images = request.images() != null
                ? fileService.encodeImages(request.images())
                : existing == null ? null : existing.getImages();
        if (value == null && images == null) {
            // 传空表示清空这题的答案
            if (existing != null) {
                answerMapper.deleteById(existing.getId());
            }
            return;
        }
        if (existing == null) {
            SubmissionAnswer answer = new SubmissionAnswer();
            answer.setSubmissionId(submission.getId());
            answer.setQuestionId(questionId);
            answer.setAnswer(value);
            answer.setImages(images);
            answer.setUpdatedAt(LocalDateTime.now());
            answerMapper.insert(answer);
        } else {
            // 大题可能只传了照片、文字还没写，answer 为 null 时也要更新
            answerMapper.update(Wrappers.<SubmissionAnswer>lambdaUpdate()
                    .eq(SubmissionAnswer::getId, existing.getId())
                    .set(SubmissionAnswer::getAnswer, value)
                    .set(SubmissionAnswer::getImages, images)
                    .set(SubmissionAnswer::getUpdatedAt, LocalDateTime.now()));
        }
    }

    /** 交卷：判完所有自动题；没有待判大题的试卷直接出成绩。 */
    @Transactional
    public StudentDtos.ResultVO submit(Long paperId, Long studentId) {
        Submission submission = lockOrCreate(paperId, studentId);
        requireInProgress(submission);

        List<Question> questions = paperService.questionsOf(paperId);
        Map<Long, SubmissionAnswer> answers = answersOf(submission);
        // 大题的文字结果必填，照片选填
        long missingEssays = questions.stream()
                .filter(q -> q.getType() == QuestionType.ESSAY && !isAnswered(answers.get(q.getId())))
                .count();
        if (missingEssays > 0) {
            throw BizException.badRequest("还有 " + missingEssays + " 道大题没有填写结果，不能交卷");
        }
        LocalDateTime now = LocalDateTime.now();
        BigDecimal autoScore = BigDecimal.ZERO;
        boolean needsTeacher = false;

        for (Question question : questions) {
            SubmissionAnswer answer = answers.get(question.getId());
            AutoGrader.Outcome outcome = autoGrader.grade(question, answer == null ? null : answer.getAnswer());
            if (outcome.score() == null) {
                needsTeacher = true;
            } else {
                autoScore = autoScore.add(outcome.score());
            }
            if (answer == null) {
                // 未作答也落一条记录，这样每题都有判分结果
                answer = new SubmissionAnswer();
                answer.setSubmissionId(submission.getId());
                answer.setQuestionId(question.getId());
                answer.setCorrect(outcome.correct());
                answer.setScore(outcome.score());
                answer.setUpdatedAt(now);
                answerMapper.insert(answer);
            } else {
                answer.setCorrect(outcome.correct());
                answer.setScore(outcome.score());
                answer.setUpdatedAt(now);
                answerMapper.updateById(answer);
            }
        }

        submission.setAutoScore(autoScore);
        submission.setSubmittedAt(now);
        if (needsTeacher) {
            submission.setStatus(SubmissionStatus.SUBMITTED);
        } else {
            submission.setStatus(SubmissionStatus.GRADED);
            submission.setTotalScore(autoScore);
            submission.setGradedAt(now);
        }
        submissionMapper.updateById(submission);
        return buildResult(submission, questions);
    }

    public StudentDtos.ResultVO result(Long paperId, Long studentId) {
        Submission submission = findSubmission(paperId, studentId);
        if (submission == null || submission.getStatus() == SubmissionStatus.IN_PROGRESS) {
            throw BizException.conflict("这篇试卷还没有交卷");
        }
        requireVisiblePaper(paperId, submission);
        return buildResult(submission, paperService.questionsOf(paperId));
    }

    private StudentDtos.ResultVO buildResult(Submission submission, List<Question> questions) {
        Paper paper = paperService.requirePaper(submission.getPaperId());
        BigDecimal maxScore = PaperService.sumScore(questions);
        if (submission.getStatus() != SubmissionStatus.GRADED) {
            // 老师判完之前不返回任何分数和对错
            return new StudentDtos.ResultVO(paper.getId(), paper.getTitle(), submission.getStatus(), null, maxScore,
                    submission.getSubmittedAt(), null, List.of());
        }
        Map<Long, SubmissionAnswer> answers = answersOf(submission);
        List<StudentDtos.ResultItemVO> items = questions.stream().map(q -> {
            SubmissionAnswer a = answers.get(q.getId());
            boolean fill = q.getType() == QuestionType.FILL_BLANK;
            return new StudentDtos.ResultItemVO(q.getId(), q.getType(), q.getSortOrder(), q.getContent(),
                    q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(),
                    fill ? null : a.getAnswer(), fill ? blankCodec.decodeStudent(a.getAnswer()) : null,
                    a.getCorrect(), a.getScore(), q.getScore(), a.getTeacherComment(),
                    fileService.decodeImages(q.getImages()), fileService.decodeImages(a.getImages()));
        }).toList();
        return new StudentDtos.ResultVO(paper.getId(), paper.getTitle(), submission.getStatus(),
                submission.getTotalScore(), maxScore, submission.getSubmittedAt(), submission.getGradedAt(), items);
    }

    private Submission findSubmission(Long paperId, Long studentId) {
        return submissionMapper.selectOne(Wrappers.<Submission>lambdaQuery()
                .eq(Submission::getPaperId, paperId)
                .eq(Submission::getStudentId, studentId));
    }

    /** 未发布的试卷只有已交卷的学生能看。 */
    private Paper requireVisiblePaper(Long paperId, Submission submission) {
        Paper paper = paperMapper.selectById(paperId);
        boolean submitted = submission != null && submission.getStatus() != SubmissionStatus.IN_PROGRESS;
        if (paper == null || (paper.getStatus() != PaperStatus.PUBLISHED && !submitted)) {
            throw BizException.notFound("试卷不存在或已下架");
        }
        return paper;
    }

    private Submission lockOrCreate(Long paperId, Long studentId) {
        Submission locked = submissionMapper.selectForUpdate(paperId, studentId);
        if (locked != null && locked.getStatus() != SubmissionStatus.IN_PROGRESS) {
            return locked;
        }
        Paper paper = paperMapper.selectById(paperId);
        if (paper == null || paper.getStatus() != PaperStatus.PUBLISHED) {
            throw BizException.notFound("试卷不存在或已下架");
        }
        if (locked != null) {
            return locked;
        }
        Submission submission = new Submission();
        submission.setPaperId(paperId);
        submission.setStudentId(studentId);
        submission.setStatus(SubmissionStatus.IN_PROGRESS);
        submission.setCreatedAt(LocalDateTime.now());
        try {
            submissionMapper.insert(submission);
            return submission;
        } catch (DuplicateKeyException e) {
            // 同一学生并发创建，用另一个请求创建好的那条
            return submissionMapper.selectForUpdate(paperId, studentId);
        }
    }

    private void requireInProgress(Submission submission) {
        if (submission.getStatus() != SubmissionStatus.IN_PROGRESS) {
            throw BizException.conflict("已经交卷，不能再修改");
        }
    }

    private Map<Long, SubmissionAnswer> answersOf(Submission submission) {
        if (submission == null) {
            return Map.of();
        }
        return answerMapper.selectList(Wrappers.<SubmissionAnswer>lambdaQuery()
                        .eq(SubmissionAnswer::getSubmissionId, submission.getId()))
                .stream().collect(Collectors.toMap(SubmissionAnswer::getQuestionId, Function.identity()));
    }

    private boolean isAnswered(SubmissionAnswer answer) {
        return answer != null && answer.getAnswer() != null;
    }

    /** 校验并转成存储格式；全空返回 null。 */
    private String normalizeAnswer(Question question, StudentDtos.SaveAnswerRequest request) {
        return switch (question.getType()) {
            case SINGLE_CHOICE -> checkedOption(request.answer(), CHOICES, "选择题答案必须是 A、B、C、D 之一");
            case TRUE_FALSE -> checkedOption(request.answer(), TRUE_FALSE, "判断题答案必须是 TRUE 或 FALSE");
            case ESSAY -> {
                if (!StringUtils.hasText(request.answer())) {
                    yield null;
                }
                checkLength(request.answer());
                yield request.answer();
            }
            case FILL_BLANK -> normalizeBlanks(question, request.blanks());
        };
    }

    private String checkedOption(String value, Set<String> allowed, String message) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (!allowed.contains(value)) {
            throw BizException.badRequest(message);
        }
        return value;
    }

    private String normalizeBlanks(Question question, List<String> blanks) {
        if (blanks == null || blanks.stream().noneMatch(StringUtils::hasText)) {
            return null;
        }
        int blankCount = blankCodec.decodeCorrect(question.getAnswer()).size();
        if (blanks.size() != blankCount) {
            throw BizException.badRequest("这题有 " + blankCount + " 个空，提交了 " + blanks.size() + " 个");
        }
        blanks.stream().filter(StringUtils::hasText).forEach(this::checkLength);
        return blankCodec.encode(blanks.stream().map(b -> b == null ? "" : b.trim()).toList());
    }

    private void checkLength(String value) {
        if (value.length() > MAX_ANSWER_LENGTH) {
            throw BizException.badRequest("答案不能超过 " + MAX_ANSWER_LENGTH + " 字");
        }
    }

    private StudentDtos.StudentQuestionVO toStudentVO(Question q, SubmissionAnswer answer) {
        boolean fill = q.getType() == QuestionType.FILL_BLANK;
        String saved = answer == null ? null : answer.getAnswer();
        return new StudentDtos.StudentQuestionVO(q.getId(), q.getType(), q.getSortOrder(), q.getContent(),
                q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(),
                fill ? blankCodec.decodeCorrect(q.getAnswer()).size() : null, q.getScore(),
                fill ? null : saved, fill ? blankCodec.decodeStudent(saved) : null,
                fileService.decodeImages(q.getImages()),
                fileService.decodeImages(answer == null ? null : answer.getImages()));
    }
}
