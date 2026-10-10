/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.commerce.application.PromoCodeService;
import vkurse.platform.commerce.domain.Discount;
import vkurse.platform.commerce.domain.OfferScope;
import vkurse.platform.commerce.domain.PromoCodeText;

/** A school's promo codes, for its owner and admins. */
@RestController
class PromoCodeController {
    private final PromoCodeService promoCodes;

    PromoCodeController(PromoCodeService promoCodes) {
        this.promoCodes = promoCodes;
    }

    @PostMapping("/schools/{schoolId}/promo-codes")
    PromoCodeResponse create(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody CreateRequest data) {
        return PromoCodeResponse.of(promoCodes.create(
                session.userId(),
                schoolId,
                data.code(),
                discount(data.discount()),
                data.validUntil(),
                data.maxUses(),
                data.offerIds() == null ? OfferScope.EVERY : new OfferScope(data.offerIds())));
    }

    @GetMapping("/schools/{schoolId}/promo-codes")
    List<PromoCodeResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return promoCodes.list(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(PromoCodeResponse::of)
                .toList();
    }

    @PatchMapping("/promo-codes/{id}")
    PromoCodeResponse update(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody UpdateRequest data) {
        var limits = data.limits();
        return PromoCodeResponse.of(promoCodes.update(
                session.userId(),
                id,
                data.discount() == null ? null : discount(data.discount()),
                data.offerIds() == null ? null : new OfferScope(data.offerIds()),
                limits != null,
                limits == null ? null : limits.validUntil(),
                limits == null ? null : limits.maxUses()));
    }

    @PostMapping("/promo-codes/{id}/activate")
    PromoCodeResponse activate(@GetSession UserSession session, @PathVariable UUID id) {
        return PromoCodeResponse.of(promoCodes.activate(session.userId(), id));
    }

    @PostMapping("/promo-codes/{id}/deactivate")
    PromoCodeResponse deactivate(@GetSession UserSession session, @PathVariable UUID id) {
        return PromoCodeResponse.of(promoCodes.deactivate(session.userId(), id));
    }

    /** Stops the code for good and frees its text. Orders already placed keep their discount. */
    @DeleteMapping("/promo-codes/{id}")
    ResponseEntity<Void> archive(@GetSession UserSession session, @PathVariable UUID id) {
        promoCodes.archive(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    private static Discount discount(DiscountRequest data) {
        return Discount.of(data.kind(), data.percent(), data.amount());
    }

    /** Without {@code offerIds}, or with an empty list, the code is for every offer of the school. */
    record CreateRequest(
            @NotBlank @Size(min = PromoCodeText.MIN_LENGTH, max = PromoCodeText.MAX_LENGTH)
            String code,

            @Valid @NotNull DiscountRequest discount,
            @Nullable Instant validUntil,
            @Nullable Integer maxUses,
            @Size(max = OfferScope.MAX_OFFERS) @Nullable List<@NotNull UUID> offerIds) {}

    /** {@code percent} goes with {@code PERCENT}, {@code amount} (rubles) with {@code AMOUNT}. */
    record DiscountRequest(
            @NotNull Discount.Kind kind,
            @Nullable Integer percent,
            @Nullable BigDecimal amount) {}

    /**
     * {@code null} leaves a field unchanged. To change the limits, send {@code limits} with both of them; a
     * {@code null} inside it removes that limit.
     */
    record UpdateRequest(
            @Valid @Nullable DiscountRequest discount,
            @Size(max = OfferScope.MAX_OFFERS) @Nullable List<@NotNull UUID> offerIds,
            @Nullable LimitsRequest limits) {}

    record LimitsRequest(
            @Nullable Instant validUntil, @Nullable Integer maxUses) {}
}
