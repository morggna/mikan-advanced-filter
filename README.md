# 蜜柑计划高级筛选器

为蜜柑计划（Mikan Project）的每个字幕组提供独立的批量筛选和下载功能。

## ✨ 功能特点

- 🎯 **独立筛选** - 每个字幕组独立配置，互不干扰
- ⚡ **自动筛选** - 点击"批量选择"自动应用默认筛选（简体+1080p）
- 🔍 **精确匹配** - 准确区分简体、繁体、简繁双语
- 🚀 **性能优化** - 针对Mac和Windows平台优化，流畅不卡顿
- 📋 **批量复制** - 一键复制所选磁力链接

## 📥 安装

### 前置要求
需要先安装用户脚本管理器：
- [Tampermonkey](https://www.tampermonkey.net/) (推荐)
- [Violentmonkey](https://violentmonkey.github.io/)
- [Greasemonkey](https://www.greasespot.net/)

### 安装脚本
1. 点击 [安装脚本](https://github.com/你的用户名/mikan-advanced-filter/raw/main/mikan-group-filter.user.js)
2. 在弹出的页面点击"安装"

或者：
1. 复制 `mikan-group-filter.user.js` 的内容
2. 在Tampermonkey中创建新脚本并粘贴
3. 保存

## 🎮 使用方法

1. 访问蜜柑计划的番剧页面
2. 等待脚本自动展开所有字幕组
3. 点击字幕组右侧的 **"批量选择"** 按钮
4. 自动应用简体+1080p筛选，或点击 **"筛选选项"** 自定义
5. 点击 **"复制选中项"** 复制磁力链接

## ⚙️ 筛选选项

### 语言选项
- **简体** - 仅简体中文（排除繁体和简繁）
- **繁体** - 仅繁体中文（排除简体和简繁）
- **简繁** - 简繁双语内封

### 分辨率选项
- **1080p** - Full HD（自动排除4K）
- **4K** - Ultra HD 2160p
- **720p** - HD（自动排除1080p和4K）

## 🔧 性能优化

脚本针对不同平台进行了优化：
- **Mac** - 标准优化，流畅体验
- **Windows** - 分批处理、延长节流，适配Windows渲染特性

## 📝 更新日志

### v14.3.0 (2024-xx-xx)
- ✨ 针对Windows平台优化性能
- 🐛 修复多字幕组互相干扰的问题
- 🎯 优化语言筛选逻辑，准确区分简繁
- ⚡ 添加正则表达式缓存机制

### v14.0.0
- 🎉 初始版本
- ✨ 为每个字幕组提供独立筛选功能

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

[MIT License](LICENSE)

## 👤 作者

- 作者: morggna
- 性能优化: Claude
- GitHub: [@morggna](https://github.com/morggna)

## 🔗 相关链接

- [蜜柑计划官网](https://mikanani.me/)
- [Tampermonkey官网](https://www.tampermonkey.net/)
