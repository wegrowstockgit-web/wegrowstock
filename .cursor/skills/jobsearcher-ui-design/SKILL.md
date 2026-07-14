---
name: jobsearcher-ui-design
description: >-
  Elevates JobSearcher frontend UI to production-grade SaaS quality. Use when
  designing, styling, or refactoring React components in frontend/, improving
  layout, typography, color, spacing, motion, accessibility, or wizard UX.
  Covers Tailwind v4 design tokens, framer-motion patterns, dark-mode hierarchy,
  job-search workflow UI, and anti-generic-AI aesthetics. Synthesizes practices
  from effective-ui-design, ui-design-brain, and premium-web-design skills.
paths: frontend/**
---

# JobSearcher UI Design

Apply this skill whenever changing UI in `frontend/`. Goal: a trustworthy, calm **job-search copilot** — not a generic purple-gradient AI dashboard.

## Stack (do not swap without asking)

| Layer | Choice |
|-------|--------|
| Framework | React 19 + TypeScript |
| Styling | Tailwind CSS v4 (`@import "tailwindcss"`, `@theme` in `index.css`) |
| Motion | framer-motion (`AnimatedStep`, `JobListItem`, wizard transitions) |
| Components | Custom primitives in `components/ui/` (`Button`, `Card`, `Input`) |
| Routing | react-router-dom, shell in `AppShell.tsx` |

Extend existing primitives before adding new UI libraries.

## Design direction

**Product personality:** focused, professional, high-signal. Users are stressed job seekers — reduce noise, surface match % and next action clearly.

**Visual reference tier:** Linear, Raycast, Stripe Dashboard (dark), not Dribbble hero mockups.

**Avoid (AI tells):**
- Purple/indigo gradients on everything
- Identical `rounded-xl` on every surface
- Glow shadows on all cards
- Centered marketing hero inside an app wizard
- More than one primary CTA per step
- Decorative animation without `prefers-reduced-motion` guard

## Token system — define in `index.css` `@theme`

Use CSS variables so components stop hard-coding `slate-*` / `cyan-*` everywhere.

```css
@theme {
  /* Typography — pick ONE sans; avoid Segoe-only default */
  --font-sans: "DM Sans", system-ui, sans-serif;
  --font-mono: "IBM Plex Mono", ui-monospace, monospace;

  /* Surfaces (dark) — low-saturation, tinted neutrals */
  --color-bg: oklch(16% 0.02 260);
  --color-surface: oklch(20% 0.02 260);
  --color-surface-raised: oklch(24% 0.025 260);
  --color-border: oklch(32% 0.02 260);
  --color-border-strong: oklch(42% 0.03 260);

  /* Text */
  --color-text: oklch(92% 0.01 260);
  --color-text-muted: oklch(68% 0.02 260);
  --color-text-faint: oklch(52% 0.02 260);

  /* Brand accent — ONE hue; cyan/teal is fine if restrained */
  --color-accent: oklch(72% 0.14 195);
  --color-accent-hover: oklch(78% 0.14 195);
  --color-accent-muted: oklch(72% 0.14 195 / 0.15);

  /* Semantic */
  --color-success: oklch(72% 0.16 145);
  --color-warning: oklch(78% 0.14 85);
  --color-danger: oklch(62% 0.18 25);

  /* Radius — 2 tiers only */
  --radius-sm: 0.5rem;
  --radius-md: 0.75rem;

  /* Spacing — 4px base, prefer 8px multiples */
  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-3: 0.75rem;
  --space-4: 1rem;
  --space-6: 1.5rem;
  --space-8: 2rem;
  --space-12: 3rem;
}
```

Map to Tailwind: `bg-[var(--color-surface)]`, or add `@theme` color aliases. Keep **60-30-10**: ~60% background, ~30% surfaces, ~10% accent.

## Typography

| Role | Size | Weight | Notes |
|------|------|--------|-------|
| Page title | `text-2xl` / `clamp(1.5rem, 2vw, 1.75rem)` | 600 | One per view |
| Section title | `text-lg` | 600 | Wizard step headings |
| Body | `text-sm` / `text-base` | 400 | 16px for long JD text |
| Meta / caption | `text-xs` | 400 | Company, posted time — **min 12px**, never 10px |
| Mono data | `font-mono text-xs` | 400 | IDs, scores — use sparingly |

- Line-height ≥ 1.5 for body and job descriptions.
- Left-align; max-width `max-w-3xl` for prose blocks.
- Sentence case for labels and buttons.

## Layout & spacing

- **8px grid:** padding/gaps use 4, 8, 16, 24, 32, 48 (Tailwind `1–12`).
- **Content width:** `max-w-5xl` shell (already in `AppShell`); wizard steps `max-w-2xl`–`max-w-3xl`.
- **Proximity:** related controls share a group with `gap-2`; sections separated by `gap-8` or `py-8`.
- **Cards:** `border` + subtle bg shift, not heavy shadow stacks. Selected state = border accent + `bg-accent-muted`.

## Component patterns (this app)

### Buttons (`components/ui/Button.tsx`)

- One **primary** per step (filled accent).
- Secondary = bordered surface; ghost = tertiary.
- Min tap target **44×44px** (`py-2.5 px-4` minimum).
- Verb + noun labels: "Search jobs", "Apply to selected" — not "Submit" / "Continue".
- Prefer validate-on-submit over `disabled` primary buttons.

### Wizard (`HomeFlow.tsx`, `WizardProgress.tsx`)

- Always show **where you are** (step label + progress).
- One main action per step; secondary actions visually lighter.
- Loading steps: `PipelineActivity` + short status copy, not blank screens.
- Errors: inline banner at step top, rose border, plain-language fix.

### Job list (`JobListItem.tsx`)

Hierarchy (top → bottom):
1. Title (semibold, `text-text`)
2. Company · posted time · location (muted row)
3. Match % + salary (accent / mono — scannable)
4. Description (collapsed: 2 lines; expanded: full)

- Checkbox + row click targets must not fight — keep checkbox large enough.
- `aria-expanded` on expand control; `time` element with `dateTime` when available.

### Forms (`ProfileReview`, `LocationFilters`, dropzone)

- Single column; labels **above** inputs.
- Mark optional fields, not only required.
- Hints above field, errors below after touch/submit.
- Native `<select>` styled consistently with `Input`.

### Shell (`AppShell.tsx`)

- Header: logo left, account right — no debug session line in production UI (gate behind dev flag or remove).
- Sticky header with `backdrop-blur` is fine; ensure contrast on border.

## Motion (framer-motion)

```tsx
const ease = [0.22, 1, 0.36, 1]; // ease-out quart
const fadeUp = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  transition: { duration: 0.25, ease },
};
```

- Duration **150–300ms** for UI; **≤400ms** for step transitions.
- Animate `opacity` and `transform` only (GPU-friendly).
- Wrap motion in `useReducedMotion()` — skip transform when reduced motion preferred.
- `layout` on list items sparingly (job cards OK).

## Accessibility (WCAG 2.1 AA)

- Body text contrast ≥ **4.5:1**; large text / UI chrome ≥ **3:1**.
- Focus rings on all interactive elements: `focus-visible:ring-2 focus-visible:ring-accent`.
- Icon-only buttons need `aria-label`.
- Don't rely on color alone for match quality — show numeric %.
- Respect `prefers-reduced-motion`.

## Workflow before shipping UI changes

1. **Audit** — list components touched; note hierarchy, spacing, and CTA count.
2. **Tokens** — use `@theme` variables, not one-off hex in components.
3. **Implement** — smallest diff; match existing file patterns (`@/` imports).
4. **Checklist** (must pass):
   - [ ] One primary CTA per wizard step
   - [ ] No text smaller than 12px
   - [ ] Spacing on 8px grid
   - [ ] Focus states visible
   - [ ] Reduced-motion path exists for animations
   - [ ] No new generic AI aesthetic tells
5. **Run** `npm run lint` in `frontend/`

## When to go deeper

| Need | Read |
|------|------|
| Full token recipes & copy examples | [references.md](references.md) |
| Install community skills (WCAG, 60+ components) | [external-resources.md](external-resources.md) |

## Optional upgrade path

If the user wants a full component library, propose **shadcn/ui** (project has MCP plugin) — but only after tokens and layout are stable. Do not bolt shadcn onto inconsistent spacing.
