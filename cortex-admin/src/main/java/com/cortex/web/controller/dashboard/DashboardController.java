package com.cortex.web.controller.dashboard;

import com.cortex.agent.mapper.AiAgentSessionMapper;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.plugin.mapper.AiPluginMapper;
import com.cortex.skill.mapper.SkillNodeMapper;
import com.cortex.system.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard统计Controller
 * 
 * @author cortex
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController extends BaseController
{
    @Autowired
    private AiAgentSessionMapper sessionMapper;
    
    @Autowired
    private AiPluginMapper pluginMapper;
    
    @Autowired
    private SkillNodeMapper skillNodeMapper;
    
    @Autowired
    private SysUserMapper userMapper;
    
    /**
     * 获取Dashboard统计数据
     */
    @GetMapping("/stats")
    public AjaxResult getStats()
    {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 统计会话数量
            int sessionCount = sessionMapper.countSessions();
            stats.put("sessionCount", sessionCount);
            
            // 统计插件数量
            int pluginCount = pluginMapper.countPlugins();
            stats.put("pluginCount", pluginCount);
            
            // 统计技能包数量（仅统计第一层文件夹）
            int skillCount = skillNodeMapper.countSkillPackages();
            stats.put("skillCount", skillCount);
            
            // 统计用户数量（排除已删除）
            int userCount = userMapper.countActiveUsers();
            stats.put("userCount", userCount);
            
            // 计算增长趋势（对比上周/上月）
            stats.put("sessionTrend", calculateTrend(sessionMapper.countSessionsLastPeriod(), sessionCount));
            stats.put("pluginTrend", calculateTrend(pluginMapper.countPluginsLastPeriod(), pluginCount));
            stats.put("skillTrend", calculateTrend(skillNodeMapper.countSkillPackagesLastPeriod(), skillCount));
            stats.put("userTrend", calculateTrend(userMapper.countActiveUsersLastPeriod(), userCount));
            
        } catch (Exception e) {
            // 如果查询失败，返回默认值
            logger.error("获取统计数据失败", e);
            stats.put("sessionCount", 0);
            stats.put("pluginCount", 0);
            stats.put("skillCount", 0);
            stats.put("userCount", 0);
            stats.put("sessionTrend", 0);
            stats.put("pluginTrend", 0);
            stats.put("skillTrend", 0);
            stats.put("userTrend", 0);
        }
        
        return success(stats);
    }
    
    /**
     * 获取趋势数据
     */
    @GetMapping("/trend")
    public AjaxResult getTrend(@RequestParam(defaultValue = "week") String period)
    {
        Map<String, Object> trendData = new HashMap<>();
        
        try {
            int days = "month".equals(period) ? 30 : 7;
            
            List<String> dates = new ArrayList<>();
            List<Integer> sessions = new ArrayList<>();
            List<Integer> plugins = new ArrayList<>();
            List<Integer> skills = new ArrayList<>();
            
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
            LocalDate today = LocalDate.now();
            
            for (int i = days - 1; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                String dateStr = date.format(formatter);
                dates.add(dateStr);
                
                // 查询当天的统计数据
                String dateParam = date.toString();
                sessions.add(sessionMapper.countSessionsByDate(dateParam));
                plugins.add(pluginMapper.countPluginsByDate(dateParam));
                skills.add(skillNodeMapper.countSkillPackagesByDate(dateParam));
            }
            
            trendData.put("dates", dates);
            trendData.put("sessions", sessions);
            trendData.put("plugins", plugins);
            trendData.put("skills", skills);
            
        } catch (Exception e) {
            logger.error("获取趋势数据失败", e);
            // 返回空数据
            trendData.put("dates", new ArrayList<>());
            trendData.put("sessions", new ArrayList<>());
            trendData.put("plugins", new ArrayList<>());
            trendData.put("skills", new ArrayList<>());
        }
        
        return success(trendData);
    }
    
    /**
     * 计算增长趋势百分比
     */
    private int calculateTrend(int oldValue, int newValue) {
        if (oldValue == 0) {
            return newValue > 0 ? 100 : 0;
        }
        return (int) (((double) (newValue - oldValue) / oldValue) * 100);
    }
}
