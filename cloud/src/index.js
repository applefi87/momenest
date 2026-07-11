/**
 * 環境監測 — Cloudflare Worker (單一 Worker 包辦 API + 儀表板)
 *
 * 路由：
 *   POST /api/ingest  — ESP32 上傳 (需 X-API-Key，密鑰存於 Worker secret)
 *   GET  /api/data    — 公開查詢歷史 (?from=&to= unix秒, ?limit=)
 *   GET  /api/latest  — 公開查詢最新一筆
 *   GET  /            — 儀表板網頁 (即時數值 + 歷史折線圖)
 */

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-API-Key',
};

const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  });

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === 'OPTIONS') return new Response(null, { headers: CORS });

    // ---------- ESP32 上傳 (寫入需密鑰) ----------
    if (path === '/api/ingest' && request.method === 'POST') {
      if (request.headers.get('X-API-Key') !== env.API_KEY) {
        return json({ error: 'unauthorized' }, 401);
      }
      let b;
      try { b = await request.json(); } catch { return json({ error: 'bad json' }, 400); }

      const num = (v) => (typeof v === 'number' && isFinite(v) ? v : null);
      await env.DB.prepare(
        `INSERT INTO readings (ts, air_temp, air_hum, water_temp, soil, water_level)
         VALUES (?, ?, ?, ?, ?, ?)`
      ).bind(
        Math.floor(Date.now() / 1000),   // 伺服器時間戳，ESP32 免 NTP
        num(b.air_temp), num(b.air_hum), num(b.water_temp),
        num(b.soil), num(b.water_level)
      ).run();
      return json({ ok: true });
    }

    // ---------- 公開讀取：歷史區間 ----------
    if (path === '/api/data' && request.method === 'GET') {
      const now = Math.floor(Date.now() / 1000);
      const from = parseInt(url.searchParams.get('from')) || now - 86400; // 預設近 24h
      const to = parseInt(url.searchParams.get('to')) || now;
      const limit = Math.min(parseInt(url.searchParams.get('limit')) || 2000, 5000);

      const { results } = await env.DB.prepare(
        `SELECT ts, air_temp, air_hum, water_temp, soil, water_level
         FROM readings WHERE ts BETWEEN ? AND ?
         ORDER BY ts ASC LIMIT ?`
      ).bind(from, to, limit).all();
      return json(results);
    }

    // ---------- 公開讀取：最新一筆 ----------
    if (path === '/api/latest' && request.method === 'GET') {
      const { results } = await env.DB.prepare(
        `SELECT ts, air_temp, air_hum, water_temp, soil, water_level
         FROM readings ORDER BY ts DESC LIMIT 1`
      ).all();
      return json(results[0] ?? null);
    }

    // ---------- 儀表板 ----------
    if (path === '/' && request.method === 'GET') {
      return new Response(DASHBOARD_HTML, {
        headers: { 'Content-Type': 'text/html; charset=utf-8' },
      });
    }

    return json({ error: 'not found' }, 404);
  },
};

// ============================================================
// 儀表板網頁 (內嵌於 Worker，單一部署)
// ============================================================
const DASHBOARD_HTML = `<!DOCTYPE html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>環境監測</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4"></script>
<style>
  body { font-family: system-ui, sans-serif; margin: 0; background: #111; color: #eee; }
  h1 { font-size: 1.2rem; padding: 12px 16px; margin: 0; border-bottom: 1px solid #333; }
  #cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; padding: 12px; }
  .card { background: #1c1c1c; border-radius: 10px; padding: 12px; text-align: center; }
  .card .label { font-size: .8rem; color: #999; }
  .card .value { font-size: 1.6rem; font-weight: 700; margin-top: 4px; }
  #range { display: flex; gap: 8px; padding: 0 12px; }
  #range button { flex: 1; padding: 8px; border: 1px solid #444; background: #1c1c1c; color: #eee; border-radius: 8px; }
  #range button.on { background: #2563eb; border-color: #2563eb; }
  .chartBox { padding: 12px; }
  canvas { background: #1c1c1c; border-radius: 10px; }
  #updated { text-align: center; color: #777; font-size: .75rem; padding-bottom: 16px; }
</style>
</head>
<body>
<h1>環境監測儀表板</h1>
<div id="cards"></div>
<div id="range"></div>
<div class="chartBox"><canvas id="chartT"></canvas></div>
<div class="chartBox"><canvas id="chartA"></canvas></div>
<div id="updated"></div>
<script>
const FIELDS = [
  { key: 'air_temp',    label: '空氣溫度', unit: '°C', color: '#facc15' },
  { key: 'air_hum',     label: '空氣濕度', unit: '%',  color: '#38bdf8' },
  { key: 'water_temp',  label: '水溫',     unit: '°C', color: '#4ade80' },
  { key: 'soil',        label: '土壤(ADC)', unit: '',  color: '#fb923c' },
  { key: 'water_level', label: '水位(ADC)', unit: '',  color: '#818cf8' },
];
const RANGES = [ ['1小時',3600], ['24小時',86400], ['7天',604800], ['30天',2592000] ];
let rangeSec = 86400, chartT, chartA;

// 即時數值卡片
async function refreshLatest() {
  const r = await (await fetch('/api/latest')).json();
  const el = document.getElementById('cards');
  el.innerHTML = FIELDS.map(f => {
    const v = r && r[f.key] != null ? r[f.key] + f.unit : '--';
    return '<div class="card"><div class="label">' + f.label + '</div><div class="value" style="color:' + f.color + '">' + v + '</div></div>';
  }).join('');
  if (r) document.getElementById('updated').textContent =
    '最後更新：' + new Date(r.ts * 1000).toLocaleString();
}

// 歷史折線圖 (溫濕度一張、類比感測一張)
async function refreshChart() {
  const now = Math.floor(Date.now() / 1000);
  const rows = await (await fetch('/api/data?from=' + (now - rangeSec) + '&to=' + now)).json();
  const labels = rows.map(r => new Date(r.ts * 1000).toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit' }));
  const ds = (key, color) => ({
    label: FIELDS.find(f => f.key === key).label,
    data: rows.map(r => r[key]), borderColor: color,
    pointRadius: 0, borderWidth: 1.5, tension: .3, spanGaps: true,
  });
  const opts = { responsive: true, scales: { x: { ticks: { maxTicksLimit: 8, color: '#888' } }, y: { ticks: { color: '#888' } } }, plugins: { legend: { labels: { color: '#ccc' } } } };
  chartT?.destroy(); chartA?.destroy();
  chartT = new Chart(document.getElementById('chartT'),
    { type: 'line', data: { labels, datasets: [ds('air_temp','#facc15'), ds('air_hum','#38bdf8'), ds('water_temp','#4ade80')] }, options: opts });
  chartA = new Chart(document.getElementById('chartA'),
    { type: 'line', data: { labels, datasets: [ds('soil','#fb923c'), ds('water_level','#818cf8')] }, options: opts });
}

// 時間範圍按鈕
document.getElementById('range').innerHTML = RANGES.map(([t, s]) =>
  '<button data-s="' + s + '"' + (s === rangeSec ? ' class="on"' : '') + '>' + t + '</button>').join('');
document.getElementById('range').onclick = (e) => {
  if (!e.target.dataset.s) return;
  rangeSec = +e.target.dataset.s;
  document.querySelectorAll('#range button').forEach(b => b.classList.toggle('on', b === e.target));
  refreshChart();
};

refreshLatest(); refreshChart();
setInterval(refreshLatest, 10000);   // 即時卡片每 10 秒更新
setInterval(refreshChart, 60000);    // 圖表每分鐘更新
</script>
</body>
</html>`;
