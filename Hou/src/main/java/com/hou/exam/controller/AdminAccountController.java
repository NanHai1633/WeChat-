package com.hou.exam.controller;

import com.hou.exam.auth.CurrentUser;
import com.hou.exam.common.ApiResponse;
import com.hou.exam.common.PageResult;
import com.hou.exam.dto.AuthDtos;
import com.hou.exam.service.AccountService;
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

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountService accountService;

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        accountService.changeAdminPassword(CurrentUser.userId(), request);
        return ApiResponse.ok();
    }

    @PostMapping("/students")
    public ApiResponse<AccountService.StudentVO> create(@Valid @RequestBody AuthDtos.CreateStudentRequest request) {
        return ApiResponse.ok(accountService.createStudent(request));
    }

    @GetMapping("/students")
    public ApiResponse<PageResult<AccountService.StudentVO>> list(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(accountService.listStudents(keyword, page, size));
    }

    @DeleteMapping("/students/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        accountService.deleteStudent(id);
        return ApiResponse.ok();
    }

    @PutMapping("/students/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        accountService.resetStudentPassword(id, request);
        return ApiResponse.ok();
    }
}
