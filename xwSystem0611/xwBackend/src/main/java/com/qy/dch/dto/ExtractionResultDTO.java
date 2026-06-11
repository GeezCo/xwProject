package com.qy.dch.dto;

import com.alibaba.fastjson.JSONArray;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 属性抽取结果数据传输对象
 * <p>
 * 对应数据库extraction_result表，存储LLM对原始文本进行属性抽取的结果，
 * 包含抽取的事件信息（时间、地点、人物、组织、行为等）。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionResultDTO {

    /** 主键ID */
    private Integer id;

    /** 原始文本ID（关联origin_text.sid） */
    private Integer originTextId;

    /** 抽取时间 */
    private LocalDateTime extractionTime;

    /** 使用的模型 */
    private String model;

    /** 抽取事件总数 */
    private Integer totalEvents;

    /** 分类标签（所有事件的标签汇总去重） */
    private String labelsJson;

    /** 实体（按标签分类） */
    private String entitiesJson;

    /** 事件抽取结果JSON字符串 */
    private String eventsJson;

    /** 状态：processing/completed/failed */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /**
     * 获取解析后的事件列表
     * @return 事件数组
     */
    public JSONArray getEvents() {
        if (eventsJson != null && !eventsJson.isEmpty()) {
            try {
                com.alibaba.fastjson.JSONObject obj = com.alibaba.fastjson.JSON.parseObject(eventsJson);
                if (obj.containsKey("events")) {
                    return obj.getJSONArray("events");
                }
                return com.alibaba.fastjson.JSON.parseArray(eventsJson);
            } catch (Exception e) {
                return new JSONArray();
            }
        }
        return new JSONArray();
    }

    /**
     * 获取解析后的标签列表
     * @return 标签数组
     */
    public JSONArray getLabels() {
        if (labelsJson != null && !labelsJson.isEmpty()) {
            try {
                return com.alibaba.fastjson.JSON.parseArray(labelsJson);
            } catch (Exception e) {
                return new JSONArray();
            }
        }
        return new JSONArray();
    }

    /**
     * 获取解析后的实体对象
     * @return 实体对象
     */
    public com.alibaba.fastjson.JSONObject getEntities() {
        if (entitiesJson != null && !entitiesJson.isEmpty()) {
            try {
                return com.alibaba.fastjson.JSON.parseObject(entitiesJson);
            } catch (Exception e) {
                return new com.alibaba.fastjson.JSONObject();
            }
        }
        return new com.alibaba.fastjson.JSONObject();
    }
}