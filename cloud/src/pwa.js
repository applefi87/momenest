/**
 * PWA 資源 — manifest / service worker / SVG 圖示
 * （PNG 圖示在 icon-180.png，經 wrangler Data 規則 import）
 */

export const MANIFEST = JSON.stringify({
  name: '環境監測',
  short_name: '環境監測',
  start_url: '/',
  scope: '/',
  display: 'standalone',
  background_color: '#f5f5f7',
  theme_color: '#f5f5f7',
  icons: [
    { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any maskable' },
    { src: '/icon-180.png', sizes: '180x180', type: 'image/png' },
  ],
});

// 網頁殼 network-first、離線退回快取；/api 即時資料不快取
export const SW_JS = `
const CACHE = 'env-monitor-v1';
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(clients.claim()));
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.pathname.startsWith('/api/')) return;
  e.respondWith(
    fetch(e.request).then(r => {
      const copy = r.clone();
      caches.open(CACHE).then(c => c.put(e.request, copy));
      return r;
    }).catch(() => caches.match(e.request))
  );
});
`;

export const ICON_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 180 180">
<rect width="180" height="180" fill="#34c759"/>
<path d="M90 38c-20 26-34 44-34 62a34 34 0 0 0 68 0c0-18-14-36-34-62z" fill="#fff"/>
<path d="M74 106a16 16 0 0 0 32 0" fill="none" stroke="#34c759" stroke-width="7" stroke-linecap="round"/>
</svg>`;
