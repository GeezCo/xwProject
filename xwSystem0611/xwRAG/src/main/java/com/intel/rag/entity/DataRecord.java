package com.intel.rag.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 原始文本实体
 * 对应MySQL中的origin_text表
 */
@Data
@Entity
@Table(name = "origin_text")
public class DataRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sid")
    private Integer sid;

    @Column(name = "title")
    private String title;

    @Column(name = "content", length = 7000)
    private String content;

    @Column(name = "times")
    private String times;

    @Column(name = "type")
    private Integer type;

    @Column(name = "extend1")
    private String extend1;

    @Column(name = "extend2")
    private String extend2;

    @Column(name = "extend3")
    private String extend3;

    @Column(name = "is_extracted")
    private Boolean isExtracted;

    @Column(name = "modal_type", length = 20)
    private String modalType;
}
