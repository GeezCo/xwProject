package com.qy.dch.mapper;

import com.qy.dch.dto.OriginTextDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface RagMapper {

    @Select("<script>" +
            "SELECT sid FROM origin_text " +
            "WHERE is_indexed = 0 " +
            "<if test='startDate != null and startDate != \"\"'>" +
            "AND times &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'>" +
            "AND times &lt;= #{endDate} " +
            "</if>" +
            "ORDER BY sid" +
            "</script>")
    List<Long> selectUnindexedIds(@Param("startDate") String startDate,
                                  @Param("endDate") String endDate);

    @Select("SELECT sid FROM origin_text WHERE is_indexed = 0 ORDER BY sid")
    List<Long> selectAllUnindexedIds();

    @Select("<script>" +
            "SELECT sid, title, content, times, type, modal_type, is_indexed " +
            "FROM origin_text WHERE sid IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "ORDER BY sid" +
            "</script>")
    List<OriginTextDTO> selectByIds(@Param("ids") List<Long> ids);

    @Update("<script>" +
            "UPDATE origin_text SET is_indexed = 1 WHERE sid IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int updateIndexedStatus(@Param("ids") List<Long> ids);

    @Update("UPDATE origin_text SET is_indexed = 1 WHERE sid = #{sid}")
    int updateIndexedStatusById(@Param("sid") Long sid);

    @Select("SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN is_indexed=1 THEN 1 ELSE 0 END) as indexedCount, " +
            "SUM(CASE WHEN is_indexed=0 THEN 1 ELSE 0 END) as unindexedCount " +
            "FROM origin_text")
    Map<String, Object> getIndexStats();

    @Select("SELECT DATE(times) as date, COUNT(*) as total, " +
            "SUM(CASE WHEN is_indexed=1 THEN 1 ELSE 0 END) as indexedCount, " +
            "SUM(CASE WHEN is_indexed=0 THEN 1 ELSE 0 END) as unindexedCount " +
            "FROM origin_text " +
            "WHERE times IS NOT NULL " +
            "GROUP BY DATE(times) " +
            "ORDER BY date DESC " +
            "LIMIT 30")
    List<Map<String, Object>> getIndexStatsByDate();
}
