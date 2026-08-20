package com.wrj.platform.repository;

import com.wrj.platform.entity.LlmUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LlmUsageLogRepository extends JpaRepository<LlmUsageLog, Long> {

    Page<LlmUsageLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LlmUsageLog> findByModelIdOrderByCreatedAtDesc(Long modelId, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime since);

    /** 每日 Token 用量:[日期, promptTokens, completionTokens, 调用次数] */
    @Query(value = "SELECT TO_CHAR(created_at, 'YYYY-MM-DD') AS d, " +
            "COALESCE(SUM(prompt_tokens),0), COALESCE(SUM(completion_tokens),0), COUNT(*) " +
            "FROM llm_usage_log WHERE created_at >= :since GROUP BY d ORDER BY d",
            nativeQuery = true)
    List<Object[]> dailyStats(@Param("since") LocalDateTime since);

    /** 按模型聚合:[modelId, modelName, provider, 调用次数, 总Token, 失败次数, 平均耗时ms] */
    @Query(value = "SELECT model_id, MAX(model_name), MAX(provider), COUNT(*), " +
            "COALESCE(SUM(total_tokens),0), COALESCE(SUM(CASE WHEN status='FAIL' THEN 1 ELSE 0 END),0), " +
            "COALESCE(AVG(duration_ms),0) " +
            "FROM llm_usage_log WHERE created_at >= :since GROUP BY model_id ORDER BY 5 DESC",
            nativeQuery = true)
    List<Object[]> modelStats(@Param("since") LocalDateTime since);

    /** 总览:[总次数, 总Token, 失败次数, 平均耗时ms] */
    @Query(value = "SELECT COUNT(*), COALESCE(SUM(total_tokens),0), " +
            "COALESCE(SUM(CASE WHEN status='FAIL' THEN 1 ELSE 0 END),0), COALESCE(AVG(duration_ms),0) " +
            "FROM llm_usage_log", nativeQuery = true)
    List<Object[]> overview();

    /** 近 N 小时内 id 升序(用量裁剪用) */
    @Query("SELECT u.id FROM LlmUsageLog u ORDER BY u.id ASC")
    List<Long> findIdsAsc(Pageable pageable);

    long count();
}
