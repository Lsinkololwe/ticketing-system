package com.pml.identity.web.graphql.dto.platform;

import java.util.List;

public record RulesRefundPolicy(String code, String label, String summary, List<RulesRefundTier> rules) {}
