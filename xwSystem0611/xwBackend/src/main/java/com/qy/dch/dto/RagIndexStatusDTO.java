package com.qy.dch.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class RagIndexStatusDTO {
    private Long totalDocs;
    private Long indexedDocs;
    private Long unindexedDocs;
    private List<Map<String, Object>> byDate;
}
