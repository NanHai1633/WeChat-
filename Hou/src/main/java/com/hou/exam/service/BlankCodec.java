package com.hou.exam.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 填空题在数据库里以 JSON 存储：标准答案是二维数组，学生答案是一维数组。 */
@Component
@RequiredArgsConstructor
public class BlankCodec {

    private final ObjectMapper objectMapper;

    public String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("填空题数据序列化失败", e);
        }
    }

    public List<List<String>> decodeCorrect(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("填空题答案数据损坏", e);
        }
    }

    public List<String> decodeStudent(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("填空题作答数据损坏", e);
        }
    }
}
