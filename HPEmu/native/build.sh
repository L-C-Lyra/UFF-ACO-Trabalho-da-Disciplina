#!/usr/bin/env bash
# Builds libhpym2608.so (ymfm OPNA core + C ABI wrapper) into the resources tree
# so it ships on the classpath and is extracted at runtime by Ym2608Native.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out_dir="$here/../src/main/resources/native/linux-x86-64"
mkdir -p "$out_dir"

CXX="${CXX:-g++}"
CXXFLAGS="-O2 -fPIC -std=c++17 -Wall -fvisibility=hidden -I$here"

srcs=(
  "$here/hpym2608.cpp"
  "$here/ymfm/ymfm_opn.cpp"
  "$here/ymfm/ymfm_adpcm.cpp"
  "$here/ymfm/ymfm_ssg.cpp"
)

echo "Compiling libhpym2608.so ..."
$CXX $CXXFLAGS -shared -o "$out_dir/libhpym2608.so" "${srcs[@]}"
echo "Built: $out_dir/libhpym2608.so"
