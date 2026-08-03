#!/usr/bin/env bash
# 在 PC 上編譯並執行所有 host 單元測試 (不需要 ESP32)。
# 用法：在 EnvMonitor/tests/ 下執行  ./run_tests.sh
# 需求：g++ (C++11 以上)
set -e
cd "$(dirname "$0")"

CXX="${CXX:-g++}"
FLAGS="-std=c++11 -Wall -Wextra -O0"

fail=0

echo "== reading_format =="
"$CXX" $FLAGS test_reading_format.cpp ../reading_format.cpp -o test_reading_format
./test_reading_format || fail=1

echo ""
echo "== ota_protocol =="
"$CXX" $FLAGS test_ota_protocol.cpp ../ota_protocol.cpp -o test_ota_protocol
./test_ota_protocol || fail=1

echo ""
[ $fail -eq 0 ] && echo "== 全部測試通過 ==" || echo "== 有測試失敗 =="
exit $fail
