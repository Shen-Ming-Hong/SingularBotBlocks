import {build} from 'esbuild';
import {cp, mkdir, readFile, readdir, writeFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {dirname, resolve} from 'node:path';
const out = resolve('../build/generated/editor/assets/singularbots/html');
await mkdir(out, {recursive:true});
await build({entryPoints:['src/editor.js'],bundle:true,outfile:out+'/editor.js',target:'chrome116',minify:true,legalComments:'eof'});
await cp('src/index.html', out+'/index.html');
await cp('src/boot.js', out+'/boot.js');
await cp('src/style.css', out+'/style.css');
await cp('src/theme.css', out+'/theme.css');
await cp('assets', out+'/assets', {recursive:true});
await cp('node_modules/blockly/media', out+'/media', {recursive:true});
await cp('assets/Blockly-LICENSE.txt', out+'/blockly-license.txt');
// One script per Minecraft language: the editor's own text (i18n/<locale>.json) and Blockly's messages for the
// nearest language Blockly has. Classroom Chinese and English are already inside editor.js.
const require = createRequire(import.meta.url);
const languages = JSON.parse(await readFile('i18n/languages.json', 'utf8'));
const catalogs = new Set((await readdir('i18n')).filter(f => f !== 'languages.json' && f.endsWith('.json')).map(f => f.slice(0, -5)));
await mkdir(out+'/i18n', {recursive:true});
for (const [locale, {blockly}] of Object.entries(languages)) {
  if (locale === 'zh_tw' || locale === 'en_us') continue;
  const ui = catalogs.has(locale) ? JSON.parse(await readFile(`i18n/${locale}.json`, 'utf8')) : {};
  const messages = blockly && blockly !== 'en' ? {...require(`blockly/msg/${blockly}`)} : {};
  await writeFile(`${out}/i18n/${locale}.js`, `window.CCEDU_I18N=${JSON.stringify({locale, ui, blockly:messages})};\n`);
}
await writeFile(out+'/versions.json',JSON.stringify({blockly:'11.2.2',targetChromium:116,stage:'browser-foundation'}));
console.log('Bundled offline Blockly 11.2.2 editor:', out);
