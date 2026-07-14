# JobSearcher UI — Reference

## Color usage (60-30-10)

| Share | Role | Token |
|-------|------|-------|
| ~60% | Page bg, large empty areas | `--color-bg` |
| ~30% | Cards, inputs, header | `--color-surface`, `--color-surface-raised` |
| ~10% | Primary buttons, match highlights, links | `--color-accent` |

Semantic colors only for status: success (applied), warning (dry run), danger (errors/delete).

## Elevation without shadow soup

```html
<!-- Default card -->
<div class="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)]">

<!-- Raised / popover -->
<div class="... border-[var(--color-border-strong)] bg-[var(--color-surface-raised)] shadow-lg shadow-black/20">

<!-- Selected job -->
<div class="border-[var(--color-accent)] bg-[var(--color-accent-muted)]">
```

## Wizard step copy templates

| Step | Title | Primary CTA |
|------|-------|-------------|
| upload | Upload your resume | (auto on drop) |
| profile | Review your profile | Continue to search |
| search-config | Set search preferences | Find matching jobs |
| searching | Searching job boards… | (disabled — show pipeline) |
| results | N jobs match your profile | Apply to selected |
| applying | Submitting applications… | (pipeline) |
| complete | Applications submitted | Record outcomes |

## Job card metadata row

Order: `Company` · `Posted 3d ago` · `Remote` · `Salary`

Use `text-xs text-[var(--color-text-muted)]` with `·` separators (`aria-hidden` on dots).

## Match score display

- Show **integer %** + short label: `87% match`
- Optional ring (`MatchRing`) — ensure number remains visible for color-blind users
- Strong match (≥80%): accent text; moderate: default; weak: muted (not red unless <40%)

## Empty & error states

**Empty jobs**
```
Title: No matching jobs yet
Body: Try broadening location or adding skills to your profile.
Action: Adjust search preferences (secondary)
```

**API error**
```
Title: Search couldn't complete
Body: {plain error from API}. Check your connection and try again.
Action: Retry search (primary)
```

## Loading skeleton pattern

Prefer structured skeleton over spinners for lists:

```tsx
<div className="animate-pulse space-y-3">
  <div className="h-20 rounded-[var(--radius-md)] bg-[var(--color-surface-raised)]" />
</div>
```

## Tailwind v4 notes

- Global theme: `frontend/src/index.css` `@theme { }`
- No `tailwind.config.js` — extend via CSS variables
- After token changes, grep for hardcoded `slate-` / `cyan-` in touched files and migrate

## Font loading (recommended)

In `index.html` or CSS:

```html
<link rel="preconnect" href="https://fonts.googleapis.com" />
<link href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,400;0,9..40,500;0,9..40,600;1,9..40,400&display=swap" rel="stylesheet" />
```

Alternatives with similar restraint: **Plus Jakarta Sans**, **Source Sans 3**, **Geist** (if user adds package).

## File map

| Area | Path |
|------|------|
| Tokens | `frontend/src/index.css` |
| Shell | `frontend/src/components/layout/AppShell.tsx` |
| Wizard | `frontend/src/pages/HomeFlow.tsx` |
| Primitives | `frontend/src/components/ui/*.tsx` |
| Job row | `frontend/src/components/wizard/JobListItem.tsx` |
| Motion helpers | `frontend/src/components/wizard/AnimatedStep.tsx` |
