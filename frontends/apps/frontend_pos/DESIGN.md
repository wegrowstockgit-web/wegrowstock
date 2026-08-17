# POS design

Light retail register for weGrowStock. Cashiers work long shifts under store lighting, so the screen stays paper-light like WMS office and uses the same indigo accent as the admin console (`#6366f1`). The dark forest lane is retired.

## Thesis

Every item on the ticket is visible as a photo row. Cashiers add by barcode scan, then control qty, line void, customer, tenders, and manager-PIN transaction void.

## Palette

| Role | Token | Use |
|---|---|---|
| Surface | `#f8fafc` | Shell |
| Raised | `#ffffff` | Cart rows, pay pane, login |
| Ink | `#0f172a` | Type |
| Muted | `#475569` | Meta |
| Accent | `#6366f1` | Scan add, card, focus |
| Success | `#16a34a` | Exact cash, ready, next customer |
| Cash | `#f59e0b` | Cash tenders, amount entered |
| Danger | `#dc2626` | Void |

## Layout

Landscape: photo cart ~62% left, pay console max `28rem` right. Scan field sits above the ticket. Each purchased line is a 76px image, name, UPC, qty stepper, amount, void. Newest line highlights and scrolls into view.

## Cashier controls

Scan add · qty −/+ · line void · add customer · numpad tender · exact / next / cash preset · card · change due · transaction void (PIN) · CFDI on MX.

## Motion

Key press `scale(0.97)` at 150ms. Green next-customer flash. `prefers-reduced-motion` disables scale.
