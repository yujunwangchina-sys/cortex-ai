# Dashboard 美化优化说明

## 优化概述

本次对 Dashboard 首页进行了全面的美化和功能增强，移除了"CortexAI"大标题，使用 ECharts 图表展示真实数据，提升了界面的现代感和数据可视化效果。

## 前端优化

### 1. 界面调整

**移除内容：**
- 去掉了页面顶部的 "CortexAI" 大标题
- 只保留副标题"智能对话管理平台"

**新增内容：**
- 添加页面渐变背景
- 优化统计卡片样式（渐变色图标、悬浮动画、顶部彩色条）
- 增加数据趋势指示器（显示增长/下降百分比）

### 2. ECharts 图表集成

#### 使用趋势分析图（折线面积图）
- **位置**：左侧大卡片
- **功能**：展示会话数、插件数、技能包的趋势变化
- **特性**：
  - 支持切换近7天/近30天数据
  - 渐变色填充区域
  - 平滑曲线动画
  - 自适应暗黑模式
  - 响应式布局

#### 资源分布饼图（环形图）
- **位置**：右侧小卡片
- **功能**：展示各类资源（会话、插件、技能包、用户）的占比
- **特性**：
  - 环形展示，视觉效果更好
  - 悬浮显示详细数据
  - 交互式高亮
  - 自适应暗黑模式

### 3. 卡片样式优化

#### 统计卡片
- 4种渐变色主题（蓝、绿、黄、红紫）
- 大图标圆角设计
- 阴影和悬浮效果
- 顶部渐变色指示条
- 显示增长趋势百分比

#### 快速访问卡片
- 改为横向布局
- 渐变色图标
- 增加功能描述文字
- 优化悬浮交互效果

#### 系统信息卡片
- 添加图标装饰
- 优化行间距
- 改进视觉层次

### 4. 响应式设计
- 适配移动端显示
- 图表自动调整大小
- 窗口大小改变时重新渲染

### 5. 暗黑模式支持
- 图表配色自适应
- 卡片背景和边框自动调整
- 文字颜色自动切换

## 后端优化

### 1. Controller 层增强

**文件**：`DashboardController.java`

新增功能：
- 增加趋势数据计算
- 支持周/月维度数据查询
- 计算增长百分比

新增接口：
```java
GET /dashboard/stats       // 获取统计数据（含趋势百分比）
GET /dashboard/trend       // 获取趋势图表数据
    参数: period = week|month
```

### 2. Mapper 层扩展

为各个模块添加了趋势统计方法：

#### AiAgentSessionMapper
```java
int countSessions()                    // 统计会话总数
int countSessionsLastPeriod()          // 统计上一周期会话数
int countSessionsByDate(String date)   // 按日期统计会话数
```

#### AiPluginMapper
```java
int countPlugins()                     // 统计插件总数
int countPluginsLastPeriod()           // 统计上一周期插件数
int countPluginsByDate(String date)    // 按日期统计插件数
```

#### SkillNodeMapper
```java
int countSkillPackages()                    // 统计技能包总数
int countSkillPackagesLastPeriod()          // 统计上一周期技能包数
int countSkillPackagesByDate(String date)   // 按日期统计技能包数
```

#### SysUserMapper
```java
int countActiveUsers()                      // 统计活跃用户数
int countActiveUsersLastPeriod()            // 统计上一周期活跃用户数
int countActiveUsersByDate(String date)     // 按日期统计活跃用户数
```

### 3. XML Mapper 实现

为每个 Mapper 添加了 SQL 查询实现：

**趋势对比查询**：
```xml
<select id="countSessionsLastPeriod" resultType="int">
    select count(*) from ai_agent_session
    where create_time &lt;= DATE_SUB(NOW(), INTERVAL 7 DAY)
</select>
```

**按日期统计查询**：
```xml
<select id="countSessionsByDate" parameterType="String" resultType="int">
    select count(*) from ai_agent_session
    where DATE(create_time) &lt;= #{date}
</select>
```

## API 响应格式

### 统计数据接口
```json
{
  "code": 200,
  "data": {
    "sessionCount": 350,
    "pluginCount": 30,
    "skillCount": 20,
    "userCount": 150,
    "sessionTrend": 15,     // 增长百分比
    "pluginTrend": 5,
    "skillTrend": 10,
    "userTrend": 8
  }
}
```

### 趋势数据接口
```json
{
  "code": 200,
  "data": {
    "dates": ["01-01", "01-02", "01-03", ...],
    "sessions": [120, 150, 180, ...],
    "plugins": [15, 18, 20, ...],
    "skills": [8, 10, 12, ...]
  }
}
```

## 配色方案

### 渐变色主题
- **主题1**（对话会话）：#5470c6 → #73c0de（蓝色系）
- **主题2**（插件数量）：#91cc75 → #fac858（绿黄系）
- **主题3**（技能包）：#fac858 → #ee6666（黄红系）
- **主题4**（活跃用户）：#ee6666 → #9a60b4（红紫系）

### 图表配色
- 会话数：#5470c6（蓝色）
- 插件数：#91cc75（绿色）
- 技能包：#fac858（黄色）
- 活跃用户：#ee6666（红色）

## 文件清单

### 前端文件
- `Cortex-Vue3/src/views/index.vue` - Dashboard 主页面
- `Cortex-Vue3/src/api/dashboard/stats.js` - API 接口定义

### 后端文件
- `cortex-admin/src/main/java/com/cortex/web/controller/dashboard/DashboardController.java`
- `cortex-system/src/main/java/com/cortex/agent/mapper/AiAgentSessionMapper.java`
- `cortex-system/src/main/java/com/cortex/plugin/mapper/AiPluginMapper.java`
- `cortex-system/src/main/java/com/cortex/skill/mapper/SkillNodeMapper.java`
- `cortex-system/src/main/java/com/cortex/system/mapper/SysUserMapper.java`

### XML Mapper 文件
- `cortex-system/src/main/resources/mapper/agent/AiAgentSessionMapper.xml`
- `cortex-system/src/main/resources/mapper/plugin/AiPluginMapper.xml`
- `cortex-system/src/main/resources/mapper/skill/SkillNodeMapper.xml`
- `cortex-system/src/main/resources/mapper/system/SysUserMapper.xml`

## 技术特点

1. **数据真实性**：所有统计数据和图表数据都从数据库实时查询，不再使用模拟数据
2. **性能优化**：使用索引优化的日期查询，支持高效的时间范围统计
3. **可扩展性**：预留了多种统计维度，便于后续功能扩展
4. **用户体验**：图表自动刷新，支持时间维度切换，响应式设计
5. **视觉美观**：现代化的渐变色设计，符合当前审美趋势

## 使用说明

1. **查看统计数据**：打开系统后默认显示首页，展示所有统计信息
2. **切换时间维度**：点击趋势图上方的"近7天"/"近30天"按钮切换
3. **查看详细数据**：鼠标悬浮在图表上可以看到具体数值
4. **快速导航**：点击快速访问卡片跳转到对应功能模块

## 注意事项

1. 确保数据库表中的 `create_time` 字段有索引，以优化查询性能
2. 首次访问时如果数据为空，图表会显示空状态
3. 趋势百分比计算基于7天前的数据，新系统可能显示为0
4. 图表会自动适配暗黑模式，无需手动配置

## 后续优化建议

1. 添加数据缓存机制，减少数据库查询压力
2. 增加更多统计维度（如按用户、按时段等）
3. 支持自定义时间范围选择
4. 添加数据导出功能
5. 增加更多类型的图表（柱状图、雷达图等）
