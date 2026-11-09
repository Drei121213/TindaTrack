package tindatrack.authentication;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.Optional;

import tindatrack.core.PasswordHasher;

/** Checks credentials and returns an owner session for a successful login. */
public class AuthenticationService {

    public Optional<OwnerSession> authenticate(String username, char[] password)
            throws IOException, SQLException, GeneralSecurityException {
        Optional<UserDao.LoginAccount> account = new UserDao().findByUsername(username);
        String storedHash = account.map(UserDao.LoginAccount::passwordHash).orElse(null);
        if (!PasswordHasher.verify(password, storedHash)) {
            return Optional.empty();
        }
        return account.map(user -> new OwnerSession(user.userId(), user.fullName()));
    }
}
