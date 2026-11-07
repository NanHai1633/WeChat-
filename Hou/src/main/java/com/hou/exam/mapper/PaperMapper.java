package com.hou.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hou.exam.dto.StudentPaperRow;
import com.hou.exam.entity.Paper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PaperMapper extends BaseMapper<Paper> {

    /** 学生可见的试卷：已发布的，加上已交卷的（下架后仍可查看成绩）；已删除的不显示。 */
    @Select("""
            SELECT p.id AS paper_id, p.title, s.id AS submission_id, s.status, s.total_score, s.submitted_at,
                   (SELECT SUM(q.score) FROM question q WHERE q.paper_id = p.id) AS max_score,
                   (SELECT COUNT(*) FROM question q WHERE q.paper_id = p.id) AS question_count,
                   (SELECT COUNT(*) FROM submission_answer a
                     WHERE a.submission_id = s.id AND a.answer IS NOT NULL) AS answered_count
            FROM paper p
            LEFT JOIN submission s ON s.paper_id = p.id AND s.student_id = #{studentId}
            WHERE p.deleted = 0
              AND (p.status = 'PUBLISHED' OR s.status IN ('SUBMITTED', 'GRADED'))
            ORDER BY p.id DESC
            """)
    List<StudentPaperRow> selectForStudent(Long studentId);
}
