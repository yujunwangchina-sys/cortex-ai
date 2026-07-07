package com.ruoyi.web.controller.knowledge;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.knowledge.domain.AiKnowledgeChunk;
import com.ruoyi.knowledge.domain.AiKnowledgeDocument;
import com.ruoyi.knowledge.mapper.AiKnowledgeChunkMapper;
import com.ruoyi.knowledge.service.IAiKnowledgeDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/knowledge/document")
public class KnowledgeDocumentController extends BaseController
{
    @Autowired
    private IAiKnowledgeDocumentService documentService;

    @Autowired
    private AiKnowledgeChunkMapper chunkMapper;

    @PreAuthorize("@ss.hasPermi('knowledge:document:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiKnowledgeDocument aiKnowledgeDocument)
    {
        startPage();
        List<AiKnowledgeDocument> list = documentService.selectAiKnowledgeDocumentList(aiKnowledgeDocument);
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(documentService.selectAiKnowledgeDocumentById(id));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:upload')")
    @Log(title = "知识库文档", businessType = BusinessType.IMPORT)
    @PostMapping("/upload/{kbId}")
    public AjaxResult upload(@PathVariable("kbId") Long kbId, @RequestParam("file") MultipartFile file)
    {
        return success(documentService.uploadDocument(kbId, file));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:metadata')")
    @Log(title = "知识库文档元数据", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiKnowledgeDocument aiKnowledgeDocument)
    {
        return toAjax(documentService.updateAiKnowledgeDocument(aiKnowledgeDocument));
    }

    @PreAuthorize("@ss.hasPermi('knowledge:document:remove')")
    @Log(title = "知识库文档", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        documentService.deleteAiKnowledgeDocumentByIds(ids);
        return success();
    }

    @PostMapping("/reprocess/{id}")
    public AjaxResult reprocess(@PathVariable("id") Long id)
    {
        documentService.reprocessDocument(id);
        return success("重新处理完成");
    }

    /**
     * 查看文档分块
     */
    @GetMapping("/chunks/{documentId}")
    public AjaxResult chunks(@PathVariable("documentId") Long documentId)
    {
        List<AiKnowledgeChunk> chunks = chunkMapper.selectChunksByDocumentId(documentId);
        return success(chunks);
    }
}