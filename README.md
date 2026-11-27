# TindaTrack

A desktop sari-sari store management application using Java, JavaFX with FXML,
Maven, and MySQL through JDBC.

## Current scope: Day 4 complete

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
   stock quantity of zero or more. Type an optional expiration date as
   `YYYY-MM-DD` or select it from the calendar. **No expiration date** clears
   and disables the picker.
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

### View and search products

1. Open **Inventory** as the owner and confirm that previously saved products
   appear, including stock and any expiration date.
2. Enter part of a product name and press Enter or select **Search**. Try
   changing the case and confirm that the same names match. A search with no
   matches shows “No products found.”
3. Select **Refresh** to rerun the last submitted search. Clear the field and
   submit to see all products. Saving a product also clears the search so the
   new row appears in the full list.
4. If a query fails, check the status message and select **Refresh** after the
   connection recovers. Any rows still shown are from the previous successful
   load.

`ProductDao` reads rows through a prepared SELECT in a background JavaFX
`Task`. `InventoryController` replaces the contents of the table's
`ObservableList` on the JavaFX thread. The `TableView` observes that list and
uses each column's value factory to display fields from each `Product`.

### Edit and delete products

1. Select a product and choose **Edit Product**. Check that the current
   values are filled in. Change the price, stock, or optional expiration date
   and select **Save**. Inventory reloads with the same search applied.
2. Select a product created for testing and choose **Delete Product**. Check
   that the confirmation names that product. Select **Cancel** and confirm the
   row is still present. Open the confirmation again and choose **Delete** to
   remove the test product.

The shared product form validates edits before saving. The DAO binds the
product ID and original field values in its prepared UPDATE and DELETE
statements. If the database row has changed or disappeared, refresh the list
before retrying. A foreign-key restriction leaves the product and related
records in place and displays a reason. The supplied schema stores credit
items as text and has no foreign key to products.

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
    |   |   |   |-- ExpirationDates.java
    |   |   |   |-- ExpiringSoonController.java
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
    |       |   |-- expiring-soon.fxml
    |       |   |-- guest.fxml
    |       |   |-- inventory.fxml
    |       |   |-- login.fxml
    |       |   |-- owner-home.fxml
    |       |   |-- product-form.fxml
    |       |   |-- register.fxml
    |       |   `-- welcome.fxml
    |       `-- css/
    |           |-- .gitkeep
    |           |-- expiring-soon.css
    |           |-- inventory.css
    |           |-- login.css
    |           `-- welcome.css
    `-- test/java/tindatrack/inventory/ExpirationDatesTest.java
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
no application logic. `src/test/java` contains the JUnit expiration tests.

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
- `mvn verify` compiles the app, runs the JUnit tests, and produces
  `target/tindatrack-1.0-SNAPSHOT.jar`.
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

Git commands are managed manually by the project owner. Suggested Day 4, Task 5
commit message is `Test expiration management and fix calendar reselection`.

## Day 3 integration verification

With Java 21 selected and MySQL running, run these commands from the project root.

```powershell
mvn verify
mvn compile exec:exec
mvn javafx:run
```

1. Sign in as the owner and select **Inventory**. Add a product for testing
   after checking that a negative price and fractional stock are rejected.
2. Close and reopen the app. Sign in and confirm the product is still listed.
   Search for part of its name using a different case, then edit its price or
   stock and check the updated row.
3. Choose **Delete Product**, verify the name in the confirmation, and select
   **Cancel**. Confirm the test product remains. Repeat and confirm deletion.
4. Return home, log out, and enter Guest Mode. Check the product and credit
   views, then select **Exit Guest Mode** to return to login.

Task 5 exercised these flows against a disposable MySQL instance over two
separate app launches. It also checked first-owner registration, invalid
credentials, the Day 1 schema and connection check, and inventory refresh
recovery after a refused connection. The guest session could not add, edit,
or delete through the DAO. A test-only foreign key blocked deletion of a
referenced product and preserved its dependent record.

## Expiration date input

Try these checks in the owner Inventory with products created for testing.

1. **SetExpiration_SunnyDay**. Use the calendar to select a future date in
   **Add Product** and save. Check the Expiration Date column. Use **Edit
   Product** to change the date and confirm the updated value appears.
2. **SetExpiration_NoExpiration**. Select **No expiration date**. The picker
   clears and becomes disabled. Save and check the blank date cell. Leaving
   the date field blank also saves an absent date.
3. **SetExpiration_InvalidDate**. Type `2026-13-45` and select **Save**.
   Expect “Please enter a valid date.” and an open form with the text intact.
4. **SetExpiration_PastDateYes**. Enter a date earlier than today and select
   **Yes** when prompted. Check that Inventory displays the past date.
5. **SetExpiration_PastDateNo**. Enter a new past date and select **No**.
   The form stays open and the stored product keeps its original date.

The controller validates calendar and typed dates before saving. JDBC binds a
`LocalDate` as SQL `DATE` with `setObject`, or uses `setNull` when
the date is absent. The DAO reads the value back as a nullable `LocalDate`.

## Expiring Soon

The Expiring Soon view shows the product name, stock, expiration date, and
days left. Select 7, 14, or 30 days to reload the list. The default is 7 days.
The list includes expired products and products due from today through the
selected cutoff, inclusive. Rows are ordered by earliest expiration date.
Products with no expiration date are excluded.

`ExpiringSoonController` captures the local date when loading the view and
passes the cutoff to `ProductDao.findExpiringThrough()` on a JavaFX background
task. The query binds the cutoff as a SQL `DATE`. The controller uses
`ChronoUnit.DAYS.between()` with the same local date to label expired,
due-today, and upcoming products. Expired rows are highlighted in red and
carry the **Expired** label. A failed query displays a **Retry** button.

Sign in as the owner and select **Expiring Soon** from Home or Inventory. Use
**Inventory** to return and edit a product using the existing form. Reopen
**Expiring Soon** to read the saved dates. **Refresh** reloads the current range.

1. Check that 7 days is selected when the screen opens. Switch to 30 days and
   look for products with dates 8 to 30 days from today.
2. In Inventory, edit a test product's date to within seven days and save.
   Reopen Expiring Soon and check that the product is listed. Clear its date in
   Inventory and confirm it disappears on reopening Expiring Soon.
3. Return home, log out, and enter Guest Mode. Check the read-only product and
   credit views and exit to login.

Expired rows stay red in every range. Today's rows say **Expires Today**, and
blank dates are excluded. If there are no matching products, the table shows
“No products expiring soon.”

`TindaTrackApplication` checks the active owner session before switching views.
Both inventory screens share the owner's `ProductDao`. Date updates still use
the existing Inventory form and prepared update.

## Day 4 acceptance checks

Run `mvn test` for the 15 JUnit checks. They cover optional dates, invalid
formats and calendar dates, past-date confirmation rules, and status labels.
Run `mvn verify` to test and package together. Start MySQL and run
`mvn javafx:run` for the manual checks above.

The B4 acceptance flows ran through JavaFX against disposable MySQL databases.
Date fixtures were chosen relative to the test date where the behavior depends
on today's date. The database-error check used a refused connection in the test
configuration.

| B4 cases | Verified behavior |
| --- | --- |
| `SetExpiration_SunnyDay`, `SetExpiration_NoExpiration` | A selected date saved and appeared in Inventory. Clearing the date disabled the picker and persisted SQL NULL. |
| `SetExpiration_InvalidDate` | Invalid typed dates kept the form open with an error. Reselecting the same calendar date now replaces invalid text. |
| `SetExpiration_PastDateYes`, `SetExpiration_PastDateNo` | Yes saved the past date. No kept the form open and preserved the saved date. |
| `ViewExpiring_SunnyDay`, `ViewExpiring_ChangeRange` | The default list included day seven. The 30-day list added day 20 and day 30 and excluded day 31. Dates appeared in ascending order. |
| `ViewExpiring_ExpiredHighlighted`, `ViewExpiring_NoExpiryNotListed` | The expired row rendered in red, today's row said Expires Today, and the undated product was excluded. |
| `ViewExpiring_NoProducts`, `ViewExpiring_DatabaseError` | An empty list showed its message. A refused connection showed an error and Retry restored the current range. |

The regression checks also exercised owner registration and login, product add,
search, edit and confirmed delete, and the limits on guest navigation and writes.

## Day 5, Task 1: Customer Credit Management

Sign in as the owner and select **Credit Management** on Home. The screen shows
each customer's name, outstanding balance, and last transaction date. The last
date is the latest date from either `credit_transactions` or `payments`; a dash
means there is no recorded transaction. All customers are included, even when
their balance is zero. Search by a name fragment, or clear the field and select
**Search** to list everyone. **Refresh** reloads the currently entered search.

Select a customer to display their utang and payment history, newest first.
The history shows the date, type, purchased items (utang only), and amount.
Payment amounts are shown as positive amounts with the **Payment** type; the
outstanding balance comes directly from `customers.balance`. History is not used
to reconstruct or change the stored balance. Money is displayed with two decimals.
The divider between the two tables can be dragged to make either table taller.

`Customer` and `CreditTransaction` are simple records containing display data.
`CreditDao.findCustomers()` and `findHistory()` use prepared SELECT statements
and close their JDBC resources with try-with-resources. Both require the exact
active `OwnerSession` and request a read-only connection. `CreditController`
runs those queries in background JavaFX `Task`s. It copies successful results
into `ObservableList`s assigned to the two `TableView`s; each column's cell value
factory selects the corresponding record field. Changing the list updates the
display automatically. Old history requests are canceled and their callbacks
ignored when selection changes. Access is checked again before displaying results.

The application loads `credit.fxml` with `FXMLLoader`, obtains its separate
`CreditController`, and supplies the owner's DAO and a Home navigation callback.
The same owner session is retained while moving between Home and Credit.
`credit.css` supplements the existing shared login stylesheet.

At the end of Task 1, this screen only read existing records and both recording
buttons were disabled. Tasks 2 and 3 below now enable **+ Record Utang** and
**Record Payment** for owners. Guest Mode continues
to use its existing separate read-only customer-name/balance view.

From the project root, use Java 21 and run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn verify
mvn compile exec:exec
mvn javafx:run
```

The middle command checks the existing database connection with SELECT 1.
Start MySQL and check the ignored `config/database.properties` locally if that
check cannot connect. Never include that credential file when staging changes.

Manual checklist:

1. Sign in and open **Credit Management**. Check the screen title and **Back to
   Home** navigation. Reopening Credit should preserve the signed-in owner.
2. With existing customers, compare their names and balances with MySQL Workbench.
   Search a known name fragment; clear the field and search again. A name that
   does not exist should display **No customers found.**
3. With an empty customer table, expect **No customers recorded yet.** Do not
   delete real records to perform this check. If there are no existing customers,
   populated-list and history checks remain pending until the later recording task.
4. Select an existing customer. Compare their history with both transaction tables.
   Check that the latest date agrees with **Last Transaction**, amounts have two
   decimals, and customers without history show the appropriate message.
5. Select different customers quickly and refresh. Confirm the history belongs
   to the current selection and that Refresh keeps the current search filter.
6. Test the recording controls using the Task 2 and Task 3 checklists below.
7. Log out and enter Guest Mode. Confirm its credit tab remains view-only and
   has no owner recording controls.
8. If a database connection is unavailable, expect a clear loading error. After
   restoring the connection, select **Refresh** to retry.

Task 1 verification: the Java 21 offline Maven verify succeeded, including the
15 existing expiration JUnit checks. A temporary JavaFX check loaded all eight
current FXML layouts and exercised Credit navigation using a test owner session,
returning to Home, retry after a refused connection, and rejection of a guest on
the owner route. Both DAO reads rejected signed-out, guest, recreated, and ended
owner identities before database access. A rendered view was checked at the
normal 640 x 560 scene size. The Maven JavaFX run command also reached application
startup without a reported launch error and was then stopped.

During Task 1, the configured MySQL endpoint refused the connection. Actual
customer loading, search results from stored rows, transaction history, latest-date
calculation, and successful recovery after reconnecting were not verified then.
Task 2's read-only connection check now succeeds; see its verification notes below.
No database records or schema were changed during Task 1 and no seed data was imported.

## Day 5, Task 2: Record Utang

From owner **Credit Management**, select **+ Record Utang**. Choose an existing
customer, or select **New customer** and type their name. Existing choices show
the customer ID to distinguish records with identical names. If a typed name
already matches a single stored customer under MySQL's name comparison, the save
uses that customer instead of creating another record. If multiple records match,
the form asks you to select the intended customer from the existing-customer list.

Enter the amount and optionally describe the purchased items as plain text.
SB08 marks customer and amount as required. The date defaults to today; use the
calendar to choose an earlier date when needed. The calendar is not directly
editable, which avoids invalid typed dates. Save accepts valid dates within
MySQL's year range of 1000 through 9999.

Amounts must be greater than zero, have at most two decimal places, and fit
DECIMAL(10,2). Exponents, currency symbols, thousands separators, and extra decimal
places are rejected rather than rounded. Customer names are limited to 100
characters and the items description must fit the existing UTF-8 TEXT column.
The resulting balance must also fit DECIMAL(10,2).

The code responsibilities are:

- `UtangRequest` carries the selected customer ID (or a new name), items, amount,
  and selected date.
- `CreditRules` validates input and calculates the new balance using BigDecimal.
- `CreditService` checks owner access and revalidates requests before saving.
- `RecordUtangController` loads customers, shows field errors, and runs the save
  on a background JavaFX Task. While saving, fields, Save, Cancel, and window
  closing are disabled. Repeated Save events are ignored.
- `CreditDao.recordUtang()` performs the database transaction with prepared SQL.

The DAO calls `setAutoCommit(false)` so its changes are held together until
`commit()`. It reads the customer using SELECT FOR UPDATE, which locks that row
until the transaction finishes. The calculation uses this current balance, not
the potentially older value displayed on screen. For a new name, it resolves or
creates the customer inside the same transaction, starting at zero. A MySQL
application name lock serializes name-based creation without changing the schema.

It then inserts the credit purchase and updates the customer's balance.
`commit()` makes both changes permanent together. If validation, a database
operation, or an owner-session check fails, `rollback()` undoes the transaction,
including a newly created customer. Connections, statements, and result sets
close with try-with-resources. Inventory quantities are never changed.

For example, an existing balance of **250.00** plus an utang of **30.00** becomes
**280.00**. A new customer with a **45.00** purchase is created with a final balance
of **45.00**. Successful saving closes the form, clears the name filter, reloads
the list, selects the saved customer, and loads their history. If refreshing fails
after a successful commit, the message still says **Utang saved.** so a refresh
failure is not confused with a failed save.

Run from the project root with Java 21:

```powershell
mvn test
mvn verify
mvn compile exec:exec
mvn javafx:run
```

The first two commands run the validation, calculation, and permission tests;
verify also packages the application. The connection check only runs SELECT 1.
Use the Java 21 environment commands in the Task 1 section if needed.

Manual SID-08 checklist (use approved test records):

1. **RecordUtang_SunnyDay:** Select a customer with balance 250.00, enter
   `2 Pancit Canton` and `30.00`, and save. Expect balance 280.00, one new history
   entry, and **Utang saved.** Do not change real balances merely to match this
   example; otherwise calculate the expected result from the existing balance.
2. **RecordUtang_NewCustomer:** Choose New customer, enter an approved new name,
   items, and `45.00`. Save and confirm one customer and one credit purchase.
3. **RecordUtang_EmptyFields / InvalidAmount:** Leave the name/selection or amount
   blank, then try `0`, `-10`, `abc`, and `1.001`. Expect field errors and no save.
4. **RecordUtang_Cancel:** Fill valid fields and cancel or close the form before
   saving. Confirm no new customer, purchase, or balance change.
5. **RecordUtang_DatabaseError:** In a dedicated test setup, make the connection
   unavailable after the form has loaded and select Save. Expect
   **Utang was not saved. Please try again.** and an open form. Restore the test
   connection before retrying. Do not interrupt a production database for a test.
6. **RecordUtang_SavedImmediately:** Complete a save, close the application, reopen
   it, and verify the same balance and transaction are still present.
7. Select an earlier date and verify that the history uses that date. Check that
   inventory stock is unchanged and Guest Mode has
   no recording controls.

The Java 21 build passed 45 JUnit checks: 30 for credit rules/permissions and 15
existing expiration checks. Credit tests include the 250.00 + 30.00 calculation,
decimal accuracy, invalid input, column limits, balance overflow, and rejection
of guest, signed-out, recreated, and ended owner sessions before database access.
These tests do not insert database records.

A temporary JavaFX check loaded all nine current FXML layouts and exercised the
Record Utang form. The executed checks covered loading the empty customer list,
automatically choosing New customer, today's default date, customer/amount/date
validation, canceling a filled form, and disabled controls while saving. A save
attempt used an unreachable endpoint in a temporary, ignored configuration copy
to verify the exact database-error message and that the form stayed open. The
original credential file was not changed. Customer, credit-transaction, and payment
counts were identical before and after these checks. The form was also visually
checked at 520 x 620. Temporary harness and credential-copy files were removed.

The read-only connection check succeeded during Task 2. At that time, the database
had no customers, so populated customer/history verification and successful saving
remained manual checks. No seed data or production test records were created.
Actual commit, rollback after a partial write, concurrent saves, and persistence
after restarting have not yet been verified against MySQL. Full B4 workbook
results will be recorded in the separately approved testing task.

## Day 5, Task 3: Record Payment

In owner **Credit Management**, select **Record Payment** beside a customer.
The app first reads that customer's current balance. If it is zero, the list
shows **This customer has no balance.** and does not open the form. Otherwise,
the form shows their name, current balance, and today's date. Enter the amount
to preview the remaining balance. Partial and full payments are supported, and
the calendar can select an earlier payment date.

Blank, zero, negative, nonnumeric, excessive-precision, and oversized amounts
are rejected. A payment above the balance shows **Payment cannot be more than
the balance.** Money remains BigDecimal in Java and DECIMAL(10,2) in MySQL, with
two decimal places in the interface. The preview is informational: saving always
checks the latest locked balance inside the database transaction.

`PaymentRequest` carries an existing customer's ID, payment amount, and date.
`RecordPaymentController` handles previewing, field messages, and the background
save. `CreditService` checks owner access and validates requests. The shared
`CreditRules` now supplies the same amount and date rules to both forms and
calculates the remaining balance. `CreditDao.findCustomer()` reads a fresh
snapshot for the opening check, while `recordPayment()` performs the save.

Each payment is a separate row in the existing `payments` table, with its own
payment_id, customer_id, amount, and payment_date. Previous credit purchases
are preserved. The history table already combines utang and payment entries.
No schema, inventory, authentication, or guest-screen changes are needed.

The payment transaction performs these steps:

1. Disable automatic commits and SELECT the customer balance FOR UPDATE.
2. Validate the amount against that locked balance, rejecting zero balances or
   overpayments before inserting anything.
3. Insert the payment and update the customer's remaining balance.
4. Recheck owner access and commit both changes together. Any SQL or validation
   failure triggers rollback, undoing any changes in that transaction.

The row lock is also used by utang saves. A simultaneous operation for the same
customer must wait for the earlier transaction to finish, then reads the updated
balance. For example, two requests to pay 100.00 against 150.00 cannot both pass:
the second sees 50.00 and is rejected. The database's existing nonnegative-balance
constraint provides an additional safeguard.

If another payment leaves less than this form's requested amount, the rejected
save returns the current balance so the form updates it. A fully paid customer
cannot save again. While saving, the fields, Save, Cancel, and window-close
action are disabled. Successful saving closes the form, clears the search filter,
reloads/selects the customer, and shows **Payment saved. Remaining balance: ...**
using the committed result. A failed list reload keeps the payment confirmation
so it is distinguishable from a failed save.

Run from the project root with Java 21:

```powershell
mvn test
mvn verify
mvn compile exec:exec
mvn javafx:run
```

Use the Java 21 environment commands in Task 1 if needed. The connection check
only executes SELECT 1. The configured MySQL server must be running.

Manual SID-09 checklist (use approved test records):

1. **RecordPayment_PartialPayment:** With an existing test balance of 250.00, pay
   50.00. Expect 200.00, a payment history entry, and a success message.
2. **RecordPayment_FullPayment:** Pay the exact remaining balance. Expect 0.00.
   Try its Record Payment button again: expect the no-balance message and no form.
3. **RecordPayment_EmptyOrZero:** Try blank, 0, -10, abc, and 1.001. Expect
   **Please enter a valid amount.** with no stored payment or balance change.
4. **RecordPayment_MoreThanBalance:** Enter more than the balance. Expect the
   overpayment message, no payment, and an unchanged balance.
5. **RecordPayment_TwoDecimals:** On a suitable test balance of 250.00, pay 49.50.
   Expect 200.50. An attempted 300.00 payment must be rejected. Use your actual
   starting balance to calculate equivalent checks if it differs.
6. **RecordPayment_Cancel:** Enter valid payment details and cancel or close the
   form before saving. Confirm that no payment or balance update was stored.
7. **RecordPayment_DatabaseError:** In a dedicated test setup, make the connection
   unavailable after opening the form and select Save. Expect **Payment was not
   saved. Please try again.** and an open form. Do not interrupt production service.
8. Confirm the selected date appears in history, close/reopen the app, and verify
   the payment persists. Compare earlier utang records to confirm they remain.
9. On a dedicated test database, open two application instances for the same
   customer and attempt payments whose sum exceeds the balance. Confirm the
   second operation uses the new balance and cannot make it negative. Mid-write
   rollback testing also belongs in this separate test setup.
10. Confirm Guest Mode has no recording controls and still displays balances.

Verification performed for Task 3:

- Java 21 `mvn -B -o verify` passed 70 JUnit checks: 25 payment, 30 existing credit,
  and 15 expiration checks. Payment checks cover partial/full/decimal results,
  invalid amounts, overpayment, zero balances, request/date validation, and
  rejection of guest and inactive owner access before database operations.
- A temporary JavaFX check loaded all ten FXML layouts, read the existing customer,
  opened the payment form from its actual row button, and canceled it.
- Form checks verified partial/full/decimal previews using in-memory display
  values, invalid input, missing dates, a zero-balance configuration guard, and
  Cancel. These display values were never inserted into MySQL.
- A save attempt used a bound, non-listening endpoint in a temporary configuration
  copy to verify the exact save-error message and disabled saving controls. The
  original credential file was unchanged. Before/after customer balances and
  customer, utang, and payment counts matched. The form was visually checked at
  520 x 540, then the temporary check files and credential copy were removed.

No zero-balance customer existed for the actual row-button check, so that route
remains a manual check despite the tested zero-balance rules/form guard. Successful
MySQL payment commits, post-save refresh, simultaneous database payments, rollback
after a partial write, and persistence after restart have not been executed by
the agent. No seed data or database test records were created. JUnit calculations
do not establish database concurrency or rollback behavior. B4 workbook result
recording remains reserved for the separately approved testing task.

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
