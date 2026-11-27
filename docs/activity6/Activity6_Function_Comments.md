# CMSC 128 — Activity 6

## Building a Good Legacy Code

Project: TindaTrack

### Introduction

Meaningful comments explain business rules and decisions that are difficult to see from method names alone. They help future maintainers understand session checks, database conflicts, and date rules before changing the code. This activity adds documentation to existing TindaTrack methods without changing their executable code.

### Documented Functions

#### Authentication and registration

**1. authenticate**

Java file: `AuthenticationService.java`  
Package: `tindatrack.authentication`

Exact comment added:

```java
    /**
     * Checks the supplied credentials and creates an owner identity only on success.
     * A missing username still reaches password verification with a null stored hash.
     * @param username account name used for the database lookup
     * @param password characters to verify; this method does not clear the array
     * @return an owner identity, or an empty result when credentials do not match
     */
```

Why useful: Explains the failed-login path and who owns password cleanup.

**2. handleLogin**

Java file: `LoginController.java`  
Package: `tindatrack.authentication`

Exact comment added:

```java
    /**
     * Validates login input, then checks credentials on a background task.
     * Clears the password field before starting and clears the copied character array
     * when the task finishes its credential check, including exceptional exits.
     */
```

Why useful: Explains background work and the lifetime of the password copy.

**3. checkRegistrationAvailability**

Java file: `LoginController.java`  
Package: `tindatrack.authentication`

Exact comment added:

```java
    /**
     * Checks whether the single owner account already exists without blocking JavaFX.
     * Keeps registration unavailable while checking; a failed check exposes a retry link.
     */
```

Why useful: Clarifies why the account link may be hidden or act as a retry.

**4. handleCreateAccount**

Java file: `RegistrationController.java`  
Package: `tindatrack.authentication`

Exact comment added:

```java
    /**
     * Validates the form before hashing and saving the owner on a background task.
     * Disables form actions during saving and clears the copied password in finally.
     */
```

Why useful: Explains how registration handles slow work and password cleanup.

**5. validate**

Java file: `RegistrationController.java`  
Package: `tindatrack.authentication`

Exact comment added:

```java
    /**
     * Displays field errors for required values, name limits, a minimum six-character
     * password, and matching confirmation. Character limits count Unicode code points.
     * @return true only when every form check passes
     */
```

Why useful: Records the actual registration rules and character-count policy.

**6. findByUsername**

Java file: `UserDao.java`  
Package: `tindatrack.authentication`

Exact comment added:

```java
    /**
     * Looks up login data only while signed out, binding the username as a SQL value.
     * @return the matching login record, or an empty result if no row exists
     */
```

Why useful: Explains the access rule and missing-account result.

**7. createOwner**

Java file: `UserDao.java`  
Package: `tindatrack.authentication`

Exact comment added:

```java
    /**
     * Checks for an existing username or owner under a connection-scoped MySQL lock
     * before inserting an account. The lock is released when the connection closes.
     * @param passwordHash an already encoded password hash, never a plain password
     * @return CREATED, USERNAME_TAKEN, or OWNER_EXISTS according to the database checks
     */
```

Why useful: Explains the single-owner rule and the expected password representation.

**8. lockOwnerRegistration**

Java file: `UserDao.java`  
Package: `tindatrack.authentication`

Exact comment added:

```java
    /**
     * Waits up to three seconds for the named registration lock on this connection.
     * This serializes the owner check and insert among callers using the same lock.
     * @throws SQLException if MySQL does not grant the lock
     */
```

Why useful: Explains the purpose and limit of the advisory lock.

#### Password hashing

**9. hash**

Java file: `PasswordHasher.java`  
Package: `tindatrack.core`

Exact comment added:

```java
    /**
     * Derives a PBKDF2-HMAC-SHA256 hash with a fresh 16-byte random salt.
     * @param password characters to hash; the caller remains responsible for clearing them
     * @return algorithm, iteration count, Base64 salt, and Base64 hash separated by dollar signs
     */
```

Why useful: Describes the stored format and password-array ownership.

**10. decode**

Java file: `PasswordHasher.java`  
Package: `tindatrack.core`

Exact comment added:

```java
    /**
     * Accepts only the current algorithm and iteration count with a 16-byte salt
     * and a 32-byte hash after Base64 decoding.
     * @return decoded hash data, or null for a missing or unsupported encoding
     */
```

Why useful: Explains why some stored values cannot be verified.

**11. derive**

Java file: `PasswordHasher.java`  
Package: `tindatrack.core`

Exact comment added:

```java
    /**
     * Derives 32 bytes using the configured PBKDF2 iteration count and supplied salt.
     * Clears the key specification password copy in finally; the input array is unchanged.
     */
```

Why useful: Distinguishes internal cleanup from caller-owned input.

#### Sessions and navigation

**12. begin**

Java file: `SessionState.java`  
Package: `tindatrack.core`

Exact comment added:

```java
    /**
     * Starts a non-null owner or guest session only while the window is signed out.
     * @throws AccessDeniedException if another session is already active
     */
```

Why useful: Explains why replacing a session requires returning to login.

**13. clear**

Java file: `SessionState.java`  
Package: `tindatrack.core`

Exact comment added:

```java
    /**
     * Ends the current window session so previously issued session objects fail
     * subsequent requireActive checks. This does not cancel running background tasks.
     */
```

Why useful: Makes logout effects and their limits explicit.

**14. requireActive**

Java file: `SessionState.java`  
Package: `tindatrack.core`

Exact comment added:

```java
    /**
     * Requires the exact session object currently held by this window, using identity
     * rather than value equality so an old or recreated identity cannot pass the check.
     * @param session owner or guest session associated with the requested operation
     * @throws AccessDeniedException if the session is null or no longer current
     */
```

Why useful: Explains the intentional object-identity comparison.

**15. navigateOwner**

Java file: `TindaTrackApplication.java`  
Package: `tindatrack`

Exact comment added:

```java
    /**
     * Rechecks the owner session before opening a destination and reports loading
     * or access failures through the supplied screen feedback callback.
     */
```

Why useful: Explains the navigation guard and how errors reach the screen.

#### Guest permissions

**16. findProducts**

Java file: `GuestService.java`  
Package: `tindatrack.guest`

Exact comment added:

```java
    /**
     * Returns only product name, price, and stock for the active guest.
     * @param search literal name fragment; surrounding whitespace is removed and
     * an empty fragment matches all names through MySQL LOCATE
     * @return matching rows ordered by name and product ID
     */
```

Why useful: Documents the permitted columns and literal search behavior.

**17. openReadConnection**

Java file: `GuestService.java`  
Package: `tindatrack.guest`

Exact comment added:

```java
    /**
     * Checks that this guest session is active and requests a read-only JDBC connection.
     * Closes the connection if setting read-only mode fails, preserving any close failure.
     * @return a new connection that the caller must close
     */
```

Why useful: Explains session enforcement, connection ownership, and failure cleanup.

#### Inventory and product queries

**18. addProduct**

Java file: `ProductDao.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Checks the product name and inserts under a shared MySQL name lock.
     * Rechecks the owner session after waiting and immediately before the insert.
     * @param expirationDate expiration date, or null for no expiration
     * @return DUPLICATE_NAME when the name check finds a row; otherwise ADDED
     */
```

Why useful: Explains duplicate protection and the repeated session checks.

**19. updateProduct**

Java file: `ProductDao.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Updates price, stock, and expiration only if all stored fields still match original.
     * The MySQL null-safe equality operator also matches two absent expiration dates.
     * @param original snapshot loaded before editing; the product name is not changed
     * @return true if exactly one row was updated; false if no row matched the snapshot
     */
```

Why useful: Explains how the query avoids overwriting a changed product.

**20. deleteProduct**

Java file: `ProductDao.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Deletes only a row that still matches the complete original product snapshot.
     * @return DELETED for one removed row, STALE for no match, or REFERENCED for
     * MySQL foreign-key errors 1451 and 1217; other SQL errors are propagated
     */
```

Why useful: Explains deletion conflict results and foreign-key protection.

**21. bindOriginal**

Java file: `ProductDao.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Binds the original ID, name, price, stock, and expiration in that order.
     * @param start one-based position of the first snapshot parameter
     * @param original snapshot used by update and delete to detect stale data
     */
```

Why useful: Makes the shared SQL parameter ordering clear.

**22. findByName**

Java file: `ProductDao.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Searches product names using a bound literal fragment rather than LIKE wildcards.
     * @param name fragment to find after stripping whitespace; empty matches all names
     * @return matching products ordered by name and product ID
     */
```

Why useful: Clarifies search semantics without claiming case sensitivity.

**23. loadProducts**

Java file: `InventoryController.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Loads the last submitted search on a background task while inventory actions
     * are disabled. Ordinary failures retain earlier rows; an ended session clears them.
     * @param successMessage optional completed-action message to retain during refresh
     */
```

Why useful: Explains refresh state and the difference between failure paths.

#### Expiration management

**24. parseOptional**

Java file: `ExpirationDates.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Parses a stripped YYYY-MM-DD value with a year from 1000 to 9999.
     * @return null for blank input, or a valid calendar date
     * @throws DateTimeParseException for an incorrect format or impossible date
     */
```

Why useful: Explains both format checks and calendar validation.

**25. requiresConfirmation**

Java file: `ExpirationDates.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Requests confirmation only for a non-null expiration date that changed and is
     * before today. Keeping an existing past date does not trigger another warning.
     * @return true when the proposed date needs the past-date confirmation
     */
```

Why useful: Documents the rule that avoids repeated past-date prompts.

**26. status**

Java file: `ExpirationDates.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Counts calendar days from today to the expiration date: negative means expired,
     * zero means expires today, and positive values give the remaining day count.
     * @param date non-null expiration date to label
     * @return the display label for the calculated day count
     */
```

Why useful: Explains the sign and boundary cases of the day calculation.

**27. loadProducts**

Java file: `ExpiringSoonController.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Loads expired products and dates through today plus the selected day range.
     * Cancels the previous task and ignores callbacks from superseded loads.
     * Uses the captured query date for labels so all rows share one reference day.
     */
```

Why useful: Explains the inclusive cutoff and protection against older task results.

**28. initialize**

Java file: `ProductFormController.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Keeps invalid typed dates visible for Save to reject instead of losing the text
     * during JavaFX date commits. Selecting No Expiration clears and disables the date.
     * Reselecting the current calendar day restores valid text even without a value change.
     */
```

Why useful: Explains the date-editor handling while preserving existing inline comments.

**29. handleSave**

Java file: `ProductFormController.java`  
Package: `tindatrack.inventory`

Exact comment added:

```java
    /**
     * Validates product fields and the typed date before starting a database write.
     * Rejects unchanged edits and asks for confirmation when a changed date is in the past.
     * Uses the original snapshot on edits so the DAO can detect a conflicting change.
     */
```

Why useful: Connects validation, past-date confirmation, and stale-edit handling.

#### Database connections

**30. requiredProperty**

Java file: `DatabaseConnection.java`  
Package: `tindatrack.core`

Exact comment added:

```java
    /**
     * Rejects missing or blank database settings without trimming the returned value.
     * Preserving the value lets the caller keep meaningful whitespace in a password.
     * @return the configured value exactly as loaded from the properties file
     * @throws IOException if the requested setting is absent or blank
     */
```

Why useful: Explains why this helper deliberately leaves whitespace unchanged.

### Summary

30 existing methods were documented across authentication and registration, password hashing, sessions and navigation, guest permissions, inventory, expiration management, and database connections. Existing readable comments were preserved. Unfinished modules were excluded.

Reference: CMSC 128 Week 15 Study Guide, Module V - Software Maintenance, Activity 6.
