# TindaTrack

A desktop sari-sari store management application using Java, JavaFX with FXML,
Maven, and (in later tasks) MySQL.

## Current scope: Day 1, Task 1

The Maven project and JavaFX entry point are prepared. Running the application
starts JavaFX, prints a setup message, and exits. No window is displayed yet.
The first FXML window and CSS styling belong to Task 2.

Authentication, inventory, expiration alerts, customer credit, SQL scripts, and
database connections have not been implemented.

## Requirements

- JDK 21, including the `javac` compiler.
- Maven 3.9 or newer.
- Windows desktop session for the JavaFX startup check.
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
`-- src/
    |-- main/
    |   |-- java/tindatrack/
    |   |   |-- TindaTrackApplication.java
    |   |   |-- authentication/package-info.java
    |   |   |-- inventory/package-info.java
    |   |   |-- guest/package-info.java
    |   |   |-- credit/package-info.java
    |   |   `-- core/package-info.java
    |   `-- resources/tindatrack/
    |       |-- fxml/.gitkeep
    |       `-- css/.gitkeep
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
- `mvn javafx:run` starts the configured JavaFX entry point. It should print
  `TindaTrack setup successful: JavaFX is running.`, then exit successfully.
  No window should open during Task 1.

Look for `BUILD SUCCESS` after each command. Use `mvn javafx:run` to run this
setup; the generated JAR is not a standalone application with bundled JavaFX.

### Simple checklist

- [ ] The terminal is in the folder containing `pom.xml`.
- [ ] Java, `javac`, and Maven all use Java 21.
- [ ] `mvn compile` ends with `BUILD SUCCESS`.
- [ ] `mvn verify` ends with `BUILD SUCCESS`.
- [ ] `mvn javafx:run` prints the setup message and ends with `BUILD SUCCESS`.
- [ ] No application window appears, as expected for Task 1.

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
on its application thread. For Task 1, `start` prints the setup message and calls
`Platform.exit()` to shut down cleanly. Task 2 will replace that temporary check
with the first FXML window.

`pom.xml` declares JavaFX controls and FXML dependencies, compiles for Java 21,
and tells the JavaFX Maven plugin which class to launch. This is a simple
non-modular project, so no `module-info.java` is needed at this stage.

## Local credentials

No credentials or database configuration are needed for Task 1. `.gitignore`
excludes build output, IDE workspace files, `.env` files, and the listed local
database property files. Safe `.env.example` files are allowed. Actual database
configuration and JDBC will be added only in Task 4.

## Manual Git workflow

Git commands are managed manually by the project owner. Suggested Task 1 commit
message: `Set up Java 21 JavaFX Maven project structure`.

Stop after testing Task 1. Task 2 requires explicit approval.
