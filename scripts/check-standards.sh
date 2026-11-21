#!/usr/bin/env bash
# Checks lines ADDED in the current change set against the project standards
# (AGENTS.md §3, docs/standards/*). Existing legacy code is not reported.
#
# Usage:
#   scripts/check-standards.sh            # diff vs merge-base with origin/main (or HEAD), incl. uncommitted + untracked
#   scripts/check-standards.sh <base-ref> # diff vs an explicit ref
#
# Suppress a finding on a specific line by adding `// Legacy: <reason>` to that same line.
# Exit code: 1 if any ERROR, 0 otherwise (WARNs do not fail).

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

base="${1:-}"
if [[ -z "$base" ]]; then
  for candidate in origin/main origin/master main master; do
    if git rev-parse --verify -q "$candidate" >/dev/null; then
      base="$(git merge-base HEAD "$candidate")"
      break
    fi
  done
  base="${base:-HEAD}"
fi

# Collect "path:line-content" for every added line in .kt/.kts (tracked diff + untracked files).
added="$(
  {
    git diff --unified=0 --no-color "$base" -- '*.kt' '*.kts' |
      awk '/^\+\+\+ /{ file = substr($0, 7); next } /^\+/{ print file ":" substr($0, 2) }'
    git ls-files --others --exclude-standard -- '*.kt' '*.kts' |
      while IFS= read -r f; do sed "s|^|${f}:|" "$f"; done
  } | grep -v 'Legacy:' | grep -vE '^[^:]*:[[:space:]]*(//|/?\*)' || true
)"

if [[ -z "$added" ]]; then
  echo "check-standards: no added Kotlin lines vs ${base:0:12}."
  exit 0
fi

errors=0
warnings=0

# check <ERROR|WARN> <message> <regex> [exclude-path-regex]
check() {
  local level="$1" message="$2" regex="$3" exclude="${4:-^$}"
  local hits
  hits="$(printf '%s\n' "$added" | grep -E -- "$regex" | grep -vE -- "^(${exclude})" || true)"
  [[ -z "$hits" ]] && return 0
  local count
  count="$(printf '%s\n' "$hits" | wc -l | tr -d ' ')"
  echo "${level}: ${message} (${count})"
  printf '%s\n' "$hits" | head -n 10 | sed 's/^/    /'
  if [[ "$level" == "ERROR" ]]; then errors=$((errors + count)); else warnings=$((warnings + count)); fi
}

TESTS='[^:]*/src/(test|androidTest)/'
TESTS_OR_BENCH="${TESTS}|baselineprofile/"

# --- Architecture / async -----------------------------------------------------------------------
check ERROR "LiveData is not used — expose StateFlow" '\b(Mutable)?LiveData\b'
check ERROR "Use collectAsStateWithLifecycle() instead of collectAsState()" '\.collectAsState\('
check ERROR "GlobalScope is forbidden — inject @AppScope CoroutineScope" '\bGlobalScope\b'
check ERROR "runBlocking is forbidden outside tests" '\brunBlocking\b' "$TESTS_OR_BENCH"
check ERROR "Thread.sleep is forbidden outside tests — use delay()" '\bThread\.sleep\(' "$TESTS_OR_BENCH"
check ERROR "kapt is not used — use KSP" '(\bkapt\(|kotlin-kapt|id\("kapt"\))'
check ERROR "Hardcoded dependency version — use gradle/libs.versions.toml" '(implementation|api|ksp)\("[^"]+:[^"]+:[0-9]'

# --- Logging / libraries ------------------------------------------------------------------------
check ERROR "Use Timber instead of android.util.Log" 'import android\.util\.Log$'
check WARN  "println in production code — use Timber" ':\s*println\(' "$TESTS"
check ERROR "Compose Material 2 is not used — use material3" 'import androidx\.compose\.material\.([A-Z]|pullrefresh|ripple)'
check ERROR "Unsupported image library (project uses Coil 2 via SmartImage)" 'import (coil3\.|com\.bumptech\.glide|com\.squareup\.picasso)'
check WARN  "Gson for new code — prefer kotlinx.serialization (Gson only in backup/Retrofit/Room converters)" 'import com\.google\.gson\.'
check WARN  "New SharedPreferences usage — use a DataStore *PreferencesRepository" 'getSharedPreferences\('
check WARN  "Type-safe navigation routes are not used — add a Screen entry" 'composable<'

# --- UI ------------------------------------------------------------------------------------------
check ERROR "Hardcoded UI text — use stringResource(R.string.…)" '\bText\(\s*"' "$TESTS"
check WARN  "Hardcoded color — use MaterialTheme.colorScheme roles" '\bColor\(0x' "$TESTS|[^:]*/ui/theme/|[^:]*WearThemePalette"
check WARN  "isSystemInDarkTheme() in a component — use LocalPixelPlayDarkTheme.current" 'isSystemInDarkTheme\(\)' "$TESTS|[^:]*/ui/theme/"
check WARN  "New AbsoluteSmoothCornerShape instance — prefer ShapeCache.smoothXX" 'AbsoluteSmoothCornerShape\(' "[^:]*/ui/theme/ShapeCache\.kt"
check WARN  "Direct AsyncImage — use SmartImage" '\bAsyncImage\(' "[^:]*/SmartImage\.kt"
check WARN  "Direct performHapticFeedback — use performAppHapticFeedback(LocalAppHapticsConfig.current, …)" '\.performHapticFeedback\(' "[^:]*/presentation/utils/AppHaptics\.kt"

# --- Tests ---------------------------------------------------------------------------------------
check ERROR "Mockito is not used — use MockK" 'import org\.mockito'
check WARN  "New JUnit 4 test — use JUnit 5 (org.junit.jupiter.api.Test)" 'import org\.junit\.Test$'

echo
echo "check-standards: ${errors} error(s), ${warnings} warning(s) in lines added since ${base:0:12}."
[[ "$errors" -eq 0 ]]
