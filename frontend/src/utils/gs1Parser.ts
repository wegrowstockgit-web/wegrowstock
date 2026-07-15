/**
 * Client-side GS1-128 composite barcode parser for offline floor ops.
 * Extracts AI 01 (GTIN), 10 (lot), 17 (expiry YYMMDD), 30 (variable qty).
 * FNC1 (ASCII 29) terminates variable-length fields so Lot does not bleed into later AIs.
 */

const FNC1 = '\u001d';

/** Structured result for scanner UI + offline mutation payloads. */
export interface ParsedBarcode {
  /** Catalog lookup key — GTIN (AI 01) when present, else the raw trimmed string. */
  sku: string;
  lotNumber?: string;
  /** ISO date YYYY-MM-DD when AI 17 is present. */
  expiryDate?: string;
  quantity?: number;
  isGs1: boolean;
  /** Original wedge / camera payload (when GS1). */
  raw?: string;
}

const RE_PAREN_GTIN = /\(01\)(\d{14})/;
const RE_PAREN_LOT = /\(10\)([^\u001d()]+?)(?=\(|$|\u001d)/;
const RE_PAREN_EXPIRY = /\(17\)(\d{6})/;
const RE_PAREN_QTY = /\(30\)(\d{1,8})/;

const RE_AI_GTIN = /(?:^|\u001d)01(\d{14})/;
const RE_AI_EXPIRY = /(?:^|\u001d)17(\d{6})/;
/** Variable AI 10 — stop at FNC1 or a known following fixed/variable AI. */
const RE_AI_LOT = /(?:^|\u001d)10([^\u001d]+?)(?=\u001d(?:01|10|17|21|30)|$)/;
const RE_AI_QTY = /(?:^|\u001d)30(\d{1,8})(?=\u001d|$)/;

function looksLikeGs1(value: string): boolean {
  if (/\(01\)\d{14}/.test(value) || /\(17\)\d{6}/.test(value) || /\(10\)/.test(value)) {
    return true;
  }
  if (value.includes(FNC1)) return true;
  if (/^01\d{14}/.test(value)) return true;
  if (/17\d{6}/.test(value) && /^01\d{14}/.test(value)) return true;
  return false;
}

function normalize(raw: string): string {
  return raw
    .trim()
    .replace(/]C1/gi, '')
    .replace(/\{GS\}/gi, FNC1)
    .replace(/\x1d/g, FNC1);
}

function parseYyMmDd(yyMmDd: string): string | undefined {
  if (!/^\d{6}$/.test(yyMmDd)) return undefined;
  const yy = Number(yyMmDd.slice(0, 2));
  const mm = Number(yyMmDd.slice(2, 4));
  let dd = Number(yyMmDd.slice(4, 6));
  if (mm < 1 || mm > 12) return undefined;
  const year = yy >= 70 ? 1900 + yy : 2000 + yy;
  if (dd === 0) dd = 1;
  if (dd < 1 || dd > 31) return undefined;
  const iso = `${String(year).padStart(4, '0')}-${String(mm).padStart(2, '0')}-${String(dd).padStart(2, '0')}`;
  return Number.isFinite(Date.parse(iso)) ? iso : undefined;
}

/**
 * Regex-driven GS1 decode. Returns `{ isGs1: false, sku: raw }` for plain barcodes.
 */
export function parseGs1(raw: string | null | undefined): ParsedBarcode {
  if (raw == null || !String(raw).trim()) {
    return { sku: '', isGs1: false };
  }

  const trimmed = String(raw).trim();
  const normalized = normalize(trimmed);

  if (!looksLikeGs1(normalized)) {
    return { sku: trimmed, isGs1: false };
  }

  const parenForm = normalized.includes('(01)') || normalized.includes('(10)') || normalized.includes('(17)');

  let gtin: string | undefined;
  let lot: string | undefined;
  let expiryRaw: string | undefined;
  let qtyRaw: string | undefined;

  if (parenForm) {
    gtin = normalized.match(RE_PAREN_GTIN)?.[1];
    lot = normalized.match(RE_PAREN_LOT)?.[1]?.trim();
    expiryRaw = normalized.match(RE_PAREN_EXPIRY)?.[1];
    qtyRaw = normalized.match(RE_PAREN_QTY)?.[1];
  } else {
    // Insert synthetic FNC1 before known AIs when scanners omit separators after variable fields,
    // then run AI-prefixed regexes. First peel fixed AIs with a walk so Lot (10) stays bounded.
    const walked = walkExtract(normalized);
    gtin = walked.gtin;
    lot = walked.lot;
    expiryRaw = walked.expiry;
    qtyRaw = walked.qty;

    // Regex fallback / confirmation for FNC1-delimited payloads
    gtin ??= normalized.match(RE_AI_GTIN)?.[1];
    expiryRaw ??= normalized.match(RE_AI_EXPIRY)?.[1];
    qtyRaw ??= normalized.match(RE_AI_QTY)?.[1];
    if (!lot) {
      const m = normalized.match(RE_AI_LOT);
      if (m?.[1]) lot = m[1].trim();
    }
  }

  if (!gtin && !lot && !expiryRaw && !qtyRaw) {
    return { sku: trimmed, isGs1: false };
  }

  const expiryDate = expiryRaw ? parseYyMmDd(expiryRaw) : undefined;
  const quantity = qtyRaw != null && /^\d+$/.test(qtyRaw) ? Number(qtyRaw) : undefined;

  return {
    sku: gtin ?? trimmed,
    lotNumber: lot || undefined,
    expiryDate,
    quantity: quantity != null && Number.isFinite(quantity) ? quantity : undefined,
    isGs1: true,
    raw: trimmed,
  };
}

/** Sequential AI walk — authoritative for concatenated GS1-128 without human-readable parens. */
function walkExtract(normalized: string): {
  gtin?: string;
  lot?: string;
  expiry?: string;
  qty?: string;
} {
  const out: { gtin?: string; lot?: string; expiry?: string; qty?: string } = {};
  let i = 0;
  while (i < normalized.length) {
    if (normalized[i] === FNC1) {
      i += 1;
      continue;
    }
    if (i + 2 > normalized.length) break;
    const ai = normalized.slice(i, i + 2);
    i += 2;
    if (ai === '01') {
      if (i + 14 > normalized.length) break;
      out.gtin = normalized.slice(i, i + 14);
      i += 14;
    } else if (ai === '17' || ai === '11' || ai === '13' || ai === '15') {
      if (i + 6 > normalized.length) break;
      if (ai === '17') out.expiry = normalized.slice(i, i + 6);
      i += 6;
    } else if (ai === '30') {
      let end = normalized.indexOf(FNC1, i);
      if (end < 0) end = findNextAi(normalized, i);
      const value = normalized.slice(i, end);
      if (!/^\d{1,8}$/.test(value)) break;
      out.qty = value;
      i = end;
      if (normalized[i] === FNC1) i += 1;
    } else if (ai === '10' || ai === '21') {
      let end = normalized.indexOf(FNC1, i);
      if (end < 0) end = findNextAi(normalized, i);
      const value = normalized.slice(i, end);
      if (!value || value.length > 20) break;
      if (ai === '10') out.lot = value;
      i = end;
      if (normalized[i] === FNC1) i += 1;
    } else {
      break;
    }
  }
  return out;
}

const KNOWN_AI = /^(?:01|10|11|13|15|17|21|30)/;

function findNextAi(s: string, from: number): number {
  for (let j = from + 1; j + 2 <= s.length; j++) {
    if (KNOWN_AI.test(s.slice(j, j + 2))) {
      // Prefer boundaries that look like a new AI after at least 1 char of data
      return j;
    }
  }
  return s.length;
}

/** Prefer GTIN as catalog lookup key when the payload is GS1. */
export function gs1LookupSku(raw: string): string {
  return parseGs1(raw).sku;
}

export interface LotGraceResult {
  /** True when lot AI is present but the cached variant is not lot-tracked. */
  lotLoggedNotTracked: boolean;
  /** Offline / API metadata sink for discarded vendor lots. */
  metadata?: Record<string, string>;
}

/**
 * Graceful degradation: lot AI present + tracking disabled → do not block the scan;
 * capture the lot string into metadata for ledger sinking.
 */
export function evaluateLotGrace(
  parsed: ParsedBarcode,
  isLotTracked: boolean | undefined,
): LotGraceResult {
  const lot = parsed.lotNumber?.trim();
  if (!lot) {
    return { lotLoggedNotTracked: false };
  }
  if (isLotTracked === false) {
    return {
      lotLoggedNotTracked: true,
      metadata: { vendor_lot_captured: lot },
    };
  }
  return { lotLoggedNotTracked: false };
}
