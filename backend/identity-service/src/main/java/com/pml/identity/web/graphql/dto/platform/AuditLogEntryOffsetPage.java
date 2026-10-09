package com.pml.identity.web.graphql.dto.platform;

import com.pml.identity.web.graphql.dto.pagination.PageInfo;

import java.util.List;

public record AuditLogEntryOffsetPage(List<AuditLogEntry> content, PageInfo pageInfo) {}
