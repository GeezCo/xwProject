package com.qy.dch.mapper;

import com.qy.dch.rag.model.RagDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * RAG文档数据访问接口
 * <p>
 * 提供rag_document表的CRUD操作：
 * - insertDocument：插入新文档记录
 * - selectByDocId：根据doc_id查询文档
 * - updateParseStatus：更新文档解析状态
 * </p>
 */
@Mapper
public interface RagDocumentMapper {

    /**
     * 插入新文档记录
     *
     * @param document 文档实体
     * @return 受影响的行数
     */
    int insertDocument(RagDocument document);

    /**
     * 根据doc_id查询文档
     *
     * @param docId 文档唯一标识
     * @return 文档实体
     */
    RagDocument selectByDocId(@Param("docId") String docId);

    /**
     * 更新文档解析状态
     *
     * @param docId 文档唯一标识
     * @param parseStatus 解析状态
     * @param parseTime 解析时间
     * @param chunkCount 切片数量
     * @param errorMessage 错误信息（可选）
     * @return 受影响的行数
     */
    int updateParseStatus(@Param("docId") String docId,
                          @Param("parseStatus") String parseStatus,
                          @Param("parseTime") java.util.Date parseTime,
                          @Param("chunkCount") Integer chunkCount,
                          @Param("errorMessage") String errorMessage);
}
