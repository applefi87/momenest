/**********************************************************************
 * net.h — WiFi 連線管理與雲端上傳 (Cloudflare Worker)
 **********************************************************************/
#pragma once

extern int uploadState;   // 0=尚未上傳 1=成功 2=失敗 (供 UI 狀態圓點)

void netInit();           // 非阻塞啟動 WiFi
void netEnsure();         // 斷線自動重連 (每 15 秒最多試一次)
void netUpload();         // HTTPS POST 感測值；無效讀值送 null
