package com.invsys;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

/**
 * Run manually to create local dev JWT keys:
 * mvn -Dtest=JwtKeyExportTest test
 */
class JwtKeyExportTest {

    @Test
    void exportDevJwtKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        Path jwtDir = Path.of("..", "ops", "jwt").normalize().toAbsolutePath();
        Files.createDirectories(jwtDir);

        Files.writeString(jwtDir.resolve("dev-private.pem"), toPkcs8Pem(pair.getPrivate().getEncoded()));
        Files.writeString(jwtDir.resolve("dev-public.pem"), toSpkiPem(pair.getPublic().getEncoded()));

        System.out.println("Wrote JWT dev keys to " + jwtDir);
    }

    private static String toPkcs8Pem(byte[] der) {
        return wrapPem("PRIVATE KEY", der);
    }

    private static String toSpkiPem(byte[] der) {
        return wrapPem("PUBLIC KEY", der);
    }

    private static String wrapPem(String label, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
    }
}
