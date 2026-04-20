# Corporate Session Wallet Design

Date: 2026-04-20
Repo: `/Users/macbookpro/IdeaProjects/ProsperMentor`
Related docs:
- `/Users/macbookpro/IdeaProjects/ProsperMentor/tasks/corporate-mentorship-platform-implementation-plan.md`
- `/Users/macbookpro/IdeaProjects/ProsperMentor/tasks/mentor-matching-design.md`

## 1. Summary

Corporate billing will move from seat-based subscriptions to a prepaid session wallet model while keeping a single corporate plan record in the backend. A company buys any quantity of sessions at a flat price per session. Paid purchases top up one shared company wallet. Admins then allocate sessions from that shared wallet to individual employees. Allocated sessions are reserved immediately. Employees can use their allocated sessions across any eligible live company program.

This design keeps the existing company subscription and invoice flow as the commercial shell, but replaces seat semantics with session semantics for corporate customers. It also separates company-wide session entitlement management from company-program enrollment, so employee allocation is no longer tied to a single program roster.

## 2. Goals

- Keep a single corporate plan record in the backend as the pricing anchor.
- Support one-time prepaid corporate purchases with a flat price per session.
- Maintain one shared wallet per company that accumulates all paid session purchases.
- Allow admins to allocate, top up, withdraw, and later reallocate employee session balances.
- Consume employee sessions on booking confirmation.
- Return consumed employee balance automatically when a booked session is cancelled.
- Make allocated sessions usable across any live company program.
- Remove program-scoped seat management from the employee allocation surface.
- Preserve existing booking, invoice, and company-program behavior where possible.

## 3. Non-Goals

- No recurring monthly or annual corporate allowance.
- No session expiry logic.
- No separate company wallet per program.
- No volume-tier pricing in phase 1.
- No removal of the `SubscriptionPlan` concept for corporate pricing.
- No replacement of existing company-program enrollment workflows; those will move to the company-program area instead of staying on the current employees page.

## 4. Locked Product Decisions

- Corporate offer remains a single backend corporate plan record.
- Pricing is flat `price per session`.
- Purchases are one-time top-ups, not recurring renewals of an allowance.
- Paid purchases top up the same shared company wallet.
- Sessions remain valid until fully used.
- Allocation to an employee reserves those sessions immediately from the shared wallet.
- Admins can top up the same employee many times.
- Employee balances are company-funded and usable across any live company program.
- Every live company program is eligible by default.
- An allocation can only be reduced down to the number of sessions already consumed.
- Session consumption happens on booking confirmation.
- Cancelled bookings automatically return one session to the employee balance.
- Admins can allocate sessions to any company employee even before program enrollment.

## 5. Recommended Approach

Use the existing corporate subscription model as the commercial wrapper, but introduce a dedicated company wallet and employee allocation model for session accounting.

Why this approach:
- It preserves invoice, plan, and company subscription flows already present in the backend.
- It avoids forcing seat logic to pretend to be session logic.
- It supports auditability through explicit wallet and allocation transactions.
- It keeps the system extensible for future reporting and finance reconciliation.

Rejected alternatives:
- Simple seat-to-session rename: too weak for top-ups, returns, and reallocation history.
- Pure derived-ledger model only: stronger but heavier than needed for the current scope.

## 6. Domain Model

### 6.1 Existing concepts that remain

- `SubscriptionPlan` remains the catalog record for the corporate offer.
- `CompanySubscription` remains the company’s active commercial record.
- Invoices remain the payment mechanism for company purchases.

### 6.2 Existing concepts whose corporate meaning changes

#### `SubscriptionPlan`

For corporate plans:
- `cost` becomes the flat price per session.
- monthly and annual interval choices are no longer used on the frontend for corporate purchases.
- any corporate-facing copy that says seat must be changed to session.

#### `CompanySubscription`

For corporate subscriptions:
- it continues to represent the company’s active paid commercial relationship.
- it should no longer be treated as a seat bucket.
- the company can make repeated top-up purchases against the same active subscription.

`CompanySubscription.seatsPurchased` should not remain the authoritative quantity for corporate entitlements. A migration path is required:
- keep it temporarily for compatibility during rollout.
- introduce new wallet-backed fields and APIs.
- stop using seat count in new corporate logic.

### 6.3 New first-class entities

#### `CompanySessionWallet`

One wallet per active corporate company subscription. Ownership is by `company_subscription_id`, with `company_id` included for query convenience and tenant-scoped reporting.

Fields:
- `id`
- `company_subscription_id`
- `company_id`
- `price_per_session_snapshot`
- `sessions_purchased_total`
- `sessions_allocated_total`
- `sessions_returned_total`
- `sessions_available`
- `version`
- `created_at`
- `updated_at`

Rules:
- `sessions_available` decreases on employee allocation.
- `sessions_available` increases on allocation withdrawal or reallocation return.
- booking confirmation does not change wallet availability because sessions were already reserved at allocation time.

#### `CompanySessionWalletTransaction`

Append-only wallet audit trail.

Fields:
- `id`
- `wallet_id`
- `company_id`
- `transaction_type`
- `quantity`
- `balance_after`
- `reference_type`
- `reference_id`
- `notes`
- `created_by_user_id`
- `created_at`

Transaction types:
- `PURCHASE`
- `ALLOCATION_OUT`
- `ALLOCATION_RETURN`
- `MANUAL_ADJUSTMENT`

#### `EmployeeSessionAllocation`

Current balance snapshot for one employee in one company.

Fields:
- `id`
- `company_id`
- `profile_id`
- `allocated_total`
- `consumed_total`
- `available_balance`
- `last_allocated_at`
- `last_activity_at`
- `created_at`
- `updated_at`
- `version`

Constraints:
- unique on `company_id + profile_id`
- employee must belong to the company

#### `EmployeeSessionAllocationTransaction`

Append-only employee allocation audit trail.

Fields:
- `id`
- `employee_session_allocation_id`
- `company_id`
- `profile_id`
- `transaction_type`
- `quantity`
- `balance_after`
- `reference_type`
- `reference_id`
- `notes`
- `created_by_user_id`
- `created_at`

Transaction types:
- `ALLOCATED`
- `WITHDRAWN`
- `REALLOCATED_IN`
- `REALLOCATED_OUT`
- `BOOKED`
- `BOOKING_CANCELLED_RETURN`
- `MANUAL_ADJUSTMENT`

## 7. Business Logic

### 7.1 Purchase flow

1. Corporate admin chooses the corporate plan.
2. Admin enters a `sessionCount`.
3. Backend creates an invoice using `price_per_session * sessionCount`.
4. On payment success:
   - ensure the company has an active `CompanySubscription`
   - create or load the company session wallet
   - increment `sessions_purchased_total`
   - increment `sessions_available`
   - append `PURCHASE` wallet transaction

### 7.2 Allocation flow

1. Admin selects an employee and allocation quantity.
2. Backend checks:
   - employee belongs to company
   - wallet has enough available sessions
3. In one transaction:
   - decrement wallet `sessions_available`
   - increment wallet `sessions_allocated_total`
   - create wallet transaction `ALLOCATION_OUT`
   - create or update employee allocation snapshot
   - increment employee `allocated_total`
   - increment employee `available_balance`
   - create employee transaction `ALLOCATED`

### 7.3 Top-up flow

Top-up is the same as allocation flow, targeting an existing employee allocation record.

### 7.4 Withdrawal flow

1. Admin chooses quantity to withdraw from an employee.
2. Backend computes:
   - `consumed_total`
   - `available_balance`
   - minimum retained allocation = `consumed_total`
3. Withdrawal cannot reduce total allocated below consumed count.
4. In one transaction:
   - decrement employee `allocated_total`
   - decrement employee `available_balance`
   - create employee transaction `WITHDRAWN`
   - increment wallet `sessions_available`
   - increment wallet `sessions_returned_total`
   - create wallet transaction `ALLOCATION_RETURN`

### 7.5 Reallocation flow

Phase 1 can model reallocation as `withdraw` from employee A plus `allocate` to employee B in one transaction boundary or one application service orchestration. The transaction history must show both sides explicitly.

### 7.6 Booking confirmation flow

When a corporate-funded booking is confirmed:
- validate employee allocation availability
- decrement employee `available_balance`
- increment employee `consumed_total`
- append employee transaction `BOOKED`

The company wallet does not change at booking time because the session was already reserved during allocation.

### 7.7 Booking cancellation flow

When a booked session is cancelled:
- determine whether the consumed session came from an employee company-funded allocation
- increment employee `available_balance`
- decrement employee `consumed_total`
- append employee transaction `BOOKING_CANCELLED_RETURN`

This must be idempotent so repeated cancellation events do not double-return a session.

### 7.8 Program eligibility rule

Every live company program is eligible to use employee company-funded balances automatically. No separate program flag is required in phase 1.

### 7.9 Enrollment independence

Session allocation does not require company-program enrollment. An employee can receive company-funded sessions first and enroll in a company program later.

## 8. Booking and Program Integration

The current booking engine must remain intact. The session wallet model integrates by extending the funding/eligibility check, not by creating a second booking engine.

Required integration behavior:
- booking code must detect whether the session is company-funded
- if company-funded, use employee allocation balance checks
- session records should remain compatible with company-program context already added elsewhere
- company-program enrollment continues to be managed separately from session allocation

UI and workflow consequence:
- the current employees page can no longer be the place where company-program roster management lives
- program enrollment and roster management should move under company program detail or a company-program employees tab

## 9. API Design

All APIs remain under `/api/v1`.

### 9.1 Purchase APIs

Keep the existing company subscription purchase entrypoint but change request semantics:

`POST /api/v1/company-subscriptions`
- replace `seatCount` with `sessionCount`
- accept `billingInterval` temporarily for backward compatibility, but ignore it for corporate purchase math and corporate UI behavior

Response additions:
- wallet summary
- price per session
- purchased session count

### 9.2 Wallet APIs

`GET /api/v1/company-subscriptions/company/{companyId}/wallet`
- returns current wallet summary

`GET /api/v1/company-subscriptions/{companySubscriptionId}/wallet/transactions`
- paginated wallet transaction history

### 9.3 Employee allocation APIs

`GET /api/v1/companies/{companyId}/employee-session-allocations`
- paginated
- filter by search
- filter by balance state if needed later

`POST /api/v1/companies/{companyId}/employee-session-allocations/{profileId}/allocate`
- body includes `quantity`

`POST /api/v1/companies/{companyId}/employee-session-allocations/{profileId}/withdraw`
- body includes `quantity`

Future:
- `POST /api/v1/companies/{companyId}/employee-session-allocations/reallocate`

### 9.4 Backward compatibility

- keep old seat endpoints operational only as long as the frontend still depends on them
- do not expose new corporate UI on old seat semantics
- deprecate seat-specific naming in DTOs and frontend calls as part of rollout

## 10. Frontend Design

### 10.1 Settings > Plans

Current state in `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/settings/index.vue` is seat-based and interval-based. That must change for corporate only.

New behavior:
- show one corporate plan card
- show `price per session`
- allow quantity entry for session purchase
- show computed purchase total
- show wallet summary:
  - purchased
  - allocated
  - available
  - consumed
- show invoice/top-up history

Remove from corporate UI:
- seat count language
- seat plus/minus controls
- billing interval toggle
- upgrade/downgrade language that assumes subscription tiers

### 10.2 Employees page

Current state in `/Users/macbookpro/WebstormProjects/myProsperV2/pages/app/admin/participants.vue` is program-scoped. That must change.

New behavior:
- page remains `Employees`
- remove `Program Context`
- remove `Current Roster`
- list all company employees
- for each employee show:
  - allocated sessions
  - used sessions
  - available sessions
  - last allocation date
- row actions:
  - `Allocate`
  - `Withdraw`
  - later `Reallocate`

This page becomes the company session allocation surface, not a program roster tool.

### 10.3 Company program UI

Because roster management is being removed from the employees page, company-program enrollment must remain accessible elsewhere:
- add a dedicated `Employees` tab inside each company program detail page

This is required to avoid accidental feature loss.

## 11. Concurrency and Consistency

The wallet and allocation flows are multi-user financial operations and must be transaction-safe.

Required protections:
- optimistic locking with `version` on wallet and allocation snapshots
- service-layer transactions around purchase application, allocate, withdraw, reallocate, booking consume, and cancellation return
- idempotency guard for booking cancellation return
- validation that wallet availability cannot go negative
- validation that employee available balance cannot go negative
- validation that withdrawal cannot take allocated total below consumed total

Race conditions to cover:
- two admins allocating from the wallet at the same time
- top-up and withdrawal against the same employee at the same time
- booking confirmation racing with admin withdrawal
- repeated cancellation events returning the same session twice

## 12. Reporting

Phase 1 reporting should support:
- company purchased sessions
- company available sessions
- company allocated sessions
- company consumed sessions
- employee allocated sessions
- employee consumed sessions
- employee available sessions
- wallet transaction history
- allocation transaction history

Out of scope for this design:
- revenue recognition reporting
- finance export formats
- advanced utilization forecasting

## 13. Migration Strategy

### 13.1 Schema rollout

Use additive Flyway migrations only.

Sequence:
1. add wallet tables
2. add employee allocation tables
3. add indexes and version columns
4. add compatibility columns if needed

### 13.2 Application rollout

1. introduce new wallet/allocation domain and services
2. expose new APIs
3. update frontend corporate settings UI
4. update frontend employees UI
5. move program roster management to company-program area
6. update booking logic to consume allocation balances
7. deprecate old seat paths

### 13.3 Data migration

If active corporate customers already exist on seat semantics:
- do not attempt a blind automatic seat-to-session conversion
- require an explicit business mapping decision or manual backfill strategy
- legacy records can remain historically valid while new purchases move to wallet semantics

## 14. Error Handling

Return explicit business errors for:
- insufficient wallet balance
- insufficient employee available balance
- invalid employee-company association
- invalid withdrawal quantity
- duplicate consume
- duplicate cancellation return
- inactive or missing company subscription

Frontend behavior:
- show clear inline errors on allocation and withdrawal actions
- never silently clamp requested quantities on submit
- always refresh wallet and employee balances after mutation

## 15. Testing Strategy

Prioritize automated tests for:
- invoice payment applying sessions into the wallet
- employee allocation reducing wallet balance
- withdrawal returning unused balance to the wallet
- top-up on an existing employee allocation
- booking confirmation consuming employee balance
- cancellation restoring employee balance
- concurrency around simultaneous allocations
- company scoping and authorization
- frontend calculation and validation for purchase totals
- frontend employee allocation flows

## 16. Open Follow-Up Work

Not required to ship the model, but likely next:
- explicit reallocation UI
- admin manual adjustment tools with audit reason
- exported wallet/allocation reports
- low-balance alerts for companies

## 17. Implementation Recommendation

Implement this as a focused billing-and-allocation slice first:
- backend wallet and allocation model
- corporate settings purchase UI
- employees allocation UI
- booking integration
- program roster relocation

Do not mix this with unrelated subscription refactors. The system already has enough live billing behavior that the safest path is additive integration around a single corporate plan plus wallet-backed allocations.
