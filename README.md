# bzzq

`bzzq` 是一個基於 `libxposed API 101` 的 Android Xposed 模組，目標是為 Bilibili 客戶端提供一組偏實用、偏輕量的功能修補與廣告淨化能力。

目前專案已經適配新的模組入口與設定頁邏輯，並將框架支援範圍擴展為：

- `NPatch`
- `Vector`
- `LSPosed` 且版本大於 `7700`

不符合上述條件時，模組會在載入後保持停用，不對目標 App 注入 hook。

## 目標包名

模組目前會對以下 Bilibili 套件生效：

```text
tv.danmaku.bili
com.bilibili.app.in
tv.danmaku.bilibilihd
com.bilibili.app.blue
```

## 目前功能

目前已接入的功能如下：

- 跳過開屏廣告
- 解鎖部分影片功能
- 影片詳情頁自動點讚
- 修正直播畫質 URL
- 跳過小遊戲獎勵廣告
- 屏蔽直播預約
- 淨化豎屏影片廣告
- 在 Bilibili 設定頁與「我的」頁注入 `bzzq` 設定入口

其中「豎屏影片廣告淨化」支援按標籤過濾，並會在模組設定頁內顯示累計攔截數。

## 使用方式

1. 安裝 Xposed 框架，並確認你使用的是 `NPatch`、`Vector`，或版本碼大於 `7700` 的 `LSPosed`。
2. 安裝本模組 APK。
3. 在框架管理器中啟用模組。
4. 將作用域授予目標 Bilibili App。
5. 重啟對應 App。

啟用後，你可以透過以下任一入口打開模組設定：

- 桌面上的 `bzzq` App 圖示
- Bilibili 設定頁中的 `bzzq` 入口
- Bilibili「我的」頁中的 `bzzq` 入口

## 建置

### 環境需求

- JDK `21`
- Android Gradle Plugin `8.13.1`
- Kotlin `2.3.21`
- Gradle Wrapper `8.14.4`

### 調試包

```powershell
.\gradlew.bat assembleDebug
```

輸出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 發行包

```powershell
.\gradlew.bat assembleRelease
```

若已配置簽名資訊，release 構建完成後會額外輸出一份帶版本號的 APK：

```text
app/build/outputs/apk/release/bzzq_<version>.apk
```

目前專案版本號定義於 `gradle.properties` 的 `releaseName`。

## 專案特性

- 使用 `libxposed API 101`
- 採用靜態 scope 聲明目標包名
- 使用 Java/Kotlin `21`
- 僅依賴 `compileOnly` 的 `libxposed api`
- 以反射與動態 hook 為主，降低對特定客戶端版本的硬編碼耦合

## 注意事項

- 這是一個針對 Bilibili 客戶端行為做修改的 Xposed 模組，兼容性會受到 App 版本、框架實作與混淆變化影響。
- 某些 hook 若遇到類名、方法簽名或欄位結構變動，可能只會局部失效，不代表整個模組完全不可用。
- 若設定入口未出現，通常代表對應頁面的類名或版面結構已發生變動，需要後續調整 hook。

## 授權

本專案使用木蘭公共許可證第 2 版（Mulan PubL v2）。

完整授權內容見 [LICENSE](LICENSE)。
官方文本請參考：

<http://license.coscl.org.cn/MulanPubL-2.0>
