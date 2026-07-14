# External UI design skills (research summary)

These are community **Agent Skills** (open standard: [agentskills.io](https://agentskills.io)) that Cursor can load alongside this project skill. Install into a skill directory — **not** `~/.cursor/skills-cursor/` (reserved for Cursor built-ins).

## Recommended installs

| Skill | Source | Best for |
|-------|--------|----------|
| **effective-ui-design** | [sebastian-software/effective-ui-design-skill](https://github.com/sebastian-software/effective-ui-design-skill) | WCAG 2.1 AA, OKLCH palettes, 8pt grid, forms, buttons, SEO, Core Web Vitals |
| **ui-design-brain** | [carmahhawwari/ui-design-brain](https://github.com/carmahhawwari/ui-design-brain) | 60+ component patterns, layouts, anti-patterns (sourced from component.gallery) |
| **premium-web-design** | [Lucxar/premium-web-design-skill](https://github.com/Lucxar/premium-web-design-skill) | Premium feel, anti-AI aesthetics, motion timing, whitespace discipline |
| **robis-design-best-practice** | [Ethiopian-Cursor-Community/robis-design-best-practice](https://github.com/Ethiopian-Cursor-Community/robis-design-best-practice) | Low-saturation color, 4px spacing, dark mode borders vs shadows |

## How to install (pick one)

### Project-scoped (team shares via git)

```bash
# From repo root — example: effective-ui-design
git clone https://github.com/sebastian-software/effective-ui-design-skill.git .cursor/skills/effective-ui-design
```

### Personal (all projects)

```bash
git clone https://github.com/sebastian-software/effective-ui-design-skill.git %USERPROFILE%\.cursor\skills\effective-ui-design
```

### Cursor Settings

**Cursor Settings → Rules → Add Rule → Remote Rule (Github)** — paste the skill repo URL if your Cursor version supports remote skill import.

## How skills combine with `jobsearcher-ui-design`

| Layer | Skill |
|-------|-------|
| App-specific tokens, wizard, job cards | `jobsearcher-ui-design` (this repo) |
| Generic WCAG, form anatomy, typography law | `effective-ui-design` |
| Component-level recipes (tabs, modals, tables) | `ui-design-brain` |
| Polish pass / “feels cheap” feedback | `premium-web-design` |

**Order of application:** project skill first (stack + screens), then community skill for the component type you're building.

## Manual invocation

Type `/jobsearcher-ui-design` in Agent chat to force-load this skill.

Other skills: `/effective-ui-design`, etc., when installed.

## Design inspiration (human taste)

Study live products, not template galleries:

- [Linear](https://linear.app) — density, dark hierarchy
- [Stripe Dashboard](https://dashboard.stripe.com) — data clarity
- [Raycast](https://raycast.com) — focus, motion restraint
- [component.gallery](https://component.gallery) — component naming and anatomy

## shadcn/ui (optional)

This repo has the **shadcn MCP plugin**. If migrating to shadcn:

1. Run shadcn init against `frontend/`
2. Map shadcn CSS variables to JobSearcher `@theme` tokens
3. Replace `components/ui/*` incrementally — wizard first

Read: `C:\Users\ranit\.cursor\plugins\cache\cursor-public\shadcn\...\skills\shadcn\SKILL.md` when user requests shadcn.
