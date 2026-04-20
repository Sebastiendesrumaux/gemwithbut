#!/bin/bash
old=$1
new=$2

if [ -z "$old" ] || [ -z "$new" ]; then
    echo "Usage: ./renameproj.sh old_string new_string"
    exit 1
fi

echo "--- Étape 1 : Remplacement du contenu ---"
find . -type f -not -path '*/.*' -exec sed -i "s/$old/$new/g" {} +

echo "--- Étape 2 : Renommage des fichiers et dossiers ---"
# -depth est crucial pour traiter les enfants avant les parents
find . -depth -name "*$old*" -not -path '*/.*' | while read -r path; do
    # On récupère le dossier parent et le nom du fichier/dossier
    subdir=$(dirname "$path")
    filename=$(basename "$path")
    
    # On ne remplace que dans le nom du fichier lui-même
    new_filename=$(echo "$filename" | sed "s/$old/$new/g")
    
    # On renomme
    mv "$path" "$subdir/$new_filename"
done

echo "Terminé. $old a été remplacé par $new."
