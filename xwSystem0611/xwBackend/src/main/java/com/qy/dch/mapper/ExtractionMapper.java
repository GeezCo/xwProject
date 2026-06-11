package com.qy.dch.mapper;

import com.qy.dch.dto.ExtractionResultDTO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 属性抽取数据访问层（MyBatis Mapper）
 * <p>
 * 提供extraction_result（抽取结果表）的数据库操作，
 * 包括抽取结果的插入、查询、更新等功能。
 * </p>
 */
@Mapper
public interface ExtractionMapper {

    /**
     * 根据原始文本ID查询抽取结果
     *
     * @param originTextId 原始文本ID（origin_text.sid）
     * @return 抽取结果DTO，不存在则返回null
     */
    @Select("SELECT id, origin_text_id as originTextId, extraction_time as extractionTime, " +
            "model, total_events as totalEvents, labels_json as labelsJson, entities_json as entitiesJson, events_json as eventsJson, status, error_message as errorMessage " +
            "FROM extraction_result WHERE origin_text_id = #{originTextId}")
    ExtractionResultDTO selectByOriginTextId(Integer originTextId);

    /**
     * 插入或更新抽取结果（使用ON DUPLICATE KEY UPDATE）
     *
     * @param dto 抽取结果数据
     */
    @Insert("INSERT INTO extraction_result (origin_text_id, events_json, labels_json, entities_json, total_events, model, status, error_message) " +
            "VALUES (#{originTextId}, #{eventsJson}, #{labelsJson}, #{entitiesJson}, #{totalEvents}, #{model}, #{status}, #{errorMessage}) " +
            "ON DUPLICATE KEY UPDATE " +
            "events_json = #{eventsJson}, " +
            "labels_json = #{labelsJson}, " +
            "entities_json = #{entitiesJson}, " +
            "total_events = #{totalEvents}, " +
            "model = #{model}, " +
            "status = #{status}, " +
            "error_message = #{errorMessage}, " +
            "extraction_time = NOW()")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertOrUpdate(ExtractionResultDTO dto);

    /**
     * 根据ID查询抽取结果
     *
     * @param id 抽取结果主键ID
     * @return 抽取结果DTO
     */
    @Select("SELECT id, origin_text_id as originTextId, extraction_time as extractionTime, " +
            "model, total_events as totalEvents, labels_json as labelsJson, entities_json as entitiesJson, events_json as eventsJson, status, error_message as errorMessage " +
            "FROM extraction_result WHERE id = #{id}")
    ExtractionResultDTO selectById(Integer id);

    /**
     * 删除指定原始文本的抽取结果
     *
     * @param originTextId 原始文本ID
     */
    @Delete("DELETE FROM extraction_result WHERE origin_text_id = #{originTextId}")
    void deleteByOriginTextId(Integer originTextId);

    /**
     * 根据实体关键词搜索报文ID
     * 在entities_json字段中模糊匹配关键词
     *
     * @param keyword 目标名称关键词
     * @param limit 返回结果数量限制
     * @return 匹配的报文ID列表
     */
    @Select("SELECT DISTINCT origin_text_id FROM extraction_result " +
            "WHERE entities_json LIKE CONCAT('%', #{keyword}, '%') " +
            "LIMIT #{limit}")
    List<Integer> searchByEntityKeyword(@Param("keyword") String keyword, @Param("limit") int limit);
}