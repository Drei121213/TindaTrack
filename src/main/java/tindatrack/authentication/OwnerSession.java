package tindatrack.authentication;

import tindatrack.core.SessionState;

/** The identity of the owner signed in to this instance of TindaTrack. */
public record OwnerSession(int userId, String fullName) implements SessionState.Session {
}
