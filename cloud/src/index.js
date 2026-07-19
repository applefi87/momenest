/**
 * 環境監測 — Cloudflare Worker 入口（路由）
 *
 * 路由：
 *   POST /api/ingest  — ESP32 上傳 (需 X-API-Key，密鑰存於 Worker secret)
 *   GET  /api/data    — 公開查詢歷史 (?from=&to= unix秒, ?limit=)
 *   GET  /api/latest  — 公開查詢最新一筆
 *   GET  /            — 儀表板網頁 (dashboard.html，經 wrangler Text 規則內嵌)
 *
 * 模組：api.js = D1 讀寫；dashboard.html = 儀表板網頁
 */
import { CORS, json, handleIngest, handleData, handleLatest } from './api.js';
import DASHBOARD_HTML from './dashboard.html';
import { MANIFEST, SW_JS, ICON_SVG } from './pwa.js';
import ICON_PNG from './icon-180.png';

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'OPTIONS') return new Response(null, { headers: CORS });

    if (path === '/api/ingest' && request.method === 'POST') return handleIngest(request, env);
    if (path === '/api/data' && request.method === 'GET') return handleData(url, env);
    if (path === '/api/latest' && request.method === 'GET') return handleLatest(env);

    if (path === '/' && request.method === 'GET') {
      return new Response(DASHBOARD_HTML, {
        headers: {
          'Content-Type': 'text/html; charset=utf-8',
          // 每次都向伺服器驗證，避免部署新版後瀏覽器仍用舊快取
          'Cache-Control': 'no-cache',
        },
      });
    }

    // ---- PWA 靜態資源 ----
    if (request.method === 'GET') {
      const day = 'public, max-age=86400';
      if (path === '/manifest.webmanifest')
        return new Response(MANIFEST, { headers: { 'Content-Type': 'application/manifest+json', 'Cache-Control': day } });
      if (path === '/sw.js')
        return new Response(SW_JS, { headers: { 'Content-Type': 'application/javascript', 'Cache-Control': 'no-cache' } });
      if (path === '/icon.svg')
        return new Response(ICON_SVG, { headers: { 'Content-Type': 'image/svg+xml', 'Cache-Control': day } });
      if (path === '/icon-180.png')
        return new Response(ICON_PNG, { headers: { 'Content-Type': 'image/png', 'Cache-Control': day } });
    }

    return json({ error: 'not found' }, 404);
  },
};
