// Loads the editor text and Blockly messages for the game's language (i18n/<lang>.js) before editor.js runs.
// Classroom Chinese and English are built into editor.js; a language without a file stays in English.
(function(){var lang=new URLSearchParams(location.search).get('lang')||'';
 if(/^[a-z]{2,4}(_[a-z0-9]{2,4})?$/.test(lang)&&lang!=='zh_tw'&&lang!=='en_us')document.write('<script src="i18n/'+lang+'.js"><\/script>');})();
