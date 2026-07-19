/**
 * API 處理 — D1 讀寫（路由入口在 index.js）
 */

export const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-API-Key',
};

export const json = (obj, status = 200) =>
  new Response(JSON.stringify(obj), {
    status,
    headers: { 'Content-Type': 'application/json', ...CORS },
  });

// POST /api/ingest — ESP32 上傳（需 X-API-Key）
export async function handleIngest(request, env) {
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

// GET /api/data — 公開查詢歷史區間（?from=&to= unix秒, ?limit=）
// 長區間自動按時間分桶取平均降採樣，確保回傳資料涵蓋整個區間
// （否則 10 秒一筆 × 24h = 8640 筆會被 LIMIT 截到只剩區間開頭）
const RAW_INTERVAL_SEC = 10;   // ESP32 上傳週期
const TARGET_POINTS = 720;     // 降採樣目標點數

export async function handleData(url, env) {
  const now = Math.floor(Date.now() / 1000);
  const from = parseInt(url.searchParams.get('from')) || now - 86400; // 預設近 24h
  const to = parseInt(url.searchParams.get('to')) || now;
  const limit = Math.min(parseInt(url.searchParams.get('limit')) || 2000, 5000);

  const bucket = Math.max(1, Math.ceil((to - from) / TARGET_POINTS));
  let sql;
  if (bucket > RAW_INTERVAL_SEC) {
    // bucket 為整數運算結果，直接內插進 SQL 安全
    sql = `SELECT (CAST(ts / ${bucket} AS INTEGER)) * ${bucket} + ${Math.floor(bucket / 2)} AS ts,
                  AVG(air_temp) AS air_temp, AVG(air_hum) AS air_hum,
                  AVG(water_temp) AS water_temp,
                  CAST(ROUND(AVG(soil)) AS INTEGER) AS soil,
                  CAST(ROUND(AVG(water_level)) AS INTEGER) AS water_level
           FROM readings WHERE ts BETWEEN ? AND ?
           GROUP BY CAST(ts / ${bucket} AS INTEGER) ORDER BY ts ASC LIMIT ?`;
  } else {
    sql = `SELECT ts, air_temp, air_hum, water_temp, soil, water_level
           FROM readings WHERE ts BETWEEN ? AND ?
           ORDER BY ts ASC LIMIT ?`;
  }
  const { results } = await env.DB.prepare(sql).bind(from, to, limit).all();
  return json(results);
}

// GET /api/latest — 公開查詢最新一筆
export async function handleLatest(env) {
  const { results } = await env.DB.prepare(
    `SELECT ts, air_temp, air_hum, water_temp, soil, water_level
     FROM readings ORDER BY ts DESC LIMIT 1`
  ).all();
  return json(results[0] ?? null);
}
