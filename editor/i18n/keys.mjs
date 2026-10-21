// The English text of every editor message, found in the source the way it is written: a Chinese string literal
// followed by its English string literal, as in tr('中文','English'), ['中文','English'] or a page.js entry.
import {readFile, readdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const SRC = fileURLToPath(new URL('../src/', import.meta.url));
const LITERAL = /'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*"/g;
const CJK = /[　-ヿ㐀-鿿＀-￯]/;
const value = literal => Function(`return ${literal}`)();
/** English text → the classroom Chinese written beside it (the first one found). */
export async function editorPairs() {
  const keys = new Map();
  for (const file of (await readdir(SRC)).filter(f => f.endsWith('.js')).sort()) {
    if (file === 'wording.js' || file === 'i18n.js') continue; // Chinese-only Blockly wording; i18n.js only has examples in comments
    const source = await readFile(SRC + file, 'utf8');
    const literals = [...source.matchAll(LITERAL)];
    for (let i = 0; i + 1 < literals.length; i++) {
      const [a, b] = [literals[i], literals[i + 1]];
      if (!/^\s*,\s*$/.test(source.slice(a.index + a[0].length, b.index))) continue;
      const zh = value(a[0]), en = value(b[0]);
      if (CJK.test(zh) && !CJK.test(en) && en.trim() && !keys.has(en)) keys.set(en, zh);
    }
  }
  return new Map([...keys].sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0));
}
export const editorKeys = async () => [...(await editorPairs()).keys()];
/** The placeholders (%1, %2…) a text uses, so a translation can be checked against its English. */
export const placeholders = text => [...String(text).matchAll(/%\d+/g)].map(m => m[0]).sort();
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.stdout.write(JSON.stringify(Object.fromEntries(await editorPairs()), null, 1) + '\n');
}
