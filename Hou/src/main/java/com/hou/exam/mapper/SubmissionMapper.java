package com.hou.exam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hou.exam.dto.SubmissionRow;
import com.hou.exam.entity.Submission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SubmissionMapper extends BaseMapper<Submission> {

    /** 加行锁读取，保证保存答案和交卷不会并发交错。 */
    @Select("SELECT * FROM submission WHERE paper_id = #{paperId} AND student_id = #{studentId} FOR UPDATE")
    Submission selectForUpdate(@Param("paperId") Long paperId, @Param("studentId") Long studentId);

    /** 某篇试卷下所有学生的情况，未开始的学生 status 为 null；已删除的学生只在有答卷时出现。 */
    @Select("""
            <script>
            SELECT s.id AS submission_id, u.id AS student_id,
                   CASE WHEN u.deleted = 0 THEN u.username ELSE CONCAT(u.username, '（已删除）') END AS username,
                   s.status, s.auto_score, s.total_score, s.submitted_at, s.graded_at
            FROM sys_user u
            LEFT JOIN submission s ON s.student_id = u.id AND s.paper_id = #{paperId}
            WHERE u.role = 'STUDENT' AND (u.deleted = 0 OR s.id IS NOT NULL)
            <if test="status != null and status != ''">
              AND s.status = #{status}
            </if>
            ORDER BY s.submitted_at DESC, u.id ASC
            </script>
            """)
    IPage<SubmissionRow> selectByPaper(IPage<SubmissionRow> page, @Param("paperId") Long paperId,
                                       @Param("status") String status);

    /** 某个学生所有试卷的情况，未开始的试卷 status 为 null。 */
    @Select("""
            SELECT s.id AS submission_id, p.id AS paper_id, s.student_id,
                   CASE WHEN p.deleted = 0 THEN p.title ELSE CONCAT(p.title, '（已删除）') END AS title,
                   s.status, s.auto_score, s.total_score, s.submitted_at, s.graded_at
            FROM paper p
            LEFT JOIN submission s ON s.paper_id = p.id AND s.student_id = #{studentId}
            WHERE (p.deleted = 0 AND p.status = 'PUBLISHED') OR s.id IS NOT NULL
            ORDER BY p.id DESC
            """)
    IPage<SubmissionRow> selectByStudent(IPage<SubmissionRow> page, @Param("studentId") Long studentId);

    /** 所有待判的答卷，先交的排前面；已删除的学生和试卷不再需要判。 */
    @Select("""
            SELECT s.id AS submission_id, s.paper_id, s.student_id, u.username, p.title,
                   s.status, s.auto_score, s.total_score, s.submitted_at, s.graded_at
            FROM submission s
            JOIN sys_user u ON u.id = s.student_id AND u.deleted = 0
            JOIN paper p ON p.id = s.paper_id AND p.deleted = 0
            WHERE s.status = 'SUBMITTED'
            ORDER BY s.submitted_at ASC, s.id ASC
            """)
    IPage<SubmissionRow> selectPending(IPage<SubmissionRow> page);

    /** 查答卷详情时带上学生名和试卷名，已删除的也要能查到。 */
    @Select("""
            SELECT s.id AS submission_id, s.paper_id, s.student_id, u.username, p.title,
                   s.status, s.auto_score, s.total_score, s.submitted_at, s.graded_at
            FROM submission s
            JOIN sys_user u ON u.id = s.student_id
            JOIN paper p ON p.id = s.paper_id
            WHERE s.id = #{id}
            """)
    SubmissionRow selectRowById(Long id);
}
