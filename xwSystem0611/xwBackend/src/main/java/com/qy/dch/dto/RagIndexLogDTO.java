package com.qy.dch.dto;

import lombok.Data;

@Data
public class RagIndexLogDTO {
    private Long id;
    private String triggerType;
    private String startTime;
    private String endTime;
    private String status;
    private Integer processedCount;
    private Integer successCount;
    private Integer skippedCount;
    private Integer failedCount;
}
