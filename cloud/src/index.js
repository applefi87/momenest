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
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<title>環境監測</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js@4"></script>
<style>
:root {
  --bg: #f5f5f7;
  --card-bg: rgba(255,255,255,0.72);
  --card-border: rgba(0,0,0,0.06);
  --card-shadow: 0 2px 16px rgba(0,0,0,0.04);
  --text-primary: #1d1d1f;
  --text-secondary: #86868b;
  --text-tertiary: #aeaeb2;
  --seg-bg: rgba(118,118,128,0.12);
  --seg-active: #fff;
  --seg-shadow: 0 1px 4px rgba(0,0,0,0.08);
  --chart-bg: rgba(255,255,255,0.72);
  --grid-color: rgba(0,0,0,0.04);
  --tick-color: #86868b;
  --radius: 16px;
  --safe-top: env(safe-area-inset-top, 0px);
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #000;
    --card-bg: rgba(28,28,30,0.72);
    --card-border: rgba(255,255,255,0.08);
    --card-shadow: 0 2px 16px rgba(0,0,0,0.3);
    --text-primary: #f5f5f7;
    --text-secondary: #98989d;
    --text-tertiary: #636366;
    --seg-bg: rgba(118,118,128,0.24);
    --seg-active: rgba(50,50,52,1);
    --seg-shadow: 0 1px 4px rgba(0,0,0,0.3);
    --chart-bg: rgba(28,28,30,0.72);
    --grid-color: rgba(255,255,255,0.05);
    --tick-color: #98989d;
  }
}
* { margin: 0; padding: 0; box-sizing: border-box; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", system-ui, sans-serif;
  background: var(--bg);
  color: var(--text-primary);
  -webkit-font-smoothing: antialiased;
  padding: 0 16px;
  padding-top: calc(var(--safe-top) + 16px);
  padding-bottom: 32px;
  min-height: 100dvh;
}

/* Header */
.header { padding: 8px 0 20px; }
.header h1 {
  font-size: 2rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.15;
}
.header .subtitle {
  font-size: 0.82rem;
  color: var(--text-secondary);
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.status-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: #30d158;
  display: inline-block;
  animation: pulse 2s ease-in-out infinite;
}
.status-dot.offline { background: #ff453a; animation: none; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.4; } }

/* Cards grid */
.cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}
.card {
  background: var(--card-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--card-border);
  border-radius: var(--radius);
  padding: 16px;
  box-shadow: var(--card-shadow);
  position: relative;
  overflow: hidden;
  transition: transform 0.2s ease;
}
.card:active { transform: scale(0.97); }
.cards .card:last-child:nth-child(odd) { grid-column: 1 / -1; }
.card .label {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.02em;
  display: flex;
  align-items: center;
  gap: 5px;
}
.card .label .dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.card .value {
  font-size: 2rem;
  font-weight: 500;
  letter-spacing: -0.03em;
  margin-top: 6px;
  font-variant-numeric: tabular-nums;
  transition: opacity 0.3s ease;
}
.card .unit {
  font-size: 1rem;
  font-weight: 400;
  color: var(--text-secondary);
  margin-left: 2px;
}
.card .spark {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 32px;
  opacity: 0.4;
}

/* Segmented control */
.seg-wrap { margin-bottom: 20px; }
.seg {
  display: flex;
  background: var(--seg-bg);
  border-radius: 9px;
  padding: 2px;
  position: relative;
}
.seg button {
  flex: 1;
  padding: 7px 0;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 0.8rem;
  font-weight: 500;
  border-radius: 7px;
  cursor: pointer;
  position: relative;
  z-index: 1;
  transition: color 0.2s;
  font-family: inherit;
}
.seg .slider {
  position: absolute;
  top: 2px; bottom: 2px;
  border-radius: 7px;
  background: var(--seg-active);
  box-shadow: var(--seg-shadow);
  transition: left 0.25s cubic-bezier(0.4, 0, 0.2, 1), width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 0;
}

/* Chart */
.chart-section {
  background: var(--card-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--card-border);
  border-radius: var(--radius);
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: var(--card-shadow);
}
.chart-section .chart-title {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.chart-section .legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-weight: 500;
}
.chart-section .legend-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
}
.chart-section canvas { width: 100% !important; }

/* Calibration modal */
.cal-btn {
  display: block;
  margin: 16px auto 0;
  padding: 8px 16px;
  border: 1px solid var(--card-border);
  background: var(--card-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--text-secondary);
  font-size: 0.75rem;
  font-weight: 500;
  border-radius: 20px;
  cursor: pointer;
  font-family: inherit;
}
.modal-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  z-index: 100;
  align-items: flex-end;
  justify-content: center;
}
.modal-overlay.show { display: flex; }
.modal {
  background: var(--card-bg);
  backdrop-filter: blur(40px);
  -webkit-backdrop-filter: blur(40px);
  border: 1px solid var(--card-border);
  border-radius: 20px 20px 0 0;
  padding: 24px 20px calc(env(safe-area-inset-bottom, 16px) + 16px);
  width: 100%;
  max-width: 400px;
  animation: slideUp 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}
@keyframes slideUp { from { transform: translateY(100%); } to { transform: translateY(0); } }
.modal h3 {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 16px;
}
.modal .field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--card-border);
}
.modal .field:last-of-type { border-bottom: none; }
.modal .field label {
  font-size: 0.85rem;
  color: var(--text-secondary);
}
.modal .field input {
  width: 80px;
  padding: 6px 10px;
  border: 1px solid var(--card-border);
  border-radius: 8px;
  background: var(--seg-bg);
  color: var(--text-primary);
  font-size: 0.85rem;
  text-align: center;
  font-family: inherit;
}
.modal .actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.modal .actions button {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 12px;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}
.modal .btn-cancel {
  background: var(--seg-bg);
  color: var(--text-primary);
}
.modal .btn-save {
  background: #007aff;
  color: #fff;
}
</style>
</head>
<body>

<div class="header">
  <h1>環境監測</h1>
  <div class="subtitle">
    <span class="status-dot" id="statusDot"></span>
    <span id="updated">載入中…</span>
  </div>
</div>

<div class="cards" id="cards"></div>

<div class="seg-wrap">
  <div class="seg" id="seg">
    <div class="slider" id="slider"></div>
  </div>
</div>

<div class="chart-section">
  <div class="chart-title">
    <span class="legend-item"><span class="legend-dot" style="background:#ff9f0a"></span>氣溫</span>
    <span class="legend-item"><span class="legend-dot" style="background:#64d2ff"></span>濕度</span>
    <span class="legend-item"><span class="legend-dot" style="background:#30d158"></span>水溫</span>
  </div>
  <canvas id="chartT" height="160"></canvas>
</div>

<div class="chart-section">
  <div class="chart-title">
    <span class="legend-item"><span class="legend-dot" style="background:#ff6723"></span>土壤</span>
    <span class="legend-item"><span class="legend-dot" style="background:#bf5af2"></span>水位</span>
  </div>
  <canvas id="chartA" height="160"></canvas>
</div>

<button class="cal-btn" onclick="openCal()">校準設定</button>

<div class="modal-overlay" id="calModal">
  <div class="modal">
    <h3>ADC → % 校準</h3>
    <div class="field"><label>土壤 乾 (0%)</label><input id="calSoilMin" type="number"></div>
    <div class="field"><label>土壤 濕 (100%)</label><input id="calSoilMax" type="number"></div>
    <div class="field"><label>水位 低 (0%)</label><input id="calWaterMin" type="number"></div>
    <div class="field"><label>水位 高 (100%)</label><input id="calWaterMax" type="number"></div>
    <div class="actions">
      <button class="btn-cancel" onclick="closeCal()">取消</button>
      <button class="btn-save" onclick="saveCal()">儲存</button>
    </div>
  </div>
</div>

<script>
const FIELDS = [
  { key:'air_temp',    label:'氣溫',   unit:'°C', color:'#ff9f0a', type:'temp' },
  { key:'air_hum',     label:'濕度',   unit:'%',  color:'#64d2ff', type:'temp' },
  { key:'water_temp',  label:'水溫',   unit:'°C', color:'#30d158', type:'temp' },
  { key:'soil',        label:'土壤',   unit:'',   color:'#ff6723', type:'adc' },
  { key:'water_level', label:'水位',   unit:'',   color:'#bf5af2', type:'adc' },
];
const RANGES = [['1h',3600],['24h',86400],['7d',604800],['30d',2592000]];
let rangeSec = 86400, chartT, chartA, latestData = null, sparkData = [];

// --- Calibration (localStorage) ---
function getCal() {
  try { return JSON.parse(localStorage.getItem('cal')) || {}; } catch { return {}; }
}
function adcToPercent(key, raw) {
  const c = getCal();
  if (key === 'soil' && c.soilMin != null && c.soilMax != null) {
    return Math.round(Math.min(100, Math.max(0, (raw - c.soilMin) / (c.soilMax - c.soilMin) * 100)));
  }
  if (key === 'water_level' && c.waterMin != null && c.waterMax != null) {
    return Math.round(Math.min(100, Math.max(0, (raw - c.waterMin) / (c.waterMax - c.waterMin) * 100)));
  }
  return null;
}
function openCal() {
  const c = getCal();
  document.getElementById('calSoilMin').value = c.soilMin ?? '';
  document.getElementById('calSoilMax').value = c.soilMax ?? '';
  document.getElementById('calWaterMin').value = c.waterMin ?? '';
  document.getElementById('calWaterMax').value = c.waterMax ?? '';
  document.getElementById('calModal').classList.add('show');
}
function closeCal() { document.getElementById('calModal').classList.remove('show'); }
function saveCal() {
  const g = id => { const v = parseInt(document.getElementById(id).value); return isNaN(v) ? null : v; };
  localStorage.setItem('cal', JSON.stringify({
    soilMin: g('calSoilMin'), soilMax: g('calSoilMax'),
    waterMin: g('calWaterMin'), waterMax: g('calWaterMax'),
  }));
  closeCal();
  renderCards();
}

// --- Relative time ---
function relTime(ts) {
  const diff = Math.floor(Date.now()/1000) - ts;
  if (diff < 10) return '剛剛';
  if (diff < 60) return diff + ' 秒前';
  if (diff < 3600) return Math.floor(diff/60) + ' 分鐘前';
  if (diff < 86400) return Math.floor(diff/3600) + ' 小時前';
  return Math.floor(diff/86400) + ' 天前';
}

// --- Sparkline SVG ---
function sparkSVG(data, color) {
  if (!data || data.length < 2) return '';
  const vals = data.filter(v => v != null);
  if (vals.length < 2) return '';
  const min = Math.min(...vals), max = Math.max(...vals);
  const range = max - min || 1;
  const w = 200, h = 32;
  const pts = data.map((v, i) => {
    if (v == null) return null;
    const x = (i / (data.length - 1)) * w;
    const y = h - ((v - min) / range) * (h - 4) - 2;
    return x.toFixed(1)+','+y.toFixed(1);
  }).filter(Boolean);
  if (pts.length < 2) return '';
  return '<svg class="spark" viewBox="0 0 '+w+' '+h+'" preserveAspectRatio="none"><polyline points="'+pts.join(' ')+'" fill="none" stroke="'+color+'" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
}

// --- Cards ---
function renderCards() {
  const r = latestData;
  const el = document.getElementById('cards');
  el.innerHTML = FIELDS.map((f, i) => {
    let display = '--', unitStr = f.unit;
    if (r && r[f.key] != null) {
      if (f.type === 'adc') {
        const pct = adcToPercent(f.key, r[f.key]);
        if (pct != null) { display = pct; unitStr = '%'; }
        else { display = r[f.key]; unitStr = ''; }
      } else {
        display = (typeof r[f.key] === 'number') ? (Number.isInteger(r[f.key]) ? r[f.key] : r[f.key].toFixed(1)) : r[f.key];
      }
    }
    const spark = sparkData.length ? sparkSVG(sparkData.map(row => row[f.key]), f.color) : '';
    return '<div class="card"><div class="label"><span class="dot" style="background:'+f.color+'"></span>'+f.label+'</div><div class="value">'+display+(unitStr?'<span class="unit">'+unitStr+'</span>':'')+'</div>'+spark+'</div>';
  }).join('');
}

// --- Fetch latest ---
async function refreshLatest() {
  try {
    const r = await (await fetch('/api/latest')).json();
    if (!r) return;
    latestData = r;
    renderCards();
    const dot = document.getElementById('statusDot');
    const age = Math.floor(Date.now()/1000) - r.ts;
    dot.className = 'status-dot' + (age > 60 ? ' offline' : '');
    document.getElementById('updated').textContent = relTime(r.ts);
  } catch {}
}

// --- Fetch spark data (1h for sparklines) ---
async function refreshSpark() {
  try {
    const now = Math.floor(Date.now()/1000);
    const rows = await (await fetch('/api/data?from='+(now-3600)+'&to='+now+'&limit=60')).json();
    sparkData = rows;
    renderCards();
  } catch {}
}

// --- Charts ---
function buildGradient(ctx, color) {
  const g = ctx.createLinearGradient(0, 0, 0, ctx.canvas.height);
  g.addColorStop(0, color + '33');
  g.addColorStop(1, color + '00');
  return g;
}
async function refreshChart() {
  try {
    const now = Math.floor(Date.now()/1000);
    const rows = await (await fetch('/api/data?from='+(now-rangeSec)+'&to='+now)).json();
    const labels = rows.map(r => {
      const d = new Date(r.ts*1000);
      return rangeSec <= 86400
        ? d.toLocaleTimeString('zh-TW',{hour:'2-digit',minute:'2-digit'})
        : d.toLocaleDateString('zh-TW',{month:'numeric',day:'numeric'});
    });
    const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const opts = {
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: { legend: { display: false }, tooltip: { backgroundColor: isDark?'rgba(40,40,42,.9)':'rgba(255,255,255,.95)', titleColor: isDark?'#f5f5f7':'#1d1d1f', bodyColor: isDark?'#ccc':'#555', borderColor: isDark?'rgba(255,255,255,0.1)':'rgba(0,0,0,0.06)', borderWidth: 1, cornerRadius: 10, padding: 10, bodyFont: { family: '-apple-system' } } },
      scales: { x: { grid: { display: false }, ticks: { maxTicksLimit: 6, color: 'var(--tick-color)', font: { size: 10 } }, border: { display: false } }, y: { grid: { color: isDark?'rgba(255,255,255,0.05)':'rgba(0,0,0,0.04)' }, ticks: { color: isDark?'#98989d':'#86868b', font: { size: 10 }, maxTicksLimit: 5 }, border: { display: false } } },
      elements: { point: { radius: 0, hoverRadius: 4 } },
    };
    const ds = (key, color, canvasId) => {
      const ctx = document.getElementById(canvasId).getContext('2d');
      return {
        label: FIELDS.find(f=>f.key===key).label,
        data: rows.map(r=>r[key]), borderColor: color, backgroundColor: buildGradient(ctx, color),
        fill: true, pointRadius: 0, borderWidth: 2, tension: .4, spanGaps: true,
      };
    };
    chartT?.destroy(); chartA?.destroy();
    chartT = new Chart(document.getElementById('chartT'), { type:'line', data: { labels, datasets:[ds('air_temp','#ff9f0a','chartT'), ds('air_hum','#64d2ff','chartT'), ds('water_temp','#30d158','chartT')] }, options: opts });
    chartA = new Chart(document.getElementById('chartA'), { type:'line', data: { labels, datasets:[ds('soil','#ff6723','chartA'), ds('water_level','#bf5af2','chartA')] }, options: opts });
  } catch {}
}

// --- Segmented control ---
const segEl = document.getElementById('seg');
const sliderEl = document.getElementById('slider');
RANGES.forEach(([label, sec], i) => {
  const btn = document.createElement('button');
  btn.textContent = label;
  btn.dataset.i = i;
  btn.dataset.s = sec;
  segEl.appendChild(btn);
});
function updateSlider(idx) {
  const btns = segEl.querySelectorAll('button');
  const btn = btns[idx];
  if (!btn) return;
  sliderEl.style.left = btn.offsetLeft + 'px';
  sliderEl.style.width = btn.offsetWidth + 'px';
}
segEl.addEventListener('click', e => {
  if (!e.target.dataset.s) return;
  rangeSec = +e.target.dataset.s;
  updateSlider(+e.target.dataset.i);
  refreshChart();
});
setTimeout(() => updateSlider(1), 100);

// --- Init ---
refreshLatest(); refreshSpark(); refreshChart();
setInterval(refreshLatest, 10000);
setInterval(refreshSpark, 60000);
setInterval(refreshChart, 60000);
setInterval(() => { if(latestData) document.getElementById('updated').textContent = relTime(latestData.ts); }, 5000);
</script>
</body>
</html>`;
