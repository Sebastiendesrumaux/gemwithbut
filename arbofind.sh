#!/bin/bash
search_str=$1

if [ -z "$search_str" ]; then
    echo "Usage: ./arbofins.sh chaine_a_chercher"
    exit 1
fi

echo "--- Recherche de '$search_str' dans les NOMS de fichiers ---"
find . -name "*$search_str*" -not -path '*/.*'

echo ""
echo "--- Recherche de '$search_str' dans le CONTENU des fichiers ---"
grep -rnw . -e "$search_str" --exclude-dir={.*}
