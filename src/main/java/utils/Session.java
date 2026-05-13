package utils;

/**
 * Temporary stand-in for authentication. Every new post/comment requires a
 * user_id, but this project has no login screen yet, so we hardcode one here.
 * At integration time, replace these constants with the team's actual session
 * lookup (e.g. a shared singleton populated after the login flow completes).
 */
public class Session {

    public static int    CURRENT_USER_ID           = 2;
    public static String CURRENT_USER_ROLE         = "ADMIN";
    public static String CURRENT_USER_DISPLAY_NAME = "rania rania";

    public static boolean isAdmin() {
        return "ADMIN".equals(CURRENT_USER_ROLE);
    }
}