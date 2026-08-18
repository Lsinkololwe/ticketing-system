# Organization Admin — dashboard infographic spec

Governs every data-bearing tile on the organizer dashboard. Written before any chart
code, per `.claude/skills/infographics-skill`. Foundations (§A) are declared once and
inherited by all five graphics (§B1–B5).

Source design: Claude Design project `MyTicketZM Design System`
(`af157d5b-36fd-4e93-a690-0aa633d73e5c`), screen `Org Admin - Dashboard.dc.html`.

---

## A. Shared foundations

### A1. Audience and context

- **Who reads this:** an event organizer (owner / finance role) in Zambia, on the
  authenticated organizer portal.
- **Decision it serves:** "is the business growing, and is there money I can take out
  today?" — then, "which event or team thing needs me next?"
- **Where it appears:** the `/dashboard` route. Bento grid, 1360px max content width,
  down to a single column at 640px.
- **Time given:** 5 seconds for the top row, 30 seconds for the whole page.

### A2. Palette

Neutral base plus exactly one accent. Every value is an existing design-system token —
no new hues are introduced.

| Role | Light | Dark | Purpose |
|---|---|---|---|
| Base neutral ramp | `--gray-3` … `--gray-12` | same tokens, dark scale | structure, reference marks, all body text |
| Accent | teal — `--viz-accent` | teal — `--viz-accent` | the mark the kernel is about |
| Money (text only) | `--color-money-text` | `--color-money-text` | currency figures; semantic, never decorative |

- **Total hue count: 2** (slate + teal). Money-jade appears as *text colour on numerals
  only*, never as a data mark, so it does not add a third encoding hue.
- **Grayscale survival:** every bar carries a direct text label and a printed value, and
  bars sit at distinct grid positions. Removing colour removes emphasis, not meaning.
- **Measured contrast** (computed, not eyeballed — see `A4`):

  | Pair | Light | Dark | Min |
  |---|---|---|---|
  | body text vs card | 15.35:1 | 14.58:1 | 4.5 |
  | micro-label vs card | 6.57:1 | 8.79:1 | 4.5 |
  | money text vs card | 4.68:1 | 8.16:1 | 4.5 |
  | viz accent vs track | 4.68:1 | 6.65:1 | 3.0 |
  | viz accent vs non-focal bar | 3.75:1 | 4.55:1 | 3.0 |
  | viz muted vs track | 4.76:1 | 3.91:1 | 3.0 |

### A3. Type

- **Families (2):** Inter for all labels and prose; Fira Code for every numeral that
  stacks or is compared (`--font-mono`, tabular figures on).
- **Sizes (4):** 30px figure / 14px body / 12px value + meta / 10px micro-label.
- **Weights (2):** 400 regular, 700 bold.
- **Tabular figures:** yes, everywhere numbers stack — enforced by `.ds-amount`.
- **Largest element:** the tile's own headline figure, never the tile title. Titles are
  the 10px micro-label — the smallest type on the tile.

### A4. Token corrections required by G7

The imported design system fails the contrast gate in five measured places. These are
fixed additively in `apps/organization-admin/src/app/global.css` §2b — the Radix scales
themselves are untouched so the other two apps are unaffected.

| Token | Imported | Measured | Corrected to | Now |
|---|---|---|---|---|
| `--color-money-text` (light) | `--jade-11` | 3.88:1 | `hsl(160 50% 34%)` | 4.68:1 |
| `--status-warning-11` text (light) | `--amber-11` | 3.48:1 | `hsl(40 90% 32%)` | 4.71:1 |
| `--status-success-11` text (light) | `--green-11` | 4.13:1 | `hsl(150 50% 34%)` | 4.74:1 |
| StatCard title label (dark) | `--gray-9` | 3.97:1 | `--gray-11` | 8.79:1 |
| bar mark vs track | `teal-9` on `gray-5` | 2.32:1 | `--viz-*` layer | 4.68:1 |

The design's own dashboard mock paints `hsl(174 62% 38%)` bars on `hsl(225 4% 92%)`
tracks (2.2:1) and distinguishes focal from non-focal segment bars by hue alone at
1.36:1. Both are corrected here rather than reproduced.

### A5. Layout

- **Spacing scale:** `--space-1..9` (4px base). Within-group 8px, between-group 16px,
  between-band 24px. No arbitrary gaps.
- **Grid:** bento. Band 1 is an asymmetric `2fr 1fr`; every other band is
  `repeat(auto-fit, minmax(Npx, 1fr))`. Collapses to one column under 900px.
- **Reading path:** revenue trend (top-left, largest figure) → cash available (top-right,
  the only action button in the band) → the four counters → composition tiles →
  operational lists.

---

## B1. Revenue trend tile

### Kernel
> Revenue rose six months straight and just hit its highest month yet.

### Data
- **Source:** `myRevenueSeries(months: 6)` — booking-service, organizer scoped from JWT.
- **Time range:** trailing 6 complete calendar months.
- **n:** 6 points. **Units:** ZMW, rendered `K 125,430`.
- **Caveats:** the current month is partial and is excluded, not drawn as a short bar.

### Relationship
**Change over time** — the kernel's verb is "rose".

### Encoding

| Variable | Type | Channel | Why |
|---|---|---|---|
| month | ordinal | position on x | natural order; reads left→right as elapsed time |
| revenue | quantitative | bar length from a zero baseline | length on a common baseline is the most accurate channel available for magnitude |
| is-latest | nominal (1 of 6) | lightness (accent vs pale) + label weight | emphasis only; month labels already identify every bar |

### Form
- **Form:** vertical column chart, 6 discrete periods, zero baseline.
- **Justification:** discrete periods and few of them — `chart-selection.md` maps this to
  a column chart. A line would imply continuous measurement between month-ends.
- **Rejected:** line chart (implies continuity we do not measure); sparkline (too small to
  carry the page's primary figure); area chart (fill adds ink, encodes nothing).

### Hierarchy
- **Focal:** the `K 125,430` figure — 30px, bold, the largest type on the tile.
- **Supporting:** the six columns and the annotation.
- **Reference:** month labels, the baseline rule, the provenance line.
- **Step enforced by:** 30px/12px/10px size jumps and bold/regular weight, not by colour.

### Annotation
- **Text:** "Six straight monthly rises — up 60% since {firstMonth}, and K 15,000 of it is
  ready to withdraw now." Computed from the series, never hardcoded.
- **Placement:** directly under the columns, on a 2px accent left rule, sharing the
  columns' left edge.
- **Provenance:** "Ticket revenue · last 6 complete months · ZMW" as the tile's
  micro-label.

### Cut list
1. Y-axis with gridlines and tick labels — every bar is directly labelled and the headline
   figure gives the scale anchor. The axis was pure ink.
2. Gradient fills on the columns — encode nothing, distort perceived length.
3. A second series (tickets sold) overlaid — would need a dual axis, which manufactures
   correlation. It lives in its own StatCard.
4. Hover tooltips as the only way to read values — replaced by the always-visible figure
   plus month labels.

---

## B2. Cash-available tile

### Kernel
> K 15,000 is withdrawable now; the payout window closes in 4 days.

### Data
- **Source:** `myFinanceOverview.availableBalance` + `myPayoutWindow` (booking-service).
- **Units:** ZMW; window in whole days.
- **Caveats:** balance excludes funds still in escrow for unfinished events; stated in the
  tile.

### Relationship
**Comparison** against a benchmark — elapsed against the full window.

### Encoding

| Variable | Type | Channel | Why |
|---|---|---|---|
| available balance | quantitative | the numeral itself | a single headline number is the whole message; no chart improves on it |
| window elapsed / remaining | quantitative | segment length along one bar | length on a shared baseline; the two segments sum to a stated denominator |

### Form
- **Form:** big number + a single two-segment horizontal meter.
- **Justification:** `chart-selection.md` — "one value against a benchmark → bullet chart",
  and "single headline number → big number with one comparison anchor". A gauge or
  speedometer is on the near-always-wrong list.
- **Rejected:** donut of elapsed/remaining (angle is a weaker channel than length);
  countdown clock (motion for a value that changes daily).

### Hierarchy
- **Focal:** `K 15,000`. **Supporting:** the meter and its two labels. **Reference:** the
  settlement note and the button.

### Annotation
- **Text:** "Lands on MTN MoMo within minutes of approval."
- **Provenance:** "Ready to withdraw · settles daily · ZMW".

### Cut list
1. A sparkline of balance history — a second message competing with the kernel.
2. Percent-complete label on the meter — days are the unit the organizer thinks in.
3. Colour-coded urgency states — the day count already says it; colour would add a hue
   that encodes nothing new.

---

## B3. Ticket-mix tile

### Kernel
> General admission is nearly two-thirds of every ticket sold.

### Data
- **Source:** `myTicketMix` (booking-service), aggregated over sold tickets.
- **n:** stated on the tile as the denominator ("share of 2,847 sold").
- **Caveats:** tiers with <1% share are folded into "Other" so no sliver is unlabelled.

### Relationship
**Composition** — "X is made of Y and Z".

### Encoding

| Variable | Type | Channel | Why |
|---|---|---|---|
| tier | nominal | position (row) + text label | direct labelling; no legend, no hue dependency |
| share | quantitative | bar length from a common left baseline | length on a shared baseline beats angle (pie) and beats stacked segments, where only the bottom segment is baseline-anchored |

### Form
- **Form:** sorted horizontal bars, one row per tier, common left baseline.
- **Justification:** composition with directly-comparable parts. A pie at 3+ slices
  collapses angle comparison; a stacked bar makes only the first segment comparable.
- **Rejected:** pie / donut; stacked single bar; treemap (3 parts do not need nesting).

### Palette note
All bars share `--viz-accent`. Colour carries **nothing** here — length and the printed
percentage carry everything. This is deliberate: giving one row a different hue would
imply an encoding that does not exist.

### Hierarchy
- **Focal:** the longest bar and its printed percentage.
- **Supporting:** the other rows. **Reference:** micro-label, denominator, footnote.

### Annotation
- **Text:** "VVIP is 14% of volume but a quarter of revenue." Derived, not hardcoded.
- **Provenance:** the micro-label states the denominator: "Ticket mix · share of N sold".

### Cut list
1. Legend — every row is labelled in place.
2. Per-tier icons — decoration; the label already names the tier.
3. Revenue-share as a second bar per row — a second kernel; it belongs in the footnote as
   one sentence, which is where it went.

---

## B4. Check-in tile

### Kernel
> 87% of ticket holders actually walked through the gate.

### Data
- **Source:** `myCheckInRate` (booking-service), most recent completed event.
- **n:** printed in full ("1,044 of 1,200"). **Units:** percent of issued tickets.
- **Caveats:** counts scans, so a re-scan is deduplicated by ticket id.

### Relationship
**Composition** of two parts.

### Encoding

| Variable | Type | Channel | Why |
|---|---|---|---|
| outcome (scanned / no-show) | nominal, 2 levels | position + direct label | two labelled rows; survives grayscale |
| share | quantitative | bar length, common baseline | most accurate channel for a proportion |

### Form
- **Form:** the percentage as the headline figure, plus two labelled bars.
- **Justification:** the number *is* the message; the bars give it a denominator anchor.
- **Rejected:** donut (angle, and a 2-slice donut is a bar with extra steps); gauge
  (near-always-wrong list).

### Cut list
1. A trend of check-in rate across past events — a different kernel; belongs on Analytics.
2. Percentage without the raw counts — a rate with no denominator is not verifiable.
3. Red for the no-show row — no-show is not an error state; colouring it red asserts a
   judgement the data does not support.

---

## B5. Team-seats tile — DEFERRED, NOT BUILT

**Status: specified, not implemented.** The source design shows a "Team · 4 of 5 seats
used" tile. The platform has no seat-limit concept anywhere in the domain model:
`Organization` in identity-service carries members but no plan, no tier and no member
cap, so there is no denominator to divide by.

Building it would mean inventing the denominator, and `chart-selection.md` is explicit
that when the data does not support the kernel the answer is to say so rather than
visualise weak evidence persuasively. The dashboard therefore ships the composition band
two-up (ticket mix + check-in) rather than three-up with one fabricated tile.

**To implement:** add a seat cap to `Organization` (identity-service), expose
`myOrganizationSeats { used, limit, byRole { name count } }`, then build to the spec
below unchanged.

### Kernel
> Four of five seats are taken — one invite left before the next tier.

### Data
- **Source:** `myOrganizationSeats` (identity-service): seats used, seat limit, split by role.
- **Units:** whole seats. **Caveats:** pending invitations count against the limit.

### Relationship
**Composition** against a fixed capacity.

### Encoding

| Variable | Type | Channel | Why |
|---|---|---|---|
| role | nominal | position + label | direct labelling |
| seats in role | quantitative | bar length, common baseline | length; each bar's track is the full seat limit so bars are mutually comparable |

### Form
- **Form:** horizontal bars against a shared capacity track.
- **Justification:** capacity is the benchmark, so every bar shares one denominator —
  a bullet-style read.
- **Rejected:** stacked capacity bar (hides per-role magnitude); icon array of seats
  (fine at 5, breaks at 50).

### Cut list
1. Avatars per member — identity is not the variable; count is.
2. A "% of seats" figure — with a denominator of 5, percentages are false precision.
3. Upgrade CTA inside the tile — an action competing with the tile's one focal figure; it
   lives in the footnote as a link.

---

## C. Exceptions

1. **Bar tiles carry no y-axis.** Justified: every value is printed directly on or beside
   its mark, so the axis would be redundant ink. Logged rather than silently dropped.
2. **The revenue tile's non-focal columns use lightness, not a second hue.** This is
   emphasis, not encoding, and the month labels identify every column independently.

## D. Mesh / gradient policy

The user asked where mesh and gradient backgrounds belong in this theme. The answer that
falls out of both the design system and the infographic grammar:

| Surface | Mesh / gradient | Reason |
|---|---|---|
| Data marks (bars, meters, sparklines) | **Never** | a gradient on a mark distorts perceived length and encodes nothing (`principles.md` — data-ink) |
| Dashboard tiles and cards | **Never** | a background fill lowers contrast for every mark inside it; the DS specifies flat `--card-bg` |
| Small accent chips (avatar squares, quick-action icon tiles, brand marks) | **Yes** — `linear-gradient(135deg, var(--accent-9), var(--accent-11))` | ≤40px, carries no data, one of the two gradients the DS sanctions |
| Public marketing hero only | **Yes** — layered `radial-gradient` aurora/mesh | the one sanctioned ambient context; already implemented as `.mkt-mesh` / `.mkt-aurora` |

Rules for the marketing mesh, so it stays on-theme:

1. **Hue-locked to the brand axis.** Every mesh stop derives from
   `--marketing-accent-rgb` (teal, hue 174) or its deep/cool neighbours. No stop may
   introduce a hue outside the teal→blue arc.
2. **Alpha-capped at 0.15.** Above that the mesh starts competing with foreground text.
3. **Behind text only at ≥4.5:1.** The mesh sits on a near-black canvas whose darkest
   stop governs the contrast calculation, never the brightest.
4. **Motion is ambient, 15–30s loops, and disabled under `prefers-reduced-motion`.**
5. **Never inside a `<main>` that contains a chart.** Ambient background and data
   visualisation do not share a surface.

---

## Gate result

```
Kernel:   five, one per graphic (B1–B5); none share a focal point
Form:     B1 column chart · B2 big number + bullet meter · B3/B5 sorted horizontal bars
          · B4 big number + 2-part bars — each justified against the relationship, none
          on the near-always-wrong list
Gates:    G1 PASS  G2 PASS  G3 PASS  G4 PASS  G5 PASS  G6 PASS  G7 PASS  G8 PASS
          (G7 passes only after the §A4 token corrections; it failed as imported)
Score:    34/36
          C1 data-ink 3 · C2 direct labelling 3 · C3 focal point 3 · C4 hierarchy 3
          C5 palette 3 · C6 type 3 · C7 size=importance 3 · C8 spacing 3
          C9 alignment 3 · C10 annotation 2 · C11 precision 3 · C12 restraint 2
Verdict:  PASS
Repairs:  (1) B1 non-focal columns moved from teal-9 to a pale neutral so focal/non-focal
              separate at 3.75:1 instead of 1.36:1.
          (2) B3/B5 bars unified to one accent after the first draft used accent-vs-gray,
              which implied an encoding at 1.02:1 separation.
          (3) Money/warning/success text steps darkened to clear 4.5:1.
Low scorers, accepted:
          C10=2 — the B2 and B4 annotations are static product facts, not derived
                  findings; only B1/B3 annotations compute from the data.
          C12=2 — the quick-action tiles keep their gradient icon chips, a design-system
                  mandate (§D row 3) rather than a design choice made here.
```
