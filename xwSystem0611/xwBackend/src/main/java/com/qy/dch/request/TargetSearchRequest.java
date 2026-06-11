package com.qy.dch.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TargetSearchRequest {
    private String targetName;
    private Integer maxReports = 10;
}
