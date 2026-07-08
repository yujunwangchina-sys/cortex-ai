package com.cortex.knowledge.service.impl;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import com.cortex.common.exception.ServiceException;
import com.cortex.common.utils.DateUtils;
import com.cortex.knowledge.domain.AiKnowledgeDocument;
import com.cortex.knowledge.mapper.AiKnowledgeDocumentMapper;
import com.cortex.knowledge.rag.KnowledgeIngestService;
import com.cortex.knowledge.service.IAiKnowledgeDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档Service实现
 *
 * @author cortex
 */
@Service
public class AiKnowledgeDocumentServiceImpl implements IAiKnowledgeDocumentService
{
    private static final Logger log = LoggerFactory.getLogger(AiKnowledgeDocumentServiceImpl.class);

    @Autowired
    private AiKnowledgeDocumentMapper documentMapper;

    @Autowired
    private KnowledgeIngestService ingestService;

    @Value("${knowledge.upload-path:D:/cortex/uploadPath/knowledge}")
    private String uploadPath;

    @Override
    public AiKnowledgeDocument selectAiKnowledgeDocumentById(Long id)
    {
        return documentMapper.selectAiKnowledgeDocumentById(id);
    }

    @Override
    public List<AiKnowledgeDocument> selectAiKnowledgeDocumentList(AiKnowledgeDocument aiKnowledgeDocument)
    {
        return documentMapper.selectAiKnowledgeDocumentList(aiKnowledgeDocument);
    }

    @Override
    public AiKnowledgeDocument uploadDocument(Long kbId, MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("文件不能为空");
        }

        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".")).toLowerCase()
                : "";

        // 生成存储路径
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        String relativePath = "knowledge/" + storedName;

        File dest = new File(uploadPath, storedName);
        if (!dest.getParentFile().exists())
        {
            dest.getParentFile().mkdirs();
        }

        try
        {
            file.transferTo(dest);
        }
        catch (IOException e)
        {
            log.error("文件保存失败", e);
            throw new ServiceException("文件保存失败: " + e.getMessage());
        }

        // 创建文档记录
        AiKnowledgeDocument doc = new AiKnowledgeDocument();
        doc.setKbId(kbId);
        doc.setFileName(originalName);
        doc.setFilePath(relativePath);
        doc.setFileType(ext.replace(".", ""));
        doc.setFileSize(file.getSize());
        doc.setStatus("0");
        doc.setCreateTime(DateUtils.getNowDate());
        documentMapper.insertAiKnowledgeDocument(doc);

        log.info("文档已上传 [kbId={}, docId={}, file={}]", kbId, doc.getId(), originalName);

        // 异步入库
        final Long docId = doc.getId();
        new Thread(() -> {
            try
            {
                ingestService.ingestDocument(kbId, docId);
            }
            catch (Exception e)
            {
                log.error("异步入库失败 [docId={}]", docId, e);
            }
        }).start();

        return doc;
    }

    @Override
    public int updateAiKnowledgeDocument(AiKnowledgeDocument aiKnowledgeDocument)
    {
        aiKnowledgeDocument.setUpdateTime(DateUtils.getNowDate());
        return documentMapper.updateAiKnowledgeDocument(aiKnowledgeDocument);
    }

    @Override
    public int deleteAiKnowledgeDocumentByIds(Long[] ids)
    {
        for (Long id : ids)
        {
            AiKnowledgeDocument doc = documentMapper.selectAiKnowledgeDocumentById(id);
            if (doc != null)
            {
                ingestService.deleteDocument(doc.getKbId(), id);
            }
        }
        return 1;
    }

    @Override
    public void reprocessDocument(Long id)
    {
        AiKnowledgeDocument doc = documentMapper.selectAiKnowledgeDocumentById(id);
        if (doc == null)
        {
            throw new ServiceException("文档不存在");
        }
        ingestService.ingestDocument(doc.getKbId(), id);
    }
}