# TindaTrack: Day 6 final integration review

Executed: 2026-11-28T01:20:15.782304+08:00. Task 5 is a testing/review task. Application
source, FXML, CSS, dependencies, schema and business rules were unchanged.

## Result

B4 results: **67 cases: 66 Pass, 1 Failed,
0 Blocked, 0 Not Run**.
The application is **not fully conformant to SB02**. Successful authentication is
verified separately from the missing Dashboard summary cards.

| Suite | Result |
| --- | --- |
| Ordinary JUnit tests | 74 executed; no failures in the recorded suites |
| Real MySQL JUnit tests | 16 Pass |
| Authentication FXML/controller checks with fake DAO responses | 8 Pass |
| JavaFX integration/regression checks | 82 Pass, 1 Failed |
| Offscreen desktop/form layout checks | 20 Pass |
| Target-user manual acceptance | Not Run |

The raw JavaFX checks include supporting checks outside B4, so their count is
larger than the B4 count. B4 Login_SunnyDay is conservatively Failed because it
requires SB02, despite the lower-level credential/owner-transition check passing.
The integration command exits with code 1 while that known conformance failure
remains; Maven compilation and ordinary unit verification passed.

## Required integration flows

| Flow | Status | Evidence |
| --- | --- | --- |
| A: owner authentication/navigation/logout | Failed against full SB02; functional authentication/navigation passed | Actual form-created maggie account logged in; all owner routes retained the session; logout invalidated old access. SB02 cards are missing. |
| B: inventory add/search/update/delete | Pass | Skyflakes 7.50/60 added; Coke changed to 13.00/30; confirmed Safeguard deletion removed exactly one selected row; cancel/error paths preserved data. |
| C: expiration integration | Pass | Egg +5 days included; Nescafe +20 excluded at 7 days and included at 30; Bread -2 days red; nearest-date order and NULL exclusion verified; past-date Yes/No and invalid dates verified. |
| D: customer credit lifecycle | Pass | Existing and new customers saved; one continuous customer flow reconciled 250 -> 280 after utang -> 230 after partial payment -> 0 after full payment, including history. |
| E: guest access | Pass | Product search/price/stock and customer balances were read-only. Product add/update/delete and credit/payment write services rejected guest sessions; exit cleared the session. |

## Completed integrations and UI improvements

- One main Stage and one owner session across the shared sidebar; cached searches,
  selections and expiration range remain during a login and reset after logout.
- Database-backed screens reload on return; obsolete background results are ignored.
- Registration, login, inventory, expiration, credit, payments and guest views work
  together using the existing five-table schema and package structure.
- Common green styling, readable controls, peso amounts with two decimals,
  aligned numeric columns, explicit expiration text/colors, fixed form actions
  and scrollable fields were delivered in Tasks 2-4.
- Busy guards, safe connection errors and signed-out Retry Login recovery from
  Task 4 passed their regressions. No production-code fix was made in Task 5.

## Issues fixed during Day 6

- Task 2 corrected a null-selection failure during Credit refresh and consolidated
  owner navigation. State retention, refresh-on-return and obsolete-load handling
  passed the final regressions.
- Task 3 corrected crowded controls and the small registration heading, and kept
  form action buttons visible while validation fields scroll.
- Task 4 added explicit busy/completed guards, ignored departed-login callbacks,
  distinguished error feedback, and replaced silent logout-screen failure with
  signed-out Retry Login recovery. Final fault-injection checks passed.
- Task 5 added test coverage and exposed the SB02 gap. It made no application fix;
  original expected results and business behavior were preserved.

## Remaining issue and proposed correction

**B4 Login_SunnyDay / SB02 Dashboard: Failed.** The current owner-home FXML and
controller implement a welcome screen and sidebar, but no Total Products,
Total Credits or Expiring Soon summary cards. The storyboard explicitly requires
these cards. This is an existing incomplete feature, not a credential failure.

Proposed follow-up, requiring approval before implementation: extend the existing
Dashboard with the three cards and background read-only totals, refresh on return,
and test the displayed values against the database. Define the expiration-card
range consistently with the existing expiration screen. Do not create another
dashboard or change the schema. Until that work passes, do not describe all B4
cases or all RTM requirements as complete.

PD01's Core Validator role is currently fulfilled by controller validation and
CreditRules rather than a standalone shared Validator class. The five packages
and five database tables/relationships follow the existing implementation.
This structural difference is documented; no architecture rewrite was attempted.

## Nonfunctional checks and practical limits

| RTM requirement | Status | Evidence/limit |
| --- | --- | --- |
| NFR-01: few clicks and readable UI | Not Run for full human acceptance | Shared sidebar and 20 layout checks passed; a formal target-user click-count/readability session is still needed. |
| NFR-02: search within 2 seconds at 1000 products | Pass on this host | Exactly 1000 products; search a; query-to-JavaFX-layout milliseconds for three runs: [34, 33, 34]. Local test host only; no low-spec hardware certification. |
| NFR-03: hashed passwords and guest restrictions | Pass for executed checks | Actual registration stored a verified hash; logic-level owner writes rejected guest sessions. |
| NFR-04: immediate persistence | Pass for executed restart/rollback checks | Saved purchases/payments survived separate JVM launches; failed updates rolled back; physical power-loss behavior was not tested. |
| NFR-05: two decimals and nonnegative balances | Pass for executed checks | Peso cell formatting, partial/full/overpayment, concurrent payments and exact balance/history assertions passed. |

The benchmark creates exactly 1000 total temporary product rows and measures
three searches through JavaFX layout; the benchmark rows are removed afterward.
It uses a shown but transparent automated test window, and does not measure human
perception or certify low-specification hardware or every Windows scaling setting.

## Evidence and source preservation

- B4 reference: `C:\Users\andrei\Downloads\CMSC 128 TindaTrack\outputs\01a1e427-b7d1-79d1-b9b2-c6a35648cfdd\B4_Test_Cases_PR6_Updated.xlsx` (SHA-256 `739ba0fb084c7a3a916d1c571b96b4ac9bf866272eb4142c0e746acf5b744ae1`). The local Updated copy is the
  available B4 reference; its original steps, data and expected results are copied
  verbatim into day6-b4-coverage.json. No source workbook cells were changed.
- RTM: CMSC 128 RTM - Dadivalos PR5.xlsx, including SID-01 through SID-11,
  NFR-01 through NFR-05, Part B storyboards/use cases, and Part D diagrams.
- Raw execution evidence: day6-task5-results.json. Original method labels in B4
  are preserved; actual execution here was automated JavaFX/JUnit, not manual Pass.
- The database-unavailable cases use an unreachable isolated endpoint rather than
  stopping the installed MySQL service. Relative expiration fixtures use the
  execution day; fixed dates such as 2027-06-30 and 2026-01-01 are retained.
- Fixtures used only the approved disposable server inside ignored target. The
  benchmark expansion was explicitly approved. B4_seed_data.sql was not imported.
  No configured production credentials, records or tables were used for writes.
- The temporary server/credentials were removed; the compiled login-layout file
  used in fault injection was restored. Earlier Day 5/Day 6 reports were preserved.

## Day 7 recommendations and manual acceptance steps

1. Approve and complete the SB02 card follow-up, then rerun this suite and validate
   all three displayed totals against a separate test database.
2. With an approved test database, run mvn javafx:run. Create an owner only if the
   test users table is empty, sign in, visit every sidebar screen, and log out.
   Confirm a fresh login and Guest Mode exit reset the session/view state.
3. In Inventory, add Skyflakes Pack / 7.50 / 60, search it, edit it, cancel Delete,
   then confirm Delete. Verify only the selected test row changed in Workbench.
4. Inspect Egg +5 days, Nescafe +20 days, expired Bread and a NULL-expiry item.
   Switch 7/14/30-day ranges. Try invalid and past dates; verify No and Yes.
5. Record a 30.00 utang, partial and full payments on a test customer. Check the
   running balance, zero balance and full history. Cancel invalid/overpayments.
6. Log out and use Guest Products and Credit tabs. Verify view-only information,
   lack of owner controls, and return to Login through Exit Guest Mode.
7. Have the target user inspect 1024x768 and 1366x768 layouts with their own display
   scaling, keyboard navigation, readability and click counts. Record acceptance
   explicitly; it was not supplied by the automated checks.
8. Repeat performance on the intended low-specification machine. Test a clean
   installation, MySQL configuration and database backup/restore during Day 7.
   Prepare release/install/demo/submission artifacts only after acceptance.

## B4 case-by-case coverage

The JSON companion includes original steps, test data, expected results, actual
evidence and the execution method for every row. No expected result was weakened.

| B4 test ID | Story | Status |
| --- | --- | --- |
| Login_SunnyDay | SID-01 | Failed |
| Login_EmptyFields | SID-01 | Pass |
| Login_WrongPassword | SID-01 | Pass |
| Login_UnknownUsername | SID-01 | Pass |
| Login_DatabaseError | SID-01 | Pass |
| AddProduct_SunnyDay | SID-02 | Pass |
| AddProduct_EmptyField | SID-02 | Pass |
| AddProduct_InvalidPrice | SID-02 | Pass |
| AddProduct_InvalidStock | SID-02 | Pass |
| AddProduct_DuplicateName | SID-02 | Pass |
| AddProduct_Cancel | SID-02 | Pass |
| AddProduct_DatabaseError | SID-02 | Pass |
| SearchProduct_SunnyDay | SID-03 | Pass |
| SearchProduct_NotCaseSensitive | SID-03 | Pass |
| SearchProduct_EmptyBox | SID-03 | Pass |
| SearchProduct_NoMatch | SID-03 | Pass |
| SearchProduct_Speed | SID-03 | Pass |
| SearchProduct_DatabaseError | SID-03 | Pass |
| DeleteProduct_SunnyDay | SID-04 | Pass |
| DeleteProduct_Cancel | SID-04 | Pass |
| DeleteProduct_CreditRecordsUnchanged | SID-04 | Pass |
| DeleteProduct_DatabaseError | SID-04 | Pass |
| UpdateProduct_SunnyDay | SID-05 | Pass |
| UpdateProduct_EmptyField | SID-05 | Pass |
| UpdateProduct_InvalidValues | SID-05 | Pass |
| UpdateProduct_Cancel | SID-05 | Pass |
| UpdateProduct_DatabaseError | SID-05 | Pass |
| ViewExpiring_SunnyDay | SID-06 | Pass |
| ViewExpiring_ChangeRange | SID-06 | Pass |
| ViewExpiring_ExpiredHighlighted | SID-06 | Pass |
| ViewExpiring_NoExpiryNotListed | SID-06 | Pass |
| ViewExpiring_NoProducts | SID-06 | Pass |
| ViewExpiring_DatabaseError | SID-06 | Pass |
| SetExpiration_SunnyDay | SID-07 | Pass |
| SetExpiration_NoExpiration | SID-07 | Pass |
| SetExpiration_InvalidDate | SID-07 | Pass |
| SetExpiration_PastDateYes | SID-07 | Pass |
| SetExpiration_PastDateNo | SID-07 | Pass |
| RecordUtang_SunnyDay | SID-08 | Pass |
| RecordUtang_NewCustomer | SID-08 | Pass |
| RecordUtang_EmptyFields | SID-08 | Pass |
| RecordUtang_InvalidAmount | SID-08 | Pass |
| RecordUtang_Cancel | SID-08 | Pass |
| RecordUtang_DatabaseError | SID-08 | Pass |
| RecordUtang_SavedImmediately | SID-08 | Pass |
| RecordPayment_PartialPayment | SID-09 | Pass |
| RecordPayment_FullPayment | SID-09 | Pass |
| RecordPayment_EmptyOrZero | SID-09 | Pass |
| RecordPayment_MoreThanBalance | SID-09 | Pass |
| RecordPayment_NoBalance | SID-09 | Pass |
| RecordPayment_Cancel | SID-09 | Pass |
| RecordPayment_DatabaseError | SID-09 | Pass |
| RecordPayment_TwoDecimals | SID-09 | Pass |
| GuestMode_SunnyDay | SID-10 | Pass |
| GuestMode_SearchProduct | SID-10 | Pass |
| GuestMode_SearchNoMatch | SID-10 | Pass |
| GuestMode_CreditTab | SID-10 | Pass |
| GuestMode_ExitGuestMode | SID-10 | Pass |
| GuestMode_NoOwnerFunctions | SID-10 | Pass |
| GuestMode_DatabaseError | SID-10 | Pass |
| CreateAccount_SunnyDay | SID-11 | Pass |
| CreateAccount_EmptyField | SID-11 | Pass |
| CreateAccount_ShortPassword | SID-11 | Pass |
| CreateAccount_PasswordMismatch | SID-11 | Pass |
| CreateAccount_Cancel | SID-11 | Pass |
| CreateAccount_DatabaseError | SID-11 | Pass |
| CreateAccount_PasswordHashed | SID-11 | Pass |
