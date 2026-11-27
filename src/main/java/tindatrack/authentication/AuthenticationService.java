package tindatrack.authentication;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Optional;

import tindatrack.core.PasswordHasher;

/** Checks credentials and returns an owner session for a successful login. */
public class AuthenticationService {

    private final UserDao users;

    public AuthenticationService(UserDao users) {
        this.users = users;
    }

    /**
     * Checks the supplied credentials and creates an owner identity only on success.
     * A missing username still reaches password verification with a null stored hash.
     * @param username account name used for the database lookup
     * @param password characters to verify; this method does not clear the array
     * @return an owner identity, or an empty result when credentials do not match
     */
    public Optional<OwnerSession> authenticate(String username, char[] password)
            throws IOException, SQLException, GeneralSecurityException {
        Optional<UserDao.LoginAccount> account = users.findByUsername(username);
        String storedHash = account.map(UserDao.LoginAccount::passwordHash).orElse(null);
        if (!PasswordHasher.verify(password, storedHash)) {
            return Optional.empty();
        }
        return account.map(user -> new OwnerSession(user.userId(), user.fullName()));
    }
}
