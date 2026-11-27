package tindatrack.guest;

import org.junit.jupiter.api.Test;
import tindatrack.authentication.OwnerSession;
import tindatrack.core.SessionState;

import static org.junit.jupiter.api.Assertions.*;

class GuestPermissionsTest {

    @Test
    void deniesGuestReadsWhileSignedOut() {
        assertReadsDenied(new GuestService(new SessionState(), new GuestSession()));
    }

    @Test
    void ownerSessionCannotBeUsedAsAGuestSession() {
        SessionState sessions = new SessionState();
        sessions.begin(new OwnerSession(1, "Owner"));
        assertReadsDenied(new GuestService(sessions, new GuestSession()));
    }

    @Test
    void acceptsOnlyTheExactActiveGuestIdentity() {
        SessionState sessions = new SessionState();
        GuestSession active = new GuestSession();
        sessions.begin(active);
        assertDoesNotThrow(new GuestService(sessions, active)::requireGuestAccess);
        assertReadsDenied(new GuestService(sessions, new GuestSession()));
    }

    @Test
    void exitInvalidatesTheOldGuestEvenWhenAnotherGuestEnters() {
        SessionState sessions = new SessionState();
        GuestSession oldGuest = new GuestSession();
        sessions.begin(oldGuest);
        GuestService service = new GuestService(sessions, oldGuest);
        sessions.clear();
        assertReadsDenied(service);
        sessions.begin(new GuestSession());
        assertReadsDenied(service);
    }

    private void assertReadsDenied(GuestService service) {
        assertThrows(SessionState.AccessDeniedException.class, service::requireGuestAccess);
        assertThrows(SessionState.AccessDeniedException.class, service::findBalances);
        assertThrows(SessionState.AccessDeniedException.class, () -> service.findProducts(""));
    }
}
