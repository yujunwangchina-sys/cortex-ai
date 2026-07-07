package com.ruoyi.web.controller.knowledge;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.AiKnowledgeTestCase;
import com.ruoyi.knowledge.domain.AiKnowledgeTestResult;
import com.ruoyi.knowledge.service.IAiKnowledgeTestCaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 召回测试Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/knowledge/test")
public class KnowledgeTestCaseController extends BaseController
{
    @Autowired
    private IAiKnowledgeTestCaseService testCaseService;

    @PreAuthorize("@ss.hasPermi('knowledge:recall:test')")
    @GetMapping("/case/list")
    public TableDataInfo list(AiKnowledgeTestCase aiKnowledgeTestCase)
    {
        startPage();
        List<AiKnowledgeTestCase> list = testCaseService.selectAiKnowledgeTestCaseList(aiKnowledgeTestCase);
        return getDataTable(list);
    }

    @GetMapping("/case/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(testCaseService.selectAiKnowledgeTestCaseById(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:recall:add')")
    @Log(title = "召回测试用例", businessType = BusinessType.INSERT)
    @PostMapping("/case")
    public AjaxResult add(@RequestBody AiKnowledgeTestCase aiKnowledgeTestCase)
    {
        return toAjax(testCaseService.insertAiKnowledgeTestCase(aiKnowledgeTestCase));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:recall:add')")
    @Log(title = "召回测试用例", businessType = BusinessType.UPDATE)
    @PutMapping("/case")
    public AjaxResult edit(@RequestBody AiKnowledgeTestCase aiKnowledgeTestCase)
    {
        return toAjax(testCaseService.updateAiKnowledgeTestCase(aiKnowledgeTestCase));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:recall:remove')")
    @Log(title = "召回测试用例", businessType = BusinessType.DELETE)
    @DeleteMapping("/case/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(testCaseService.deleteAiKnowledgeTestCaseByIds(ids));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:recall:run')")
    @PostMapping("/run/{caseId}")
    public AjaxResult runTest(@PathVariable("caseId") Long caseId)
    {
        return success(testCaseService.runTest(caseId));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:recall:run')")
    @PostMapping("/run-all/{kbId}")
    public AjaxResult runAllTests(@PathVariable("kbId") Long kbId)
    {
        List<AiKnowledgeTestResult> results = testCaseService.runAllTests(kbId);
        return success(results);
    }

    @GetMapping("/result/list/{kbId}")
    public AjaxResult resultList(@PathVariable("kbId") Long kbId)
    {
        return success(testCaseService.getTestHistory(kbId));
    }
}