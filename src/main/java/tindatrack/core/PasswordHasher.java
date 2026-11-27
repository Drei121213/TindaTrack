package tindatrack.core;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Creates and verifies the salted hashes stored in users.password_hash. */
public final class PasswordHasher {

    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final String FORMAT = "pbkdf2-sha256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * Derives a PBKDF2-HMAC-SHA256 hash with a fresh 16-byte random salt.
     * @param password characters to hash; the caller remains responsible for clearing them
     * @return algorithm, iteration count, Base64 salt, and Base64 hash separated by dollar signs
     */
    public static String hash(char[] password) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);

        byte[] hash = derive(password, salt);
        try {
            Base64.Encoder encoder = Base64.getEncoder();
            return FORMAT + "$" + ITERATIONS + "$" + encoder.encodeToString(salt)
                    + "$" + encoder.encodeToString(hash);
        } finally {
            Arrays.fill(hash, (byte) 0);
        }
    }

    /**
     * Verifies the current registration format. A missing or unusable hash still
     * runs PBKDF2 so an unknown username does comparable work to a wrong password.
     */
    public static boolean verify(char[] password, String encodedHash) throws GeneralSecurityException {
        StoredHash stored = decode(encodedHash);
        byte[] salt = stored == null ? new byte[SALT_BYTES] : stored.salt();
        byte[] candidate = derive(password, salt);
        try {
            return stored != null && MessageDigest.isEqual(candidate, stored.hash());
        } finally {
            Arrays.fill(candidate, (byte) 0);
        }
    }

    /**
     * Accepts only the current algorithm and iteration count with a 16-byte salt
     * and a 32-byte hash after Base64 decoding.
     * @return decoded hash data, or null for a missing or unsupported encoding
     */
    private static StoredHash decode(String encodedHash) {
        if (encodedHash == null) {
            return null;
        }
        String[] parts = encodedHash.split("\\$", -1);
        if (parts.length != 4 || !FORMAT.equals(parts[0])
                || !Integer.toString(ITERATIONS).equals(parts[1])) {
            return null;
        }

        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode(parts[2]);
            byte[] hash = decoder.decode(parts[3]);
            if (salt.length == SALT_BYTES && hash.length == HASH_BYTES) {
                return new StoredHash(salt, hash);
            }
        } catch (IllegalArgumentException exception) {
            // Unreadable stored hashes cannot authenticate an account.
        }
        return null;
    }

    /**
     * Derives 32 bytes using the configured PBKDF2 iteration count and supplied salt.
     * Clears the key specification password copy in finally; the input array is unchanged.
     */
    private static byte[] derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, HASH_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private record StoredHash(byte[] salt, byte[] hash) {
    }
}
