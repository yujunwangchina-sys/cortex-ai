# Cortex Chat Widget 简明使用指南

## 一行代码集成AI助手

只需两个参数，即可在任何业务系统中集成AI聊天组件：

```html
<script src="http://your-cortex-server:8080/widget/cortex-chat.js"></script>
<script>
  CortexChat.init({
    apiKey: 'your-api-key',           // 必填：API密钥
    userLoginName: 'zhangsan'         // 必填：当前登录用户名
  });
</script>
```

**就这么简单！** 其他配置（业务系统、Agent等）都会根据API Key自动从后端获取。

## 参数说明

### 必填参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `apiKey` | API密钥，标识业务系统和权限 | `'sk-abc123...'` |
| `userLoginName` | 当前登录用户名 | `'zhangsan'` |

### API Key 如何获取？

1. 登录Cortex管理后台
2. 进入 **系统管理** > **API密钥管理**
3. 点击 **新建密钥**
4. 选择业务系统，设置权限和有效期
5. 复制生成的密钥

### API Key 包含的信息

API Key 关联了以下信息（无需手动配置）：
- **业务系统标识** (businessSystem) - 如 'crm', 'erp', 'oa'
- **默认Agent** (agentCode, agentName) - 该业务系统的智能助手
- **权限范围** - 可访问的功能和数据范围

## 完整示例

### 示例1：CRM系统集成

```html
<!DOCTYPE html>
<html>
<head>
    <title>CRM系统</title>
</head>
<body>
    <h1>客户关系管理系统</h1>
    <!-- 你的CRM系统内容 -->
    
    <!-- 集成Cortex Chat -->
    <script src="http://cortex-server:8080/widget/cortex-chat.js"></script>
    <script>
        // 从CRM系统获取当前用户
        var currentUser = getCRMCurrentUser(); // 你的CRM系统函数
        
        CortexChat.init({
            apiKey: 'sk-crm-xyz789',
            userLoginName: currentUser.username
        });
    </script>
</body>
</html>
```

### 示例2：ERP系统集成

```html
<script src="http://cortex-server:8080/widget/cortex-chat.js"></script>
<script>
    CortexChat.init({
        apiKey: 'sk-erp-abc123',
        userLoginName: localStorage.getItem('username')
    });
</script>
```

### 示例3：动态获取API Key

```html
<script src="http://cortex-server:8080/widget/cortex-chat.js"></script>
<script>
    // 从后端获取API Key（推荐，更安全）
    fetch('/api/get-cortex-apikey')
        .then(response => response.json())
        .then(data => {
            CortexChat.init({
                apiKey: data.apiKey,
                userLoginName: data.username
            });
        });
</script>
```

## 功能特性

✅ **圆形蓝色+按钮** - 点击展开变为−号  
✅ **窗口缩放** - 支持最大化和还原  
✅ **新建会话** - 一键开始新对话  
✅ **移动适配** - 完美支持手机和平板  
✅ **流式响应** - 实时显示AI回复  
✅ **工具审批** - 敏感操作需要用户确认  

## 按钮位置

固定在**右下角**，适配各种屏幕尺寸：
- 桌面端：距离右下角 24px
- 移动端：自动调整间距

## 窗口尺寸

固定尺寸，无需配置：
- 宽度：400px
- 高度：600px
- 移动端：自动全屏显示

## 常见问题

### Q1: API Key 在哪里配置？

A: API Key 在Cortex管理后台生成，关联到具体的业务系统。一个业务系统可以生成多个API Key，用于不同的环境（开发、测试、生产）。

### Q2: 如何切换Agent？

A: API Key 关联了默认的Agent。如果需要使用不同的Agent，需要在Cortex管理后台修改API Key的配置。

### Q3: 用户名可以是邮箱吗？

A: 可以，`userLoginName` 支持任何字符串格式，只要在业务系统中能唯一标识用户即可。

### Q4: 多个业务系统可以共用一个API Key吗？

A: 不建议。每个业务系统应该使用独立的API Key，便于权限控制和使用统计。

### Q5: API Key 泄露了怎么办？

A: 立即在Cortex管理后台禁用该API Key，然后生成新的密钥。

## 安全建议

1. **不要在前端硬编码API Key**
   - 推荐通过后端接口动态获取
   - 使用环境变量管理不同环境的密钥

2. **定期轮换密钥**
   - 建议每3-6个月更换一次
   - 在Cortex管理后台设置有效期

3. **使用HTTPS**
   - 生产环境务必使用HTTPS
   - 避免密钥在传输中泄露

4. **最小权限原则**
   - 只授予必要的权限
   - 不同环境使用不同的API Key

## 技术支持

如有问题，请联系：
- 📧 技术支持邮箱：support@cortex-platform.com
- 📚 技术文档：http://docs.cortex-platform.com
- 💬 技术交流群：查看管理后台

## 更新日志

### v2.0.0 (2026-07-07)
- ✨ 简化配置：只需 apiKey + userLoginName
- ✨ 自动获取：业务系统和Agent信息从后端查询
- 🎨 固定样式：统一的窗口尺寸和位置
- 📱 优化体验：更好的移动端适配
