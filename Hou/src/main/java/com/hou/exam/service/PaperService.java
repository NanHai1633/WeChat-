package com.hou.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hou.exam.common.BizException;
import com.hou.exam.common.PageResult;
import com.hou.exam.dto.PaperDtos;
import com.hou.exam.entity.Paper;
import com.hou.exam.entity.PaperStatus;
import com.hou.exam.entity.Question;
import com.hou.exam.entity.QuestionType;
import com.hou.exam.entity.Submission;
import com.hou.exam.mapper.PaperMapper;
import com.hou.exam.mapper.QuestionMapper;
import com.hou.exam.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaperService {

    private static final Set<String> CHOICES = Set.of("A", "B", "C", "D");
    private static final Set<String> TRUE_FALSE = Set.of("TRUE", "FALSE");

    private final PaperMapper paperMapper;
    private final QuestionMapper questionMapper;
    private final SubmissionMapper submissionMapper;
    private final BlankCodec blankCodec;
    private final FileService fileService;

    public PaperDtos.PaperVO create(PaperDtos.PaperRequest request) {
        Paper paper = new Paper();
        paper.setTitle(request.title().trim());
        paper.setStatus(PaperStatus.DRAFT);
        paper.setCreatedAt(LocalDateTime.now());
        paperMapper.insert(paper);
        return toVO(paper, List.of());
    }

    public PageResult<PaperDtos.PaperVO> list(int page, int size) {
        Page<Paper> result = paperMapper.selectPage(PageResult.pageOf(page, size),
                Wrappers.<Paper>lambdaQuery().orderByDesc(Paper::getId));
        return PageResult.of(result, paper -> toVO(paper, questionsOf(paper.getId())));
    }

    public PaperDtos.PaperDetailVO detail(Long id) {
        Paper paper = requirePaper(id);
        List<Question> questions = questionsOf(id);
        return new PaperDtos.PaperDetailVO(paper.getId(), paper.getTitle(), paper.getStatus(), sumScore(questions),
                paper.getCreatedAt(), questions.stream().map(this::toVO).toList());
    }

    public PaperDtos.PaperVO rename(Long id, PaperDtos.PaperRequest request) {
        Paper paper = requirePaper(id);
        paper.setTitle(request.title().trim());
        paperMapper.updateById(paper);
        return toVO(paper, questionsOf(id));
    }

    public void delete(Long id) {
        requirePaper(id);
        paperMapper.deleteById(id);
    }

    public PaperDtos.PaperVO changeStatus(Long id, PaperStatus status) {
        Paper paper = requirePaper(id);
        List<Question> questions = questionsOf(id);
        if (status == PaperStatus.PUBLISHED && questions.isEmpty()) {
            throw BizException.badRequest("试卷还没有题目，不能发布");
        }
        paper.setStatus(status);
        paperMapper.updateById(paper);
        return toVO(paper, questions);
    }

    @Transactional
    public List<PaperDtos.QuestionVO> addQuestions(Long paperId, List<PaperDtos.QuestionRequest> requests) {
        requirePaper(paperId);
        requireNoSubmissions(paperId);
        int nextOrder = questionsOf(paperId).stream().mapToInt(Question::getSortOrder).max().orElse(0) + 1;
        List<PaperDtos.QuestionVO> created = new ArrayList<>();
        for (PaperDtos.QuestionRequest request : requests) {
            Question question = new Question();
            question.setPaperId(paperId);
            apply(question, request);
            question.setSortOrder(request.sortOrder() != null ? request.sortOrder() : nextOrder++);
            questionMapper.insert(question);
            created.add(toVO(question));
        }
        return created;
    }

    public PaperDtos.QuestionVO updateQuestion(Long questionId, PaperDtos.QuestionRequest request) {
        Question question = requireQuestion(questionId);
        requireNoSubmissions(question.getPaperId());
        apply(question, request);
        if (request.sortOrder() != null) {
            question.setSortOrder(request.sortOrder());
        }
        questionMapper.updateById(question);
        return toVO(question);
    }

    public void deleteQuestion(Long questionId) {
        Question question = requireQuestion(questionId);
        requireNoSubmissions(question.getPaperId());
        questionMapper.deleteById(questionId);
    }

    public Paper requirePaper(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) {
            throw BizException.notFound("试卷不存在");
        }
        return paper;
    }

    public List<Question> questionsOf(Long paperId) {
        return questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .eq(Question::getPaperId, paperId)
                .orderByAsc(Question::getSortOrder, Question::getId));
    }

    public static BigDecimal sumScore(List<Question> questions) {
        return questions.stream().map(Question::getScore).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 已有学生作答后题目不能再改，否则已存的答案和分数会对不上。 */
    private void requireNoSubmissions(Long paperId) {
        boolean started = submissionMapper.exists(Wrappers.<Submission>lambdaQuery()
                .eq(Submission::getPaperId, paperId));
        if (started) {
            throw BizException.conflict("已有学生开始作答，不能再修改这篇试卷的题目");
        }
    }

    private Question requireQuestion(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw BizException.notFound("题目不存在");
        }
        return question;
    }

    private void apply(Question question, PaperDtos.QuestionRequest request) {
        question.setType(request.type());
        question.setContent(request.content().trim());
        question.setScore(request.score());
        if (request.images() != null) {
            question.setImages(fileService.encodeImages(request.images()));
        }
        question.setOptionA(null);
        question.setOptionB(null);
        question.setOptionC(null);
        question.setOptionD(null);
        switch (request.type()) {
            case SINGLE_CHOICE -> {
                if (!StringUtils.hasText(request.optionA()) || !StringUtils.hasText(request.optionB())
                        || !StringUtils.hasText(request.optionC()) || !StringUtils.hasText(request.optionD())) {
                    throw BizException.badRequest("选择题必须填写 A、B、C、D 四个选项");
                }
                if (request.answer() == null || !CHOICES.contains(request.answer())) {
                    throw BizException.badRequest("选择题答案必须是 A、B、C、D 之一");
                }
                question.setOptionA(request.optionA());
                question.setOptionB(request.optionB());
                question.setOptionC(request.optionC());
                question.setOptionD(request.optionD());
                question.setAnswer(request.answer());
            }
            case TRUE_FALSE -> {
                if (request.answer() == null || !TRUE_FALSE.contains(request.answer())) {
                    throw BizException.badRequest("判断题答案必须是 TRUE 或 FALSE");
                }
                question.setAnswer(request.answer());
            }
            case FILL_BLANK -> question.setAnswer(blankCodec.encode(normalizeBlanks(request.blanks())));
            case ESSAY -> question.setAnswer(StringUtils.hasText(request.answer()) ? request.answer() : null);
        }
    }

    private List<List<String>> normalizeBlanks(List<List<String>> blanks) {
        if (blanks == null || blanks.isEmpty()) {
            throw BizException.badRequest("填空题至少要有一个空");
        }
        List<List<String>> normalized = new ArrayList<>();
        for (List<String> options : blanks) {
            List<String> cleaned = options == null ? List.of() : options.stream()
                    .filter(StringUtils::hasText).map(String::trim).toList();
            if (cleaned.isEmpty()) {
                throw BizException.badRequest("填空题每个空都要有答案");
            }
            normalized.add(cleaned);
        }
        return normalized;
    }

    private PaperDtos.PaperVO toVO(Paper paper, List<Question> questions) {
        return new PaperDtos.PaperVO(paper.getId(), paper.getTitle(), paper.getStatus(), questions.size(),
                sumScore(questions), paper.getCreatedAt());
    }

    private PaperDtos.QuestionVO toVO(Question q) {
        boolean fill = q.getType() == QuestionType.FILL_BLANK;
        return new PaperDtos.QuestionVO(q.getId(), q.getPaperId(), q.getType(), q.getSortOrder(), q.getContent(),
                q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD(),
                fill ? null : q.getAnswer(), fill ? blankCodec.decodeCorrect(q.getAnswer()) : null, q.getScore(),
                fileService.decodeImages(q.getImages()));
    }
}
