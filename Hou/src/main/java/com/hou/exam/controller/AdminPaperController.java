package com.hou.exam.controller;

import com.hou.exam.common.ApiResponse;
import com.hou.exam.common.PageResult;
import com.hou.exam.dto.PaperDtos;
import com.hou.exam.service.PaperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminPaperController {

    private final PaperService paperService;

    @PostMapping("/papers")
    public ApiResponse<PaperDtos.PaperVO> create(@Valid @RequestBody PaperDtos.PaperRequest request) {
        return ApiResponse.ok(paperService.create(request));
    }

    @GetMapping("/papers")
    public ApiResponse<PageResult<PaperDtos.PaperVO>> list(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(paperService.list(page, size));
    }

    @GetMapping("/papers/{id}")
    public ApiResponse<PaperDtos.PaperDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(paperService.detail(id));
    }

    @PutMapping("/papers/{id}")
    public ApiResponse<PaperDtos.PaperVO> rename(@PathVariable Long id,
                                                 @Valid @RequestBody PaperDtos.PaperRequest request) {
        return ApiResponse.ok(paperService.rename(id, request));
    }

    @DeleteMapping("/papers/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        paperService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/papers/{id}/status")
    public ApiResponse<PaperDtos.PaperVO> changeStatus(@PathVariable Long id,
                                                       @Valid @RequestBody PaperDtos.PaperStatusRequest request) {
        return ApiResponse.ok(paperService.changeStatus(id, request.status()));
    }

    @PostMapping("/papers/{id}/questions")
    public ApiResponse<List<PaperDtos.QuestionVO>> addQuestions(@PathVariable Long id,
                                                                @Valid @RequestBody PaperDtos.QuestionBatchRequest request) {
        return ApiResponse.ok(paperService.addQuestions(id, request.questions()));
    }

    @PutMapping("/questions/{id}")
    public ApiResponse<PaperDtos.QuestionVO> updateQuestion(@PathVariable Long id,
                                                            @Valid @RequestBody PaperDtos.QuestionRequest request) {
        return ApiResponse.ok(paperService.updateQuestion(id, request));
    }

    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long id) {
        paperService.deleteQuestion(id);
        return ApiResponse.ok();
    }
}
