package com.qy.dch.mapper;

import com.qy.dch.dto.EventAnalysisDTO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 事件分析结果 Mapper
 */
@Mapper
public interface EventAnalysisMapper {

    /**
     * 插入事件分析结果（重复则更新）
     */
    @Insert("INSERT INTO event_analysis (origin_text_id, event_time, event_location, event_content, event_analysis, analysis_date) " +
            "VALUES (#{originTextId}, #{eventTime}, #{eventLocation}, #{eventContent}, #{eventAnalysis}, #{analysisDate}) " +
            "ON DUPLICATE KEY UPDATE event_time=VALUES(event_time), event_location=VALUES(event_location), " +
            "event_analysis=VALUES(event_analysis), analysis_date=VALUES(analysis_date)")
    void insertOrUpdate(EventAnalysisDTO dto);

    /**
     * 按日期范围和关键词查询事件
     */
    @Select("<script>" +
            "SELECT ea.id, ea.origin_text_id, ea.event_time, ea.event_location, " +
            "ea.event_content, ea.event_analysis, ea.analysis_date, ea.create_time, " +
            "ot.title as sourceTitle " +
            "FROM event_analysis ea " +
            "LEFT JOIN origin_text ot ON ea.origin_text_id = ot.sid " +
            "WHERE ea.analysis_date BETWEEN #{startDate} AND #{endDate} " +
            "<if test='keywords != null and keywords.size() > 0'>" +
            "AND (" +
            "<foreach collection='keywords' item='keyword' separator=' OR '>" +
            "(ea.event_content LIKE CONCAT('%', #{keyword}, '%') " +
            "OR ea.event_analysis LIKE CONCAT('%', #{keyword}, '%') " +
            "OR ot.title LIKE CONCAT('%', #{keyword}, '%'))" +
            "</foreach>" +
            ")" +
            "</if>" +
            "ORDER BY ea.analysis_date DESC, ea.id DESC" +
            "</script>")
    List<EventAnalysisDTO> queryByDateAndKeywords(@Param("startDate") String startDate,
                                                   @Param("endDate") String endDate,
                                                   @Param("keywords") List<String> keywords);

    /**
     * 查询指定日期的分析状态
     */
    @Select("SELECT COUNT(*) FROM event_analysis WHERE analysis_date = #{date}")
    int countByDate(@Param("date") String date);

    /**
     * 检查报文是否已分析
     */
    @Select("SELECT COUNT(*) FROM event_analysis WHERE origin_text_id = #{originTextId}")
    int countByOriginTextId(@Param("originTextId") Integer originTextId);
}
