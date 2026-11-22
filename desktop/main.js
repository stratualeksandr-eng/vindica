// «Верни себя себе» на компьютере.
// The same page as the phone app, served from the app's own app:// address (so the records stay in IndexedDB between runs).
// Closing the window hides it to the tray: the program keeps running, so reminders from «План» arrive on time,
// and it starts with the computer (hidden), which the tray menu can switch off.
const { app, BrowserWindow, Tray, Menu, nativeImage, protocol, net, ipcMain, shell, session, screen } = require('electron');
const path = require('path');
const fs = require('fs');
const { pathToFileURL } = require('url');

const WWW = path.join(__dirname, 'www');
const ICON = path.join(__dirname, 'build', 'icon.png');
const HIDDEN = process.argv.includes('--hidden');
let win = null, tray = null, quitting = false;

// The window opens large, fitted to the screen (on a wide window the page shows more tasks side by side),
// and remembers its size and place between runs.
const BOUNDS = () => path.join(app.getPath('userData'), 'window.json');
function bounds() {
  try {
    const s = JSON.parse(fs.readFileSync(BOUNDS(), 'utf8'));
    const a = screen.getDisplayMatching(s).workArea;
    const ix = Math.min(s.x + s.width, a.x + a.width) - Math.max(s.x, a.x), iy = Math.min(s.y + s.height, a.y + a.height) - Math.max(s.y, a.y);
    if (s.width >= 360 && s.height >= 560 && ix >= 200 && iy >= 200) return s;
  } catch (e) {}
  const a = screen.getPrimaryDisplay().workArea;
  const width = Math.max(360, Math.min(1440, Math.round(a.width * 0.92))), height = Math.max(560, Math.min(1000, Math.round(a.height * 0.92)));
  return { x: Math.round(a.x + (a.width - width) / 2), y: Math.round(a.y + (a.height - height) / 2), width, height, max: a.width < 1100 };
}
function keepBounds() {
  if (!win || win.isMinimized() || win.isFullScreen()) return;
  try { fs.writeFileSync(BOUNDS(), JSON.stringify({ ...win.getNormalBounds(), max: win.isMaximized() })); } catch (e) {}
}

protocol.registerSchemesAsPrivileged([{ scheme: 'app', privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } }]);
if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', () => show());
app.setAppUserModelId('app.vindica.desktop');

function show() {
  if (!win) return create(true);
  if (win.isMinimized()) win.restore();
  win.show(); win.focus();
}

function create(visible) {
  const b = bounds();
  win = new BrowserWindow({
    x: b.x, y: b.y, width: b.width, height: b.height, minWidth: 360, minHeight: 560,
    show: false, backgroundColor: '#F4F0E9', title: 'Верни себя себе', icon: ICON, autoHideMenuBar: true,
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true, backgroundThrottling: false, spellcheck: true }
  });
  win.setMenuBarVisibility(false);
  win.loadURL('app://vindica/index.html');
  // maximize() would also show the window, so it waits until the window is meant to be seen
  win.once('ready-to-show', () => { if (visible) { if (b.max) win.maximize(); win.show(); } });
  let kt = 0;
  const later = () => { clearTimeout(kt); kt = setTimeout(keepBounds, 500); };
  ['resize', 'move', 'maximize', 'unmaximize'].forEach(ev => win.on(ev, later));
  win.on('close', e => { keepBounds(); if (!quitting) { e.preventDefault(); win.hide(); } });
  win.on('closed', () => { win = null; });
  // links outside the app (Google Calendar, a web page) open in the usual browser
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^(https?|mailto):/.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('app://')) { e.preventDefault(); if (/^https?:/.test(url)) shell.openExternal(url); } });
}

function menu() {
  const login = app.getLoginItemSettings().openAtLogin;
  return Menu.buildFromTemplate([
    { label: 'Открыть', click: show },
    { label: 'Запускать вместе с компьютером', type: 'checkbox', checked: login, click: it => { app.setLoginItemSettings({ openAtLogin: it.checked, openAsHidden: true, args: ['--hidden'] }); tray.setContextMenu(menu()); } },
    { type: 'separator' },
    { label: 'Выйти', click: () => { quitting = true; app.quit(); } }
  ]);
}

app.whenReady().then(() => {
  protocol.handle('app', req => {
    const u = new URL(req.url);
    let p = decodeURIComponent(u.pathname);
    if (p === '/' || p === '') p = '/index.html';
    const f = path.normalize(path.join(WWW, p));
    if (!f.startsWith(WWW) || !fs.existsSync(f)) return new Response('Not found', { status: 404 });
    return net.fetch(pathToFileURL(f).toString());
  });
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb) => cb(['notifications', 'clipboard-sanitized-write', 'clipboard-read', 'fullscreen'].includes(perm)));
  session.defaultSession.setPermissionCheckHandler((wc, perm) => ['notifications', 'clipboard-sanitized-write', 'clipboard-read'].includes(perm));
  // the first start: start with the computer, quietly in the tray (the menu switches it off)
  const flag = path.join(app.getPath('userData'), 'login-set');
  if (!fs.existsSync(flag)) {
    try { app.setLoginItemSettings({ openAtLogin: true, openAsHidden: true, args: ['--hidden'] }); fs.writeFileSync(flag, '1'); } catch (e) {}
  }
  create(!HIDDEN);
  const img = nativeImage.createFromPath(path.join(__dirname, 'build', 'tray.png'));
  tray = new Tray(img);
  tray.setToolTip('Верни себя себе');
  tray.on('click', show);
  tray.setContextMenu(menu());
});

ipcMain.on('vindica:show', () => show());
app.on('before-quit', () => { quitting = true; });
app.on('activate', () => show());
app.on('window-all-closed', () => { /* stays in the tray */ });
