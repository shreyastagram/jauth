# AGENTS.md — Operating Rules (READ BEFORE EVERY WORK SESSION)

This is a **live, production application**. The prime directive is: **no unknown havoc.**
If something might go wrong, we must know about it *before* it does — and decide deliberately.

These rules apply to all work on the phone-signup feature (and any related auth/login/registration
work) across the three repos: `jauth`, `noefix`, `renfi`.

## The Pre-Flight Protocol (mandatory before ANY code change)

Before editing a single file for a step, I must:

1. **Re-validate against live code, not memory or the plan.**
   Re-read the actual current source of every file the step touches. Confirm every assumption in
   the plan still matches reality (signatures, callers, constraints, indexes). Code may have
   changed; the plan may be stale. Trust the code.

2. **Map the blast radius.**
   Identify every caller / dependent of what I'm about to change (grep for usages). Explicitly ask:
   *who else relies on this behaviour?* Existing users, active sessions, other endpoints, the other
   two repos, the DB.

3. **FLAG-BEFORE-PROCEED gate.** STOP and surface to the user *before* changing anything if the
   change could:
   - affect existing users, their data, or their logged-in sessions;
   - invalidate or change the meaning of already-issued tokens;
   - alter a DB schema/constraint/index on a table with existing rows;
   - break or change behaviour of an endpoint that is already in use;
   - have a blast radius I cannot fully enumerate.
   The flag must state: **what changes, who/what is affected, the risk, and the mitigation.**
   Only proceed after the user acknowledges, OR when I can show the change is provably safe
   (additive, backward-compatible, legacy path preserved) — and I say so explicitly.

4. **Prefer the smallest reversible, backward-compatible step.**
   Additive over destructive. Keep legacy paths working (e.g. token/email fallbacks) so nothing
   that works today stops working.

5. **No silent assumptions.**
   Every change must trace to something I verified in the code. If I'm inferring, I say so and
   verify first.

## After every change

6. **Verify.** Build/compile (and run tests if present) after each meaningful change. Report the
   real result — if it fails, say so with the output. Never claim "done" without verification.

7. **Never run destructive operations** (DB drops/updates against a real DB, force pushes, deletes)
   without explicit, in-context approval. DB migrations are *documented* in the plan and applied
   deliberately, with existing-data impact stated up front.

8. **Update the tracker.** After each step, update `PHONE_SIGNUP_PLAN.md`: mark status, and append
   to the Validation Log what I checked and what I found (especially anything flagged).

## Status legend (used in the plan tracker)
- `[ ]` not started
- `[~]` in progress
- `[x]` done + verified
- `[!]` blocked / flagged — needs a decision before proceeding

## One-line contract
**Re-validate → map blast radius → flag if risky → smallest safe step → verify → update tracker.**
If everything checks out clean, proceed. If anything is uncertain, flag it first.
