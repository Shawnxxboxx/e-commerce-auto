package com.auto.ecommerce.ecommerceauto.draft.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DraftConflictException extends IllegalArgumentException {

    public DraftConflictException(String message) {
        super(message);
    }
}
