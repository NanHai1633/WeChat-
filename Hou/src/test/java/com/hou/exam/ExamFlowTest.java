package com.hou.exam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExamFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    String admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        admin = login("admin1", "Admin1@test");
    }

    private record Res(int status, JsonNode body) {
        JsonNode data() {
            return body.get("data");
        }
    }

    private Res call(HttpMethod method, String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, url);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        MvcResult result = mvc.perform(request).andReturn();
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Res(result.getResponse().getStatus(), text.isEmpty() ? null : json.readTree(text));
    }

    private Res get(String url, String token) throws Exception {
        return call(HttpMethod.GET, url, token, null);
    }

    private Res post(String url, String token, Object body) throws Exception {
        return call(HttpMethod.POST, url, token, body);
    }

    private Res put(String url, String token, Object body) throws Exception {
        return call(HttpMethod.PUT, url, token, body);
    }

    private String login(String username, String password) throws Exception {
        Res res = post("/api/auth/login", null, Map.of("username", username, "password", password));
        assertThat(res.status()).isEqualTo(200);
        return res.data().get("token").asText();
    }

    private String[] newStudent() throws Exception {
        String name = "s" + UUID.randomUUID().toString().substring(0, 8);
        Res res = post("/api/admin/students", admin, Map.of("username", name, "password", "pass123"));
        assertThat(res.status()).isEqualTo(200);
        return new String[]{res.data().get("id").asText(), name, login(name, "pass123")};
    }

    private static Map<String, Object> single(String answer, double score) {
        Map<String, Object> q = new HashMap<>();
        q.put("type", "SINGLE_CHOICE");
        q.put("content", "选择题");
        q.put("optionA", "a");
        q.put("optionB", "b");
        q.put("optionC", "c");
        q.put("optionD", "d");
        q.put("answer", answer);
        q.put("score", score);
        return q;
    }

    private static Map<String, Object> tf(String answer, double score) {
        return Map.of("type", "TRUE_FALSE", "content", "判断题", "answer", answer, "score", score);
    }

    private static Map<String, Object> fill(List<List<String>> blanks, double score) {
        return Map.of("type", "FILL_BLANK", "content", "填空题", "blanks", blanks, "score", score);
    }

    private static Map<String, Object> essay(double score) {
        return Map.of("type", "ESSAY", "content", "大题", "answer", "参考答案", "score", score);
    }

    /** 创建并发布试卷，返回 [paperId, q1Id, q2Id, ...]。 */
    @SafeVarargs
    private List<String> publishPaper(Map<String, Object>... questions) throws Exception {
        String paperId = post("/api/admin/papers", admin, Map.of("title", "卷" + UUID.randomUUID()))
                .data().get("id").asText();
        Res added = post("/api/admin/papers/" + paperId + "/questions", admin, Map.of("questions", List.of(questions)));
        assertThat(added.status()).isEqualTo(200);
        List<String> ids = new ArrayList<>(List.of(paperId));
        added.data().forEach(q -> ids.add(q.get("id").asText()));
        assertThat(put("/api/admin/papers/" + paperId + "/status", admin, Map.of("status", "PUBLISHED")).status())
                .isEqualTo(200);
        return ids;
    }

    private Res answer(String token, String paper, String question, Object body) throws Exception {
        return put("/api/student/papers/" + paper + "/questions/" + question + "/answer", token, body);
    }

    @Test
    void 只有两个管理员且密码错误不能登录() throws Exception {
        login("admin2", "Admin2@test");
        Res wrong = post("/api/auth/login", null, Map.of("username", "admin1", "password", "bad"));
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.body().get("code").asInt()).isEqualTo(401);
    }

    @Test
    void 登录页选择的身份必须和账号一致() throws Exception {
        String[] s = newStudent();
        assertThat(post("/api/auth/login", null, Map.of("username", s[1], "password", "pass123", "role", "ADMIN"))
                .status()).isEqualTo(403);
        assertThat(post("/api/auth/login", null, Map.of("username", "admin1", "password", "Admin1@test", "role", "STUDENT"))
                .status()).isEqualTo(403);
        assertThat(post("/api/auth/login", null, Map.of("username", s[1], "password", "pass123", "role", "STUDENT"))
                .status()).isEqualTo(200);
    }

    @Test
    void 学生和管理员接口互相隔离() throws Exception {
        String student = newStudent()[2];
        assertThat(get("/api/admin/students", null).status()).isEqualTo(401);
        assertThat(get("/api/admin/students", "not-a-token").status()).isEqualTo(401);
        assertThat(get("/api/admin/students", student).status()).isEqualTo(403);
        assertThat(get("/api/student/papers", admin).status()).isEqualTo(403);
    }

    @Test
    void 路径带分号参数也不能绕过管理员校验() throws Exception {
        String student = newStudent()[2];
        assertThat(get("/api/admin;x=1/students", student).status()).isIn(400, 403, 404);
        assertThat(get("/api/admin/students;x=1", student).status()).isIn(400, 403, 404);
    }

    @Test
    void 账号管理_删除后旧token失效且账号名可重新注册() throws Exception {
        String[] s = newStudent();
        assertThat(post("/api/admin/students", admin, Map.of("username", s[1], "password", "pass123")).status())
                .isEqualTo(409);
        assertThat(get("/api/admin/students?keyword=" + s[1], admin).data().get("total").asInt()).isEqualTo(1);

        assertThat(call(HttpMethod.DELETE, "/api/admin/students/" + s[0], admin, null).status()).isEqualTo(200);
        assertThat(get("/api/student/papers", s[2]).status()).isEqualTo(401);
        assertThat(post("/api/auth/login", null, Map.of("username", s[1], "password", "pass123")).status()).isEqualTo(401);
        assertThat(post("/api/admin/students", admin, Map.of("username", s[1], "password", "pass456")).status())
                .isEqualTo(200);
    }

    @Test
    void 不能通过学生接口删除或重置管理员() throws Exception {
        String adminId = post("/api/auth/login", null, Map.of("username", "admin2", "password", "Admin2@test"))
                .data().get("userId").asText();
        assertThat(call(HttpMethod.DELETE, "/api/admin/students/" + adminId, admin, null).status()).isEqualTo(404);
        assertThat(put("/api/admin/students/" + adminId + "/password", admin, Map.of("password", "hacked1")).status())
                .isEqualTo(404);
        login("admin2", "Admin2@test");
    }

    @Test
    void 重置密码后旧token失效() throws Exception {
        String[] s = newStudent();
        assertThat(put("/api/admin/students/" + s[0] + "/password", admin, Map.of("password", "newpass1")).status())
                .isEqualTo(200);
        assertThat(get("/api/student/papers", s[2]).status()).isEqualTo(401);
        login(s[1], "newpass1");
    }

    @Test
    void 题目校验() throws Exception {
        String paperId = post("/api/admin/papers", admin, Map.of("title", "校验")).data().get("id").asText();
        String url = "/api/admin/papers/" + paperId + "/questions";
        assertThat(put("/api/admin/papers/" + paperId + "/status", admin, Map.of("status", "PUBLISHED")).status())
                .isEqualTo(400);
        assertThat(post(url, admin, Map.of("questions", List.of(single("E", 5)))).status()).isEqualTo(400);
        assertThat(post(url, admin, Map.of("questions", List.of(tf("YES", 5)))).status()).isEqualTo(400);
        assertThat(post(url, admin, Map.of("questions", List.of(fill(List.of(List.of(" ")), 5)))).status()).isEqualTo(400);
        assertThat(post(url, admin, Map.of("questions", List.of(essay(-1)))).status()).isEqualTo(400);
        assertThat(post(url, admin, Map.of("questions", List.of(Map.of("type", "NOPE", "content", "x", "score", 1)))).status())
                .isEqualTo(400);
        // 批量中有一题不合法时整批回滚
        assertThat(post(url, admin, Map.of("questions", List.of(single("A", 5), single("X", 5)))).status()).isEqualTo(400);
        assertThat(get("/api/admin/papers/" + paperId, admin).data().get("questions").size()).isZero();
    }

    @Test
    void 未发布的试卷学生看不到() throws Exception {
        String student = newStudent()[2];
        String paperId = post("/api/admin/papers", admin, Map.of("title", "草稿")).data().get("id").asText();
        assertThat(get("/api/student/papers/" + paperId, student).status()).isEqualTo(404);
        assertThat(post("/api/student/papers/" + paperId + "/submit", student, null).status()).isEqualTo(404);
    }

    @Test
    void 学生接口不泄露正确答案_只显示这篇有的题型_答案可修改() throws Exception {
        List<String> ids = publishPaper(single("B", 10), fill(List.of(List.of("秘密答案")), 10));
        String paper = ids.get(0);
        String student = newStudent()[2];

        JsonNode overview = get("/api/student/papers/" + paper, student).data();
        assertThat(overview.get("sections").size()).isEqualTo(2);
        assertThat(overview.get("sections").get(0).get("type").asText()).isEqualTo("SINGLE_CHOICE");
        assertThat(overview.get("sections").get(1).get("type").asText()).isEqualTo("FILL_BLANK");

        JsonNode choice = get("/api/student/papers/" + paper + "/questions?type=SINGLE_CHOICE", student).data();
        assertThat(choice.size()).isEqualTo(1);
        assertThat(choice.get(0).has("answer")).isFalse();
        assertThat(choice.get(0).get("optionD").asText()).isEqualTo("d");

        Res all = get("/api/student/papers/" + paper + "/questions", student);
        assertThat(all.body().toString()).doesNotContain("秘密答案");
        assertThat(all.data().get(1).get("blankCount").asInt()).isEqualTo(1);

        assertThat(answer(student, paper, ids.get(1), Map.of("answer", "A")).status()).isEqualTo(200);
        assertThat(answer(student, paper, ids.get(1), Map.of("answer", "B")).status()).isEqualTo(200);
        choice = get("/api/student/papers/" + paper + "/questions?type=SINGLE_CHOICE", student).data();
        assertThat(choice.get(0).get("myAnswer").asText()).isEqualTo("B");
        assertThat(get("/api/student/papers/" + paper, student).data().get("answeredCount").asInt()).isEqualTo(1);

        // 清空答案
        assertThat(answer(student, paper, ids.get(1), Map.of("answer", "")).status()).isEqualTo(200);
        assertThat(get("/api/student/papers/" + paper, student).data().get("answeredCount").asInt()).isZero();

        assertThat(answer(student, paper, ids.get(1), Map.of("answer", "Z")).status()).isEqualTo(400);
        assertThat(answer(student, paper, ids.get(2), Map.of("blanks", List.of("a", "b"))).status()).isEqualTo(400);
        assertThat(answer(student, paper, "999999", Map.of("answer", "A")).status()).isEqualTo(404);
    }

    @Test
    void 交卷前不能查成绩_没有大题的试卷交卷立刻出分_交卷后不能改() throws Exception {
        List<String> ids = publishPaper(single("B", 10), tf("FALSE", 10),
                fill(List.of(List.of("北京", "Beijing"), List.of("上海")), 20), single("C", 5));
        String paper = ids.get(0);
        String student = newStudent()[2];

        assertThat(get("/api/student/papers/" + paper + "/result", student).status()).isEqualTo(409);

        answer(student, paper, ids.get(1), Map.of("answer", "B"));
        answer(student, paper, ids.get(2), Map.of("answer", "TRUE"));
        answer(student, paper, ids.get(3), Map.of("blanks", List.of(" Beijing ", "广州")));

        JsonNode result = post("/api/student/papers/" + paper + "/submit", student, null).data();
        assertThat(result.get("status").asText()).isEqualTo("GRADED");
        assertThat(result.get("totalScore").decimalValue()).isEqualByComparingTo("20");
        assertThat(result.get("maxScore").decimalValue()).isEqualByComparingTo("45");

        JsonNode items = result.get("items");
        assertThat(items.get(0).get("correct").asBoolean()).isTrue();
        assertThat(items.get(1).get("correct").asBoolean()).isFalse();
        assertThat(items.get(2).get("correct").asBoolean()).isFalse();
        assertThat(items.get(2).get("score").decimalValue()).isEqualByComparingTo("10");
        assertThat(items.get(3).get("correct").asBoolean()).isFalse();
        assertThat(items.get(3).get("myAnswer").isNull()).isTrue();
        assertThat(items.get(0).has("answer")).isFalse();
        assertThat(items.get(0).has("correctAnswer")).isFalse();

        assertThat(answer(student, paper, ids.get(1), Map.of("answer", "A")).status()).isEqualTo(409);
        assertThat(post("/api/student/papers/" + paper + "/submit", student, null).status()).isEqualTo(409);

        // 下架后仍能看成绩，且有作答后题目不能再改
        assertThat(put("/api/admin/papers/" + paper + "/status", admin, Map.of("status", "DRAFT")).status()).isEqualTo(200);
        assertThat(get("/api/student/papers/" + paper + "/result", student).status()).isEqualTo(200);
        assertThat(call(HttpMethod.DELETE, "/api/admin/questions/" + ids.get(1), admin, null).status()).isEqualTo(409);
        assertThat(put("/api/admin/questions/" + ids.get(1), admin, single("A", 10)).status()).isEqualTo(409);
    }

    @Test
    void 有大题要老师判完学生才能看到成绩() throws Exception {
        List<String> ids = publishPaper(single("A", 10), essay(30), essay(20));
        String paper = ids.get(0);
        String[] s = newStudent();
        String student = s[2];

        answer(student, paper, ids.get(1), Map.of("answer", "A"));
        answer(student, paper, ids.get(2), Map.of("answer", "我的大题答案"));
        answer(student, paper, ids.get(3), Map.of("answer", "第二道大题"));

        // 学生还没交卷时老师不能判
        JsonNode rows = get("/api/admin/papers/" + paper + "/submissions", admin).data().get("records");
        String submission = null;
        for (JsonNode row : rows) {
            if (s[0].equals(row.get("studentId").asText())) {
                submission = row.get("submissionId").asText();
                assertThat(row.get("status").asText()).isEqualTo("IN_PROGRESS");
            }
        }
        assertThat(submission).isNotNull();
        assertThat(post("/api/admin/submissions/" + submission + "/finish", admin, null).status()).isEqualTo(409);

        JsonNode submitted = post("/api/student/papers/" + paper + "/submit", student, null).data();
        assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(submitted.get("totalScore").isNull()).isTrue();
        assertThat(submitted.get("items").size()).isZero();

        // 待判卷筛选
        JsonNode pending = get("/api/admin/papers/" + paper + "/submissions?status=SUBMITTED", admin).data();
        assertThat(pending.get("total").asInt()).isEqualTo(1);

        String grades = "/api/admin/submissions/" + submission + "/grades";
        assertThat(put(grades, admin, Map.of("grades", List.of(Map.of("questionId", ids.get(2), "score", 31)))).status())
                .isEqualTo(400);
        assertThat(put(grades, admin, Map.of("grades", List.of(Map.of("questionId", ids.get(1), "score", 1)))).status())
                .isEqualTo(400);
        assertThat(put(grades, admin, Map.of("grades", List.of(Map.of("questionId", ids.get(2), "score", 30, "comment", "很好")))).status())
                .isEqualTo(200);

        // 还有一题没打分，不能完成
        assertThat(post("/api/admin/submissions/" + submission + "/finish", admin, null).status()).isEqualTo(400);
        assertThat(get("/api/student/papers/" + paper + "/result", student).data().get("totalScore").isNull()).isTrue();

        assertThat(put(grades, admin, Map.of("grades", List.of(Map.of("questionId", ids.get(3), "score", 12.5)))).status())
                .isEqualTo(200);
        JsonNode detail = post("/api/admin/submissions/" + submission + "/finish", admin, null).data();
        assertThat(detail.get("totalScore").decimalValue()).isEqualByComparingTo("52.5");
        assertThat(detail.get("answers").get(1).get("studentAnswer").asText()).isEqualTo("我的大题答案");
        assertThat(detail.get("answers").get(0).get("correctAnswer").asText()).isEqualTo("A");

        JsonNode result = get("/api/student/papers/" + paper + "/result", student).data();
        assertThat(result.get("status").asText()).isEqualTo("GRADED");
        assertThat(result.get("totalScore").decimalValue()).isEqualByComparingTo("52.5");
        assertThat(result.get("items").get(1).get("correct").asBoolean()).isTrue();
        assertThat(result.get("items").get(1).get("comment").asText()).isEqualTo("很好");
        assertThat(result.get("items").get(2).get("correct").asBoolean()).isFalse();
        assertThat(result.get("items").get(2).get("score").decimalValue()).isEqualByComparingTo("12.5");
        assertThat(result.toString()).doesNotContain("参考答案");

        // 判完可以改分，总分同步
        assertThat(put(grades, admin, Map.of("grades", List.of(Map.of("questionId", ids.get(3), "score", 20)))).status())
                .isEqualTo(200);
        assertThat(get("/api/student/papers/" + paper + "/result", student).data().get("totalScore").decimalValue())
                .isEqualByComparingTo("60");

        // 学生列表带分数，管理员能按学生查
        JsonNode list = get("/api/student/papers", student).data();
        boolean found = false;
        for (JsonNode row : list) {
            if (paper.equals(row.get("paperId").asText())) {
                found = true;
                assertThat(row.get("totalScore").decimalValue()).isEqualByComparingTo("60");
                assertThat(row.get("maxScore").decimalValue()).isEqualByComparingTo("60");
            }
        }
        assertThat(found).isTrue();
        JsonNode byStudent = get("/api/admin/students/" + s[0] + "/submissions?size=100", admin).data().get("records");
        boolean foundAdmin = false;
        for (JsonNode row : byStudent) {
            foundAdmin |= paper.equals(row.get("paperId").asText()) && "GRADED".equals(row.get("status").asText());
        }
        assertThat(foundAdmin).isTrue();

        // 删除学生后成绩仍保留
        assertThat(call(HttpMethod.DELETE, "/api/admin/students/" + s[0], admin, null).status()).isEqualTo(200);
        JsonNode after = get("/api/admin/papers/" + paper + "/submissions", admin).data().get("records");
        boolean kept = false;
        for (JsonNode row : after) {
            kept |= s[0].equals(row.get("studentId").asText()) && row.get("username").asText().contains("已删除");
        }
        assertThat(kept).isTrue();
    }

    private Res upload(String url, String token, String filename, byte[] content) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.multipart(url)
                        .file(new MockMultipartFile("file", filename, "image/png", content))
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        return new Res(result.getResponse().getStatus(),
                json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)));
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};

    @Test
    void 大题文字结果必填_只传照片不能交卷() throws Exception {
        List<String> ids = publishPaper(essay(10));
        String paper = ids.get(0);
        String student = newStudent()[2];

        Res empty = post("/api/student/papers/" + paper + "/submit", student, null);
        assertThat(empty.status()).isEqualTo(400);
        assertThat(empty.body().get("message").asText()).contains("没有填写结果");

        String photo = upload("/api/student/files", student, "a.png", PNG).data().get("name").asText();
        assertThat(answer(student, paper, ids.get(1), Map.of("images", List.of(photo))).status()).isEqualTo(200);
        JsonNode saved = get("/api/student/papers/" + paper + "/questions", student).data().get(0);
        assertThat(saved.get("myImages").get(0).asText()).isEqualTo(photo);
        assertThat(saved.get("myAnswer").isNull()).isTrue();
        assertThat(get("/api/student/papers/" + paper, student).data().get("answeredCount").asInt()).isZero();
        assertThat(post("/api/student/papers/" + paper + "/submit", student, null).status()).isEqualTo(400);

        // 只改文字不传 images，照片保留
        assertThat(answer(student, paper, ids.get(1), Map.of("answer", "文字结果")).status()).isEqualTo(200);
        saved = get("/api/student/papers/" + paper + "/questions", student).data().get(0);
        assertThat(saved.get("myAnswer").asText()).isEqualTo("文字结果");
        assertThat(saved.get("myImages").size()).isEqualTo(1);

        assertThat(post("/api/student/papers/" + paper + "/submit", student, null).data().get("status").asText())
                .isEqualTo("SUBMITTED");

        // 老师判卷能看到照片，判完学生也能看到
        JsonNode rows = get("/api/admin/papers/" + paper + "/submissions?status=SUBMITTED", admin).data().get("records");
        String submission = rows.get(0).get("submissionId").asText();
        assertThat(get("/api/admin/submissions/" + submission, admin).data().get("answers").get(0)
                .get("studentImages").get(0).asText()).isEqualTo(photo);
        put("/api/admin/submissions/" + submission + "/grades", admin,
                Map.of("grades", List.of(Map.of("questionId", ids.get(1), "score", 10))));
        post("/api/admin/submissions/" + submission + "/finish", admin, null);
        assertThat(get("/api/student/papers/" + paper + "/result", student).data().get("items").get(0)
                .get("myImages").get(0).asText()).isEqualTo(photo);
    }

    @Test
    void 大题不传照片也能交卷_文字和照片都清空会删除作答() throws Exception {
        List<String> ids = publishPaper(essay(10));
        String paper = ids.get(0);
        String student = newStudent()[2];
        String photo = upload("/api/student/files", student, "a.png", PNG).data().get("name").asText();
        answer(student, paper, ids.get(1), Map.of("answer", "结果", "images", List.of(photo)));
        answer(student, paper, ids.get(1), Map.of("answer", "", "images", List.of()));
        JsonNode saved = get("/api/student/papers/" + paper + "/questions", student).data().get(0);
        assertThat(saved.get("myAnswer").isNull()).isTrue();
        assertThat(saved.get("myImages").size()).isZero();
        answer(student, paper, ids.get(1), Map.of("answer", "只有文字"));
        assertThat(post("/api/student/papers/" + paper + "/submit", student, null).status()).isEqualTo(200);
    }

    @Test
    void 只有大题能带照片_图片必须是上传过的() throws Exception {
        List<String> ids = publishPaper(single("A", 5), essay(5));
        String paper = ids.get(0);
        String student = newStudent()[2];
        String photo = upload("/api/student/files", student, "a.png", PNG).data().get("name").asText();
        assertThat(answer(student, paper, ids.get(1), Map.of("answer", "A", "images", List.of(photo))).status()).isEqualTo(400);
        assertThat(answer(student, paper, ids.get(2), Map.of("answer", "x", "images", List.of("../../etc/passwd"))).status()).isEqualTo(400);
        assertThat(answer(student, paper, ids.get(2), Map.of("answer", "x", "images", List.of("0".repeat(32) + ".png"))).status()).isEqualTo(400);
    }

    @Test
    void 每种题型都能带题干图片_学生能看到_修改时不传则保留() throws Exception {
        String image = upload("/api/admin/files", admin, "q.png", PNG).data().get("name").asText();
        String paperId = post("/api/admin/papers", admin, Map.of("title", "带图")).data().get("id").asText();
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Map<String, Object> q : List.of(single("A", 5), tf("TRUE", 5), fill(List.of(List.of("a")), 5), essay(5))) {
            Map<String, Object> copy = new HashMap<>(q);
            copy.put("images", List.of(image));
            questions.add(copy);
        }
        Res added = post("/api/admin/papers/" + paperId + "/questions", admin, Map.of("questions", questions));
        assertThat(added.status()).isEqualTo(200);
        added.data().forEach(q -> assertThat(q.get("images").get(0).asText()).isEqualTo(image));

        String firstId = added.data().get(0).get("id").asText();
        JsonNode updated = put("/api/admin/questions/" + firstId, admin, single("B", 5)).data();
        assertThat(updated.get("images").get(0).asText()).isEqualTo(image);
        Map<String, Object> removed = new HashMap<>(single("B", 5));
        removed.put("images", List.of());
        assertThat(put("/api/admin/questions/" + firstId, admin, removed).data().get("images").size()).isZero();

        put("/api/admin/papers/" + paperId + "/status", admin, Map.of("status", "PUBLISHED"));
        JsonNode view = get("/api/student/papers/" + paperId + "/questions", newStudent()[2]).data();
        assertThat(view.get(1).get("images").get(0).asText()).isEqualTo(image);
        assertThat(view.get(3).get("images").get(0).asText()).isEqualTo(image);

        Map<String, Object> tooMany = new HashMap<>(essay(5));
        tooMany.put("images", java.util.Collections.nCopies(10, image));
        assertThat(post("/api/admin/papers/" + paperId + "/questions", admin, Map.of("questions", List.of(tooMany))).status())
                .isEqualTo(400);
    }

    @Test
    void 图片上传校验类型和权限_读取校验文件名() throws Exception {
        String student = newStudent()[2];
        Res fake = upload("/api/admin/files", admin, "evil.png", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8));
        assertThat(fake.status()).isEqualTo(400);
        assertThat(upload("/api/admin/files", student, "a.png", PNG).status()).isEqualTo(403);
        assertThat(upload("/api/student/files", admin, "a.png", PNG).status()).isEqualTo(403);
        assertThat(upload("/api/student/files", "bad-token", "a.png", PNG).status()).isEqualTo(401);

        String name = upload("/api/admin/files", admin, "原名.png", PNG).data().get("name").asText();
        assertThat(name).matches("[0-9a-f]{32}\\.png");
        MvcResult image = mvc.perform(MockMvcRequestBuilders.get("/api/files/" + name)).andReturn();
        assertThat(image.getResponse().getStatus()).isEqualTo(200);
        assertThat(image.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(image.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(image.getResponse().getContentAsByteArray()).isEqualTo(PNG);
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/files/..%2F..%2Fpom.xml")).andReturn().getResponse().getStatus())
                .isIn(400, 404);
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/files/application.yml")).andReturn().getResponse().getStatus())
                .isEqualTo(404);
    }

    private JsonNode rowOf(JsonNode rows, String field, String value) {
        for (JsonNode row : rows) {
            if (value.equals(row.get(field).asText())) {
                return row;
            }
        }
        return null;
    }

    @Test
    void 学生列表带题数和已答数_照片不算已答() throws Exception {
        List<String> ids = publishPaper(single("A", 5), tf("TRUE", 5), essay(5));
        String paper = ids.get(0);
        String student = newStudent()[2];
        JsonNode row = rowOf(get("/api/student/papers", student).data(), "paperId", paper);
        assertThat(row.get("questionCount").asInt()).isEqualTo(3);
        assertThat(row.get("answeredCount").asInt()).isZero();

        answer(student, paper, ids.get(1), Map.of("answer", "A"));
        String photo = upload("/api/student/files", student, "a.png", PNG).data().get("name").asText();
        answer(student, paper, ids.get(3), Map.of("images", List.of(photo)));
        row = rowOf(get("/api/student/papers", student).data(), "paperId", paper);
        assertThat(row.get("answeredCount").asInt()).isEqualTo(1);
    }

    @Test
    void 待判卷总列表_判完或删除后不再出现() throws Exception {
        List<String> ids = publishPaper(essay(10));
        String paper = ids.get(0);
        String[] a = newStudent();
        String[] b = newStudent();
        assertThat(get("/api/admin/submissions/pending", a[2]).status()).isEqualTo(403);
        for (String[] s : List.of(a, b)) {
            answer(s[2], paper, ids.get(1), Map.of("answer", "结果"));
            post("/api/student/papers/" + paper + "/submit", s[2], null);
        }
        JsonNode rows = get("/api/admin/submissions/pending?size=100", admin).data().get("records");
        JsonNode first = rowOf(rows, "username", a[1]);
        assertThat(first).isNotNull();
        assertThat(first.get("title").asText()).startsWith("卷");
        assertThat(rowOf(rows, "username", b[1])).isNotNull();

        String submission = first.get("submissionId").asText();
        put("/api/admin/submissions/" + submission + "/grades", admin,
                Map.of("grades", List.of(Map.of("questionId", ids.get(1), "score", 10))));
        post("/api/admin/submissions/" + submission + "/finish", admin, null);
        rows = get("/api/admin/submissions/pending?size=100", admin).data().get("records");
        assertThat(rowOf(rows, "username", a[1])).isNull();
        assertThat(rowOf(rows, "username", b[1])).isNotNull();

        call(HttpMethod.DELETE, "/api/admin/students/" + b[0], admin, null);
        rows = get("/api/admin/submissions/pending?size=100", admin).data().get("records");
        assertThat(rowOf(rows, "username", b[1])).isNull();
    }

    @Test
    void 不选身份也能登录_后端返回角色() throws Exception {
        Res res = post("/api/auth/login", null, Map.of("username", "admin1", "password", "Admin1@test"));
        assertThat(res.data().get("role").asText()).isEqualTo("ADMIN");
        String[] s = newStudent();
        res = post("/api/auth/login", null, Map.of("username", s[1], "password", "pass123"));
        assertThat(res.data().get("role").asText()).isEqualTo("STUDENT");
    }

    @Test
    void 填空部分得分向下取整() throws Exception {
        List<String> ids = publishPaper(fill(List.of(List.of("a"), List.of("b"), List.of("c")), 10));
        String student = newStudent()[2];
        answer(student, ids.get(0), ids.get(1), Map.of("blanks", List.of("a", "x", "")));
        JsonNode result = post("/api/student/papers/" + ids.get(0) + "/submit", student, null).data();
        assertThat(result.get("totalScore").decimalValue()).isEqualByComparingTo("3.33");
    }

    @Test
    void 学生看不到别人的答卷和其他学生互不影响() throws Exception {
        List<String> ids = publishPaper(single("A", 10));
        String a = newStudent()[2];
        String b = newStudent()[2];
        answer(a, ids.get(0), ids.get(1), Map.of("answer", "A"));
        JsonNode bView = get("/api/student/papers/" + ids.get(0) + "/questions", b).data();
        assertThat(bView.get(0).get("myAnswer").isNull()).isTrue();
        post("/api/student/papers/" + ids.get(0) + "/submit", a, null);
        assertThat(get("/api/student/papers/" + ids.get(0) + "/result", b).status()).isEqualTo(409);
    }

    @Test
    void 删除试卷后学生列表不再显示() throws Exception {
        List<String> ids = publishPaper(single("A", 10));
        String student = newStudent()[2];
        assertThat(call(HttpMethod.DELETE, "/api/admin/papers/" + ids.get(0), admin, null).status()).isEqualTo(200);
        assertThat(get("/api/student/papers", student).body().toString()).doesNotContain("\"paperId\":" + ids.get(0) + ",");
        assertThat(get("/api/student/papers/" + ids.get(0), student).status()).isEqualTo(404);
    }

    @Test
    void 并发保存和交卷不会产生重复答卷() throws Exception {
        List<String> ids = publishPaper(single("A", 10));
        String student = newStudent()[2];
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Res>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> post("/api/student/papers/" + ids.get(0) + "/submit", student, null)));
        }
        int ok = 0;
        for (Future<Res> future : futures) {
            int status = future.get().status();
            assertThat(status).isIn(200, 409);
            ok += status == 200 ? 1 : 0;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }

    @Test
    void 管理员可以修改自己的密码() throws Exception {
        String admin2 = login("admin2", "Admin2@test");
        assertThat(put("/api/admin/password", admin2, Map.of("oldPassword", "wrong", "newPassword", "Changed@1")).status())
                .isEqualTo(400);
        assertThat(put("/api/admin/password", admin2, Map.of("oldPassword", "Admin2@test", "newPassword", "Changed@1")).status())
                .isEqualTo(200);
        assertThat(get("/api/admin/students", admin2).status()).isEqualTo(401);
        String fresh = login("admin2", "Changed@1");
        assertThat(put("/api/admin/password", fresh, Map.of("oldPassword", "Changed@1", "newPassword", "Admin2@test")).status())
                .isEqualTo(200);
    }

    @Test
    void 错误请求返回统一格式() throws Exception {
        assertThat(get("/api/admin/papers/abc", admin).status()).isEqualTo(400);
        assertThat(get("/api/admin/nothing", admin).status()).isEqualTo(404);
        assertThat(post("/api/auth/login", null, Map.of("username", "")).status()).isEqualTo(400);
    }
}
