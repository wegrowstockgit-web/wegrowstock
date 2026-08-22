---
title: "Exceptions & Conflict Resolution SOP (Beginner Guide)"
slug: "sop-exceptions-conflicts"
sourcePath: "docs/sops/04_exceptions_and_conflict_resolution.md"
audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER"]
audienceLevel: "beginner"
routeHints: ["/exceptions", "/settings", "/fulfillment", "/returns", "/returns/receive", "/purchase-orders", "/cycle-counts"]
keywords: ["sync conflict", "offline", "caching scans", "stale state", "parked scan", "discard transaction", "approve re-process", "skip and flag", "hardware fallback", "manual entry", "two workers same pallet", "RMA", "returns receive"]
---

# Exceptions & Conflict Resolution — Beginner Playbook

Warehouses are messy: Wi-Fi drops, labels tear, two people grab the same pallet. This guide explains what the system does when reality gets complicated — and exactly which button fixes each situation. **Nothing in this guide is scary: an "exception" means the system protected you from a bad number, not that you broke something.**

---

## Before you start: why scans "park" instead of failing

**The scanner works offline on purpose.** When Wi-Fi drops, the header badge changes from **Connected** to **Offline - Caching Scans**. Your scans are saved on the device and replayed when the badge shows **Syncing…** then **Connected** again.

**But the world may have changed while you were offline.** Maybe a coworker picked from the same bin. When your saved scan is replayed and the math no longer works, the system does NOT guess — it **parks** the scan as a *Sync Conflict* and asks a manager to decide. Parked ≠ lost. Parked = safely waiting for a human.

**Who does what:**

| Role | Exception powers |
|---|---|
| **OWNER / ADMIN** | Everything; oversee queues |
| **WAREHOUSE_MANAGER** | Decides every conflict: **Approve & Re-process**, **Discard Transaction**, **Clear**, **Lot override** |
| **PICKER** | Flags problems (**Skip & Flag Barcode**), reads their own parked scans, fixes nothing alone |
| **VIEWER** | Read-only |

---

### How to Read the Exceptions Hub

**What is this?**
One screen listing everything that stopped: **Fulfillment Holds** (a wave line blocked by damage, a torn label, a lot problem) and **Sync Conflicts** (offline scans that couldn't be replayed). Each card says in plain words what happened and what kind of scan it was (**Inbound Receive**, **Outbound Pick**, **Cycle Count**).

**Who can do this? (Privileges Required)**
Anyone can open and read it. Only WAREHOUSE_MANAGER and above can click the resolution buttons.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inventory** → **Exceptions** (tabs: **Fulfillment Holds** | **Sync Conflicts**)
Shortcuts: the Dashboard shows **Resolve Now** / **Open queue (N)** banners when anything is waiting. The same Sync Conflicts list also appears under **Settings → Sync Conflicts**.

**Step-by-Step Instructions:**
1. Open **Exceptions**.
2. Pick the tab: **Fulfillment Holds** or **Sync Conflicts**.
3. Read the card: reason text, scan type badge, who scanned, and when.
4. Resolve (manager) using the buttons described in the next two sections.
5. Tell the floor to continue — the blocked device/wave can proceed once its card is cleared.

**⚠️ What if I make a mistake?**
- Reading is always safe. If you're a picker and see your own scan parked here — that's the system doing its job. Nothing is wrong yet; a manager just needs to look.

---

### How to Resolve a Sync Conflict (offline scans that collided)

**What is this?**
The classic case: **two workers scanned the same pallet while offline.** Worker A picked 5 from bin B-2; Worker B, also offline, picked 5 from the same bin — but the bin only had 8. When both devices reconnect, the first scan replays fine; the second can't (5 needed, 3 left). The second scan parks as a Sync Conflict instead of forcing the bin negative.

**Who can do this? (Privileges Required)**
WAREHOUSE_MANAGER or above. Pickers cannot resolve conflicts — even their own.

**Where to go in weGrowStock:**
🖥️ Sidebar Navigation → **Inventory** → **Exceptions** → tab **Sync Conflicts**

**Step-by-Step Instructions (manager):**
1. Open the conflict card and read it fully: who, what bin, what quantity, what type.
2. **Go look at the physical bin** (or send someone). The question is always: *did the physical action really happen?*
3. Choose one of exactly two buttons:
   - **Approve & Re-process** — "the physical action really happened; replay it." Use when the worker truly took/moved the stock. If the replay still fails, first fix the bin state (run a cycle count on it), then approve again.
   - **Discard Transaction** — "this scan should not count." Use when the action didn't happen physically, was a duplicate of another scan, or was a known offline mistake. Discarding drops only the parked attempt — it never rewrites older history.
4. Confirm the dialog (**Approve & re-process?** / **Discard transaction?**).
5. If the bin is now physically ambiguous (nobody is sure what's left), order a **Cycle count** on it before releasing more work.

**⚠️ What if I make a mistake?**
- **Approved something that didn't physically happen:** The ledger now overstates movement. Fix forward: cycle count the bin, and the variance approval writes the correction. Nothing is lost — just one extra honest step.
- **Discarded something that DID physically happen:** The shelf and system disagree now. Same medicine: cycle count the bin; the count restores truth.
- **Not sure which button?** Don't guess from the desk. Walk to the bin. Ninety percent of conflict resolution is looking at the shelf.
- **Two workers keep colliding on the same pallet:** That's a process smell — use **Claim wave (device lock)** so each wave belongs to one device, and split zones during Wi-Fi outages.

---

### How to Handle a Stale State Error ("this order changed while you were looking at it")

**What is this?**
Sometimes a screen refuses your action because someone else changed the thing first — e.g. you try to **Allocate** an order a coworker just cancelled, or complete a pick on a line a manager just edited. The system rejects your action instead of applying it to an outdated picture. That rejection is called stale state.

**Who can do this? (Privileges Required)**
Anyone can hit this — it's not an error you caused.

**Where to go in weGrowStock:**
🖥️ Wherever you were working — the fix is on the same screen.

**Step-by-Step Instructions:**
1. Read the on-screen message.
2. Refresh / reopen the record (close the peek panel and open it again).
3. Look at the new status chip — someone probably moved the order forward or backward.
4. Redo your action only if it still makes sense against the new state.
5. If a next-action card offers **Retry**, use it after the state looks right; use **Dismiss** to clear the card once read.

**⚠️ What if I make a mistake?**
- You can't corrupt anything with a stale-state rejection — that's the whole point. The only real mistake is hammering the same button without refreshing. Refresh first, then act.

---

### How to Use Skip & Flag (torn labels, damaged goods mid-wave)

**What is this?**
When a barcode won't scan or an item is damaged, **Skip & Flag Barcode** moves that line into an exception and lets you keep working the rest of the wave. It's the honest alternative to typing digits from memory.

**Who can do this? (Privileges Required)**
PICKER in an active claimed wave. A WAREHOUSE_MANAGER clears the resulting hold.

**Where to go in weGrowStock:**
🖥️ Picker: **Outbound** → **Fulfillment**, during any scan mode
🖥️ Manager: **Inventory** → **Exceptions** → tab **Fulfillment Holds**

**Step-by-Step Instructions:**
1. Barcode won't read or item is damaged → tap **Skip & Flag Barcode**.
2. Physically set the unit aside / quarantine it per site rules.
3. Keep picking the rest of the wave.
4. *Manager:* open **Fulfillment Holds**, read the card, and use **Clear**, **Discard**, or **Lot override** as the card allows. Reprint a label if that's all it was, then the wave line resumes.

**⚠️ What if I make a mistake?**
- **Flagged something that was actually fine:** No harm — the manager clears the hold and the line resumes. Skipping is always cheaper than a wrong scan.
- **Typed a barcode by hand instead of flagging:** Tell a manager immediately. A plausible-but-wrong barcode is the single hardest error to find later; the sooner it's corrected, the cheaper it is.

---

### Hardware Fallbacks (scanner dead, scale missing, printer down)

**What is this?**
Devices fail. The system has manual fallbacks so work can continue safely — but manual entry has more room for typos, so it comes with extra care.

**Who can do this? (Privileges Required)**
Any floor role may use on-screen manual fallbacks where offered. Managers decide when a station falls back for a whole shift.

**Where to go in weGrowStock:**
🖥️ The same screen you were on — fallback controls appear in place (e.g. a manual entry field when the camera/scanner can't read, **Connect packing scale** / **Connect Bluetooth scale** retry buttons at pack).

**Step-by-Step Instructions:**
1. Try the quick fixes first: rescan, clean the label, reposition, **Retry** on the device card.
2. If the scanner is truly dead, use the manual entry the screen offers — and read back every value twice before confirming. Type from the label in front of you, never from memory.
3. Scale won't connect at pack: click **Connect packing scale** again; if it stays dead, a manager decides whether to pack with manual weight checks.
4. When any device comes back, click **Disconnect**/reconnect properly so the next user starts clean.
5. Report broken hardware — a station limping on manual entry all week is how quiet errors pile up.

**⚠️ What if I make a mistake?**
- **Manual-entry typo:** Same recovery as any wrong scan — tell a manager, who corrects the movement or orders a cycle count on the touched bin. Attribution shows it was manual entry, which helps everyone understand what happened.
- **PIN-locked device:** Complete the scanner PIN unlock before resuming; don't borrow a logged-in device from a coworker — attribution matters when mistakes need fixing.

---

### How to Receive Customer Returns (RMA receive)

**What is this?**
When a customer sends goods back, the office approves an RMA (Return Merchandise Authorization) and the floor scans the goods back in **with condition photos** — so nobody can later claim damaged goods were received as pristine.

**Who can do this? (Privileges Required)**
Office review: WAREHOUSE_MANAGER or ADMIN (approve/deny; QC steps may need the **Process RMA QC** permission). Floor receive: PICKER/ops.

**Where to go in weGrowStock:**
🖥️ Office: Sidebar Navigation → **Inbound** → **Returns**
🖥️ Floor: **Returns receive** terminal (office can jump you there via **Receive terminal**)

**Step-by-Step Instructions:**
1. *Office:* open **Returns**, review the request (statuses: **REQUESTED**, **PENDING_REVIEW**, **APPROVED**, **RECEIVED**, **CLOSED**, **REJECTED**).
2. *Office:* choose **Approve & Buy Label**, **Approve without Label**, or **Deny & Close**.
3. *Floor:* on **Returns receive**, scan the RMA barcode.
4. *Floor:* photograph the item's real condition — **Condition photo** — then tap **Confirm +1** per unit.
5. *Floor:* tap **Scan next RMA** to continue the queue.

**⚠️ What if I make a mistake?**
- **Confirmed +1 too many times:** Tell a manager — a correction fixes the received quantity; the extra tap stays in the log.
- **Received damaged goods as good:** The photo protects you — escalate so a manager moves the stock to quarantine and corrects the condition, before it gets picked for another customer.
- **Approved a return that shouldn't exist:** Deny is impossible after receive; instead the manager quarantines the stock and finance decides on the credit side. History stays; decisions are documented forward.

---

## Quick reference: which button fixes what

| Situation | Button | Who clicks it |
|---|---|---|
| Offline scan collided, action really happened | **Approve & Re-process** | Manager |
| Offline scan collided, action didn't happen / duplicate | **Discard Transaction** | Manager |
| Bin state uncertain after any conflict | order a **Cycle count** | Manager |
| Torn label / damaged item mid-wave | **Skip & Flag Barcode** | Picker |
| Hold card after a flag | **Clear** / **Discard** / **Lot override** | Manager |
| "Record changed" rejection | refresh, then **Retry** | Anyone |
| Return arriving at dock | **Condition photo** + **Confirm +1** | Floor |
| Anything you're unsure about | chat bubble → describe it | Anyone (Manager approves drafts) |

**Golden rule:** exceptions are the system *refusing to guess*. Every parked scan is a question addressed to a human. Answer the question honestly (walk to the shelf, look), and the ledger stays trustworthy. The chatbot can draft resolutions — e.g. *"Discard my parked pick on bin B-2, I never took the stock"* — but a Manager must click **Approve** on the Action Draft.
