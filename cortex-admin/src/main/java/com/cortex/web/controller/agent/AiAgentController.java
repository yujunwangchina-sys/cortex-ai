package com.cortex.web.controller.agent;

import java.util.List;
import java.io.File;

import com.cortex.common.config.CortexConfig;
import com.cortex.common.utils.DateUtils;
import com.cortex.common.utils.file.FileUploadUtils;
import com.cortex.common.utils.uuid.IdUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.cortex.common.annotation.Log;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.common.enums.BusinessType;
import com.cortex.agent.domain.AiAgent;
import com.cortex.agent.service.IAiAgentService;
import com.cortex.common.utils.poi.ExcelUtil;
import com.cortex.common.core.page.TableDataInfo;

/**
 * AI AgentController
 * 
 * @author cortex
 */
@RestController
@RequestMapping("/agent/agent")
public class AiAgentController extends BaseController
{
    @Autowired
    private IAiAgentService aiAgentService;

    /**
     * 查询AI Agent列表
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiAgent aiAgent)
    {
        startPage();
        List<AiAgent> list = aiAgentService.selectAiAgentList(aiAgent);
        return getDataTable(list);
    }

    /**
     * 导出AI Agent列表
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:export')")
    @Log(title = "AI Agent", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiAgent aiAgent)
    {
        List<AiAgent> list = aiAgentService.selectAiAgentList(aiAgent);
        ExcelUtil<AiAgent> util = new ExcelUtil<AiAgent>(AiAgent.class);
        util.exportExcel(response, list, "AI Agent数据");
    }

    /**
     * 获取AI Agent详细信息
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        AjaxResult ajax = AjaxResult.success();
        ajax.put("data", aiAgentService.selectAiAgentById(id));
        ajax.put("skillIds", aiAgentService.getAgentSkillIds(id));
        ajax.put("pluginIds", aiAgentService.getAgentPluginIds(id));
        return ajax;
    }

    /**
     * 根据agentCode获取AI Agent详细信息
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:query')")
    @GetMapping(value = "/code/{agentCode}")
    public AjaxResult getInfoByCode(@PathVariable("agentCode") String agentCode)
    {
        AjaxResult ajax = AjaxResult.success();
        AiAgent agent = aiAgentService.selectAiAgentByCode(agentCode);
        if (agent == null) {
            return error("Agent不存在");
        }
        ajax.put("data", agent);
        ajax.put("skillIds", aiAgentService.getAgentSkillIds(agent.getId()));
        ajax.put("pluginIds", aiAgentService.getAgentPluginIds(agent.getId()));
        return ajax;
    }

    /**
     * 新增AI Agent
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:add')")
    @Log(title = "AI Agent", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiAgent aiAgent)
    {
        // 检查编码唯一性
        if (!aiAgentService.checkAgentCodeUnique(aiAgent.getAgentCode()))
        {
            return error("新增Agent失败，Agent编码已存在");
        }
        return toAjax(aiAgentService.insertAiAgent(aiAgent));
    }

    /**
     * 修改AI Agent
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:edit')")
    @Log(title = "AI Agent", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiAgent aiAgent)
    {
        return toAjax(aiAgentService.updateAiAgent(aiAgent));
    }

    /**
     * 删除AI Agent
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:remove')")
    @Log(title = "AI Agent", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(aiAgentService.deleteAiAgentByIds(ids));
    }

    /**
     * 保存Agent的Skill权限
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:edit')")
    @Log(title = "Agent权限", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/skills")
    public AjaxResult saveSkills(@PathVariable("id") Long id, @RequestBody List<Long> skillIds)
    {
        return toAjax(aiAgentService.saveAgentSkills(id, skillIds));
    }

    /**
     * 保存Agent的插件权限
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:edit')")
    @Log(title = "Agent权限", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/plugins")
    public AjaxResult savePlugins(@PathVariable("id") Long id, @RequestBody List<Long> pluginIds)
    {
        return toAjax(aiAgentService.saveAgentPlugins(id, pluginIds));
    }

    /**
     * 保存Agent的委派授权
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:edit')")
    @Log(title = "Agent委派授权", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/delegations")
    public AjaxResult saveDelegations(@PathVariable("id") Long id, @RequestBody List<Long> delegateAgentIds)
    {
        return toAjax(aiAgentService.saveAgentDelegations(id, delegateAgentIds));
    }

    /**
     * 查询Agent的委派授权
     */
    @PreAuthorize("@ss.hasPermi('agent:agent:query')")
    @GetMapping("/{id}/delegations")
    public AjaxResult getDelegations(@PathVariable("id") Long id)
    {
        return AjaxResult.success(aiAgentService.selectDelegateAgentIds(id));
    }

    /**
     * Agent头像上传
     */
    @Log(title = "Agent头像", businessType = BusinessType.UPDATE)
    @PostMapping("/avatar")
    public AjaxResult uploadAvatar(
            @RequestParam("avatarfile") MultipartFile file,
            @RequestParam("agentId") Long agentId)
    {
        try
        {
            if (file.isEmpty())
            {
                return error("上传文件不能为空");
            }

            AiAgent agent = aiAgentService.selectAiAgentById(agentId);
            if (agent == null)
            {
                return error("Agent不存在");
            }

            // 验证文件类型
            String fileName = file.getOriginalFilename();
            String extension = fileName.substring(fileName.lastIndexOf("."));
            if (!".png".equalsIgnoreCase(extension) && !".jpg".equalsIgnoreCase(extension) 
                && !".jpeg".equalsIgnoreCase(extension) && !".gif".equalsIgnoreCase(extension))
            {
                return error("只允许上传png、jpg、jpeg、gif格式的图片");
            }

            // 文件大小限制 5MB
            if (file.getSize() > 5 * 1024 * 1024)
            {
                return error("文件大小不能超过5MB");
            }

            // 生成文件名
            String storedFilename = IdUtils.fastSimpleUUID() + extension;
            
            // 按日期存储: /profile/avatar/agent/2024/01/15/xxx.png
            String datePath = DateUtils.datePath();
            String relativePath = "avatar/agent/" + datePath + "/" + storedFilename;
            
            // 完整的物理路径
            String uploadPath = CortexConfig.getProfile();
            String absolutePath = uploadPath + "/" + relativePath;
            
            File dest = new File(absolutePath);
            if (!dest.getParentFile().exists())
            {
                dest.getParentFile().mkdirs();
            }
            file.transferTo(dest);

            // 更新Agent头像路径 - 返回 /profile/avatar/... 格式
            String avatarUrl = "/profile/" + relativePath;
            agent.setAvatar(avatarUrl);
            aiAgentService.updateAiAgent(agent);

            AjaxResult ajax = AjaxResult.success();
            ajax.put("imgUrl", avatarUrl);
            return ajax;
        }
        catch (Exception e)
        {
            logger.error("上传Agent头像失败", e);
            return error("上传失败: " + e.getMessage());
        }
    }
}
