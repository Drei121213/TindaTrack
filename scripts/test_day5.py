"""Run credit regressions and optional navigation/layout checks on disposable MySQL.

Requires Java 21, Maven, MySQL Server 8, and Python 3. No production configuration
is read or changed. This script deliberately creates test fixtures in its own
server; run it only when that test-data generation is approved.
"""

from datetime import datetime, timedelta, timezone
import argparse
from pathlib import Path
import json
import os
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET


PROJECT = Path(__file__).resolve().parents[1]
TARGET = PROJECT / "target"
REPORT = PROJECT / "docs" / "testing" / "day5-results.json"
MYSQL_BIN = Path(os.environ.get("TINDATRACK_MYSQL_BIN", r"C:\Program Files\MySQL\MySQL Server 8.0\bin"))
JAVA_HOME = Path(os.environ.get("JAVA_HOME", r"C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"))
MAVEN = shutil.which("mvn.cmd") or shutil.which("mvn")
HIDDEN = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def run(command, *, cwd=PROJECT, env=None, timeout=120, input_text=None):
    result = subprocess.run(command, cwd=cwd, env=env, input=input_text,
                            text=True, encoding="utf-8", errors="replace",
                            capture_output=True, timeout=timeout, creationflags=HIDDEN)
    return result


def junit_results(path):
    suite = ET.parse(path).getroot()
    results = []
    for test in suite.findall("testcase"):
        problem = test.find("failure")
        if problem is None:
            problem = test.find("error")
        skipped = test.find("skipped")
        results.append({"id": test.attrib["name"],
                        "status": "Failed" if problem is not None else "Not Run" if skipped is not None else "Pass",
                        "actual": (problem.attrib.get("message", "Test failed") if problem is not None
                                   else "Skipped; isolated launcher required." if skipped is not None
                                   else "JUnit assertions passed against the verified isolated MySQL instance."),
                        "method": "JUnit / MySQL"})
    return results


def main():
    global REPORT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--navigation-checks", action="store_true", help="Also check Day 6 owner navigation and state.")
    parser.add_argument("--layout-checks", action="store_true", help="Also render Day 6 layouts at two desktop sizes; includes navigation checks.")
    parser.add_argument("--validation-checks", action="store_true", help="Also check Day 6 validation and recovery; includes navigation checks.")
    parser.add_argument("--integration-checks", action="store_true", help="Run Day 6 final integration/B4 checks, including the approved 1000-product benchmark.")
    parser.add_argument("--dashboard-checks", action="store_true", help="Run Day 7 SB02 totals, refresh and recovery checks plus existing integration checks.")
    parser.add_argument("--report", help="Project-local JSON report path; defaults to the matching day.")
    args = parser.parse_args()
    if args.dashboard_checks:
        args.integration_checks = True
    if args.integration_checks:
        args.validation_checks = True
        args.layout_checks = True
    if args.layout_checks or args.validation_checks:
        args.navigation_checks = True
    report_name = args.report or ("docs/testing/day7-task2-results.json" if args.dashboard_checks else
                                  "docs/testing/day6-task5-results.json" if args.integration_checks else
                                  "docs/testing/day6-task4-results.json" if args.validation_checks else
                                  "docs/testing/day6-task3-results.json" if args.layout_checks else
                                  "docs/testing/day6-task2-results.json" if args.navigation_checks else "docs/testing/day5-results.json")
    REPORT = (PROJECT / report_name).resolve()
    if not REPORT.is_relative_to(PROJECT):
        raise SystemExit("The test report must stay inside the project.")
    if MAVEN is None or not (JAVA_HOME / "bin" / "java.exe").is_file():
        raise SystemExit("Java and Maven must be installed; select Java 21 with JAVA_HOME.")
    for executable in ("mysqld.exe", "mysql.exe", "mysqladmin.exe"):
        if not (MYSQL_BIN / executable).is_file():
            raise SystemExit("Set TINDATRACK_MYSQL_BIN to the MySQL Server 8 bin directory.")
    env = os.environ.copy()
    env["JAVA_HOME"] = str(JAVA_HOME)
    env["PATH"] = str(JAVA_HOME / "bin") + os.pathsep + env.get("PATH", "")
    env.pop("TINDATRACK_ISOLATED_TESTS", None)
    print("Building and running ordinary tests...", flush=True)
    build = run([MAVEN, "-B", "-o", "verify"], env=env)
    print(build.stdout, flush=True)
    if build.returncode:
        print(build.stderr)
        raise SystemExit("Build failed; no temporary database was started.")

    TARGET.mkdir(exist_ok=True)
    report = {"executedAt": datetime.now(timezone(timedelta(hours=8))).isoformat(),
              "scope": "Day 7 Task 2 SB02 fix and core regression checks" if args.dashboard_checks else
                       "Day 6 Task 5 final integration and B4 checks" if args.integration_checks else
                       "Day 6 Task 4 validation and recovery checks" if args.validation_checks else
                       "Day 6 Task 3 UI and regression checks" if args.layout_checks else
                       "Day 6 Task 2 navigation regression" if args.navigation_checks else "Day 5 acceptance",
              "environment": "Java 21 / JavaFX / disposable MySQL 8 inside project target",
              "productionDatabaseTouched": False,
              "seedApproval": "User approved isolated test data for Day 5 Task 5.",
              "ui": [], "database": [], "unit": [], "layout": [], "authentication": []}
    if args.integration_checks:
        report["benchmarkApproval"] = "User explicitly approved 1000 temporary products for the Day 6 NFR-02 benchmark."
    if REPORT.is_file():
        previous = json.loads(REPORT.read_text(encoding="utf-8"))
        history = previous.get("previousFailures", []) + [r for r in previous.get("ui", []) + previous.get("layout", []) + previous.get("authentication", []) if r["status"] == "Failed"]
        report["previousFailures"] = list({r["id"]: r for r in history}.values())
    for path in (TARGET / "surefire-reports").glob("TEST-*.xml"):
        if path.name not in ("TEST-tindatrack.credit.CreditDatabaseTest.xml", "TEST-tindatrack.core.DashboardDatabaseTest.xml"):
            suite = ET.parse(path).getroot()
            report["unit"].append({"suite": suite.attrib["name"],
                                   "tests": int(suite.attrib["tests"]),
                                   "failures": int(suite.attrib.get("failures", 0)),
                                   "errors": int(suite.attrib.get("errors", 0)),
                                   "skipped": int(suite.attrib.get("skipped", 0))})
    failed = False
    with tempfile.TemporaryDirectory(prefix="day5-mysql-", dir=TARGET) as temp:
        root = Path(temp).resolve()
        if not root.is_relative_to(TARGET.resolve()):
            raise RuntimeError("Temporary server must stay inside project target.")
        data = root / "data"
        data.mkdir()
        log = root / "mysql.log"
        password = secrets.token_hex(24)
        bootstrap = root / "bootstrap.sql"
        bootstrap.write_text("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + password + "';\n", encoding="utf-8")
        initialized = run([str(MYSQL_BIN / "mysqld.exe"), "--no-defaults", "--initialize-insecure",
                           "--basedir=" + str(MYSQL_BIN.parent), "--datadir=" + str(data),
                           "--log-error=" + str(log)])
        if initialized.returncode:
            raise RuntimeError("Temporary MySQL initialization failed; see the MySQL installation.")
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
        server = subprocess.Popen([str(MYSQL_BIN / "mysqld.exe"), "--no-defaults",
                                   "--basedir=" + str(MYSQL_BIN.parent), "--datadir=" + str(data),
                                   "--bind-address=127.0.0.1", "--port=" + str(port), "--mysqlx=OFF",
                                   "--skip-log-bin", "--innodb-buffer-pool-size=32M", "--max-connections=30",
                                   "--init-file=" + str(bootstrap), "--log-error=" + str(log)],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=HIDDEN)
        client_env = env.copy()
        client_env["MYSQL_PWD"] = password
        client = ["--no-defaults", "--protocol=TCP", "--host=127.0.0.1", "--port=" + str(port), "--user=root"]

        def sql(text):
            response = run([str(MYSQL_BIN / "mysql.exe"), *client, "--default-character-set=utf8mb4"],
                           env=client_env, input_text=text)
            if response.returncode:
                raise RuntimeError("Isolated SQL setup failed: " + response.stderr)

        try:
            for _ in range(100):
                if server.poll() is not None:
                    raise RuntimeError("Temporary MySQL exited during startup.")
                ping = run([str(MYSQL_BIN / "mysqladmin.exe"), *client, "ping"], env=client_env, timeout=5)
                if ping.returncode == 0:
                    break
                time.sleep(0.1)
            else:
                raise RuntimeError("Temporary MySQL did not become ready.")
            sql((PROJECT / "database" / "schema.sql").read_text(encoding="utf-8"))
            config = root / "config"
            config.mkdir()
            (config / "database.properties").write_text(
                f"db.url=jdbc:mysql://127.0.0.1:{port}/tindatrack\ndb.username=root\ndb.password={password}\n",
                encoding="utf-8")
            test_env = env.copy()
            test_env.update(TINDATRACK_ISOLATED_TESTS="true", TINDATRACK_TEST_DATADIR=str(data),
                            TINDATRACK_TEST_TARGET=str(TARGET.resolve()))
            test_env["TINDATRACK_NAVIGATION_CHECKS"] = str(args.navigation_checks).lower()
            test_env["TINDATRACK_VALIDATION_CHECKS"] = str(args.validation_checks).lower()
            test_env["TINDATRACK_INTEGRATION_CHECKS"] = str(args.integration_checks).lower()
            test_env["TINDATRACK_DASHBOARD_CHECKS"] = str(args.dashboard_checks).lower()
            print("Running isolated database tests...", flush=True)
            database = run([MAVEN, "-B", "-o", "-Dtest=CreditDatabaseTest,DashboardDatabaseTest",
                            "-Dtindatrack.test.workingDirectory=" + str(root), "test"], env=test_env)
            print(database.stdout, flush=True)
            if database.returncode:
                print(database.stderr)
                failed = True
            report["database"] = junit_results(TARGET / "surefire-reports" / "TEST-tindatrack.credit.CreditDatabaseTest.xml")
            report["database"] += junit_results(TARGET / "surefire-reports" / "TEST-tindatrack.core.DashboardDatabaseTest.xml")

            # Only the approved B4 fixtures, on the isolated instance. Never import B4_seed_data.sql.
            sql("""USE tindatrack;
                DELETE FROM payments; DELETE FROM credit_transactions; DELETE FROM customers;
                INSERT INTO customers (name,balance) VALUES ('Aling Nena',250.00),('Kuya Jun',0.00);
                INSERT INTO credit_transactions (customer_id,items,amount,transaction_date)
                SELECT customer_id,'Opening balance',250.00,'2026-09-28 08:00:00' FROM customers WHERE name='Aling Nena';
                DELETE FROM products;
                INSERT INTO products (name,price,stock_qty,expiration_date) VALUES
                ('Lucky Me Pancit Canton',15.00,40,DATE_ADD(CURDATE(), INTERVAL 5 DAY)),
                ('Coke Mismo 8oz',12.00,24,DATE_ADD(CURDATE(), INTERVAL 5 DAY)),
                ('Expired test item',12.00,24,DATE_SUB(CURDATE(), INTERVAL 1 DAY));
                """)
            fx = os.pathsep.join(str(p.resolve()) for p in (PROJECT / ".m2/repository/org/openjfx").rglob("*21.0.12-win.jar"))
            jdbc = next((PROJECT / ".m2/repository/com/mysql/mysql-connector-j").rglob("*.jar")).resolve()
            classpath = os.pathsep.join(str(p) for p in (TARGET / "classes", TARGET / "test-classes", jdbc))
            command = [str(JAVA_HOME / "bin" / "java.exe"), "--module-path", fx,
                       "--add-modules", "javafx.controls,javafx.fxml", "-cp", classpath,
                       "tindatrack.credit.Day5UiCheck"]
            if args.validation_checks:
                print("Running authentication controller checks with fake DAO responses...", flush=True)
                authentication = run([*command[:-1], "tindatrack.authentication.Day6AuthenticationCheck"], env=test_env, timeout=90)
                print(authentication.stdout, flush=True)
                for line in authentication.stdout.splitlines():
                    if line.startswith("AUTH\t"):
                        _, test_id, status, actual = line.split("\t", 3)
                        report["authentication"].append({"id": test_id, "status": status, "actual": actual,
                                                         "method": "JavaFX / fake DAO / no database"})
                if authentication.returncode:
                    print(authentication.stderr, flush=True)
                    failed = True
            if args.layout_checks:
                print("Rendering and checking JavaFX layouts...", flush=True)
                layout = run([*command[:-1], "tindatrack.core.Day6LayoutCheck"], env=test_env, timeout=90)
                print(layout.stdout, flush=True)
                for line in layout.stdout.splitlines():
                    if line.startswith("LAYOUT\t"):
                        _, test_id, status, actual = line.split("\t", 3)
                        report["layout"].append({"id": test_id, "status": status, "actual": actual,
                                                 "method": "Offscreen JavaFX / in-memory rows / no database"})
                if layout.returncode:
                    print(layout.stderr, flush=True)
                    failed = True
            for phase in ("first", "reopen"):
                print("Running JavaFX checks: " + phase, flush=True)
                ui = run([*command, phase], cwd=root, env=test_env, timeout=120)
                print(ui.stdout, flush=True)
                for line in ui.stdout.splitlines():
                    if line.startswith("DAY5\t"):
                        _, test_id, status, actual = line.split("\t", 3)
                        report["ui"].append({"id": test_id, "status": status, "actual": actual,
                                             "method": "Automated JavaFX / isolated MySQL"})
                        if status == "Failed":
                            failed = True
                if ui.returncode:
                    print(ui.stderr)
                    failed = True
                    break
        finally:
            shutdown = run([str(MYSQL_BIN / "mysqladmin.exe"), *client, "shutdown"], env=client_env, timeout=15)
            try:
                server.wait(timeout=15)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=5)
            # TemporaryDirectory only removes this verified project-local test directory.
            if not root.resolve().is_relative_to(TARGET.resolve()):
                raise RuntimeError("Refusing to clean an unexpected directory.")
            REPORT.parent.mkdir(parents=True, exist_ok=True)
            REPORT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Isolated server removed. Results: " + str(REPORT), flush=True)
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
