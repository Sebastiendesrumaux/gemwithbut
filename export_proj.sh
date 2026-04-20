#!/bin/bash
output="arbo.tmp"

echo "--- POSITION ---" > $output
pwd >> $output
echo -e "\n--- ENVIRONNEMENT ---" >> $output
env >> $output
echo -e "\n--- CONTENU DES SOURCES ---\n" >> $output

find . -type f \( \
   -name "*.java" -o \
   -name "*.xml"  -o \
   -name "*.properties" -o \
   -name "build.gradle" -o \
   -name "settings.gradle" -o \
   -name "gradle.properties" -o \
   -name "*.gradle" \
\) | while read -r file; do
    echo "========================================" >> $output
    echo "FILE: $file" >> $output
    echo "========================================" >> $output
    cat "$file" >> $output
    echo -e "\n" >> $output
done

cat $output | termux-clipboard-set
echo "Projet copié dans le presse-papier !"
# rm $output
