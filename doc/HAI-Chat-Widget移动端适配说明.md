# CORTEX Chat Widget 移动端适配说明

## 📱 概述

CORTEX Chat Widget 是一个可嵌入任何网站的AI聊天组件,使用纯原生JavaScript和Shadow DOM实现,零依赖。本次更新为Widget添加了完整的移动端响应式支持。

## 🎯 适配特性

### 1. 启动按钮 (Launcher)

#### 桌面端
- 尺寸: 56x56px
- 位置: 右下角,距边缘24px
- 字体: 14px

#### 移动端 (≤768px)
- 尺寸: 52x52px
- 位置: 右下角,距边缘20px
- 字体: 13px

#### 小屏手机 (≤480px)
- 尺寸: 48x48px
- 位置: 右下角,距边缘16px
- 字体: 12px

#### 交互优化
- ✅ 添加`:hover`缩放效果 (1.1倍)
- ✅ 添加`:active`按下效果 (0.95倍)
- ✅ 平滑过渡动画 (200ms)

### 2. 聊天面板

#### 桌面端
- 尺寸: 380x560px
- 位置: 右下角,距启动按钮90px
- 圆角: 12px

#### 移动端 (≤768px)
- **全屏显示**: 占满整个屏幕 (100vw x 100vh)
- **无圆角**: border-radius设为0
- **固定定位**: top:0, left:0, right:0, bottom:0

这样在手机上打开时,聊天窗口会以全屏形式展示,类似原生APP体验。

### 3. 界面元素尺寸调整

#### 头部 (Header)

| 元素 | 桌面端 | 移动端 | 小屏手机 |
|------|--------|--------|----------|
| 内边距 | 14px 16px | 12px 14px | - |
| 标题字号 | 15px | 14px | - |

#### 消息列表 (Messages)

| 元素 | 桌面端 | 移动端 | 小屏手机 |
|------|--------|--------|----------|
| 内边距 | 12px | 10px | - |
| 消息字号 | 14px | 13px | 12px |
| 消息内边距 | 8px 12px | 7px 10px | 6px 9px |

#### 输入区域 (Input Area)

| 元素 | 桌面端 | 移动端 | 小屏手机 |
|------|--------|--------|----------|
| 区域内边距 | 10px | 8px | - |
| 输入框高度 | 40px | 36px | 34px |
| 输入框字号 | 14px | 13px | 12px |
| 输入框内边距 | 8px 12px | 6px 10px | - |
| 发送按钮字号 | 14px | 13px | 12px |
| 发送按钮最小宽度 | 60px | 54px | - |

### 4. 触摸优化

#### 按钮交互
```css
.cortex-btn-approve:active { background:#388e3c }  /* 深绿 */
.cortex-btn-reject:active { background:#d32f2f }   /* 深红 */
.cortex-send:active { background:#3730a3 }         /* 深蓝 */
```

#### 滚动优化
```css
.cortex-messages { -webkit-overflow-scrolling: touch }
```
在iOS上启用惯性滚动,提升触摸体验。

## 🔧 技术实现

### CSS媒体查询

```javascript
var CSS = '' +
  // ... 基础样式 ...
  // 移动端适配 (≤768px)
  '@media (max-width: 768px){' +
  '.cortex-launcher{width:52px;height:52px;bottom:20px;right:20px}' +
  '.cortex-panel{width:100%;height:100%;border-radius:0;bottom:0;right:0;left:0;top:0}' +
  // ... 更多样式 ...
  '}' +
  // 小屏手机适配 (≤480px)
  '@media (max-width: 480px){' +
  '.cortex-launcher{width:48px;height:48px;bottom:16px;right:16px}' +
  // ... 更多样式 ...
  '}';
```

### Shadow DOM隔离

Widget使用Shadow DOM,样式完全隔离,不会与宿主页面的CSS冲突:

```javascript
host = document.createElement('div');
host.id = 'cortex-chat-widget';
document.body.appendChild(host);
shadow = host.attachShadow({ mode: 'open' });
```

## 📋 使用示例

### 基础引入

```html
<!-- 引入Widget脚本 -->
<script src="http://your-cortex-server:8080/widget/cortex-chat.js"></script>

<!-- 初始化Widget -->
<script>
  HaiChat.init({
    apiKey: 'your-api-key',
    businessSystem: 'crm',
    userLoginName: 'zhangsan',
    agentCode: 'crm-assistant',
    position: 'bottom-right',
    title: 'CRM助手'
  });
</script>
```

### 响应式测试

```javascript
// 自动适配,无需额外配置
// 在移动端浏览器打开即可看到全屏效果
```

## 🎨 视觉效果对比

### 桌面端
```
┌─────────────────┐
│                 │  
│                 │ ← 380x560px 浮动窗口
│    聊天内容      │    带圆角和阴影
│                 │
└─────────────────┘
              (O) ← 56x56px 启动按钮
```

### 移动端
```
┏━━━━━━━━━━━━━━━┓
┃  AI助手    [×] ┃ ← 头部
┣━━━━━━━━━━━━━━━┫
┃               ┃
┃               ┃
┃   聊天内容     ┃ ← 全屏显示
┃               ┃    无圆角
┃               ┃
┣━━━━━━━━━━━━━━━┫
┃ [输入框] [发送]┃ ← 底部输入
┗━━━━━━━━━━━━━━━┛
       (O) ← 52px 启动按钮(未展开时)
```

## ✅ 测试清单

### 功能测试
- [ ] **启动按钮**: 点击打开/关闭聊天窗口
- [ ] **全屏显示**: 移动端聊天窗口占满屏幕
- [ ] **发送消息**: 可以正常发送和接收消息
- [ ] **工具调用**: 工具调用提示显示正常
- [ ] **审批功能**: 批准/拒绝按钮可正常点击
- [ ] **滚动**: 消息列表滚动流畅
- [ ] **软键盘**: 输入时软键盘不遮挡输入框

### 兼容性测试
- [ ] **iOS Safari**: iPhone上显示正常
- [ ] **Android Chrome**: 安卓手机显示正常
- [ ] **微信浏览器**: 在微信中打开正常
- [ ] **桌面浏览器**: Chrome/Firefox/Safari/Edge

### 响应式测试

#### 断点测试
1. **1920px** (桌面): 380x560px浮动窗口
2. **768px** (平板临界点): 开始全屏
3. **480px** (小屏临界点): 更紧凑布局
4. **375px** (iPhone SE): 正常显示
5. **320px** (极小屏): 依然可用

#### 真机测试
- [ ] iPhone SE (375px)
- [ ] iPhone 12 (390px)
- [ ] iPhone 12 Pro Max (428px)
- [ ] 小米/华为等Android手机
- [ ] iPad (768px)

## 🚀 部署说明

### 构建与发布

```bash
# 源文件位置
cortex-admin/src/main/resources/static/widget/cortex-chat.js

# 编译后位置
cortex-admin/target/classes/static/widget/cortex-chat.js
```

修改源文件后,需要重新编译项目才能生效:

```bash
mvn clean package
```

### CDN部署

如果使用CDN加速:

```html
<script src="https://cdn.example.com/cortex-chat.js"></script>
```

记得清除CDN缓存,确保用户获取到最新版本。

## 📊 性能优化

### 已实现
- ✅ **零依赖**: 纯原生JS,无需加载任何库
- ✅ **轻量级**: 压缩后仅约6KB
- ✅ **Shadow DOM**: CSS隔离,不影响宿主页面
- ✅ **硬件加速**: 使用transform实现动画
- ✅ **惯性滚动**: iOS触摸体验优化

### 建议优化
- 🔄 启用Gzip压缩 (可减少70%体积)
- 🔄 设置合理的缓存策略
- 🔄 考虑使用Service Worker离线缓存

## 🔍 调试技巧

### Chrome DevTools

```javascript
// 查看Shadow DOM内容
document.querySelector('#cortex-chat-widget').shadowRoot

// 查看CSS样式
document.querySelector('#cortex-chat-widget').shadowRoot.querySelector('style').textContent

// 模拟移动设备
// F12 → Ctrl+Shift+M → 选择设备
```

### 控制台日志

Widget会输出关键日志:

```
[CORTEX Chat] Widget initialized {apiKey: "...", ...}
[CORTEX Chat] parse error ...
[CORTEX Chat] approve error ...
```

## 🐛 常见问题

### Q1: 移动端聊天窗口不是全屏?
**A**: 检查浏览器是否支持CSS媒体查询,尝试清除缓存。

### Q2: 软键盘遮挡输入框?
**A**: iOS系统会自动处理,Android可能需要在页面添加:
```html
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
```

### Q3: 样式与宿主页面冲突?
**A**: Widget使用Shadow DOM隔离,不应该冲突。如有问题,检查z-index设置。

### Q4: 微信浏览器中启动按钮不显示?
**A**: 检查z-index是否足够高 (当前999999),微信某些组件层级很高。

## 📈 更新记录

### v1.1.0 (2026-07-06)
- ✅ 新增移动端全屏模式
- ✅ 优化触摸交互体验
- ✅ 调整各断点尺寸
- ✅ 添加iOS惯性滚动
- ✅ 增强按钮点击反馈

### v1.0.0 (初始版本)
- 基础聊天功能
- SSE流式输出
- 工具调用支持
- 审批机制

## 📞 技术支持

如有问题或建议,请联系开发团队。

---

**更新日期**: 2026-07-06  
**版本**: v1.1.0  
**维护者**: HAI团队
