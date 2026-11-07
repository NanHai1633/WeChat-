package com.hou.exam.controller;

import com.hou.exam.common.ApiResponse;
import com.hou.exam.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    public record FileVO(String name) {
    }

    @PostMapping("/api/admin/files")
    public ApiResponse<FileVO> adminUpload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(new FileVO(fileService.store(file)));
    }

    @PostMapping("/api/student/files")
    public ApiResponse<FileVO> studentUpload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(new FileVO(fileService.store(file)));
    }

    /** img 标签带不了 token，所以读取不校验登录；文件名是随机的，猜不出来。 */
    @GetMapping("/api/files/{name}")
    public ResponseEntity<Resource> download(@PathVariable String name) {
        Resource resource = fileService.load(name);
        return ResponseEntity.ok()
                .contentType(fileService.contentType(name))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)))
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
