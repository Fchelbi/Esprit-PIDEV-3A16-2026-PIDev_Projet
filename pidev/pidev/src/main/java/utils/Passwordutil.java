package utils;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Hachage mot de passe avec SHA-256 + sel aléatoire
 * Compatible avec Symfony BCrypt ($2y$)
 */
public class Passwordutil {

    private static final int SALT_BYTES = 16;

    /** Hacher un mot de passe → format "salt:hash" (Java natif) */
    public static String hash(String password) {
        try {
            SecureRandom rng = new SecureRandom();
            byte[] salt = new byte[SALT_BYTES];
            rng.nextBytes(salt);
            byte[] hash = sha256(salt, password);
            return Base64.getEncoder().encodeToString(salt) + ":"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Erreur hachage: " + e.getMessage(), e);
        }
    }

    /** Vérifier un mot de passe contre le hash stocké */
    public static boolean verify(String password, String stored) {
        try {
            // BCrypt hash from Symfony ($2y$ or $2a$ or $2b$)
            if (stored.startsWith("$2y$") || stored.startsWith("$2a$") || stored.startsWith("$2b$")) {
                String bcryptHash = stored.replace("$2y$", "$2a$");
                return BCrypt.checkpw(password, bcryptHash);
            }
            // SHA-256 format (Java natif: salt:hash)
            if (stored.contains(":")) {
                String[] parts = stored.split(":", 2);
                byte[] salt    = Base64.getDecoder().decode(parts[0]);
                byte[] expected= Base64.getDecoder().decode(parts[1]);
                byte[] actual  = sha256(salt, password);
                if (expected.length != actual.length) return false;
                int diff = 0;
                for (int i = 0; i < expected.length; i++) diff |= (expected[i] ^ actual[i]);
                return diff == 0;
            }
            // Plain text (migration anciens mdp)
            return stored.equals(password);
        } catch (Exception e) {
            return false;
        }
    }

    /** Détecter si un mot de passe est déjà haché */
    public static boolean isHashed(String stored) {
        if (stored == null) return false;
        // BCrypt format
        if (stored.startsWith("$2y$") || stored.startsWith("$2a$") || stored.startsWith("$2b$")) return true;
        // SHA-256 format
        return stored.contains(":");
    }

    private static byte[] sha256(byte[] salt, String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        return md.digest(password.getBytes("UTF-8"));
    }
}