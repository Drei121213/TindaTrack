"""Map Day 7 Task 2 evidence to original B4 definitions without changing old reports."""

from collections import Counter
from pathlib import Path
import hashlib
import json
import sys

# Reuse the read-only parser without generating a project-local Python cache.
sys.dont_write_bytecode = True
from report_day6 import specifications


def main():
    project = Path(__file__).resolve().parents[1]
    output = project / "docs/testing"
    result = json.loads((output / "day7-task2-results.json").read_text(encoding="utf-8"))
    sources = list((project.parent / "outputs").rglob("B4_Test_Cases_PR6_Updated.xlsx"))
    if len(sources) != 1:
        raise SystemExit("Expected one local B4_Test_Cases_PR6_Updated.xlsx reference.")
    source = sources[0]
    before = hashlib.sha256(source.read_bytes()).hexdigest()
    checks = {row["id"]: row for row in result["ui"]}
    cases = specifications(source)
    for row in cases:
        test_id = row["specification"]["A"]
        evidence = checks.get(test_id)
        row.update(id=test_id, status=evidence["status"] if evidence else "Not Run",
                   actual=evidence["actual"] if evidence else "No matching executed check.",
                   method=evidence["method"] if evidence else "Not Run",
                   evidence=[test_id] if evidence else [])
        if test_id == "Login_SunnyDay":
            dashboard = checks.get("SB02_DashboardConformance")
            row["evidence"].append("SB02_DashboardConformance")
            if dashboard:
                row["actual"] += " " + dashboard["actual"]
            if not dashboard or dashboard["status"] != "Pass":
                row["status"] = dashboard["status"] if dashboard else "Not Run"
        if "DatabaseError" in test_id:
            row["executionNote"] = "An unreachable isolated endpoint simulated unavailability; the installed MySQL service was not stopped."
    if before != hashlib.sha256(source.read_bytes()).hexdigest():
        raise SystemExit("The B4 reference changed during reporting.")
    audit = {"executedAt": result["executedAt"], "source": str(source), "sourceSha256": before,
             "referenceModified": False,
             "specificationColumns": {"A": "ID", "B": "Story", "C": "Use case", "D": "Scenario",
                                      "E": "Case", "F": "Flow", "G": "Steps", "H": "Data", "I": "Expected result",
                                      "L": "Original type", "M": "Original method"},
             "counts": dict(Counter(row["status"] for row in cases)), "cases": cases,
             "manualAcceptance": "Not Run; Windows installer and clean-machine testing belong to later tasks."}
    path = output / "day7-task2-b4-coverage.json"
    path.write_text(json.dumps(audit, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("B4 coverage: " + str(audit["counts"]))
    print("Saved: " + str(path))


if __name__ == "__main__":
    main()
