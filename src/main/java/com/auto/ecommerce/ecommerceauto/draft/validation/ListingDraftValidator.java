package com.auto.ecommerce.ecommerceauto.draft.validation;

import com.auto.ecommerce.ecommerceauto.draft.model.ListingDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ListingDraftValidator {

    public List<String> validate(ListingDraft draft) {
        List<String> errors = new ArrayList<>();
        if (draft == null) {
            errors.add("草稿不能为空");
            return errors;
        }
        requireText(draft.getChineseTitle(), "缺少中文标题", errors);
        requireText(draft.getShopName(), "缺少店铺", errors);
        requireText(draft.getCategoryName(), "缺少类目", errors);
        requireText(draft.getProductMainImage(), "缺少产品主图", errors);
        if (draft.getDescriptionImagePaths() == null || draft.getDescriptionImagePaths().isEmpty()) {
            errors.add("缺少描述图");
        }
        if (draft.getTransactionInfo() == null || draft.getTransactionInfo().isEmpty()) {
            errors.add("缺少交易信息");
        }
        return errors;
    }

    private void requireText(String value, String message, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(message);
        }
    }
}
