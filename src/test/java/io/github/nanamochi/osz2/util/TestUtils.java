package io.github.nanamochi.osz2.util;

import java.security.SecureRandom;

public class TestUtils {
    private static final SecureRandom RNG = new SecureRandom();

    public static byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        RNG.nextBytes(b);
        return b;
    }
}
