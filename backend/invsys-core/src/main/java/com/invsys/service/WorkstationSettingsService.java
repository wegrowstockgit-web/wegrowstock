package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.domain.WorkstationSettings;
import com.invsys.repository.WorkstationSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import com.invsys.domain.User;

@Service
public class WorkstationSettingsService {

    private static final Set<String> PRINT_MODES = Set.of("PDF", "ZPL");
    private static final Set<String> LABEL_FORMATS = Set.of("4x6", "4x4", "4x8", "8.5x11");

    private final WorkstationSettingsRepository repository;

    public WorkstationSettingsService(WorkstationSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WorkstationSettings getOrDefaultForCurrentUser() {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = requireUserId();
        return repository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> defaults(tenantId, userId));
    }

    @Transactional
    public WorkstationSettings updateCurrentUser(String printMode, String zplPrinterName, String labelFormat) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID userId = requireUserId();

        String mode = normalizePrintMode(printMode);
        String format = normalizeLabelFormat(labelFormat);
        String printer = blankToNull(zplPrinterName);

        if ("ZPL".equals(mode) && printer == null) {
            // Allow save without printer yet — UI binds after QZ discovery.
        }

        WorkstationSettings settings = repository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> defaults(tenantId, userId));
        settings.setPrintMode(mode);
        settings.setZplPrinterName(printer);
        settings.setLabelFormat(format);
        return repository.save(settings);
    }

    /** EasyPost options.label_format derived from workstation print_mode. */
    public String easypostLabelFormat(WorkstationSettings settings) {
        if (settings != null && "ZPL".equalsIgnoreCase(settings.getPrintMode())) {
            return "ZPL";
        }
        return "PDF";
    }

    public String easypostLabelSize(WorkstationSettings settings) {
        if (settings == null || settings.getLabelFormat() == null || settings.getLabelFormat().isBlank()) {
            return "4x6";
        }
        return settings.getLabelFormat();
    }

    private static WorkstationSettings defaults(UUID tenantId, UUID userId) {
        WorkstationSettings settings = new WorkstationSettings();
        settings.setTenantId(tenantId);
        settings.setUserId(userId);
        settings.setPrintMode("PDF");
        settings.setLabelFormat("4x6");
        return settings;
    }

    private static UUID requireUserId() {
        return TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User context required"));
    }

    private static String normalizePrintMode(String printMode) {
        if (printMode == null || printMode.isBlank()) {
            return "PDF";
        }
        String normalized = printMode.trim().toUpperCase(Locale.ROOT);
        if (!PRINT_MODES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "print_mode must be PDF or ZPL");
        }
        return normalized;
    }

    private static String normalizeLabelFormat(String labelFormat) {
        if (labelFormat == null || labelFormat.isBlank()) {
            return "4x6";
        }
        String normalized = labelFormat.trim().toLowerCase(Locale.ROOT);
        if ("8.5x11".equals(normalized) || LABEL_FORMATS.contains(normalized)) {
            return normalized.equals("8.5x11") ? "8.5x11" : normalized;
        }
        // tolerate 4X6 style
        String compact = normalized.replace('X', 'x');
        if (LABEL_FORMATS.contains(compact)) {
            return compact;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                "label_format must be one of 4x6, 4x4, 4x8, 8.5x11");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
