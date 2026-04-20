
echo `pwd` >arbo.tmp
echo `env` >>arbo.tmp
find . -type f \( \
   -name "*.java" -o \
   -name "*.xml"  -o \
   -name "*.properties" -o \
   -name "build.gradle" -o \
   -name "settings.gradle" -o \
   -name "gradle.properties" -o \
   -name "*.gradle" \
\) -print  >>arbo.tmp

cat arbo.tmp | termux-clipboard-set

#rm arbo.tmp

