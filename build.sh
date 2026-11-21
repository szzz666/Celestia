#!/bin/bash
# 本地编译脚本：直接用 javac 打出插件 jar（无需 Maven）。
# 用法：./build.sh [输出目录]
set -euo pipefail

cd "$(dirname "$0")"

JAVA_HOME_21="/Users/mac/Library/Java/JavaVirtualMachines/azul-21.0.12.1/Contents/Home"
if [ -x "$JAVA_HOME_21/bin/javac" ]; then
  JAVAC="$JAVA_HOME_21/bin/javac"
  JAVA="$JAVA_HOME_21/bin/java"
else
  JAVAC="javac"
  JAVA="java"
fi

OUT="${1:-build}"
CLASSES="$OUT/classes"
JAR="$OUT/Celestia.jar"

LIBS=$(find lib -name '*.jar' | tr '\n' ':')

rm -rf "$CLASSES" && mkdir -p "$CLASSES"

echo "==> 编译"
find src/main/java -name '*.java' > "$OUT/sources.txt"
"$JAVAC" -encoding UTF-8 -nowarn -d "$CLASSES" -cp "$LIBS" @"$OUT/sources.txt"

echo "==> 复制资源"
cp -r src/main/resources/* "$CLASSES"/

echo "==> 打包"
( cd "$CLASSES" && jar --create --file "../Celestia.jar" . )
echo "==> 完成: $JAR ($(du -h "$JAR" | cut -f1))"
