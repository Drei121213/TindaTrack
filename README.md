# TindaTrack

A desktop sari-sari store management application using Java, JavaFX with FXML,
Maven, and MySQL through JDBC.

## Current scope: Day 3, Task 2

TindaTrack opens the login screen from storyboard SB01. When the MySQL
`users` table is empty, **Create Account** opens the owner registration pop-up
from SB12. A successful login opens a temporary owner home screen with a
welcome message, Inventory, and Logout. **Continue as Guest (View Only)** opens product
search and customer balances without an owner account.

Day 1 includes the MySQL schema in `database/schema.sql`, a shared connection
utility, and a manual command-line connection test. Registration and login use
that connection utility and the existing `users` table. Guest Mode reads product
names, prices, stock counts, customer names, and balances from the existing schema.

### Inventory interface

1. Start MySQL, launch the app with `mvn javafx:run`, and sign in as the owner.
2. Select **Inventory**, then **Add Product**. Try submitting blank fields,
   a price of `0`, a price with three decimal places, or a fractional stock count.
   Check the messages under the fields.
3. Enter a name, a positive price with up to two decimal places, and a whole
   stock quantity of zero or more. Select an optional expiration date from the
   calendar. A past date prompts for confirmation. **No expiration date** clears
   the date and disables the picker.
4. Select **Save**. The dialog closes, the inventory reloads, and the saved
   product appears. Try the same product name again and expect
   “This product already exists.”
5. Select **Back to Home** or log out and open Guest Mode to check the separate
   read-only route.

`ProductFormController` validates the form and starts a background task.
`ProductDao.addProduct()` checks the owner session, checks the name using the
database's collation, and binds the fields to a prepared INSERT. A MySQL named
lock serializes name checks and inserts made by the app on the same server.
`InventoryController` reloads the table after a save. Each `Product` record maps
to one row, with `BigDecimal` for price and an optional `LocalDate` for expiration.

The table starts empty on entry. Initial loading, Search, and manual Refresh
are Task 3. Edit and Delete are Task 4.

## Requirements

- JDK 21, including the `javac` compiler.
- Maven 3.9 or newer.
- Windows desktop session for the JavaFX window.
- Internet access on the first build if dependencies are not already cached.

The project uses JavaFX 21.0.12. Maven resolves the JavaFX libraries and native
Windows dependencies, so a separate JavaFX SDK installation is unnecessary.

## Project structure and RTM mapping

Open this nested `TindaTrack` folder in VS Code, or run Maven from this folder
where `pom.xml` is located.

```text
TindaTrack/
|-- pom.xml
|-- README.md
|-- .gitignore
|-- .mvn/maven.config
|-- config/database.properties.example
|-- database/schema.sql
`-- src/
    |-- main/
    |   |-- java/tindatrack/
    |   |   |-- TindaTrackApplication.java
    |   |   |-- authentication/
    |   |   |   |-- package-info.java
    |   |   |   |-- AuthenticationService.java
    |   |   |   |-- LoginController.java
    |   |   |   |-- OwnerSession.java
    |   |   |   |-- RegistrationController.java
    |   |   |   `-- UserDao.java
    |   |   |-- inventory/
    |   |   |   |-- package-info.java
    |   |   |   |-- InventoryController.java
    |   |   |   |-- Product.java
    |   |   |   |-- ProductDao.java
    |   |   |   `-- ProductFormController.java
    |   |   |-- guest/
    |   |   |   |-- package-info.java
    |   |   |   |-- GuestController.java
    |   |   |   |-- GuestService.java
    |   |   |   `-- GuestSession.java
    |   |   |-- credit/package-info.java
    |   |   `-- core/
    |   |       |-- package-info.java
    |   |       |-- DatabaseConnection.java
    |   |       |-- DatabaseConnectionTest.java
    |   |       |-- OwnerHomeController.java
    |   |       |-- PasswordHasher.java
    |   |       |-- SessionState.java
    |   |       `-- WelcomeController.java
    |   `-- resources/tindatrack/
    |       |-- fxml/
    |       |   |-- .gitkeep
    |       |   |-- guest.fxml
    |       |   |-- inventory.fxml
    |       |   |-- login.fxml
    |       |   |-- owner-home.fxml
    |       |   |-- product-form.fxml
    |       |   |-- register.fxml
    |       |   `-- welcome.fxml
    |       `-- css/
    |           |-- .gitkeep
    |           |-- inventory.css
    |           |-- login.css
    |           `-- welcome.css
    `-- test/java/.gitkeep
```

The packages follow the conceptual packages in RTM Part D. Java identifiers use
lowercase names without spaces:

| RTM Part D package | Java package | Responsibility from the diagram |
| --- | --- | --- |
| Authentication | `tindatrack.authentication` | User Data, Authentication Management |
| Inventory | `tindatrack.inventory` | Product Data, Inventory Management |
| Guest Access | `tindatrack.guest` | Guest Management |
| Credit | `tindatrack.credit` | Credit Data, Credit Management |
| Core (Shared) | `tindatrack.core` | Database Connection, Validator, Password Hasher, Dashboard |

Each `package-info.java` documents a package without implementing features.
The empty `.gitkeep` files preserve directories for future work. They contain
no application logic. `src/test/java` is reserved for future automated tests.

`.mvn/maven.config` keeps Maven's dependency cache in this project's ignored
`.m2/repository` directory. This keeps dependency downloads inside the project
folder. Dependencies already in the user-wide cache are not used automatically,
so the first build needs internet access.

The reference workbook and PDF folders remain in the parent workspace. The
package diagram is at `../RTM Part D/(D.1) Package Diagram.pdf`, and the schema
is at `../RTM Part D/(D.3) Schema.pdf`. They are not application resources.

## Select Java 21 in PowerShell

Your machine also has Java 25, which is currently the system default. Run these
commands in the VS Code PowerShell terminal before building:

```powershell
Set-Location -LiteralPath 'C:\Users\andrei\Downloads\CMSC 128 TindaTrack\TindaTrack'
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
javac -version
mvn -version
```

Both Java commands should report `21.0.10`. Maven should also report Java 21.
These settings affect only this terminal session. They do not change your
system environment variables. On another machine, use its JDK 21 path.

For VS Code's Java language support, use **Java: Configure Java Runtime** from
the Command Palette to select JDK 21 for this project if needed. The terminal
commands above control the JDK used by Maven in that terminal.

## Build and test

From the project root, after selecting Java 21:

```powershell
mvn compile
mvn verify
mvn javafx:run
```

- `mvn compile` compiles the Java source into `target/classes`.
- `mvn verify` runs the Maven build lifecycle through verification and produces
  `target/tindatrack-1.0-SNAPSHOT.jar`. There are no automated tests yet; Maven
  reporting `No tests to run` is expected.
- `mvn javafx:run` starts the configured JavaFX entry point and opens the login
  window. Maven stays running while the window is open. Close the window to
  finish the run and see `BUILD SUCCESS` in the terminal.

Look for `BUILD SUCCESS` after each command. Use `mvn javafx:run` to run this
setup; the generated JAR is not a standalone application with bundled JavaFX.

### Simple checklist

- [ ] The terminal is in the folder containing `pom.xml`.
- [ ] Java, `javac`, and Maven all use Java 21.
- [ ] `mvn compile` ends with `BUILD SUCCESS`.
- [ ] `mvn verify` ends with `BUILD SUCCESS`.
- [ ] `mvn javafx:run` opens a window titled `TindaTrack`.
- [ ] The form shows Username, Password, Login, and Continue as Guest (View Only).
- [ ] Clicking Login with both fields empty displays `Please fill in all fields.`
  and focuses Username.
- [ ] Entering a username and pressing Enter with Password blank gives the same
  message and focuses Password.
- [ ] Password characters are masked. Submitting both fields clears the
  password input while login runs.
- [ ] A wrong password and an unknown username both show
  `Invalid username or password.`
- [ ] Correct owner credentials show a welcome message and Logout.
- [ ] Logout returns to an empty login form.
- [ ] Create Account opens the registration pop-up when no owner exists.
- [ ] Continue as Guest opens the view-only product and credit tabs.
- [ ] Exit Guest Mode returns to a fresh login form.
- [ ] Closing the window returns to the terminal with `BUILD SUCCESS`.
- [ ] No FXML loading errors or CSS warnings appear in the terminal.

### Troubleshooting

- **`mvn` is not recognized:** add Maven's `bin` folder to your PATH and reopen
  the terminal.
- **`release version 21 not supported`:** Maven is using an older JDK. Select
  JDK 21 again and check `mvn -version`.
- **Dependency download failure:** check the network connection and any Maven
  proxy settings, then retry the command. Offline mode (`mvn -o verify`) works
  only when all required dependencies and plugins are already cached.
- **`Access is denied`:** if the build fails, check the reported path and whether
  your account can read the JDK/Maven installation and write to the project and
  its `.m2` cache. Keep the full error output for diagnosis.

## Important code

`TindaTrackApplication` extends JavaFX's `Application`. Its `main` method calls
`launch(args)` to initialize JavaFX. JavaFX then calls `start(Stage primaryStage)`
on its application thread. `start` finds the FXML and CSS using classpath paths
beginning with `/tindatrack/`. Maven copies these resources into `target/classes`
during the build, so loading does not depend on a hardcoded source-file path.
Missing resources produce a clear `IOException`.

`FXMLLoader.load()` creates the layout described in `login.fxml`. The resulting
root node goes into a `Scene`, which holds the window's content. The CSS file is
attached to the scene. The `Stage` is the desktop window: `setTitle` names it,
`setScene` supplies its content, and `show` makes it visible.

`fx:controller="tindatrack.authentication.LoginController"` tells the loader
which controller to create. The controls' `fx:id` attributes match the
controller's `@FXML` fields. For example, `fx:id="passwordField"` gives the
controller access to the PasswordField created by the loader.

The Login button's `onAction="#handleLogin"` calls the controller's
`handleLogin()` method. Login is the default button, so Enter can submit
the form. The controller checks the fields and updates the feedback label.
`login.css` styles the form and makes error messages red.

## Owner login and logout

`LoginController` checks for blank fields and starts a JavaFX `Task` to check
credentials. The form is disabled while the task runs. `UserDao.findByUsername()`
binds the username to a prepared SELECT and reads the owner's ID, full name, and
stored hash. The database lookup and password calculation run off the JavaFX
application thread.

`AuthenticationService` calls `PasswordHasher.verify()`. Verification reads the
salt and checks the format and iteration count saved by registration. It derives a
candidate hash with the same settings and compares the bytes using
`MessageDigest.isEqual()`. An unknown username still runs the password
calculation and gets the same error as an incorrect password.

On a match, the service returns an `OwnerSession` containing the owner ID and
display name. `TindaTrackApplication` stores it in `SessionState` and switches the scene
to `owner-home.fxml`. Logout clears the session and loads a fresh login form.
Database failures show `Cannot connect to the database. Please try again.`

### Test login

1. Start MySQL and launch with `mvn javafx:run`. Use the owner account you
   created during registration. If this is a fresh database, create the owner
   using the steps below.
2. Submit empty fields. Expect `Please fill in all fields.`
3. Try the correct username with a wrong password. Then try an unknown
   username. Both attempts should show `Invalid username or password.`
4. Enter the correct credentials. Confirm the owner home screen greets you
   by name. Click **Logout**, confirm both fields are empty, and sign in again.

## Guest Mode

UC10 and storyboards SB10 and SB11 define the guest views. **Continue as Guest
(View Only)** creates an in-memory `GuestSession` without asking for credentials.
An `OwnerSession` represents a registered owner who has passed the password
check. The guest session carries no account details.

`GuestController` loads each table on a background thread. `GuestService`
checks that its guest session is still active before opening a JDBC connection
in read-only mode. Its prepared SELECT statements return the allowed columns.
Product search matches literal text in the product name. An empty search shows
all products. Credit shows customer names and their current balances.

The guest tables are read-only. `UserDao` checks the shared session before account
lookup or creation and tells the caller to return to login when a session is
active. **Exit Guest Mode** clears the guest session. From login, a user can
sign in or choose **Create Account** if the owner table is empty.

### Test Guest Mode

1. Start MySQL and run `mvn javafx:run`. Leave the login fields blank and choose
   **Continue as Guest (View Only)**. Confirm the Products tab shows name,
   price, and stock.
2. If your database has products, search for part of a known name. Then search
   for a name that cannot match and confirm `No products found.` appears.
   Clear the search and submit again to see all products.
3. Open **Credit (Utang)** and check the customer names and balances. Empty
   product and customer tables show an empty-state message on a fresh schema.
4. Choose **Exit Guest Mode**. Confirm that the login form opens and that owner
   sign-in or first-owner registration works as usual.

## Owner registration

`LoginController` checks for an existing owner on a background thread. It
shows **Create Account** when the table is empty and **Retry account check**
if MySQL cannot be reached. `register.fxml` defines the pop-up. Its
`RegistrationController` checks the four fields, including the six-character
password minimum from UC11 and the name and username lengths in the schema.

A JavaFX `Task` hashes and saves valid input in the background.
`PasswordHasher.hash()` uses PBKDF2-HMAC-SHA256 with 600,000 iterations and a
fresh 16-byte random salt. The `password_hash` field stores the algorithm,
iteration count, salt, and derived hash together. Password confirmation is used
only for validation. [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

`UserDao.createOwner()` checks for a duplicate username and an existing
owner, then binds the full name, username, and encoded hash to an INSERT
prepared statement. A MySQL named lock serializes registration across app
windows connected to the same server. Closing the connection releases the lock.
On success, the pop-up closes, Login shows the new username and the account
creation message, and the Create Account link disappears.

### Test registration

1. Start MySQL and run `mvn compile exec:exec` from the project root to verify
   your connection settings. Start the app with `mvn javafx:run`.
2. With an empty `users` table, open **Create Account** and submit a blank form.
   Expect a message under each field. Try a password shorter than six characters
   and a mismatched confirmation to check both password messages.
3. Click **Cancel** to return to Login. Reopen the pop-up, fill all four fields,
   and submit. Use the name, username, and password you want for the store's
   single owner account.
4. Expect `Account created. You can now log in.` with the username filled in
   and the Create Account link hidden. Restart the app to confirm the link stays
   hidden when the owner already exists.
5. In MySQL Workbench, verify the account and the hash format with a query that
   returns only a prefix check and length:

   ```sql
   SELECT full_name, username,
          password_hash LIKE 'pbkdf2-sha256$600000$%' AS has_expected_hash_format,
          CHAR_LENGTH(password_hash) AS hash_length
   FROM tindatrack.users;
   ```

   Expect one owner row with `has_expected_hash_format` equal to `1` and
   `hash_length` equal to `90`. Use that owner's credentials to test login.

`pom.xml` declares JavaFX controls and FXML dependencies, compiles for Java 21,
and tells the JavaFX Maven plugin which class to launch. This is a simple
non-modular project, so no `module-info.java` is needed at this stage.

## Local credentials

The login screen runs without database credentials. `.gitignore`
excludes build output, IDE workspace files, `.env` files, and the listed local
database property files. Safe example files are allowed. Task 4 uses
`config/database.properties` for credentials; it is already listed in
`.gitignore`. Keep this file outside `src/main/resources` so Maven does not
package credentials into the application's JAR.

## Java-to-MySQL connection - Task 4

### How JDBC works

JDBC is Java's standard database API (`java.sql`). MySQL Connector/J implements
that API for MySQL. The dependency in `pom.xml` places the driver on the classpath,
where JDBC discovers it automatically; no `Class.forName` call is necessary.
Connector/J 9.7.0 is selected for this project's MySQL 8.0 setup.

`DatabaseConnection.getConnection()` reads local properties, validates required
settings, and calls `DriverManager.getConnection(url, properties)`. Every call
opens a new connection. There is no shared permanently open connection and no
connection pool at this stage. Connect and socket timeouts are five seconds.
The configuration reader is closed automatically.

`DatabaseConnectionTest.main()` opens a connection and runs `SELECT 1`. Getting
the value `1` confirms that Java connected, authenticated, selected the database
specified in the URL, and executed a query. It does not validate all schema
tables or implement store features. The constant query needs no user input.
Try-with-resources closes the result set, statement, and connection, even if an
error occurs. A five-second statement timeout also bounds the check query.

Configuration errors identify missing settings. SQL errors show a helpful
description plus SQLState and vendor error code. Raw driver messages and
credential values are not printed. Failure returns process exit code 1; success
returns 0. The Maven Exec plugin runs this test in a separate Java process so a
failure becomes `BUILD FAILURE` without stopping Maven itself unexpectedly.
`mvn verify` compiles this manual test but does not execute it.

### Configure your local connection

1. Make sure your MySQL Server is running and the Task 3 `tindatrack` schema
   exists. Use the same server host/port and MySQL account you use in Workbench.
2. From the project root, copy the safe example only if no local file exists:

   ```powershell
   if (-not (Test-Path -LiteralPath 'config/database.properties')) {
       Copy-Item -LiteralPath 'config/database.properties.example' -Destination 'config/database.properties'
   }
   ```

3. Open `config/database.properties` in VS Code and fill in these settings:

   ```properties
   db.url=jdbc:mysql://localhost:3306/tindatrack
   db.username=YOUR_MYSQL_USERNAME
   db.password=YOUR_MYSQL_PASSWORD
   ```

   The names above are placeholders. Enter actual values only in your local
   ignored file, never in the example, SQL script, Java source, or chat. All three
   settings must be nonblank. Keep credentials out of the URL. Password text is
   not trimmed; Java properties interpret backslashes as escapes, so a literal
   backslash must be written as `\\`. This file uses UTF-8.

4. Select Java 21 with the PowerShell commands above, then run:

   ```powershell
   mvn verify
   mvn compile exec:exec
   ```

5. Expect `Database connection successful: SELECT 1 returned 1.` followed by
   `BUILD SUCCESS`. No rows are inserted, updated, or deleted.

The Maven command runs from the project root automatically. If launching the
test directly through an IDE, set its working directory to the folder containing
`pom.xml`. Local properties are read each time a connection is opened; after
editing the file, rerun the test.

### Connection troubleshooting and checklist

- **Create config/database.properties:** copy the example and fill it in.
- **Set db.username or db.password:** the setting is missing or blank.
- **Access denied (1045/1044):** check the MySQL account, password, and privileges
  on `tindatrack`. These are MySQL credentials, not a Windows account or a future
  TindaTrack application login.
- **Database not found (1049):** check the database name in the URL and run the
  Task 3 script on that same server.
- **Cannot connect (SQLState starting with 08):** check that MySQL Server is
  running and that the host/port match Workbench. If the server requires TLS,
  configure its trusted certificates with Connector/J; this project does not
  disable SSL or enable public-key retrieval as a workaround.
- **Other SQL errors:** use the reported SQLState and vendor error code when
  troubleshooting. Keep passwords out of shared error reports.

- [ ] `mvn verify` succeeds with Java 21.
- [ ] Only the ignored local file contains your real credentials.
- [ ] `mvn compile exec:exec` prints the success message and `BUILD SUCCESS`.
- [ ] A missing/incorrect local setting produces a clear failure message.
- [ ] `mvn javafx:run` opens the login screen and checks registration availability.

The build and configuration-failure paths can be checked without credentials.
A successful live connection must be verified using your own local account.

References: [Connector/J Maven dependency](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html),
[Connector/J 9.7.0](https://dev.mysql.com/doc/relnotes/connector-j/en/news-9-7-0.html),
and [Maven Exec Java process configuration](https://www.mojohaus.org/exec-maven-plugin/examples/example-exec-for-java-programs.html).

## MySQL schema - Task 3

Use MySQL Server 8.0.16 or newer and MySQL Workbench. Workbench is the graphical
client; MySQL Server must also be installed and running. A MySQL command-line
client was found on the development machine, but the script has not been
executed against a server as part of this task.

`database/schema.sql` follows the five tables and two foreign-key relationships
in RTM Part D, `(D.3) Schema.pdf`. The columns are:

| Table | Columns from the RTM |
| --- | --- |
| `users` | `user_id`, `full_name`, `username`, `password_hash`, `created_at` |
| `products` | `product_id`, `name`, `price`, `stock_qty`, `expiration_date` |
| `customers` | `customer_id`, `name`, `balance` |
| `credit_transactions` | `transaction_id`, `customer_id`, `items`, `amount`, `transaction_date` |
| `payments` | `payment_id`, `customer_id`, `amount`, `payment_date` |

Each ID is a primary key. `credit_transactions.customer_id` and
`payments.customer_id` reference `customers.customer_id`. One customer can have
many credit transactions and payments. No extra tables or relationships are added.

### SQL details chosen for implementation

The diagram specifies names and relationships, but does not specify SQL data
types, string lengths, defaults, nullability, or referential actions. This script
makes the following choices:

- Database name: `tindatrack`, with Unicode `utf8mb4` encoding and
  `utf8mb4_unicode_ci` collation. Text comparisons, including usernames, are
  case-insensitive under this collation.
- IDs: `INT AUTO_INCREMENT` so MySQL assigns each new record its ID.
- Names: `VARCHAR(100)` for people and `VARCHAR(150)` for products.
  Usernames use `VARCHAR(50)` and must be unique.
- `password_hash`: `VARCHAR(255)`, reserved for a future password hash.
  The schema does not perform hashing or implement authentication.
- Money: `DECIMAL(10,2)`, matching RTM NFR-05. Prices and balances cannot be
  negative. Credit and payment amounts must be greater than zero.
- Stock: a nonnegative `INT`, defaulting to zero. Customer balances default to
  `0.00`.
- Expiration: an optional `DATE`; `NULL` means no expiration date was recorded.
- Record times: `DATETIME DEFAULT CURRENT_TIMESTAMP` for `created_at`,
  `transaction_date`, and `payment_date`.
- Items: `TEXT`, preserving the single `items` field in the RTM.
- All other fields are required (`NOT NULL`).
- Tables use InnoDB to support foreign keys. `RESTRICT` prevents deleting or
  changing a referenced customer ID while financial records still reference it.
- `CHECK` constraints enforce the numeric rules. MySQL versions before 8.0.16
  do not enforce these checks.

The script creates `customers` before its dependent tables. MySQL creates the
indexes required by the foreign keys. There are no sample accounts, products,
customers, transactions, or payments.

`IF NOT EXISTS` makes repeat execution skip existing objects. It does not verify
or update an existing table's structure. If a `tindatrack` database already has
tables, inspect them before execution and compare their definitions afterward.
Do not treat this file as a migration script. MySQL DDL is not an all-or-nothing
transaction: if a statement fails, earlier successful statements can remain.

### Run in MySQL Workbench

1. Open Workbench and connect to your local MySQL Server using your own account.
   Enter credentials only in Workbench; do not put passwords in this script.
2. Open a query tab and run `SELECT VERSION();`. Confirm MySQL 8.0.16 or newer.
3. Choose **File > Open SQL Script** and open this file:
   `C:\Users\andrei\Downloads\CMSC 128 TindaTrack\TindaTrack\database\schema.sql`.
4. Read the script. Clear any selected text, then choose **Query > Execute
   (All or Selection)** to run the complete script. The account needs permission
   to create the database and its tables.
5. Check **Action Output** for errors. On repeat runs, notes about existing
   objects are expected. If execution fails, fix the reported issue before
   continuing; do not delete existing objects to retry.
6. Refresh the **Schemas** panel and expand **tindatrack > Tables**. You should
   see all five tables.
7. In a separate query tab, run the read-only checks below.

```sql
USE tindatrack;
SHOW TABLES;
SHOW CREATE TABLE users;
SHOW CREATE TABLE products;
SHOW CREATE TABLE customers;
SHOW CREATE TABLE credit_transactions;
SHOW CREATE TABLE payments;

-- For a newly created database, every count should be zero.
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL SELECT 'products', COUNT(*) FROM products
UNION ALL SELECT 'customers', COUNT(*) FROM customers
UNION ALL SELECT 'credit_transactions', COUNT(*) FROM credit_transactions
UNION ALL SELECT 'payments', COUNT(*) FROM payments;
```

Use the `SHOW CREATE TABLE` results to confirm primary keys, the unique username,
checks, and the two customer foreign keys. Existing data, if any, should remain.

### Database test checklist

- [ ] Server version is MySQL 8.0.16 or newer.
- [ ] The script executes without errors.
- [ ] All five tables and their RTM columns are present.
- [ ] Every table has its expected primary key.
- [ ] Usernames have a unique constraint; money uses `DECIMAL(10,2)`.
- [ ] The numeric checks and both customer foreign keys are present.
- [ ] A fresh database contains no rows.
- [ ] Running the script again does not remove existing tables or data.

This schema alone does not update a customer's balance when credit or payment
rows are added, and it does not reject payments above the customer's balance.
Those operations will need coordinated Java/JDBC transactions and validation
when customer credit is implemented. No triggers or feature logic are added now.

MySQL references: [CHECK constraints](https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html)
and [foreign keys](https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html).

## Manual Git workflow

Git commands are managed manually by the project owner. Suggested Day 3, Task 2
commit message is `Add owner product registration`.

## Day 2 integration verification

With Java 21 selected and MySQL running, use these commands from the project root.

```powershell
mvn verify
mvn compile exec:exec
mvn javafx:run
```

The first command builds the project. The second runs the Day 1 connection check.
Use the registration, login, and Guest Mode checklists above after launching the
app. First-owner registration is best checked with an empty test database.

Task 5 exercised the JavaFX flows and database services against a disposable
MySQL instance. The checks covered registration, validation, duplicate rejection,
salted password storage, login, logout, guest search and balances, service-level
access restrictions, and recovery after a refused database connection. The Day 1
schema loaded successfully and its `SELECT 1` connection check passed.
