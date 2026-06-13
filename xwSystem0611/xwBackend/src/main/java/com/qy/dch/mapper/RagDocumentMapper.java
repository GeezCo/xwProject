package com.qy.dch.mapper;

import com.qy.dch.rag.model.RagDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RagDocumentMapper {
    int insert(RagDocument doc);
    RagDocument selectByDocId(@Param("docId") String docId);
    int updateStatus(@Param("docId") String docId,
                     @Param("status") String status,
                     @Param("chunkCount") Integer chunkCount,
                     @Param("errorMsg") String errorMsg);
}
