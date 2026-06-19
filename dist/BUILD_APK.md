# Android APK 编译说明

## 环境要求
- Android Studio (推荐 2023.1+)
- JDK 17
- Android SDK 34

## 编译步骤

1. 用 Android Studio 打开 `android-app/` 目录
2. 等待 Gradle 同步完成
3. 修改 `src/main/java/com/jiashouna/app/App.java` 中的服务器地址：
   ```java
   public static final String BASE_URL = "http://你的服务器地址/backend/api/";
   ```
4. 菜单 Build → Build Bundle(s) / APK(s) → Build APK(s)
5. 编译完成后 APK 在 `app/build/outputs/apk/release/` 目录

## 命令行编译
```bash
cd android-app
./gradlew assembleRelease
```

编译好的 APK 在 `app/build/outputs/apk/release/app-release.apk`
