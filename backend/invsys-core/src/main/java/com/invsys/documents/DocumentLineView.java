package com.invsys.documents;

/**
 * Row model for PDF templates (invoice lines include prices; packing slips omit them).
 */
public record DocumentLineView(
        String description,
        String qty,
        String unitPrice,
        String amount
) {
    public static DocumentLineView packing(String description, String qty) {
        return new DocumentLineView(description, qty, "", "");
    }
}
