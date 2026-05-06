package com.translator.translation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryStatsDTO {
    private long totalTranslations;
    private long favoriteCount;
    private String mostUsedLanguage;
    private long translationsThisWeek;
    private double cacheHitRate;
}
