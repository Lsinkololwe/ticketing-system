package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.pml.shared.security.RequiresTenantPermission;
import com.pml.shared.security.RequiresPermission;
import com.pml.shared.security.Permission;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.security.MediaAuthority;
import com.pml.catalog.service.MediaService;
import com.pml.catalog.service.UploadLimiter;
import com.pml.catalog.web.graphql.dto.StockImage;
import com.pml.catalog.web.graphql.dto.CompleteMediaUploadInput;
import com.pml.catalog.web.graphql.dto.CompleteStockImageUploadInput;
import com.pml.catalog.web.graphql.dto.MediaUploadTicket;
import com.pml.catalog.web.graphql.dto.RequestMediaUploadInput;
import com.pml.catalog.web.graphql.dto.UpdateMediaInput;
import com.pml.catalog.web.graphql.dto.UpdateStockImageInput;
import com.pml.catalog.web.graphql.dto.UploadMediaInput;
import com.pml.catalog.web.graphql.dto.UploadStockImageInput;
import com.pml.shared.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

/**
 * The image library mutations.
 *
 * <p>An organizer's upload takes its organization from the permission check, never from input; the
 * same check that lets a member create an event lets them manage pictures, so a MARKETER or
 * CONTRIBUTOR cannot. Moderation and the stock library are administrators', and each action is
 * recorded against the administrator who took it.
 */
@DgsComponent
@Validated
@RequiredArgsConstructor
public class MediaMutationResolver {

    private final MediaService media;
    private final MediaAuthority authority;
    private final UploadLimiter uploads;
    private final com.pml.catalog.web.graphql.query.MediaQueryResolver stockPresenter;

    @DgsMutation
    @RequiresTenantPermission(Permission.MEDIA_MANAGE)
    public Mono<MediaAsset> uploadMedia(@Valid @InputArgument UploadMediaInput input) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(userId -> organizationOf(userId)
                .flatMap(organizationId -> uploads.take(organizationId)
                        .then(media.upload(input, userId, organizationId))));
    }

    @DgsMutation
    @RequiresTenantPermission(Permission.MEDIA_MANAGE)
    public Mono<MediaUploadTicket> requestMediaUpload(@Valid @InputArgument RequestMediaUploadInput input) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(userId -> organizationOf(userId)
                .flatMap(organizationId -> uploads.take(organizationId)
                        .then(media.requestUpload(input.fileName(), input.contentType(), input.sizeBytes(), userId,
                                organizationId))))
                .map(MediaMutationResolver::present);
    }

    @DgsMutation
    @RequiresTenantPermission(Permission.MEDIA_MANAGE)
    public Mono<MediaAsset> completeMediaUpload(@Valid @InputArgument CompleteMediaUploadInput input) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(userId -> organizationOf(userId)
                .flatMap(organizationId -> media.completeOrganizationUpload(input.uploadToken(), input.title(),
                        input.altText(), input.eventId(), userId, organizationId)));
    }

    @DgsMutation
    @RequiresPermission(Permission.STOCK_IMAGE_MANAGE)
    public Mono<MediaUploadTicket> requestStockImageUpload(@Valid @InputArgument RequestMediaUploadInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actor -> media.requestUpload(input.fileName(), input.contentType(), input.sizeBytes(), actor, null))
                .map(MediaMutationResolver::present);
    }

    @DgsMutation
    @RequiresPermission(Permission.STOCK_IMAGE_MANAGE)
    public Mono<StockImage> completeStockImageUpload(@Valid @InputArgument CompleteStockImageUploadInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actor -> media.completeStockUpload(input.uploadToken(), input.purpose(), input.categoryCode(),
                        input.title(), input.altText(), actor))
                .map(stockPresenter::stockImage);
    }

    private static MediaUploadTicket present(MediaService.UploadTicket ticket) {
        return new MediaUploadTicket(ticket.uploadToken(), ticket.uploadUrl(), ticket.method(),
                ticket.headers().entrySet().stream()
                        .map(e -> new MediaUploadTicket.Header(e.getKey(), e.getValue())).toList(),
                ticket.expiresAt(), (int) ticket.maxBytes());
    }

    @DgsMutation
    @RequiresTenantPermission(Permission.MEDIA_MANAGE)
    public Mono<MediaAsset> updateMedia(@InputArgument String id, @Valid @InputArgument UpdateMediaInput input) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(userId -> organizationOf(userId)
                .then(media.update(id, input)));
    }

    @DgsMutation
    @RequiresTenantPermission(Permission.MEDIA_MANAGE)
    public Mono<String> deleteMedia(@InputArgument String id) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(userId -> organizationOf(userId)
                .then(media.delete(id)));
    }

    @DgsMutation
    @RequiresPermission(Permission.MEDIA_MODERATION_MANAGE)
    public Mono<MediaAsset> flagMedia(@InputArgument String id, @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> media.flag(id, reason, actor));
    }

    @DgsMutation
    @RequiresPermission(Permission.MEDIA_MODERATION_MANAGE)
    public Mono<MediaAsset> removeMedia(@InputArgument String id, @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> media.remove(id, reason, actor));
    }

    @DgsMutation
    @RequiresPermission(Permission.MEDIA_MODERATION_MANAGE)
    public Mono<MediaAsset> restoreMedia(@InputArgument String id) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(actor -> media.restore(id, actor));
    }

    @DgsMutation
    @RequiresPermission(Permission.MEDIA_MODERATION_MANAGE)
    public Mono<Event> overrideEventBanner(@InputArgument String eventId, @InputArgument String mediaId,
                                           @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actor -> media.overrideBanner(eventId, mediaId, reason, actor));
    }

    @DgsMutation
    @RequiresPermission(Permission.STOCK_IMAGE_MANAGE)
    public Mono<StockImage> uploadStockImage(@Valid @InputArgument UploadStockImageInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(actor -> media.uploadStock(input, actor))
                .map(stockPresenter::stockImage);
    }

    @DgsMutation
    @RequiresPermission(Permission.STOCK_IMAGE_MANAGE)
    public Mono<StockImage> updateStockImage(@InputArgument String id, @Valid @InputArgument UpdateStockImageInput input) {
        return media.updateStock(id, input).map(stockPresenter::stockImage);
    }

    @DgsMutation
    @RequiresPermission(Permission.STOCK_IMAGE_MANAGE)
    public Mono<String> deleteStockImage(@InputArgument String id) {
        return media.deleteStock(id);
    }

    /** The organization a member may add pictures to: the one identity names for {@code event:create}. */
    private Mono<String> organizationOf(String userId) {
        return authority.organizationOf(userId);
    }
}
