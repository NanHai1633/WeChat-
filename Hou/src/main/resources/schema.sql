CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(32)  NOT NULL,
    password      VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    token_version INT          NOT NULL DEFAULT 0,
    -- 0 表示未删除；软删除时写入自身 id，这样删除后账号名可以重新注册
    deleted       BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL,
    UNIQUE KEY uk_user_username (username, deleted)
);

CREATE TABLE IF NOT EXISTS paper (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    title      VARCHAR(200) NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    deleted    INT          NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS question (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id   BIGINT        NOT NULL,
    type       VARCHAR(16)   NOT NULL,
    sort_order INT           NOT NULL,
    content    TEXT          NOT NULL,
    option_a   VARCHAR(1000) NULL,
    option_b   VARCHAR(1000) NULL,
    option_c   VARCHAR(1000) NULL,
    option_d   VARCHAR(1000) NULL,
    -- 选择题 A-D；判断题 TRUE/FALSE；填空题 JSON 二维数组（每个空的可接受答案）；大题为参考答案
    answer     TEXT          NULL,
    score      DECIMAL(6, 2) NOT NULL,
    -- 题干图片的文件名，JSON 数组
    images     TEXT          NULL,
    KEY idx_question_paper (paper_id, type, sort_order)
);

CREATE TABLE IF NOT EXISTS submission (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id     BIGINT        NOT NULL,
    student_id   BIGINT        NOT NULL,
    status       VARCHAR(16)   NOT NULL,
    auto_score   DECIMAL(8, 2) NULL,
    total_score  DECIMAL(8, 2) NULL,
    submitted_at DATETIME      NULL,
    graded_at    DATETIME      NULL,
    graded_by    BIGINT        NULL,
    created_at   DATETIME      NOT NULL,
    UNIQUE KEY uk_submission_paper_student (paper_id, student_id),
    KEY idx_submission_student (student_id)
);

CREATE TABLE IF NOT EXISTS submission_answer (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT        NOT NULL,
    question_id   BIGINT        NOT NULL,
    -- 填空题为 JSON 数组，其余为纯文本；未作答为 NULL
    answer        TEXT          NULL,
    correct       BOOLEAN       NULL,
    score         DECIMAL(6, 2) NULL,
    teacher_comment VARCHAR(1000) NULL,
    -- 大题作答附带的照片文件名，JSON 数组
    images        TEXT          NULL,
    updated_at    DATETIME      NOT NULL,
    UNIQUE KEY uk_answer_submission_question (submission_id, question_id)
);
