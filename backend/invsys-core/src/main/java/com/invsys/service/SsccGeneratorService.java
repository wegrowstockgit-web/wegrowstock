package com.invsys.service;

import com.invsys.repository.PalletManifestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class SsccGeneratorService {

    private final String companyPrefix;
    private final PalletManifestRepository palletManifestRepository;
    private final AtomicLong serialCounter;

    public SsccGeneratorService(
            @Value("${invsys.gs1.company-prefix:0614141}") String companyPrefix,
            PalletManifestRepository palletManifestRepository) {
        this.companyPrefix = companyPrefix.replaceAll("\\D", "");
        this.palletManifestRepository = palletManifestRepository;
        long seed = System.nanoTime() % 1_000_000_000L;
        this.serialCounter = new AtomicLong(seed);
    }

    /**
     * Builds an 18-digit SSCC with GS1 mod-10 check digit.
     * Structure: extension digit + company prefix + serial reference + check digit = 18 digits.
     */
    public static String generateSscc18(String extensionDigit, String companyPrefix, String serial) {
        String ext = normalizeDigits(extensionDigit, 1);
        String prefix = normalizeDigits(companyPrefix, 10);
        int serialLen = 17 - ext.length() - prefix.length();
        if (serialLen < 1) {
            throw new IllegalArgumentException("Company prefix too long for SSCC-18");
        }
        String serialDigits = normalizeDigits(serial, serialLen);
        if (serialDigits.length() > serialLen) {
            serialDigits = serialDigits.substring(serialDigits.length() - serialLen);
        } else if (serialDigits.length() < serialLen) {
            serialDigits = "0".repeat(serialLen - serialDigits.length()) + serialDigits;
        }
        String withoutCheck = ext + prefix + serialDigits;
        int check = gs1Mod10CheckDigit(withoutCheck);
        return withoutCheck + check;
    }

    public String nextSscc() {
        long count = palletManifestRepository.count();
        long serial = serialCounter.incrementAndGet() + count;
        serial = Math.floorMod(serial, 1_000_000_000L);
        return generateSscc18("0", companyPrefix, Long.toString(serial));
    }

    public static int gs1Mod10CheckDigit(String digits) {
        int sum = 0;
        boolean multiplyByThree = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            sum += multiplyByThree ? d * 3 : d;
            multiplyByThree = !multiplyByThree;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static String normalizeDigits(String value, int maxLen) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() > maxLen) {
            return digits.substring(0, maxLen);
        }
        return digits;
    }
}
