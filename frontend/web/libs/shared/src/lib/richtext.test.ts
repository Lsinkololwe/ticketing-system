import { describe, expect, it } from 'vitest';
import { isRichHtml, richToText, richView, sanitizeRich } from './richtext';

describe('sanitizeRich', () => {
  it('keeps the allowed subset and strips attributes', () => {
    expect(sanitizeRich('<p class="x" onclick="evil()">Hi <b style="color:red">there</b></p>')).toBe('<p>Hi <b>there</b></p>');
  });
  it('removes scripts, styles and frames with their content', () => {
    expect(sanitizeRich('<p>a</p><script>alert(1)</script><style>p{}</style><iframe src="x">z</iframe><p>b</p>')).toBe('<p>a</p><p>b</p>');
  });
  it('unwraps disallowed tags (links, images) but keeps their text', () => {
    expect(sanitizeRich('<a href="javascript:alert(1)">click</a><img src=x onerror=alert(1)>')).toBe('click');
  });
  it('escapes stray angle brackets and ampersands', () => {
    expect(sanitizeRich('1 < 2 & 3 > 2')).toBe('1 &lt; 2 &amp; 3 &gt; 2');
    expect(sanitizeRich('&lt;script&gt;x&lt;/script&gt;')).toBe('&lt;script&gt;x&lt;/script&gt;');
  });
  it('closes unbalanced tags and ignores stray closers', () => {
    expect(sanitizeRich('<ul><li>one<li>two</ul></b>')).toBe('<ul><li>one<li>two</li></li></ul>');
  });
  it('never emits an attribute or an event handler', () => {
    const out = sanitizeRich('<div onmouseover="x" data-a="1"><h3 id="a">T</h3><br/></div>');
    expect(out).toBe('<div><h3>T</h3><br></div>');
    expect(out).not.toMatch(/\son\w+=|javascript:/i);
  });
});

describe('richView / richToText', () => {
  it('turns legacy plain text into escaped paragraphs', () => {
    expect(isRichHtml('plain')).toBe(false);
    expect(richView('a & b\nline')).toBe('<p>a &amp; b<br>line</p>');
  });
  it('plain text of markup', () => {
    expect(richToText('<p>Hello</p><ul><li>one</li><li>two</li></ul>')).toBe('Hello one two');
    expect(richToText('')).toBe('');
  });
});
