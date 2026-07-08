package com.cortex.web.controller.agent;

import java.util.List;
import java.io.File;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cortex.common.annotation.Log;
import com.cortex.common.config.CortexConfig;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.common.enums.BusinessType;
import com.cortex.agent.domain.AiAgentFile;
import com.cortex.agent.service.IAiAgentFileService;
import com.cortex.common.utils.poi.ExcelUtil;
import com.cortex.common.core.page.TableDataInfo;

/**
 * Agent文件管理 Controller
 *
 * @author cortex
 */
@RestController
@RequestMapping("/agent/file")
public class AiAgentFileController extends BaseController
{
    @Autowired
    private IAiAgentFileService aiAgentFileService;

    /**
     * 查询Agent文件列表
     */
    @PreAuthorize("@ss.hasPermi('agent:file:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiAgentFile aiAgentFile)
    {
        startPage();
        List<AiAgentFile> list = aiAgentFileService.selectAiAgentFileList(aiAgentFile);
        return getDataTable(list);
    }

    /**
     * 导出Agent文件列表
     */
    @PreAuthorize("@ss.hasPermi('agent:file:export')")
    @Log(title = "Agent文件", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiAgentFile aiAgentFile)
    {
        List<AiAgentFile> list = aiAgentFileService.selectAiAgentFileList(aiAgentFile);
        ExcelUtil<AiAgentFile> util = new ExcelUtil<AiAgentFile>(AiAgentFile.class);
        util.exportExcel(response, list, "Agent文件数据");
    }

    /**
     * 获取Agent文件详细信息
     */
    @PreAuthorize("@ss.hasPermi('agent:file:query')")
    @GetMapping(value = "/{fileId}")
    public AjaxResult getInfo(@PathVariable("fileId") Long fileId)
    {
        return success(aiAgentFileService.selectAiAgentFileByFileId(fileId));
    }

    /**
     * 删除Agent文件（同时删除物理文件）
     */
    @PreAuthorize("@ss.hasPermi('agent:file:remove')")
    @Log(title = "Agent文件", businessType = BusinessType.DELETE)
    @DeleteMapping("/{fileIds}")
    public AjaxResult remove(@PathVariable Long[] fileIds)
    {
        String uploadPath = CortexConfig.getProfile();
        for (Long fileId : fileIds)
        {
            AiAgentFile file = aiAgentFileService.selectAiAgentFileByFileId(fileId);
            if (file != null && file.getFilePath() != null)
            {
                File physical;
                if (new File(file.getFilePath()).isAbsolute())
                {
                    physical = new File(file.getFilePath());
                }
                else
                {
                    physical = new File(uploadPath, file.getFilePath());
                }
                if (physical.exists())
                {
                    physical.delete();
                }
            }
        }
        return toAjax(aiAgentFileService.deleteAiAgentFileByFileIds(fileIds));
    }
}