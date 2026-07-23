package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "rma_qc_inspections")
public class RmaQcInspection extends TenantScopedEntity {

    @Column(name = "return_line_id", nullable = false)
    private UUID returnLineId;

    @Column(name = "inspector_user_id")
    private UUID inspectorUserId;

    @Column(nullable = false, length = 32)
    private String grade;

    @Column(name = "inspection_notes")
    private String inspectionNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photo_attachment_ids", nullable = false, columnDefinition = "jsonb")
    private List<UUID> photoAttachmentIds = new ArrayList<>();

    @Column(name = "disposition_action", nullable = false, length = 32)
    private String dispositionAction;

    public UUID getReturnLineId() {
        return returnLineId;
    }

    public void setReturnLineId(UUID returnLineId) {
        this.returnLineId = returnLineId;
    }

    public UUID getInspectorUserId() {
        return inspectorUserId;
    }

    public void setInspectorUserId(UUID inspectorUserId) {
        this.inspectorUserId = inspectorUserId;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getInspectionNotes() {
        return inspectionNotes;
    }

    public void setInspectionNotes(String inspectionNotes) {
        this.inspectionNotes = inspectionNotes;
    }

    public List<UUID> getPhotoAttachmentIds() {
        return photoAttachmentIds;
    }

    public void setPhotoAttachmentIds(List<UUID> photoAttachmentIds) {
        this.photoAttachmentIds = photoAttachmentIds != null ? photoAttachmentIds : new ArrayList<>();
    }

    public String getDispositionAction() {
        return dispositionAction;
    }

    public void setDispositionAction(String dispositionAction) {
        this.dispositionAction = dispositionAction;
    }
}
