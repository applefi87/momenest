#!/usr/bin/env bash
# 在 PC 上編譯並執行所有 host 單元測試 (不需要 ESP32)。
# 用法：在 EnvMonitor/tests/ 下執行  ./run_tests.sh
# 需求：g++ (C++11 以上)
set -e
cd "$(dirname "$0")"

CXX="${CXX:-g++}"
FLAGS="-std=c++11 -Wall -Wextra -O0"

echo "== 編譯測試 =="
"$CXX" $FLAGS test_reading_format.cpp ../reading_format.cpp -o test_reading_format

echo "== 執行測試 =="
./test_reading_format
