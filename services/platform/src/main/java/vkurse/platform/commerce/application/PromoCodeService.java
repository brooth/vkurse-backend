/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.web.Paging;
import vkurse.platform.commerce.domain.CommerceErrors.PromoCodeTakenException;
import vkurse.platform.commerce.domain.Discount;
import vkurse.platform.commerce.domain.OfferScope;
import vkurse.platform.commerce.domain.PromoCode;
import vkurse.platform.commerce.domain.PromoCodeRepository;
import vkurse.platform.commerce.domain.PromoCodeText;

/** A school's promo codes, managed by its owner and admins. */
@Service
@Transactional
public class PromoCodeService {
    private final PromoCodeRepository promoCodes;
    private final SalesAccess access;
    private final Clock clock;

    public PromoCodeService(PromoCodeRepository promoCodes, SalesAccess access, Clock clock) {
        this.promoCodes = promoCodes;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Adds a promo code to the school. Two requests can still take the same free text at once: the unique index then
     * rejects the second with a plain 409 {@code conflict}.
     */
    public PromoCode create(
            UUID userId,
            UUID schoolId,
            String code,
            Discount discount,
            @Nullable Instant validUntil,
            @Nullable Integer maxUses,
            OfferScope scope) {
        access.ensureManages(userId, schoolId, "School");
        var text = new PromoCodeText(code);
        if (promoCodes.findBySchoolAndCode(schoolId, text).isPresent()) {
            throw new PromoCodeTakenException();
        }
        return promoCodes.save(PromoCode.create(
                schoolId,
                text,
                discount,
                validUntil,
                maxUses,
                scope,
                promoCodes.countBySchool(schoolId),
                clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<PromoCode> list(UUID userId, UUID schoolId, Paging paging) {
        access.ensureManages(userId, schoolId, "School");
        return promoCodes.findBySchool(schoolId, paging.limit(), paging.offset());
    }

    /**
     * Changes a promo code. A {@code null} discount or scope leaves it as it is.
     *
     * @param changeLimits whether to set the end time and the number of uses to the given values ({@code null} then
     *     removes that limit)
     */
    public PromoCode update(
            UUID userId,
            UUID promoCodeId,
            @Nullable Discount discount,
            @Nullable OfferScope scope,
            boolean changeLimits,
            @Nullable Instant validUntil,
            @Nullable Integer maxUses) {
        var promoCode = manageable(userId, promoCodeId);
        promoCode.change(discount, scope);
        if (changeLimits) {
            promoCode.changeLimits(validUntil, maxUses);
        }
        return promoCodes.save(promoCode);
    }

    public PromoCode activate(UUID userId, UUID promoCodeId) {
        var promoCode = manageable(userId, promoCodeId);
        promoCode.activate();
        return promoCodes.save(promoCode);
    }

    public PromoCode deactivate(UUID userId, UUID promoCodeId) {
        var promoCode = manageable(userId, promoCodeId);
        promoCode.deactivate();
        return promoCodes.save(promoCode);
    }

    /** Stops the code for good and frees its text. */
    public void archive(UUID userId, UUID promoCodeId) {
        var promoCode = manageable(userId, promoCodeId);
        promoCode.archive(clock.instant());
        promoCodes.save(promoCode);
    }

    private PromoCode manageable(UUID userId, UUID promoCodeId) {
        var promoCode = promoCodes
                .findByIdForUpdate(promoCodeId)
                .filter(found -> !found.isArchived())
                .orElseThrow(() -> new NotFoundException("Promo code not found"));
        access.ensureManages(userId, promoCode.getSchoolId(), "Promo code");
        return promoCode;
    }
}
