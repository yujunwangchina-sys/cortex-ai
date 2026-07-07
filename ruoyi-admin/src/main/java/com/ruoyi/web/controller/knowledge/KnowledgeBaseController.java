package com.ruoyi.web.controller.knowledge;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.AiKnowledgeBase;
import com.ruoyi.knowledge.service.IAiKnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库管理Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/knowledge/base")
public class KnowledgeBaseController extends BaseController
{
    @Autowired
    private IAiKnowledgeBaseService kbService;

    @PreAuthorize("@ss.hasPermi('knowledge:base:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiKnowledgeBase aiKnowledgeBase)
    {
        startPage();
        List<AiKnowledgeBase> list = kbService.selectAiKnowledgeBaseList(aiKnowledgeBase);
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(kbService.selectAiKnowledgeBaseById(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:base:add')")
    @Log(title = "知识库", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiKnowledgeBase aiKnowledgeBase)
    {
        return toAjax(kbService.insertAiKnowledgeBase(aiKnowledgeBase));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:base:edit')")
    @Log(title = "知识库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiKnowledgeBase aiKnowledgeBase)
    {
        return toAjax(kbService.updateAiKnowledgeBase(aiKnowledgeBase));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:base:remove')")
    @Log(title = "知识库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        kbService.deleteAiKnowledgeBaseByIds(ids);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('knowledge:base:edit')")
    @Log(title = "知识库重建索引", businessType = BusinessType.UPDATE)
    @PostMapping("/rebuild/{id}")
    public AjaxResult rebuildIndex(@PathVariable("id") Long id)
    {
        kbService.rebuildIndex(id);
        return success("索引重建完成");
    }

    /**
     * 获取所有可用知识库(供Agent授权选择)
     */
    @GetMapping("/available")
    public AjaxResult available()
    {
        AiKnowledgeBase query = new AiKnowledgeBase();
        query.setStatus("0");
        return success(kbService.selectAiKnowledgeBaseList(query));
    }
}