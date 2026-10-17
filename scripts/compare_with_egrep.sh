#!/usr/bin/env bash
# Compares the number of matching lines given by our Egrep and by the system's grep -E
# on every testbed, for a list of regexes.
# Carriage returns are removed first: Gutenberg files use CRLF and grep would let '.' match '\r'.
set -uo pipefail

cd "$(dirname "$0")/.."
mkdir -p out
javac -encoding UTF-8 -d out src/*.java || exit 2

regexes=(
    "Sargon" "S(a|g|r)*on" "(B|b)abylon|Assyria" "th(e|is|at)" "h.s"
    "a(b|c|d)*e" "q.*z" "((a|e|i|o|u)(n|r|s|t))*ing" "Ch(a|e)p.*(I|V|X)"
)

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT
differences=0

printf "%-26s %-30s %10s %10s\n" "file" "regex" "ours" "grep -E"
for file in testbeds/*.txt; do
    tr -d '\r' < "$file" > "$tmp"
    for regex in "${regexes[@]}"; do
        ours=$(java -cp out Egrep "$regex" "$tmp" | wc -l)
        theirs=$(LC_ALL=C.UTF-8 grep -E -c -- "$regex" "$tmp")
        mark=""
        if [ "$ours" -ne "$theirs" ]; then mark="  <-- DIFFERENT"; differences=$((differences + 1)); fi
        printf "%-26s %-30s %10d %10d%s\n" "$(basename "$file")" "$regex" "$ours" "$theirs" "$mark"
    done
done

if [ "$differences" -eq 0 ]; then echo "SAME RESULTS AS grep -E"; else echo "$differences DIFFERENCE(S)"; fi
[ "$differences" -eq 0 ]
