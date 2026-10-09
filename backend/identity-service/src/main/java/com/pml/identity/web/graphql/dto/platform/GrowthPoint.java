package com.pml.identity.web.graphql.dto.platform;

import java.time.Instant;

public record GrowthPoint(Instant bucketStart, int newUsers, long cumulative) {}
