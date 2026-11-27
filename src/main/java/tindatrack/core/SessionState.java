package tindatrack.core;

import java.util.Objects;

/** Holds the session for this app window. Background tasks can read it. */
public final class SessionState {

    public interface Session {
    }

    private volatile Session current;

    /**
     * Starts a non-null owner or guest session only while the window is signed out.
     * @throws AccessDeniedException if another session is already active
     */
    public void begin(Session session) {
        requireSignedOut();
        current = Objects.requireNonNull(session);
    }

    /**
     * Ends the current window session so previously issued session objects fail
     * subsequent requireActive checks. This does not cancel running background tasks.
     */
    public void clear() {
        current = null;
    }

    public void requireSignedOut() {
        if (current != null) {
            throw new AccessDeniedException("Return to login before signing in or creating an account.");
        }
    }

    /**
     * Requires the exact session object currently held by this window, using identity
     * rather than value equality so an old or recreated identity cannot pass the check.
     * @param session owner or guest session associated with the requested operation
     * @throws AccessDeniedException if the session is null or no longer current
     */
    public void requireActive(Session session) {
        if (session == null || current != session) {
            throw new AccessDeniedException("This session has ended. Return to login and try again.");
        }
    }

    public static final class AccessDeniedException extends IllegalStateException {
        private AccessDeniedException(String message) {
            super(message);
        }
    }
}
