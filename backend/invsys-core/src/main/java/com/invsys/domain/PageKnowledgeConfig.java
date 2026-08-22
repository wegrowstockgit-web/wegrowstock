package com.invsys.domain;

import com.invsys.core.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "page_knowledge_configs")
public class PageKnowledgeConfig extends BaseEntity {

    @Column(name = "route_pattern", nullable = false, unique = true, length = 255)
    private String routePattern;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "role_privileges", nullable = false, columnDefinition = "text")
    private String rolePrivileges;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_actions", nullable = false, columnDefinition = "jsonb")
    private List<String> keyActions = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "common_mistakes", nullable = false, columnDefinition = "jsonb")
    private List<MistakeFix> commonMistakes = new ArrayList<>();

    @Column(name = "pro_tip", columnDefinition = "text")
    private String proTip;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    public String getRoutePattern() {
        return routePattern;
    }

    public void setRoutePattern(String routePattern) {
        this.routePattern = routePattern;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getRolePrivileges() {
        return rolePrivileges;
    }

    public void setRolePrivileges(String rolePrivileges) {
        this.rolePrivileges = rolePrivileges;
    }

    public List<String> getKeyActions() {
        return keyActions;
    }

    public void setKeyActions(List<String> keyActions) {
        this.keyActions = keyActions != null ? new ArrayList<>(keyActions) : new ArrayList<>();
    }

    public List<MistakeFix> getCommonMistakes() {
        return commonMistakes;
    }

    public void setCommonMistakes(List<MistakeFix> commonMistakes) {
        this.commonMistakes = commonMistakes != null ? new ArrayList<>(commonMistakes) : new ArrayList<>();
    }

    public String getProTip() {
        return proTip;
    }

    public void setProTip(String proTip) {
        this.proTip = proTip;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public static class MistakeFix {
        private String mistake;
        private String solution;
        private String requiredRole;

        public MistakeFix() {
        }

        public MistakeFix(String mistake, String solution, String requiredRole) {
            this.mistake = mistake;
            this.solution = solution;
            this.requiredRole = requiredRole;
        }

        public String getMistake() {
            return mistake;
        }

        public void setMistake(String mistake) {
            this.mistake = mistake;
        }

        public String getSolution() {
            return solution;
        }

        public void setSolution(String solution) {
            this.solution = solution;
        }

        public String getRequiredRole() {
            return requiredRole;
        }

        public void setRequiredRole(String requiredRole) {
            this.requiredRole = requiredRole;
        }
    }
}
