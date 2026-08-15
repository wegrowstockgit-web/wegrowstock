---
name: affinity-design
description: >-
  Design in Affinity by Canva via Affinity MCP whenever the user pastes a brief,
  moodboard notes, copy, brand constraints, or asks to design / layout / create
  a poster, banner, social post, logo, packaging, slide, or visual. Use when the
  user mentions Affinity, AffinityMCP, Canva Affinity, or pastes design content
  to produce. Prefer Affinity MCP tools over generating images in chat.
---

# Affinity Design (paste → Affinity)

When the user pastes a brief or asks you to design something, **build it in Affinity** through the Affinity MCP bridge. Do not stop at describing the design unless Affinity is unreachable.

## Preconditions (do this first)

1. Call `affinity_status` (or the equivalent status tool from the Affinity MCP server).
2. If Affinity is **not reachable**, tell the user to:
   - Open **Affinity by Canva** (3.2+)
   - Enable **Settings → Model Context Protocol → Enable MCP server**
   - Restart Affinity
   - Confirm nothing else blocks port `6767`
3. Only after status is healthy, proceed to design.

## How to treat pasted content

Treat the user's paste as the design brief. Extract and honor:

- **Job**: deliverable type (poster, IG post, banner, logo lockup, packaging, etc.)
- **Copy**: headlines, body, CTAs — use their words unless they ask for rewrites
- **Brand**: colors, fonts, logo rules, tone
- **Size / format**: canvas dimensions, aspect ratio, export format
- **Constraints**: must-include elements, legal lines, accessibility

If size is missing, pick a sensible default and state it:

| Deliverable | Default |
|-------------|---------|
| Instagram post | 1080×1080 |
| Story / Reel cover | 1080×1920 |
| LinkedIn / X banner | 1200×628 |
| Poster (letter) | 2550×3300 @ 300dpi (or 8.5×11 in) |
| Logo exploration | 2000×2000 square artboard |

## Design workflow in Affinity

1. **Status** — confirm MCP connection.
2. **Document** — create or open the right canvas size/units.
3. **Structure** — set up layers/artboards clearly (Background, Brand, Type, Accents).
4. **Compose** — place brand marks, imagery placeholders, and typography hierarchy from the brief.
5. **Polish** — spacing, contrast, alignment; avoid purple-gradient / generic AI aesthetic unless requested.
6. **Verify** — render/screenshot selection or spread via Affinity MCP when available; iterate once if something is off.
7. **Export** — export PNG/PDF (or asked format) to a path the user can open; report the path.

Use Affinity's script / document tools exposed by the MCP server. Prefer real Affinity document edits over inventing SVG/HTML stand-ins.

## Design quality bar

- One clear focal point; one job per composition.
- Strong typography hierarchy (headline > support > meta).
- Enough contrast for body text.
- Real margins and grid alignment — no cramped edges.
- Brand colors and type from the brief when provided; otherwise choose a deliberate, non-default palette and name it.
- No clutter: skip decorative badge piles, random gradients, and emoji unless the brief asks.

## Response format after designing

Keep it short:

1. What you made (size + intent)
2. Where it lives in Affinity / export path
3. 2–4 bullet design choices tied to the brief
4. Optional next tweak question (one only)

## When Affinity tools are limited

If the MCP only exposes status/docs/scripts:

1. Still drive Affinity via available script/document tools.
2. If a step is impossible through MCP, say exactly which Affinity menu/action the user should confirm, then continue with what MCP can do.
3. Do not silently fall back to chat-only mockups unless the user asks for a mockup outside Affinity.
