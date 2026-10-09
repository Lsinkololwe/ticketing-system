package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.security.Callers;
import com.pml.catalog.service.MediaService;
import com.pml.catalog.util.KeysetCursor;
import com.pml.catalog.web.graphql.dto.CursorPaginationInput;
import com.pml.catalog.web.graphql.dto.MediaAssetConnection;
import com.pml.catalog.web.graphql.dto.MediaAssetEdge;
import com.pml.catalog.web.graphql.dto.MediaAssetOffsetPage;
import com.pml.catalog.web.graphql.dto.MediaFilterInput;
import com.pml.catalog.web.graphql.dto.MediaModerationFilterInput;
import com.pml.catalog.web.graphql.dto.OffsetPaginationInput;
import com.pml.catalog.web.graphql.dto.PageInfo;
import com.pml.catalog.web.graphql.dto.StockImage;
import com.pml.catalog.web.graphql.dto.StockImageConnection;
import com.pml.catalog.web.graphql.dto.StockImageEdge;
import com.pml.catalog.web.graphql.dto.StockImageFilterInput;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;

/** The image library queries: an organizer's own pictures, the stock library, and the moderation queue. */
@DgsComponent
@RequiredArgsConstructor
public class MediaQueryResolver {

    private final MediaService media;

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<MediaAssetConnection> myMedia(@InputArgument MediaFilterInput filter,
                                              @InputArgument CursorPaginationInput pagination) {
        CursorPaginationInput page = forward(pagination);
        return media.mine(filter, page.getAfter(), page.getLimit()).map(result -> connection(result));
    }

    @DgsQuery
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<StockImageConnection> stockImages(@InputArgument StockImageFilterInput filter,
                                                  @InputArgument CursorPaginationInput pagination) {
        CursorPaginationInput page = forward(pagination);
        return Callers.platformAdministrator()
                .flatMap(admin -> media.stockImages(filter, admin, page.getAfter(), page.getLimit()))
                .map(this::stockConnection);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<MediaAssetOffsetPage> mediaAssets(@InputArgument MediaModerationFilterInput filter,
                                                  @InputArgument OffsetPaginationInput pagination) {
        return media.moderationQueue(filter, pagination).map(result -> new MediaAssetOffsetPage(
                result.content(), result.pageNumber(), result.pageSize(), result.totalElements(),
                result.totalPages(), result.hasNext(), result.hasPrevious()));
    }

    private static CursorPaginationInput forward(CursorPaginationInput pagination) {
        CursorPaginationInput page = pagination != null ? pagination : new CursorPaginationInput();
        if (page.isBackward()) {
            throw new ValidationRefusal(List.of(new FieldViolation("pagination.before",
                    "this list pages forward only")));
        }
        return page;
    }

    private static MediaAssetConnection connection(MediaService.Page page) {
        List<MediaAssetEdge> edges = page.assets().stream()
                .map(asset -> new MediaAssetEdge(asset, KeysetCursor.encode(asset.getCreatedAt(), asset.getId())))
                .toList();
        return new MediaAssetConnection(edges, pageInfo(edges.isEmpty() ? null : edges.get(0).cursor(),
                edges.isEmpty() ? null : edges.get(edges.size() - 1).cursor(), page));
    }

    private StockImageConnection stockConnection(MediaService.Page page) {
        List<StockImageEdge> edges = page.assets().stream()
                .map(asset -> new StockImageEdge(stockImage(asset), KeysetCursor.encode(asset.getCreatedAt(), asset.getId())))
                .toList();
        return new StockImageConnection(edges, pageInfo(edges.isEmpty() ? null : edges.get(0).cursor(),
                edges.isEmpty() ? null : edges.get(edges.size() - 1).cursor(), page));
    }

    private static PageInfo pageInfo(String start, String end, MediaService.Page page) {
        return PageInfo.builder().hasNextPage(page.hasNext()).hasPreviousPage(page.hasPrevious())
                .startCursor(start).endCursor(end).build();
    }

    /** A stock asset as the library presents it. */
    public StockImage stockImage(MediaAsset asset) {
        return new StockImage(asset.getId(), media.urlOf(asset), asset.getTitle(), asset.getAltText(),
                asset.getPurpose(), asset.getCategoryCode(), asset.isActive(), asset.getCreatedAt(), asset.getUpdatedAt());
    }
}
