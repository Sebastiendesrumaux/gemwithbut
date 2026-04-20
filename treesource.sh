env
pwd
find . -type f \( \
  -name "*.java" -o \
  -name "run.sh" -o \
  -name "build.sh" -o \
  -name "settings.gradle" -o \
  -name "gradle.properties" -o \
  -name "build.gradle" -o \
  -name "AndroidManifest.xml" -o \
  -name "activity_*.xml" \
\) -print -exec sh -c 'echo "===== {} ====="; sed "s/^/    /" "{}"' \;

