/**
 * Rich text for event and organization descriptions.
 *
 * Contract: the backend stores a plain string. A description is either legacy plain text (no tags) or a
 * small HTML subset produced by `sanitizeRich`: bold, italic, underline, a heading, bulleted and numbered
 * lists, quotes, paragraphs and line breaks, with no attributes. Nothing else survives: scripts, styles,
 * frames, event handlers, links and images are dropped. The sanitiser is a string tokeniser (no DOM) so it
 * gives the same result on the server and in the browser, and the output is rebuilt from escaped text and
 * bare allowed tags only, so it is safe to render with `dangerouslySetInnerHTML`.
 */
const ALLOWED = new Set(['b', 'i', 'u', 'strong', 'em', 'h3', 'ul', 'ol', 'li', 'p', 'br', 'blockquote', 'div']);
const VOID = new Set(['br']);
const DROP_WITH_CONTENT = new Set(['script', 'style', 'iframe', 'object', 'embed', 'link', 'meta', 'template', 'noscript', 'svg', 'math']);
const BLOCK_END = /<\/(p|li|div|h3|blockquote)>|<br\s*\/?>/gi;

const ENTITIES: Record<string, string> = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ' };
const decode = (s: string) =>
  s.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (m, e: string) => {
    if (e[0] === '#') {
      const n = e[1].toLowerCase() === 'x' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return Number.isFinite(n) && n > 0 && n < 0x110000 ? String.fromCodePoint(n) : '';
    }
    return ENTITIES[e.toLowerCase()] ?? m;
  });
export const escapeText = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** True when the value carries markup (as opposed to legacy plain text). */
export const isRichHtml = (v: unknown) => /<[a-z][\s\S]*>/i.test(String(v ?? ''));

/** Reduce arbitrary markup to the allowed subset. Always returns well-formed, attribute-free HTML. */
export function sanitizeRich(input: unknown): string {
  const src = String(input ?? '');
  const out: string[] = [];
  const stack: string[] = [];
  const re = /<(\/?)([a-zA-Z][a-zA-Z0-9]*)\b[^>]*?>|<!--[\s\S]*?-->/g;
  let last = 0;
  let skipUntil: string | null = null;
  const text = (t: string) => {
    if (t) out.push(escapeText(decode(t)));
  };
  for (let m = re.exec(src); m; m = re.exec(src)) {
    const chunk = src.slice(last, m.index);
    last = m.index + m[0].length;
    const closing = m[1] === '/';
    const name = (m[2] ?? '').toLowerCase();
    if (skipUntil) {
      if (closing && name === skipUntil) skipUntil = null;
      continue;
    }
    text(chunk);
    if (!name) continue; // comment
    if (!closing && DROP_WITH_CONTENT.has(name)) {
      if (!/\/\s*>$/.test(m[0])) skipUntil = name;
      continue;
    }
    if (!ALLOWED.has(name)) continue; // unwrap: keep the text, drop the tag
    if (VOID.has(name)) {
      if (!closing) out.push('<br>');
      continue;
    }
    if (!closing) {
      stack.push(name);
      out.push(`<${name}>`);
    } else {
      const i = stack.lastIndexOf(name);
      if (i === -1) continue;
      while (stack.length > i) out.push(`</${stack.pop()}>`);
    }
  }
  if (!skipUntil) text(src.slice(last));
  while (stack.length) out.push(`</${stack.pop()}>`);
  return out.join('');
}

/** What to render: legacy plain text becomes escaped paragraphs; markup is sanitised. */
export function richView(value: unknown): string {
  const v = String(value ?? '');
  if (!v.trim()) return '';
  if (!isRichHtml(v)) return `<p>${escapeText(v).replace(/\n/g, '<br>')}</p>`;
  return sanitizeRich(v);
}

/** Plain text of a description (for character counts, search and previews). */
export function richToText(value: unknown): string {
  const v = String(value ?? '');
  if (!isRichHtml(v)) return v.trim();
  const spaced = sanitizeRich(v.replace(BLOCK_END, ' '));
  return decode(spaced.replace(/<[^>]*>/g, '')).replace(/\s+/g, ' ').trim();
}
