package tindatrack.core;

import java.util.Objects;

/** Holds the session for this app window. Background tasks can read it. */
public final class SessionState {

    public interface Session {
    }

    private volatile Session current;

    public void begin(Session session) {
        requireSignedOut();
        current = Objects.requireNonNull(session);
    }

    public void clear() {
        current = null;
    }

    public void requireSignedOut() {
        if (current != null) {
            throw new AccessDeniedException("Return to login before signing in or creating an account.");
        }
    }

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
