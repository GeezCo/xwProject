package com.qy.dch.rag.model;

import lombok.Data;
import java.util.Date;

@Data
public class RagDocument {
    private Long id;
    private String docId;
    private String filename;
    private Long fileSize;
    private Integer chunkCount;
    private String status;       // pending / indexed / failed
    private String errorMsg;
    private Date uploadTime;
    private Date indexedTime;
}
