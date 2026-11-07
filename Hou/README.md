# 做题系统后端

Spring Boot 3.5、MyBatis-Plus、JWT。代码按 Java 17 编译。

## 运行

默认使用 H2 本地文件库，数据在 `data/` 目录，不用安装数据库。

- **IDEA：** 打开 `Hou` 文件夹，运行 `ExamApplication`。
- **命令行：** 在 `Hou` 目录执行 `mvn spring-boot:run`。

启动后用浏览器打开 `http://127.0.0.1:8080/` 就是网页版。网页源码在 `../Qian/web`，打包时会放进后端；改了网页要重新构建或在 IDEA 里重新运行。

## 本地账号

启动时自动创建两个管理员，配置在 `application-dev.yml`，只用于本地：

| 账号 | 密码 |
|---|---|
| admin1 | Admin1@2026 |
| admin2 | Admin2@2026 |

## 切换到 MySQL

1. 建库：`CREATE DATABASE exam CHARACTER SET utf8mb4;`
2. 设置环境变量：`DB_PASSWORD`、`JWT_SECRET`（至少 32 位）、`ADMIN1_PASSWORD`、`ADMIN2_PASSWORD`。可选：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`。
3. 加上启动参数 `--spring.profiles.active=mysql`。表会在启动时自动创建。

## 测试

```bash
mvn test
```

## 接口

返回格式统一为 `{ code, message, data }`，`code` 为 0 表示成功。除登录外，请求头都要带 `Authorization: Bearer <token>`。

### 登录

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | `username`、`password`，返回的 `role` 决定进入哪个页面。`role` 也可以作为参数传（小程序在用），传了就要和账号角色一致 |

### 图片

上传用 `multipart/form-data`，字段名 `file`，支持 JPG、PNG、GIF、WEBP，每张不超过 5MB，返回 `{ name }`。题目或答案的 `images` 字段存文件名，每处最多 9 张。图片存在 `data/uploads`，可用环境变量 `UPLOAD_DIR` 修改。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/admin/files` | 管理员上传题干图片 |
| POST | `/api/student/files` | 学生上传大题照片 |
| GET | `/api/files/{name}` | 读取图片，不需要登录，文件名是随机的 |

### 管理员

| 方法 | 路径 | 说明 |
|---|---|---|
| PUT | `/api/admin/password` | 修改自己的密码 |
| POST | `/api/admin/students` | 注册学生 |
| GET | `/api/admin/students` | 学生列表，参数 `keyword`、`page`、`size` |
| DELETE | `/api/admin/students/{id}` | 删除学生，成绩保留 |
| PUT | `/api/admin/students/{id}/password` | 重置学生密码 |
| GET | `/api/admin/students/{id}/submissions` | 某学生所有试卷的情况 |
| POST | `/api/admin/papers` | 新建试卷 |
| GET | `/api/admin/papers` | 试卷列表 |
| GET | `/api/admin/papers/{id}` | 试卷详情，含题目和答案 |
| PUT | `/api/admin/papers/{id}` | 改名称 |
| DELETE | `/api/admin/papers/{id}` | 删除试卷，成绩保留 |
| PUT | `/api/admin/papers/{id}/status` | `PUBLISHED` 发布，`DRAFT` 下架 |
| POST | `/api/admin/papers/{id}/questions` | 上传题目和答案，`{ questions: [...] }` |
| PUT | `/api/admin/questions/{id}` | 修改题目或答案 |
| DELETE | `/api/admin/questions/{id}` | 删除题目 |
| GET | `/api/admin/papers/{id}/submissions` | 某试卷所有学生的情况，`status` 可筛选 |
| GET | `/api/admin/submissions/pending` | 所有待判的答卷，先交的排前面 |
| GET | `/api/admin/submissions/{id}` | 答卷详情 |
| PUT | `/api/admin/submissions/{id}/grades` | 保存大题分数，`{ grades: [{ questionId, score, comment }] }` |
| POST | `/api/admin/submissions/{id}/finish` | 完成判卷，学生可见成绩 |

有学生开始作答后，这篇试卷的题目不能再改。

题目字段：

| 题型 `type` | 答案字段 |
|---|---|
| `SINGLE_CHOICE` | `optionA`-`optionD`，`answer` 为 `A`-`D` |
| `TRUE_FALSE` | `answer` 为 `TRUE` / `FALSE` |
| `FILL_BLANK` | `blanks`，如 `[["北京","Beijing"],["上海"]]`，每个空可有多个正确答案 |
| `ESSAY` | `answer` 为可选的参考答案 |

所有题型都要填 `content` 和 `score`，`images` 可选。修改题目时不传 `images` 表示不改动，传空数组表示清空。

### 学生

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/student/papers` | 题目列表，含状态和分数 |
| GET | `/api/student/papers/{id}` | 这篇有哪些题型，每种的题数和已答数 |
| GET | `/api/student/papers/{id}/questions?type=` | 取题，不含正确答案，含自己已存的答案 |
| PUT | `/api/student/papers/{id}/questions/{qid}/answer` | 保存或修改答案。填空题传 `blanks`，其他传 `answer`，传空表示清空。大题可以另传 `images`（选填） |
| POST | `/api/student/papers/{id}/submit` | 交卷并自动判分。每道大题都要填文字结果，否则返回 400 |
| GET | `/api/student/papers/{id}/result` | 判完后返回每题对错、得分和总分 |
