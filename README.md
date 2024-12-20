# MoodListener - 心情记录应用 📱
<img src="https://github.com/user-attachments/assets/84012e67-26c2-4cb3-9f37-c7d56f778703" width="128" height="128" alt="moodicon" style="display: block; margin: 20px auto;">

`v1.0.0`

MoodListener 是一个简单而强大的心情记录应用，帮助用户追踪、记录和回顾自己的情绪变化。
## 开发初衷 💭

```
这款应用的初衷是希望能够通过记录和了解自己的心情状态，帮助用户及时发现情绪问题，调整心态，从而让生活变得更轻松、更平衡, 更加了解自己。
```
## 🌟 主要功能

### 心情记录
- 📝 支持多选心情标签（最多3个）
- 🗂 包含积极、中性、消极三大心情分类
- ✍️ 可添加详细文字备注
- 🔔 智能提醒功能，避免遗忘记录

### 数据管理
- 📊 直观的历史记录查看
- 📱 支持编辑和删除记录
- 📤 多格式数据导出（`CSV`/`TXT`/`JSON`）
- 💾 本地数据安全存储

### 个性化设置
- ⏰ 自定义提醒时间段
- ⌚ 灵活设置提醒间隔
- 🎨 多种主题配色
- 🌙 智能夜间模式
- 📝 自定义提醒文案

## 📱 界面预览

### 记录界面
- 心情选择区：展示所有可选心情标签
- 分类选择：快速选择心情分类
- 备注输入：支持添加详细文字说明
- 提交按钮：一键保存心情记录

### 历史界面
- 时间轴展示：按时间顺序展示所有记录
- 筛选功能：支持按类别和时间筛选
- 编辑功能：支持修改已有记录
- 删除功能：支持删除单条记录

### 设置界面
- 提醒设置：时间段和间隔设置
- 主题设置：主题色彩切换
- 夜间模式：自动/手动切换
- 数据管理：导出数据选项

## ⚙️ 开发环境要求

```gradle
minSdk: 24
targetSdk: 34
compileSdk: 34
Kotlin: 1.8+
```

## 📚 主要依赖

```gradle
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.9.0'
implementation 'androidx.work:work-runtime-ktx:2.9.0'
implementation 'com.google.android.flexbox:flexbox:3.0.0'
```

## 🚀 快速开始

### 安装步骤
1. 从 Release 页面下载最新版本 APK
2. 允许安装来自未知来源的应用
3. 安装完成后首次运行需要授予通知权限

### 初始设置
1. 设置提醒时间段和间隔
2. 选择喜欢的主题颜色
3. 根据需要开启夜间模式
4. 自定义提醒文案（可选）

## 💡 使用建议

- 坚持每天记录心情
- 选择合适的提醒时间
- 详细记录当时的感受
- 定期导出数据备份
- 回顾历史记录，了解情绪变化

## 🤝 参与贡献

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

## 📄 开源协议

本项目采用 MIT 协议开源。详见 [LICENSE](LICENSE) 文件。

## 👨‍💻 作者

[@fayeeeeee1](https://github.com/fayeeeeee1)

---

希望 MoodListener 能帮助你更好地了解自己的情绪变化，让生活更加美好！

如果你觉得这个应用对你有帮助，欢迎给个 Star ⭐️
