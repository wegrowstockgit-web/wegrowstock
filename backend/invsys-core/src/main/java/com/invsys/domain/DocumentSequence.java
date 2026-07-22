package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "document_sequences")
public class DocumentSequence extends TenantScopedEntity {

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(nullable = false)
    private String period = "";

    @Column(name = "next_value", nullable = false)
    private Long nextValue = 1L;

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public Long getNextValue() {
        return nextValue;
    }

    public void setNextValue(Long nextValue) {
        this.nextValue = nextValue;
    }
}
