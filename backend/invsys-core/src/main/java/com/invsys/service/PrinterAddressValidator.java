package com.invsys.service;

import com.invsys.core.common.ApiException;
import org.springframework.http.HttpStatus;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks cloud-metadata / loopback / link-local printer targets (SSRF).
 * RFC1918 LAN addresses remain allowed — thermal printers live on the warehouse network.
 */
public final class PrinterAddressValidator {

    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata",
            "metadata.google.internal",
            "metadata.google.com");

    private PrinterAddressValidator() {
    }

    public static void assertSafePrinterTarget(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTER_IP_REQUIRED",
                    "Printer IP address is required");
        }
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(host) || host.endsWith(".localhost") || host.endsWith(".internal")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTER_TARGET_BLOCKED",
                    "Printer address is not allowed");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedSsrfTarget(address)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTER_TARGET_BLOCKED",
                            "Printer address is not allowed");
                }
            }
        } catch (UnknownHostException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRINTER_TARGET_BLOCKED",
                    "Unable to resolve printer address");
        }
    }

    static boolean isBlockedSsrfTarget(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            int a0 = raw[0] & 0xFF;
            int a1 = raw[1] & 0xFF;
            // 0.0.0.0/8, 127.0.0.0/8, 169.254.0.0/16 (metadata), 100.64.0.0/10 CGNAT
            return a0 == 0
                    || a0 == 127
                    || (a0 == 169 && a1 == 254)
                    || (a0 == 100 && a1 >= 64 && a1 <= 127);
        }
        return false;
    }
}
