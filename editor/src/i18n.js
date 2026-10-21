// The editor speaks the player's Minecraft language. Traditional Chinese (the classroom wording) and English are
// written side by side in the code as tr('中文','English'); every other language looks the English text up in its
// catalog, editor/i18n/<locale>.json, which the build bundles with Blockly's own messages into html/i18n/<locale>.js.
// Text a catalog does not have yet stays in English, like Minecraft's own fallback.
let current='zh_tw',catalog={};
// Minecraft language codes, e.g. zh_tw, en_us, fil_ph, lol_us, bar, zlm_arab.
export const LOCALE=/^[a-z]{2,4}(_[a-z0-9]{2,4})?$/;
export function setLanguage(locale,messages){current=LOCALE.test(locale||'')?locale:'en_us';catalog=messages&&typeof messages==='object'?messages:{};}
export const language=()=>current;
/** Runs fn with another language's words, e.g. a toolbox built for a given locale, then switches back. */
export function inLanguage(locale,fn){if(!locale||locale===current)return fn();const [was,words]=[current,catalog];current=locale;try{return fn();}finally{current=was;catalog=words;}}
export const isChinese=()=>current==='zh_tw';
/** Fills %1, %2… with the arguments; text without arguments is returned as is, so Blockly's own %1 slots survive. */
export function fill(text,args){return args.length?text.replace(/%(\d+)/g,(m,n)=>n>=1&&n<=args.length?String(args[n-1]):m):text;}
/** The English text in the current language. */
export function translate(en,...args){const local=catalog[en];return fill(typeof local==='string'&&local?local:en,args);}
/** Classroom Traditional Chinese, or the English text in the current language. */
export function tr(zh,en,...args){return current==='zh_tw'?fill(zh,args):translate(en,...args);}
/** The HTML lang attribute for a Minecraft language code. */
export function htmlLang(locale=current){
 if(locale==='zh_tw'||locale==='zh_hk')return 'zh-Hant';
 if(locale==='zh_cn')return 'zh-Hans';
 if(locale==='lzh')return 'lzh';
 const [lang,region]=locale.split('_');
 return region&&/^[a-z]{2}$/.test(region)?`${lang}-${region.toUpperCase()}`:lang;
}
