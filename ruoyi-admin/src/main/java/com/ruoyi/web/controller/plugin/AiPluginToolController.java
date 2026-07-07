package com.ruoyi.web.controller.plugin;

import java.util.List;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.plugin.domain.AiPluginTool;
import com.ruoyi.plugin.service.IAiPluginToolService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * AI插件工具Controller
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/plugin/tool")
public class AiPluginToolController extends BaseController
{
    @Autowired
    private IAiPluginToolService aiPluginToolService;

    /**
     * 查询AI插件工具列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:query')")
    @GetMapping("/list")
    public TableDataInfo list(AiPluginTool aiPluginTool)
    {
        startPage();
        List<AiPluginTool> list = aiPluginToolService.selectAiPluginToolList(aiPluginTool);
        return getDataTable(list);
    }

    /**
     * 根据插件ID查询工具列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:query')")
    @GetMapping("/listByPlugin/{pluginId}")
    public AjaxResult listByPlugin(@PathVariable Long pluginId)
    {
        List<AiPluginTool> list = aiPluginToolService.selectAiPluginToolListByPluginId(pluginId);
        return success(list);
    }

    /**
     * 根据插件名称查询工具列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:query')")
    @GetMapping("/listByPluginCode/{pluginName}")
    public AjaxResult listByPluginCode(@PathVariable String pluginName)
    {
        List<AiPluginTool> list = aiPluginToolService.selectAiPluginToolListByPluginName(pluginName);
        return success(list);
    }

    /**
     * 导出AI插件工具列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:export')")
    @Log(title = "AI插件工具", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiPluginTool aiPluginTool)
    {
        List<AiPluginTool> list = aiPluginToolService.selectAiPluginToolList(aiPluginTool);
        ExcelUtil<AiPluginTool> util = new ExcelUtil<AiPluginTool>(AiPluginTool.class);
        util.exportExcel(response, list, "AI插件工具数据");
    }

    /**
     * 获取AI插件工具详细信息
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:query')")
    @GetMapping(value = "/{toolId}")
    public AjaxResult getInfo(@PathVariable("toolId") Long toolId)
    {
        return success(aiPluginToolService.selectAiPluginToolByToolId(toolId));
    }

    /**
     * 新增AI插件工具
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:add')")
    @Log(title = "AI插件工具", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiPluginTool aiPluginTool)
    {
        return toAjax(aiPluginToolService.insertAiPluginTool(aiPluginTool));
    }

    /**
     * 修改AI插件工具
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:edit')")
    @Log(title = "AI插件工具", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiPluginTool aiPluginTool)
    {
        return toAjax(aiPluginToolService.updateAiPluginTool(aiPluginTool));
    }

    /**
     * 删除AI插件工具
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:remove')")
    @Log(title = "AI插件工具", businessType = BusinessType.DELETE)
	@DeleteMapping("/{toolIds}")
    public AjaxResult remove(@PathVariable Long[] toolIds)
    {
        return toAjax(aiPluginToolService.deleteAiPluginToolByToolIds(toolIds));
    }

    /**
     * 执行工具（核心接口）
     */
    @PreAuthorize("@ss.hasPermi('plugin:tool:execute')")
    @Log(title = "执行AI工具", businessType = BusinessType.OTHER)
    @PostMapping("/execute")
    public AjaxResult executeTool(@RequestBody ToolExecuteRequest request)
    {
        try {
            JSONObject result = aiPluginToolService.executeTool(
                request.getSessionId(),
                request.getPluginName(),
                request.getToolName(),
                request.getParams()
            );
            return success(result);
        } catch (Exception e) {
            logger.error("执行工具失败", e);
            return error("执行失败: " + e.getMessage());
        }
    }

    /**
     * 工具执行请求对象
     */
    public static class ToolExecuteRequest {
        private String sessionId;
        private String pluginName;
        private String toolName;
        private JSONObject params;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getPluginName() { return pluginName; }
        public void setPluginName(String pluginName) { this.pluginName = pluginName; }

        public String getToolName() { return toolName; }
        public void setToolName(String toolName) { this.toolName = toolName; }

        public JSONObject getParams() { return params; }
        public void setParams(JSONObject params) { this.params = params; }
    }
}
