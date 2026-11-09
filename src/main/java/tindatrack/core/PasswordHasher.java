package tindatrack.core;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Encodes a salted password hash for storage in users.password_hash. */
public final class PasswordHasher {

    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, 256);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            try {
                Base64.Encoder encoder = Base64.getEncoder();
                return "pbkdf2-sha256$" + ITERATIONS + "$" + encoder.encodeToString(salt)
                        + "$" + encoder.encodeToString(hash);
            } finally {
                Arrays.fill(hash, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }
}
