package com.qy.dch.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {

    /** 切片唯一标识 (doc_id_chunk_N) */
    private String id;

    /** 父文档ID (origin_text.sid) */
    private String documentId;

    /** 切片内容 */
    private String content;

    /** 切片序号 */
    private int chunkIndex;

    /** 向量表示 (1024维) */
    private float[] embedding;

    /** 元数据 */
    private Map<String, Object> metadata;

    /** 切片类型 (short / medium / long) */
    private String chunkType;

    /** 字符长度 */
    private int length;

    /** 检索得分 */
    private Float score;
}