package com.qy.dch.rag.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class ParsedDocument {
    private String id;
    private String content;
    private String source;       // "docx"
    private String type;         // "text" / "mixed" / "table"
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
