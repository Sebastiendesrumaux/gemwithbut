# On filtre l'env pour ne garder que l'essentiel et éviter le bruit
env | grep -E 'PWD|PATH|ANDROID|JAVA|TERMUX' 
pwd
# On "prune" (élague) les dossiers inutiles et on cherche les sources vitales
find . -type d \( -name "build" -o -name ".gradle" -o -name ".git" \) -prune -o \
-type f \( \
  -name "*.java" -o \
  -name "*.sh" -o \
  -name "*.gradle" -o \
  -name "*.properties" -o \
  -name "AndroidManifest.xml" -o \
  -name "*.xml" -o \
  -name "*.json" -o \
  -name "*.pro" \
\) -print -exec sh -c 'echo "===== {} ====="; sed "s/^/    /" "{}"' \;

