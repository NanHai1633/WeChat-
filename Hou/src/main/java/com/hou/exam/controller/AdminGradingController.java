package com.hou.exam.controller;

import com.hou.exam.auth.CurrentUser;
import com.hou.exam.common.ApiResponse;
import com.hou.exam.common.PageResult;
import com.hou.exam.dto.GradingDtos;
import com.hou.exam.dto.SubmissionRow;
import com.hou.exam.entity.SubmissionStatus;
import com.hou.exam.service.GradingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminGradingController {

    private final GradingService gradingService;

    @GetMapping("/papers/{id}/submissions")
    public ApiResponse<PageResult<SubmissionRow>> listByPaper(@PathVariable Long id,
                                                              @RequestParam(required = false) SubmissionStatus status,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(gradingService.listByPaper(id, status == null ? null : status.name(), page, size));
    }

    @GetMapping("/students/{id}/submissions")
    public ApiResponse<PageResult<SubmissionRow>> listByStudent(@PathVariable Long id,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(gradingService.listByStudent(id, page, size));
    }

    @GetMapping("/submissions/pending")
    public ApiResponse<PageResult<SubmissionRow>> listPending(@RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(gradingService.listPending(page, size));
    }

    @GetMapping("/submissions/{id}")
    public ApiResponse<GradingDtos.SubmissionDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.ok(gradingService.detail(id));
    }

    @PutMapping("/submissions/{id}/grades")
    public ApiResponse<Void> saveGrades(@PathVariable Long id, @Valid @RequestBody GradingDtos.GradesRequest request) {
        gradingService.saveGrades(id, request.grades());
        return ApiResponse.ok();
    }

    @PostMapping("/submissions/{id}/finish")
    public ApiResponse<GradingDtos.SubmissionDetailVO> finish(@PathVariable Long id) {
        return ApiResponse.ok(gradingService.finish(id, CurrentUser.userId()));
    }
}
