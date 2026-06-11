package com.qy.dch.rag.model;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SearchResult {

    /** 切片ID */
    private String chunkId;

    /** 文档ID (origin_text.sid) */
    private String docId;

    /** 切片内容 */
    private String content;

    /** 融合得分 */
    private Float finalScore;

    /** BM25得分 */
    private Float bm25Score;

    /** 向量相似度得分 */
    private Float vectorScore;

    /** 文档标题 */
    private String title;

    /** 发布时间 */
    private String publishTime;

    /** 分类 */
    private String category;

    /** 切片索引 */
    private Integer chunkIndex;

    /** 切片类型 */
    private String chunkType;

    /** 排名 */
    private Integer rank;
}