package com.hou.exam.controller;

import com.hou.exam.auth.CurrentUser;
import com.hou.exam.common.ApiResponse;
import com.hou.exam.dto.StudentDtos;
import com.hou.exam.dto.StudentPaperRow;
import com.hou.exam.entity.QuestionType;
import com.hou.exam.service.StudentExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/papers")
@RequiredArgsConstructor
public class StudentController {

    private final StudentExamService examService;

    @GetMapping
    public ApiResponse<List<StudentPaperRow>> list() {
        return ApiResponse.ok(examService.listPapers(CurrentUser.userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudentDtos.PaperOverviewVO> overview(@PathVariable Long id) {
        return ApiResponse.ok(examService.overview(id, CurrentUser.userId()));
    }

    @GetMapping("/{id}/questions")
    public ApiResponse<List<StudentDtos.StudentQuestionVO>> questions(@PathVariable Long id,
                                                                      @RequestParam(required = false) QuestionType type) {
        return ApiResponse.ok(examService.questions(id, type, CurrentUser.userId()));
    }

    @PutMapping("/{id}/questions/{questionId}/answer")
    public ApiResponse<Void> saveAnswer(@PathVariable Long id, @PathVariable Long questionId,
                                        @RequestBody StudentDtos.SaveAnswerRequest request) {
        examService.saveAnswer(id, questionId, request, CurrentUser.userId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<StudentDtos.ResultVO> submit(@PathVariable Long id) {
        return ApiResponse.ok(examService.submit(id, CurrentUser.userId()));
    }

    @GetMapping("/{id}/result")
    public ApiResponse<StudentDtos.ResultVO> result(@PathVariable Long id) {
        return ApiResponse.ok(examService.result(id, CurrentUser.userId()));
    }
}
