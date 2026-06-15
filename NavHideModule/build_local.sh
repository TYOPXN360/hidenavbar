#!/bin/bash
# NavHideModule 本地编译脚本(用 source/ 里的 gradle + JDK 离线编译)
# 用法: cd NavHideModule && ./build_local.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HIDENAVBAR_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_DIR="$HIDENAVBAR_DIR/source"

# 1) JDK: source/.gradle/jdks/jetbrains_s_r_o_-21-amd64-linux.2
JDK_DIR=$(find "$SOURCE_DIR/.gradle/jdks" -maxdepth 1 -type d -name "jetbrains*" 2>/dev/null | head -1)
if [ -z "$JDK_DIR" ]; then
    echo "[!] JDK not found under $SOURCE_DIR/.gradle/jdks/"
    echo "    Please check source/.gradle/jdks/ exists."
    exit 1
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
echo "[+] JAVA_HOME=$JAVA_HOME"
java -version

# 2) gradle: 直接用 source/gradle-9.3.1/bin/gradle(不走 wrapper)
GRADLE_BIN="$SCRIPT_DIR/gradle-9.3.1/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
    GRADLE_BIN="$SOURCE_DIR/gradle-9.3.1/bin/gradle"
fi
if [ ! -x "$GRADLE_BIN" ]; then
    echo "[!] gradle not found at $GRADLE_BIN or $SOURCE_DIR/gradle-9.3.1/bin/gradle"
    exit 1
fi
echo "[+] gradle: $GRADLE_BIN"

# 3) gradle user home 指向 source/.gradle,所有缓存 / 解压都走这里(不污染 ~/)
export GRADLE_USER_HOME="$SOURCE_DIR/.gradle"
echo "[+] GRADLE_USER_HOME=$GRADLE_USER_HOME"

# 4) 关掉代理(避免 sandbox 找不到代理)
unset systemProp.socksProxyHost
unset systemProp.socksProxyPort

# 5) gradle.properties 里关掉代理(覆盖项目设置)
cd "$SCRIPT_DIR"
echo "[+] build start"
"$GRADLE_BIN" "$@"
