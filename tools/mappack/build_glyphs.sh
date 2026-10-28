#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Generates offline map-label glyphs (SDF PBF ranges) from the bundled Inter fonts.
# Without these, maps still render but without text labels.
#
# Requires the Rust tool build_pbf_glyphs: cargo install build_pbf_glyphs
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FONTS="$(mktemp -d)"
cp "$ROOT/app/src/main/res/font/inter_regular.ttf" "$FONTS/Inter-Regular.ttf"
cp "$ROOT/app/src/main/res/font/inter_semibold.ttf" "$FONTS/Inter-SemiBold.ttf"
OUT="$ROOT/app/src/main/assets/glyphs"
rm -rf "$OUT" && mkdir -p "$OUT"
build_pbf_glyphs "$FONTS" "$OUT"
# The style asks for "Inter Regular" and "Inter SemiBold"; check the generated folder names match.
ls "$OUT"
