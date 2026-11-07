package com.hou.exam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hou.exam.common.BizException;
import com.hou.exam.config.AppProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** 图片存在本地目录，文件名是随机生成的，数据库里只存文件名。 */
@Service
public class FileService {

    private static final int MAX_IMAGES = 9;
    private static final Pattern NAME = Pattern.compile("^[0-9a-f]{32}\\.(jpg|png|gif|webp)$");
    private static final Map<String, MediaType> TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"));

    private final Path dir;
    private final ObjectMapper objectMapper;

    public FileService(AppProperties properties, ObjectMapper objectMapper) {
        this.dir = Path.of(properties.uploadDir()).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public String store(MultipartFile file) {
        try {
            String extension;
            try (InputStream in = file.getInputStream()) {
                extension = detectExtension(in.readNBytes(12));
            }
            if (extension == null) {
                throw BizException.badRequest("只支持 JPG、PNG、GIF、WEBP 格式的图片");
            }
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            file.transferTo(dir.resolve(name));
            return name;
        } catch (IOException e) {
            throw new IllegalStateException("保存图片失败", e);
        }
    }

    public Resource load(String name) {
        if (!NAME.matcher(name).matches() || !Files.isRegularFile(dir.resolve(name))) {
            throw BizException.notFound("图片不存在");
        }
        return new FileSystemResource(dir.resolve(name));
    }

    public MediaType contentType(String name) {
        return TYPES.get(name.substring(name.lastIndexOf('.') + 1));
    }

    /** 校验图片都已上传过，转成存储格式；没有图片时返回 null。 */
    public String encodeImages(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        if (names.size() > MAX_IMAGES) {
            throw BizException.badRequest("最多只能放 " + MAX_IMAGES + " 张图片");
        }
        names.forEach(name -> {
            if (name == null || !NAME.matcher(name).matches() || !Files.isRegularFile(dir.resolve(name))) {
                throw BizException.badRequest("图片不存在，请重新上传");
            }
        });
        try {
            return objectMapper.writeValueAsString(names);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("图片数据序列化失败", e);
        }
    }

    public List<String> decodeImages(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("图片数据损坏", e);
        }
    }

    /** 按文件头判断类型，不信任客户端传的文件名和 Content-Type。 */
    private String detectExtension(byte[] head) {
        if (startsWith(head, 0, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
            return "jpg";
        }
        if (startsWith(head, 0, (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G')) {
            return "png";
        }
        if (startsWith(head, 0, "GIF8".getBytes(StandardCharsets.US_ASCII))) {
            return "gif";
        }
        if (startsWith(head, 0, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && startsWith(head, 8, "WEBP".getBytes(StandardCharsets.US_ASCII))) {
            return "webp";
        }
        return null;
    }

    private boolean startsWith(byte[] head, int offset, byte... expected) {
        return head.length >= offset + expected.length
                && Arrays.equals(head, offset, offset + expected.length, expected, 0, expected.length);
    }
}
