package com.cortex.web.controller.plugin;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cortex.common.annotation.Log;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.common.enums.BusinessType;
import com.cortex.plugin.domain.AiPluginExecutionLog;
import com.cortex.plugin.service.IAiPluginExecutionLogService;
import com.cortex.common.utils.poi.ExcelUtil;
import com.cortex.common.core.page.TableDataInfo;

/**
 * AI插件执行日志Controller
 * 
 * @author cortex
 */
@RestController
@RequestMapping("/plugin/log")
public class AiPluginExecutionLogController extends BaseController
{
    @Autowired
    private IAiPluginExecutionLogService aiPluginExecutionLogService;

    /**
     * 查询AI插件执行日志列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:log:query')")
    @GetMapping("/list")
    public TableDataInfo list(AiPluginExecutionLog aiPluginExecutionLog)
    {
        startPage();
        List<AiPluginExecutionLog> list = aiPluginExecutionLogService.selectAiPluginExecutionLogList(aiPluginExecutionLog);
        return getDataTable(list);
    }

    /**
     * 根据会话ID查询日志列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:log:query')")
    @GetMapping("/listBySession/{sessionId}")
    public AjaxResult listBySession(@PathVariable String sessionId)
    {
        List<AiPluginExecutionLog> list = aiPluginExecutionLogService.selectLogListBySessionId(sessionId);
        return success(list);
    }

    /**
     * 导出AI插件执行日志列表
     */
    @PreAuthorize("@ss.hasPermi('plugin:log:export')")
    @Log(title = "AI插件执行日志", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiPluginExecutionLog aiPluginExecutionLog)
    {
        List<AiPluginExecutionLog> list = aiPluginExecutionLogService.selectAiPluginExecutionLogList(aiPluginExecutionLog);
        ExcelUtil<AiPluginExecutionLog> util = new ExcelUtil<AiPluginExecutionLog>(AiPluginExecutionLog.class);
        util.exportExcel(response, list, "AI插件执行日志");
    }

    /**
     * 获取AI插件执行日志详细信息
     */
    @PreAuthorize("@ss.hasPermi('plugin:log:query')")
    @GetMapping(value = "/{logId}")
    public AjaxResult getInfo(@PathVariable("logId") Long logId)
    {
        return success(aiPluginExecutionLogService.selectAiPluginExecutionLogByLogId(logId));
    }

    /**
     * 删除AI插件执行日志
     */
    @PreAuthorize("@ss.hasPermi('plugin:log:remove')")
    @Log(title = "AI插件执行日志", businessType = BusinessType.DELETE)
	@DeleteMapping("/{logIds}")
    public AjaxResult remove(@PathVariable Long[] logIds)
    {
        return toAjax(aiPluginExecutionLogService.deleteAiPluginExecutionLogByLogIds(logIds));
    }

    /**
     * 清理指定天数之前的日志
     */
    @PreAuthorize("@ss.hasPermi('plugin:log:remove')")
    @Log(title = "清理旧日志", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean/{days}")
    public AjaxResult clean(@PathVariable int days)
    {
        return toAjax(aiPluginExecutionLogService.cleanOldLogs(days));
    }
}
