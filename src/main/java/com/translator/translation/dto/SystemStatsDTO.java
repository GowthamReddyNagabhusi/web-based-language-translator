package com.translator.translation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemStatsDTO {
    private long totalUsers;
    private long totalTranslationsToday;
    private double cacheHitRate;
    private long awsTranslateCount;
    private long libreTranslateCount;
    private long myMemoryCount;
}
