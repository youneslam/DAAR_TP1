#!/usr/bin/env bash
# Downloads the testbeds (plain-text books from Project Gutenberg) into testbeds/.
# Sizes range from ~150 KB to ~5.5 MB so that search time can be measured against text size.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p testbeds

# Gutenberg id : file name
books=(
    "56667:babylon.txt"         # History of Babylon (example of the TP)
    "11:alice.txt"              # Alice's Adventures in Wonderland
    "84:frankenstein.txt"       # Frankenstein
    "1342:pride_and_prejudice.txt"
    "2701:moby_dick.txt"
    "135:les_miserables.txt"
    "100:shakespeare.txt"       # Complete Works of William Shakespeare
)

for book in "${books[@]}"; do
    id="${book%%:*}"
    name="${book#*:}"
    if [ -s "testbeds/$name" ]; then
        echo "testbeds/$name already present"
        continue
    fi
    echo "Downloading Gutenberg #$id -> testbeds/$name"
    curl -sSfL "https://www.gutenberg.org/cache/epub/$id/pg$id.txt" -o "testbeds/$name"
    sleep 1 # be polite with the Gutenberg servers
done

ls -l testbeds
