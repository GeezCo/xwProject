package com.qy.dch.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * RAG文档实体类
 * <p>
 * 对应数据库表 rag_document，存储上传文档的元信息及解析状态。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocument {

    /** 文档唯一标识 */
    private String docId;

    /** 原始文件名 */
    private String originalFilename;

    /** 文件存储路径（MinIO路径） */
    private String filePath;

    /** 文档类型（docx/pdf/txt） */
    private String contentType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 上传时间 */
    private Date uploadTime;

    /** 解析状态：pending/parsing/completed/failed */
    private String parseStatus;

    /** 解析完成时间 */
    private Date parseTime;

    /** 切片数量 */
    private Integer chunkCount;

    /** 文档元数据（JSON字符串） */
    private String metadata;

    /** 错误信息（解析失败时记录） */
    private String errorMessage;
}
