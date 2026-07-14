/**
 * GS1-128 / GS1 DataMatrix Application Identifier parser (frontend mirror of backend Gs1BarcodeParser).
 * AI 01 (GTIN), AI 10 (lot), AI 17 (expiry YYMMDD), AI 21 (serial).
 */

const FNC1 = '\u001d';

export interface Gs1Elements {
  raw: string;
  gtin: string | null;
  lot: string | null;
  expiry: string | null; // ISO date YYYY-MM-DD
  serial: string | null;
  all: Record<string, string>;
}

function looksLikeGs1(value: string): boolean {
  if (value.startsWith('01') && value.length >= 16) return true;
  return value.includes(FNC1) || /^01\d{14}/.test(value) || /17\d{6}/.test(value);
}

function indexOfFnc1OrEnd(s: string, from: number): number {
  const idx = s.indexOf(FNC1, from);
  return idx < 0 ? s.length : idx;
}

function findNextFixedAiBoundary(s: string, from: number): number {
  for (let j = from + 1; j + 2 <= s.length; j++) {
    const maybe = s.slice(j, j + 2);
    if (['01', '17', '11', '15', '10', '21'].includes(maybe)) {
      return j;
    }
  }
  return s.length;
}

function parseYyMmDd(yyMmDd: string): string | null {
  if (yyMmDd.length !== 6) return null;
  const yy = Number(yyMmDd.slice(0, 2));
  const mm = Number(yyMmDd.slice(2, 4));
  let dd = Number(yyMmDd.slice(4, 6));
  const year = yy >= 70 ? 1900 + yy : 2000 + yy;
  if (dd === 0) dd = 1;
  const iso = `${String(year).padStart(4, '0')}-${String(mm).padStart(2, '0')}-${String(dd).padStart(2, '0')}`;
  return Number.isFinite(Date.parse(iso)) ? iso : null;
}

export function parseGs1Barcode(barcode: string | null | undefined): Gs1Elements | null {
  if (!barcode || !barcode.trim()) return null;

  let normalized = barcode
    .trim()
    .replace(/]C1/g, '')
    .replace(/\{GS\}/g, FNC1)
    .replace(/\(01\)/g, '01')
    .replace(/\(10\)/g, '10')
    .replace(/\(17\)/g, '17')
    .replace(/\(21\)/g, '21');

  if (normalized.includes('(') && normalized.includes(')')) {
    normalized = normalized.replace(/[()]/g, '');
  }

  if (!looksLikeGs1(normalized)) return null;

  const elements: Record<string, string> = {};
  let i = 0;
  while (i < normalized.length) {
    if (normalized[i] === FNC1) {
      i++;
      continue;
    }
    if (i + 2 > normalized.length) break;
    const ai = normalized.slice(i, i + 2);
    i += 2;
    if (ai === '01') {
      if (i + 14 > normalized.length) return null;
      elements['01'] = normalized.slice(i, i + 14);
      i += 14;
    } else if (['17', '11', '13', '15'].includes(ai)) {
      if (i + 6 > normalized.length) return null;
      elements[ai] = normalized.slice(i, i + 6);
      i += 6;
    } else if (ai === '10' || ai === '21') {
      let end = Math.min(indexOfFnc1OrEnd(normalized, i), findNextFixedAiBoundary(normalized, i));
      const value = normalized.slice(i, end);
      if (!value || value.length > 20) return null;
      elements[ai] = value;
      i = end;
      if (i < normalized.length && normalized[i] === FNC1) i++;
    } else {
      return null;
    }
  }

  if (!elements['01'] && !elements['10'] && !elements['17']) return null;

  return {
    raw: barcode.trim(),
    gtin: elements['01'] ?? null,
    lot: elements['10'] ?? null,
    expiry: elements['17'] ? parseYyMmDd(elements['17']) : null,
    serial: elements['21'] ?? null,
    all: elements,
  };
}

/** Prefer GTIN (AI 01) as catalog lookup key when present. */
export function gs1LookupKey(barcode: string): string {
  const parsed = parseGs1Barcode(barcode);
  return parsed?.gtin ?? barcode.trim();
}
