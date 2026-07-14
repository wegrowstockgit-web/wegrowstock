package com.invsys.service;

import com.invsys.domain.DocumentSequence;
import com.invsys.repository.DocumentSequenceRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentSequenceService {

    private static final Pattern SEQ_PATTERN = Pattern.compile("\\{seq:(\\d+)\\}");

    private final DocumentSequenceRepository repository;

    public DocumentSequenceService(DocumentSequenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String nextNumber(String docType, String format) {
        String period = String.valueOf(Year.now().getValue());
        DocumentSequence seq = repository.findForUpdate(TenantContext.requireTenantId(), docType, period)
                .orElseGet(() -> {
                    DocumentSequence created = new DocumentSequence();
                    created.setTenantId(TenantContext.requireTenantId());
                    created.setDocType(docType);
                    created.setPeriod(period);
                    created.setNextValue(1L);
                    return repository.save(created);
                });
        long value = seq.getNextValue();
        seq.setNextValue(value + 1);
        repository.save(seq);

        String result = format.replace("{YYYY}", period);
        Matcher matcher = SEQ_PATTERN.matcher(result);
        if (matcher.find()) {
            int width = Integer.parseInt(matcher.group(1));
            result = matcher.replaceFirst(String.format("%0" + width + "d", value));
        } else {
            result = result + "-" + value;
        }
        return result;
    }
}
