package com.auto.ecommerce.ecommerceauto.draft.controller;

import com.auto.ecommerce.ecommerceauto.draft.dto.GenerateListingDraftRequest;
import com.auto.ecommerce.ecommerceauto.draft.dto.ListingDraftPageResponse;
import com.auto.ecommerce.ecommerceauto.draft.dto.ListingDraftResponse;
import com.auto.ecommerce.ecommerceauto.draft.service.ListingDraftService;
import com.auto.ecommerce.ecommerceauto.playwright.MabangPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/listing-drafts")
@RequiredArgsConstructor
public class ListingDraftController {

    private final ListingDraftService service;

    @PostMapping("/generate")
    public ListingDraftResponse generate(@RequestBody GenerateListingDraftRequest request) {
        return service.startGeneration(request);
    }

    @GetMapping
    public ListingDraftPageResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return service.list(page, size, keyword, status);
    }

    @GetMapping("/{draftId}")
    public ListingDraftResponse get(@PathVariable String draftId) {
        return service.get(draftId);
    }

    @DeleteMapping("/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String draftId) {
        service.delete(draftId);
    }

    @PostMapping("/{draftId}/publish")
    public MabangPublisher.PublishResult publish(@PathVariable String draftId) {
        return service.publish(draftId);
    }
}
