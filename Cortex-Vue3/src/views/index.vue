<template>
  <div class="dashboard-container">
    <div class="welcome-section">
      <p class="welcome-subtitle">智能对话管理平台</p>
    </div>

    <el-row :gutter="20" class="stats-row" v-loading="loading">
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card gradient-1">
          <div class="stat-icon">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-label">对话会话</div>
            <div class="stat-value">{{ stats.sessionCount?.toLocaleString() || 0 }}</div>
            <div class="stat-trend" v-if="stats.sessionTrend">
              <span :class="stats.sessionTrend > 0 ? 'trend-up' : 'trend-down'">
                {{ stats.sessionTrend > 0 ? '+' : '' }}{{ stats.sessionTrend }}%
              </span>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card gradient-2">
          <div class="stat-icon">
            <el-icon><Connection /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-label">插件数量</div>
            <div class="stat-value">{{ stats.pluginCount?.toLocaleString() || 0 }}</div>
            <div class="stat-trend" v-if="stats.pluginTrend">
              <span :class="stats.pluginTrend > 0 ? 'trend-up' : 'trend-down'">
                {{ stats.pluginTrend > 0 ? '+' : '' }}{{ stats.pluginTrend }}%
              </span>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card gradient-3">
          <div class="stat-icon">
            <el-icon><Document /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-label">技能包</div>
            <div class="stat-value">{{ stats.skillCount?.toLocaleString() || 0 }}</div>
            <div class="stat-trend" v-if="stats.skillTrend">
              <span :class="stats.skillTrend > 0 ? 'trend-up' : 'trend-down'">
                {{ stats.skillTrend > 0 ? '+' : '' }}{{ stats.skillTrend }}%
              </span>
            </div>
          </div>
        </div>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <div class="stat-card gradient-4">
          <div class="stat-icon">
            <el-icon><User /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-label">活跃用户</div>
            <div class="stat-value">{{ stats.userCount?.toLocaleString() || 0 }}</div>
            <div class="stat-trend" v-if="stats.userTrend">
              <span :class="stats.userTrend > 0 ? 'trend-up' : 'trend-down'">
                {{ stats.userTrend > 0 ? '+' : '' }}{{ stats.userTrend }}%
              </span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <el-col :xs="24" :lg="16">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>使用趋势分析</span>
              <el-radio-group v-model="trendPeriod" size="small" @change="updateTrendChart">
                <el-radio-button label="week">近7天</el-radio-button>
                <el-radio-button label="month">近30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card class="chart-card">
          <template #header>
            <span>资源分布</span>
          </template>
          <div ref="pieChartRef" class="chart-container-small"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :lg="12">
        <el-card class="main-card">
          <template #header>
            <span>快速访问</span>
          </template>
          <div class="quick-links">
            <router-link to="/agent/chat" class="quick-link-item">
              <div class="quick-icon gradient-1">
                <el-icon><ChatDotRound /></el-icon>
              </div>
              <div class="quick-text">
                <span class="quick-title">Agent对话</span>
                <span class="quick-desc">开始智能对话</span>
              </div>
            </router-link>
            <router-link to="/plugin/list" class="quick-link-item">
              <div class="quick-icon gradient-2">
                <el-icon><Connection /></el-icon>
              </div>
              <div class="quick-text">
                <span class="quick-title">MCP插件</span>
                <span class="quick-desc">管理插件配置</span>
              </div>
            </router-link>
            <router-link to="/skill" class="quick-link-item">
              <div class="quick-icon gradient-3">
                <el-icon><Document /></el-icon>
              </div>
              <div class="quick-text">
                <span class="quick-title">技能管理</span>
                <span class="quick-desc">配置技能包</span>
              </div>
            </router-link>
            <router-link to="/system/user" class="quick-link-item">
              <div class="quick-icon gradient-4">
                <el-icon><User /></el-icon>
              </div>
              <div class="quick-text">
                <span class="quick-title">用户管理</span>
                <span class="quick-desc">管理系统用户</span>
              </div>
            </router-link>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="12">
        <el-card class="info-card">
          <template #header>
            <span>系统信息</span>
          </template>
          <div class="info-list">
            <div class="info-item">
              <div class="info-left">
                <el-icon class="info-icon"><Platform /></el-icon>
                <span class="info-label">当前版本</span>
              </div>
              <span class="info-value">v{{ version }}</span>
            </div>
            <div class="info-item">
              <div class="info-left">
                <el-icon class="info-icon"><Grid /></el-icon>
                <span class="info-label">框架</span>
              </div>
              <span class="info-value">若依 Vue3</span>
            </div>
            <div class="info-item">
              <div class="info-left">
                <el-icon class="info-icon"><Service /></el-icon>
                <span class="info-label">后端</span>
              </div>
              <span class="info-value">Spring Boot</span>
            </div>
            <div class="info-item">
              <div class="info-left">
                <el-icon class="info-icon"><Monitor /></el-icon>
                <span class="info-label">前端</span>
              </div>
              <span class="info-value">Vue 3 + Element Plus</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ChatDotRound, Connection, Document, User, Platform, Grid, Service, Monitor } from '@element-plus/icons-vue'
import { getDashboardStats, getTrendData } from '@/api/dashboard/stats'
import * as echarts from 'echarts'

const version = import.meta.env.VITE_APP_VERSION || '1.0.0'

// 统计数据
const stats = ref({
  sessionCount: 0,
  pluginCount: 0,
  skillCount: 0,
  userCount: 0,
  sessionTrend: 0,
  pluginTrend: 0,
  skillTrend: 0,
  userTrend: 0
})

const loading = ref(false)
const trendPeriod = ref('week')

// 图表引用
const trendChartRef = ref(null)
const pieChartRef = ref(null)
let trendChart = null
let pieChart = null

// 图表数据
const chartData = ref({
  dates: [],
  sessions: [],
  plugins: [],
  skills: []
})

// 初始化趋势图表
const initTrendChart = () => {
  if (!trendChartRef.value) return
  
  const isDark = document.documentElement.classList.contains('dark')
  const textColor = isDark ? '#ffffff' : '#333333'
  const lineColor = isDark ? '#3a3a3a' : '#e0e0e0'
  
  trendChart = echarts.init(trendChartRef.value)
  
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark ? '#2a2a2a' : '#ffffff',
      borderColor: lineColor,
      textStyle: {
        color: textColor
      }
    },
    legend: {
      data: ['会话数', '插件数', '技能包'],
      textStyle: {
        color: textColor
      },
      top: 10
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '60px',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: chartData.value.dates,
      axisLine: {
        lineStyle: {
          color: lineColor
        }
      },
      axisLabel: {
        color: textColor
      }
    },
    yAxis: {
      type: 'value',
      axisLine: {
        lineStyle: {
          color: lineColor
        }
      },
      axisLabel: {
        color: textColor
      },
      splitLine: {
        lineStyle: {
          color: lineColor,
          type: 'dashed'
        }
      }
    },
    series: [
      {
        name: '会话数',
        type: 'line',
        smooth: true,
        data: chartData.value.sessions,
        itemStyle: {
          color: '#5470c6'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(84, 112, 198, 0.3)' },
            { offset: 1, color: 'rgba(84, 112, 198, 0.05)' }
          ])
        }
      },
      {
        name: '插件数',
        type: 'line',
        smooth: true,
        data: chartData.value.plugins,
        itemStyle: {
          color: '#91cc75'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(145, 204, 117, 0.3)' },
            { offset: 1, color: 'rgba(145, 204, 117, 0.05)' }
          ])
        }
      },
      {
        name: '技能包',
        type: 'line',
        smooth: true,
        data: chartData.value.skills,
        itemStyle: {
          color: '#fac858'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(250, 200, 88, 0.3)' },
            { offset: 1, color: 'rgba(250, 200, 88, 0.05)' }
          ])
        }
      }
    ]
  }
  
  trendChart.setOption(option)
}

// 初始化饼图
const initPieChart = () => {
  if (!pieChartRef.value) return
  
  const isDark = document.documentElement.classList.contains('dark')
  const textColor = isDark ? '#ffffff' : '#333333'
  
  pieChart = echarts.init(pieChartRef.value)
  
  const option = {
    tooltip: {
      trigger: 'item',
      backgroundColor: isDark ? '#2a2a2a' : '#ffffff',
      borderColor: isDark ? '#3a3a3a' : '#e0e0e0',
      textStyle: {
        color: textColor
      },
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: {
        color: textColor
      }
    },
    series: [
      {
        name: '资源分布',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: isDark ? '#2a2a2a' : '#ffffff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold',
            color: textColor
          }
        },
        labelLine: {
          show: false
        },
        data: [
          { value: stats.value.sessionCount || 0, name: '对话会话', itemStyle: { color: '#5470c6' } },
          { value: stats.value.pluginCount || 0, name: '插件数量', itemStyle: { color: '#91cc75' } },
          { value: stats.value.skillCount || 0, name: '技能包', itemStyle: { color: '#fac858' } },
          { value: stats.value.userCount || 0, name: '活跃用户', itemStyle: { color: '#ee6666' } }
        ]
      }
    ]
  }
  
  pieChart.setOption(option)
}

// 更新趋势图表
const updateTrendChart = async () => {
  await loadTrendData()
  
  if (!trendChart) return
  
  trendChart.setOption({
    xAxis: {
      data: chartData.value.dates
    },
    series: [
      { data: chartData.value.sessions },
      { data: chartData.value.plugins },
      { data: chartData.value.skills }
    ]
  })
}

// 加载趋势数据
const loadTrendData = async () => {
  try {
    const res = await getTrendData(trendPeriod.value)
    if (res.code === 200 && res.data) {
      chartData.value = res.data
    } else {
      // 后端还没实现时使用默认提示
      console.warn('趋势数据API未实现，使用空数据')
      chartData.value = {
        dates: [],
        sessions: [],
        plugins: [],
        skills: []
      }
    }
  } catch (error) {
    console.error('加载趋势数据失败:', error)
    // API不存在时显示空图表
    chartData.value = {
      dates: [],
      sessions: [],
      plugins: [],
      skills: []
    }
  }
}

// 加载统计数据
const loadStats = async () => {
  loading.value = true
  try {
    const res = await getDashboardStats()
    if (res.code === 200) {
      stats.value = {
        sessionCount: res.data.sessionCount || 0,
        pluginCount: res.data.pluginCount || 0,
        skillCount: res.data.skillCount || 0,
        userCount: res.data.userCount || 0,
        sessionTrend: res.data.sessionTrend || 0,
        pluginTrend: res.data.pluginTrend || 0,
        skillTrend: res.data.skillTrend || 0,
        userTrend: res.data.userTrend || 0
      }
      
      // 更新饼图数据
      await nextTick()
      if (pieChart) {
        pieChart.setOption({
          series: [{
            data: [
              { value: stats.value.sessionCount || 0, name: '对话会话' },
              { value: stats.value.pluginCount || 0, name: '插件数量' },
              { value: stats.value.skillCount || 0, name: '技能包' },
              { value: stats.value.userCount || 0, name: '活跃用户' }
            ]
          }]
        })
      }
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 窗口大小改变时重新渲染图表
const handleResize = () => {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  await loadStats()
  await loadTrendData()
  await nextTick()
  initTrendChart()
  initPieChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})
</script>

<style lang="scss" scoped>
.dashboard-container {
  padding: 20px;
  min-height: calc(100vh - 100px);
  background: linear-gradient(135deg, #f5f7fa 0%, #f0f2f5 100%);
}

.welcome-section {
  text-align: center;
  padding: 30px 20px;
  margin-bottom: 30px;

  .welcome-subtitle {
    font-size: 20px;
    color: #666666;
    margin: 0;
    font-weight: 500;
  }
}

.stats-row {
  margin-bottom: 20px;

  .stat-card {
    background: #ffffff;
    border-radius: 12px;
    padding: 24px;
    display: flex;
    align-items: center;
    gap: 20px;
    border: none;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    transition: all 0.3s;
    height: 100%;
    position: relative;
    overflow: hidden;

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 3px;
      background: linear-gradient(90deg, #5470c6, #91cc75);
      opacity: 0;
      transition: opacity 0.3s;
    }

    &.gradient-1::before {
      background: linear-gradient(90deg, #5470c6, #73c0de);
    }

    &.gradient-2::before {
      background: linear-gradient(90deg, #91cc75, #fac858);
    }

    &.gradient-3::before {
      background: linear-gradient(90deg, #fac858, #ee6666);
    }

    &.gradient-4::before {
      background: linear-gradient(90deg, #ee6666, #9a60b4);
    }

    &:hover {
      transform: translateY(-4px);
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);

      &::before {
        opacity: 1;
      }
    }

    .stat-icon {
      width: 64px;
      height: 64px;
      border-radius: 12px;
      background: linear-gradient(135deg, #5470c6, #73c0de);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      box-shadow: 0 4px 12px rgba(84, 112, 198, 0.3);

      .el-icon {
        font-size: 32px;
        color: #ffffff;
      }
    }

    &.gradient-1 .stat-icon {
      background: linear-gradient(135deg, #5470c6, #73c0de);
      box-shadow: 0 4px 12px rgba(84, 112, 198, 0.3);
    }

    &.gradient-2 .stat-icon {
      background: linear-gradient(135deg, #91cc75, #fac858);
      box-shadow: 0 4px 12px rgba(145, 204, 117, 0.3);
    }

    &.gradient-3 .stat-icon {
      background: linear-gradient(135deg, #fac858, #ee6666);
      box-shadow: 0 4px 12px rgba(250, 200, 88, 0.3);
    }

    &.gradient-4 .stat-icon {
      background: linear-gradient(135deg, #ee6666, #9a60b4);
      box-shadow: 0 4px 12px rgba(238, 102, 102, 0.3);
    }

    .stat-content {
      flex: 1;

      .stat-label {
        display: block;
        font-size: 14px;
        color: #999999;
        margin-bottom: 8px;
      }

      .stat-value {
        display: block;
        font-size: 32px;
        font-weight: 700;
        color: #333333;
        line-height: 1;
        margin-bottom: 8px;
      }

      .stat-trend {
        font-size: 12px;

        .trend-up {
          color: #67c23a;

          &::before {
            content: '↑ ';
          }
        }

        .trend-down {
          color: #f56c6c;

          &::before {
            content: '↓ ';
          }
        }
      }
    }
  }
}

.chart-row {
  margin-bottom: 20px;

  .chart-card {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    border-radius: 12px;
    border: none;

    :deep(.el-card__header) {
      padding: 20px 24px;
      border-bottom: 1px solid #f0f0f0;
      background: #ffffff;

      .card-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 16px;
        font-weight: 600;
        color: #333333;
      }
    }

    :deep(.el-card__body) {
      padding: 24px;
    }
  }

  .chart-container {
    width: 100%;
    height: 350px;
  }

  .chart-container-small {
    width: 100%;
    height: 350px;
  }
}

.content-row {
  .main-card,
  .info-card {
    margin-bottom: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    border-radius: 12px;
    border: none;

    :deep(.el-card__header) {
      padding: 20px 24px;
      border-bottom: 1px solid #f0f0f0;
      font-size: 16px;
      font-weight: 600;
      color: #333333;
      background: #ffffff;
    }

    :deep(.el-card__body) {
      padding: 24px;
    }
  }

  .quick-links {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;

    .quick-link-item {
      display: flex;
      align-items: center;
      padding: 20px;
      border: 1px solid #f0f0f0;
      border-radius: 12px;
      text-decoration: none;
      color: #333333;
      transition: all 0.3s;
      background: #ffffff;

      &:hover {
        border-color: transparent;
        background: linear-gradient(135deg, #f5f7fa, #e8ecf1);
        transform: translateX(4px);
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      }

      .quick-icon {
        width: 48px;
        height: 48px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-right: 16px;
        flex-shrink: 0;

        &.gradient-1 {
          background: linear-gradient(135deg, #5470c6, #73c0de);
        }

        &.gradient-2 {
          background: linear-gradient(135deg, #91cc75, #fac858);
        }

        &.gradient-3 {
          background: linear-gradient(135deg, #fac858, #ee6666);
        }

        &.gradient-4 {
          background: linear-gradient(135deg, #ee6666, #9a60b4);
        }

        .el-icon {
          font-size: 24px;
          color: #ffffff;
        }
      }

      .quick-text {
        display: flex;
        flex-direction: column;
        gap: 4px;

        .quick-title {
          font-size: 16px;
          font-weight: 600;
          color: #333333;
        }

        .quick-desc {
          font-size: 13px;
          color: #999999;
        }
      }
    }
  }

  .info-list {
    .info-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 18px 0;
      border-bottom: 1px solid #f5f5f5;

      &:last-child {
        border-bottom: none;
      }

      .info-left {
        display: flex;
        align-items: center;
        gap: 12px;

        .info-icon {
          font-size: 20px;
          color: #5470c6;
        }

        .info-label {
          font-size: 14px;
          color: #666666;
        }
      }

      .info-value {
        font-size: 14px;
        font-weight: 600;
        color: #333333;
      }
    }
  }
}

/* 暗黑模式 */
html.dark {
  .dashboard-container {
    background: linear-gradient(135deg, #1a1a1a 0%, #2a2a2a 100%);

    .welcome-section {
      .welcome-subtitle {
        color: #999999;
      }
    }

    .stats-row {
      .stat-card {
        background: #2a2a2a;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);

        .stat-content {
          .stat-label {
            color: #999999;
          }

          .stat-value {
            color: #ffffff;
          }
        }
      }
    }

    .chart-row {
      .chart-card {
        background: #2a2a2a;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);

        :deep(.el-card__header) {
          border-bottom-color: #3a3a3a;
          background: #2a2a2a;

          .card-header {
            color: #ffffff;
          }
        }

        :deep(.el-card__body) {
          background: #2a2a2a;
        }
      }
    }

    .content-row {
      .main-card,
      .info-card {
        background: #2a2a2a;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);

        :deep(.el-card__header) {
          border-bottom-color: #3a3a3a;
          color: #ffffff;
          background: #2a2a2a;
        }

        :deep(.el-card__body) {
          background: #2a2a2a;
        }
      }

      .quick-links {
        .quick-link-item {
          border-color: #3a3a3a;
          background: #2a2a2a;

          &:hover {
            background: linear-gradient(135deg, #333333, #3a3a3a);
          }

          .quick-text {
            .quick-title {
              color: #ffffff;
            }

            .quick-desc {
              color: #999999;
            }
          }
        }
      }

      .info-list {
        .info-item {
          border-bottom-color: #3a3a3a;

          .info-left {
            .info-label {
              color: #999999;
            }
          }

          .info-value {
            color: #ffffff;
          }
        }
      }
    }
  }
}

/* 响应式 */
@media (max-width: 768px) {
  .welcome-section {
    padding: 20px 20px !important;

    .welcome-subtitle {
      font-size: 16px !important;
    }
  }

  .quick-links {
    grid-template-columns: 1fr !important;
  }

  .chart-container,
  .chart-container-small {
    height: 280px !important;
  }

  .stat-card {
    .stat-icon {
      width: 48px !important;
      height: 48px !important;

      .el-icon {
        font-size: 24px !important;
      }
    }

    .stat-content {
      .stat-value {
        font-size: 24px !important;
      }
    }
  }
}
</style>
