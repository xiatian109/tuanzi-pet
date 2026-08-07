# 🐱 团子精灵 · AI桌面宠物

一只软萌带仙气的团子精灵，透过 Android 悬浮窗陪在你桌边。

## ✨ 它是什么

「大脑/身体分离」架构的 AI 桌宠：
- **身体**：一个 Android 悬浮窗 App（透明 WebView 加载本地 SVG）
- **大脑**：Operit AI 对话系统（不变）
- **桥梁**：Supabase Realtime（后续接入）

本仓库当前为 **第一版（最小可跑）**：一个能浮在屏幕上、会眨眼、可拖拽的小团子。

## 🎨 形象

- **主体**：淡淡湖蓝/冷蓝渐变圆球身
- **眼睛**：亮晶晶琥珀黄
- **特征**：头顶两个尖尖小角、一团软fufu蓬松顶、身后仙气小尾巴
- **性格**：黏人但傲娇，爱拆台，你不在又悄悄蹭过来看你

## 🛠 技术栈

- Kotlin，`minSdk 26`（Android 8.0+）
- `WindowManager.TYPE_APPLICATION_OVERLAY` 悬浮窗
- 透明 WebView + 内嵌 SVG（CSS 漂浮/眨眼动画）
- GitHub Actions 自动构建 APK

## 📁 结构

```
pet/
├── app/src/main/
│   ├── assets/tuanzi.html      # 桌宠前端（SVG 内嵌）
│   ├── java/com/tuanzi/pet/
│   │   ├── MainActivity.kt     # 权限引导 + 启停
│   │   └── OverlayService.kt   # 悬浮窗前台服务
│   └── res/                    # 图标/主题/布局
├── .github/workflows/build.yml # 自动出 APK
└── build.gradle.kts
```

## 🚀 构建与安装

1. 推送到 GitHub（本仓库有 CI），Actions 自动产出 debug APK
2. 下载 APK 安装到手机
3. 首次打开 → 授予「显示在其他应用上层」→ 点「召唤团子精灵」

## 🧭 后续路线

- [ ] 表情系统（傲娇/偷看/困了）
- [ ] 手势系统（戳一下/拖拽反馈）
- [ ] 前台 App 感知（你刷太久它会嘀咕）
- [ ] Supabase Realtime 与 AI 大脑双向同步
- [ ] 情绪引擎（Heat 系统）

## 📄 License

本项目按原蓝图 `Vael-KY/AI-Live-Overflow` 思路实现，遵守 **CC BY-NC-SA 4.0**（非商用）。
