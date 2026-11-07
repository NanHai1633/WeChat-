package com.hou.exam.entity;

public enum SubmissionStatus {
    /** 作答中，答案可以修改 */
    IN_PROGRESS,
    /** 已交卷，等待老师判大题 */
    SUBMITTED,
    /** 判卷完成，学生可以查看成绩 */
    GRADED
}
