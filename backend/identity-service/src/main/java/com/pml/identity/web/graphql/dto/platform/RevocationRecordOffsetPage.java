package com.pml.identity.web.graphql.dto.platform;

import com.pml.identity.web.graphql.dto.pagination.PageInfo;

import java.util.List;

public record RevocationRecordOffsetPage(List<RevocationRecordView> content, PageInfo pageInfo) {
}
