// Собирает веб-часть приложения: в www/ для телефона (Capacitor) и в desktop/www/ для компьютера (Electron).
// Источник — файлы сайта в корне репозитория (тот же index.html, что открывается на GitHub Pages).
const fs = require('fs');
const path = require('path');
const ROOT = path.join(__dirname, '..');
const FILES = ['index.html', 'manifest.webmanifest', 'apple-touch-icon.png', 'icon-192.png', 'icon-512.png', 'maskable-512.png', 'sc-idea.png', 'sc-idea-mask.png', 'sc-morning.png', 'sc-evening.png', 'sc-voice.png'];
const targets = process.argv.slice(2).length ? process.argv.slice(2) : ['www', 'desktop/www'];
const VER = /^\d+\.\d+\.\d+$/.test(process.env.VINDICA_VERSION || '') ? process.env.VINDICA_VERSION : '';
if (!fs.existsSync(path.join(ROOT, 'index.html'))) { console.error('Нет index.html в корне репозитория'); process.exit(1); }
for (const t of targets) {
  const dir = path.join(ROOT, t);
  fs.rmSync(dir, { recursive: true, force: true });
  fs.mkdirSync(dir, { recursive: true });
  for (const f of FILES) if (fs.existsSync(path.join(ROOT, f))) fs.copyFileSync(path.join(ROOT, f), path.join(dir, f));
  // номер выпуска (его задаёт сборка на GitHub): по нему приложение узнаёт, что вышла версия новее
  if (VER) { const p = path.join(dir, 'index.html'); fs.writeFileSync(p, fs.readFileSync(p, 'utf8').split('__VINDICA_VERSION__').join(VER)); }
  console.log('готово:', t, fs.readdirSync(dir).length, 'файлов', VER ? '· версия ' + VER : '');
}
