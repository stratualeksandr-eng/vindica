/* «Верни себя себе»: работа без сети. Сборка c30da90b94b5 */
const V = 'vindica-c30da90b94b5';
const CORE = ["./", "index.html", "manifest.webmanifest", "apple-touch-icon.png", "icon-192.png", "icon-512.png", "maskable-512.png", "sc-idea.png", "sc-idea-mask.png", "sc-morning.png", "sc-evening.png", "sc-voice.png"];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(V).then(c => c.addAll(CORE.map(u => new Request(u, { cache: 'reload' })))).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k.indexOf('vindica-') === 0 && k !== V).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const r = e.request;
  if (r.method !== 'GET') return;
  if (new URL(r.url).origin !== self.location.origin) return; /* погода идёт в сеть как есть */
  if (r.mode === 'navigate') { e.respondWith(caches.open(V).then(c => c.match('index.html')).then(m => m || fetch(r))); return; }
  e.respondWith(caches.open(V).then(c => c.match(r, { ignoreSearch: true })).then(m => m || fetch(r)));
});
/* напоминание из «Плана»: нажатие открывает приложение на этом дне */
self.addEventListener('notificationclick', e => {
  e.notification.close();
  const k = e.notification.data && e.notification.data.k;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(L => {
    for (const c of L) if ('focus' in c) { c.postMessage({ type: 'plgo', k: k }); return c.focus(); }
    return self.clients.openWindow('./#plan');
  }));
});
