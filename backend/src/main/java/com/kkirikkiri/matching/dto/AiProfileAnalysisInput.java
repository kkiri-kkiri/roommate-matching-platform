package com.kkirikkiri.matching.dto;

import java.util.List;
import com.kkirikkiri.survey.entity.PreferenceCriterionCode;

/** Only calculated results cross the Gateway boundary; no identity or raw survey answers. */
public record AiProfileAnalysisInput(int matchScore, List<PreferenceCriterionCode> matchedItems,
        List<PreferenceCriterionCode> mismatchedItems) {}
