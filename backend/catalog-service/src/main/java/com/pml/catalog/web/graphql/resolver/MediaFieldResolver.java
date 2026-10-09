package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.security.Callers;
import com.pml.catalog.service.MediaService;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.util.List;

/** The fields of a media asset, and of a category, that are computed rather than stored. */
@DgsComponent
@RequiredArgsConstructor
public class MediaFieldResolver {

    private final MediaService media;

    /** The address is computed from the file store, so it always names where the file is now. */
    @DgsData(parentType = "MediaAsset", field = "url")
    public String url(DgsDataFetchingEnvironment dfe) {
        MediaAsset asset = dfe.getSource();
        return media.urlOf(asset);
    }

    /** The audit trail is for administrators; an organizer sees why an image was removed, not who removed it. */
    @DgsData(parentType = "MediaAsset", field = "moderationLog")
    public Mono<List<MediaAsset.ModerationEntry>> moderationLog(DgsDataFetchingEnvironment dfe) {
        MediaAsset asset = dfe.getSource();
        return Callers.platformAdministrator()
                .filter(admin -> admin && asset.getModerationLog() != null)
                .map(admin -> asset.getModerationLog());
    }

    /** The tile the stock library holds for this category, if any. */
    @DgsData(parentType = "EventCategory", field = "imageUrl")
    public Mono<String> categoryImage(DgsDataFetchingEnvironment dfe) {
        EventCategory category = dfe.getSource();
        return media.categoryTileUrl(category.getCode());
    }
}
