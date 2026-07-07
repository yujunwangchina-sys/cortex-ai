package com.ruoyi.knowledge.rag;

import com.ruoyi.agent.runtime.file.FileContentParser;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.agent.runtime.file.ImageVisionFallback;
import com.ruoyi.knowledge.domain.AiKnowledgeBase;
import com.ruoyi.knowledge.domain.AiKnowledgeChunk;
import com.ruoyi.knowledge.domain.AiKnowledgeDocument;
import com.ruoyi.knowledge.mapper.AiKnowledgeBaseMapper;
import com.ruoyi.knowledge.mapper.AiKnowledgeChunkMapper;
import com.ruoyi.knowledge.mapper.AiKnowledgeDocumentMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import java.util.Collections;
/**
 * 知识库文档入库流水线
 * 解析 -> 分块 -> 向量化 -> Milvus存储 + MySQL元数据
 *
 * @author ruoyi
 */
@Service
public class KnowledgeIngestService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestService.class);

    @Autowired
    private AiKnowledgeBaseMapper kbMapper;

    @Autowired
    private AiKnowledgeDocumentMapper documentMapper;

    @Autowired
    private AiKnowledgeChunkMapper chunkMapper;

    @Autowired
    private EmbeddingModelFactory embeddingModelFactory;

    @Autowired
    private MilvusStoreManager storeManager;

    @Autowired
    private FileContentParser fileParser;


    @Autowired
    private ImageVisionFallback imageVisionFallback;
    @Value("${knowledge.upload-path:D:/ruoyi/uploadPath/knowledge}")
    private String uploadPath;

    /**
     * 入库单个文档
     */
    public void ingestDocument(Long kbId, Long documentId)
    {
        AiKnowledgeDocument doc = documentMapper.selectAiKnowledgeDocumentById(documentId);
        if (doc == null)
        {
            log.warn("文档不存在: {}", documentId);
            return;
        }

        AiKnowledgeBase kb = kbMapper.selectAiKnowledgeBaseById(kbId);
        if (kb == null)
        {
            log.warn("知识库不存在: {}", kbId);
            return;
        }

        log.info("开始入库 [kb={}, doc={}, file={}]", kb.getKbName(), documentId, doc.getFileName());

        // 标记处理中
        documentMapper.updateStatus(documentId, "1", null);

        try
        {
            // 1. 如果已有分块，先清除旧数据
            chunkMapper.deleteByDocumentId(documentId);

            // 2. 解析文档（如果启用图片提取，按图片实际位置插入标记）
            String filePath = doc.getFilePath() != null ? doc.getFilePath() : doc.getFileName();
            boolean extractImages = "1".equals(kb.getExtractImages());
            boolean descEnabled = "1".equals(kb.getImageDescEnabled());

            String text;
            List<String> allImagePaths;

            if (extractImages)
            {
                FileContentParser.ParsedFileWithImages parsed = fileParser.parseWithImages(filePath, doc.getFileName(), documentId);
                text = parsed.getContent();
                allImagePaths = parsed.getImagePaths();
                log.info("文档解析+图片提取 [doc={}, images={}]", doc.getFileName(), allImagePaths.size());
            }
            else
            {
                FileContentParser.ParsedFile parsed = fileParser.parse(filePath, doc.getFileName(), Integer.MAX_VALUE);
                if (parsed.isImage())
                {
                    throw new RuntimeException("图片文件不支持入库，请上传文本文档");
                }
                text = parsed.getContent();
                allImagePaths = new ArrayList<>();
            }

            if (text == null || text.trim().isEmpty())
            {
                throw new RuntimeException("文档内容为空或无法解析");
            }

            // 3. 分块
            int chunkSize = kb.getChunkSize() != null ? kb.getChunkSize() : 500;
            int chunkOverlap = kb.getChunkOverlap() != null ? kb.getChunkOverlap() : 50;

            Document document = Document.from(text);
            DocumentSplitter splitter;
            String separator = kb.getChunkSeparator();
            if (separator != null && !separator.trim().isEmpty())
            {
                separator = separator.replace("\\n", "\n").replace("\\t", "\t");
                splitter = new DocumentByRegexSplitter(
                        separator, separator, chunkSize, chunkOverlap,
                        DocumentSplitters.recursive(chunkSize, chunkOverlap));
            }
            else
            {
                splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
            }
            List<TextSegment> segments = splitter.split(document);

            log.info("文档分块完成 [doc={}, segments={}]", doc.getFileName(), segments.size());

            // 4. 逐块向量化 + 存储
            EmbeddingModel model = embeddingModelFactory.getModel(kb.getEmbeddingModelId());
            EmbeddingStore<TextSegment> store = storeManager.getStore(kbId);

            List<AiKnowledgeChunk> chunkRecords = new ArrayList<>();
            int totalTokens = 0;
            int totalSegments = segments.size();

            java.util.regex.Pattern imgMarkerPattern = java.util.regex.Pattern.compile("\\[img(\\d+)\\]");

            for (int i = 0; i < totalSegments; i++)
            {
                TextSegment segment = segments.get(i);
                String chunkText = segment.text();

                // 找出当前分块中实际存在的 [imgNNN] 标记，提取对应图片路径
                List<String> chunkImages = new ArrayList<>();
                List<int[]> markerPositions = new ArrayList<>();
                java.util.regex.Matcher matcher = imgMarkerPattern.matcher(chunkText);
                while (matcher.find())
                {
                    int imgNum = Integer.parseInt(matcher.group(1));
                    int idx = imgNum - 1;
                    if (idx >= 0 && idx < allImagePaths.size())
                    {
                        chunkImages.add(allImagePaths.get(idx));
                        markerPositions.add(new int[]{matcher.start(), matcher.end()});
                    }
                }

                // 添加元数据
                Metadata metadata = segment.metadata();
                metadata.put("document_id", String.valueOf(documentId));
                metadata.put("kb_id", String.valueOf(kbId));
                metadata.put("chunk_index", String.valueOf(i));
                if (doc.getDocCategory() != null) metadata.put("doc_category", doc.getDocCategory());
                if (doc.getDocTags() != null) metadata.put("doc_tags", doc.getDocTags());
                if (doc.getDocSource() != null) metadata.put("doc_source", doc.getDocSource());
                if (doc.getDocAuthor() != null) metadata.put("doc_author", doc.getDocAuthor());

                // 构建分块内容：重编号标记为分块内连续序号 + 追加图片描述
                String imagePathJson = null;
                List<String> descriptions = new ArrayList<>();
                StringBuilder chunkContent;

                if (!chunkImages.isEmpty())
                {
                    // 重编号：[img005] -> [img001], [img012] -> [img002] ...
                    chunkContent = new StringBuilder();
                    int lastEnd = 0;
                    for (int j = 0; j < markerPositions.size(); j++)
                    {
                        chunkContent.append(chunkText, lastEnd, markerPositions.get(j)[0]);
                        chunkContent.append("[img").append(String.format("%03d", j + 1)).append("]");
                        lastEnd = markerPositions.get(j)[1];
                    }
                    chunkContent.append(chunkText, lastEnd, chunkText.length());

                    imagePathJson = JSON.toJSONString(chunkImages);
                    metadata.put("image_path", imagePathJson);

                    // 图片描述追加末尾（提升检索命中率）
                    if (descEnabled)
                    {
                        for (int j = 0; j < chunkImages.size(); j++)
                        {
                            try
                            {
                                String imgPath = chunkImages.get(j);
                                String imgFileName = imgPath.substring(imgPath.lastIndexOf("/") + 1);
                                String desc = imageVisionFallback.describeImage(imgPath, imgFileName);
                                if (desc != null && !desc.startsWith("ERROR"))
                                {
                                    chunkContent.append("\n[img").append(String.format("%03d", j + 1))
                                            .append(":desc]").append(desc);
                                    descriptions.add(desc);
                                }
                            }
                            catch (Exception e)
                            {
                                log.warn("图片描述失败 [img={}]", chunkImages.get(j), e);
                            }
                        }
                    }
                }
                else
                {
                    chunkContent = new StringBuilder(chunkText);
                }

                TextSegment segmentWithMeta = TextSegment.from(chunkContent.toString(), metadata);
                Embedding embedding = model.embed(segmentWithMeta).content();
                String milvusId = store.add(embedding, segmentWithMeta);

                int tokenCount = estimateTokens(chunkContent.toString());
                totalTokens += tokenCount;

                AiKnowledgeChunk chunk = new AiKnowledgeChunk();
                chunk.setKbId(kbId);
                chunk.setDocumentId(documentId);
                chunk.setChunkIndex(i);
                chunk.setContent(chunkContent.toString());
                chunk.setTokenCount(tokenCount);
                chunk.setMilvusId(milvusId);
                chunk.setImagePath(imagePathJson);
                chunk.setImageDescription(descriptions.isEmpty() ? null : JSON.toJSONString(descriptions));
                chunkRecords.add(chunk);

                if (!chunkImages.isEmpty())
                {
                    log.info("分块关联图片 [chunk={}, images={}]", i, chunkImages.size());
                }
            }
            // 4.5 纯图片文档（无文字分块）→ 创建独立图片分块
            if (totalSegments == 0 && allImagePaths.size() > 0)
            {
                log.info("无文字内容，创建独立图片分块 [images={}]", allImagePaths.size());
                for (int j = 0; j < allImagePaths.size(); j++)
                {
                    String imgPath = allImagePaths.get(j);
                    try
                    {
                        String description = null;
                        if (descEnabled)
                        {
                            String imgFileName = imgPath.substring(imgPath.lastIndexOf("/") + 1);
                            description = imageVisionFallback.describeImage(imgPath, imgFileName);
                            if (description != null && description.startsWith("ERROR"))
                            {
                                description = null;
                            }
                        }
                        String content = description != null ? "[img001:desc]" + description : "[img001]";
                        Metadata imgMeta = new Metadata();
                        imgMeta.put("document_id", String.valueOf(documentId));
                        imgMeta.put("kb_id", String.valueOf(kbId));
                        imgMeta.put("chunk_index", String.valueOf(j));
                        imgMeta.put("image_path", JSON.toJSONString(Collections.singletonList(imgPath)));
                        TextSegment imgSegment = TextSegment.from(content, imgMeta);
                        Embedding imgEmbedding = model.embed(imgSegment).content();
                        String imgMilvusId = store.add(imgEmbedding, imgSegment);
                        AiKnowledgeChunk imgChunk = new AiKnowledgeChunk();
                        imgChunk.setKbId(kbId);
                        imgChunk.setDocumentId(documentId);
                        imgChunk.setChunkIndex(j);
                        imgChunk.setContent(content);
                        imgChunk.setTokenCount(estimateTokens(content));
                        imgChunk.setMilvusId(imgMilvusId);
                        imgChunk.setImagePath(JSON.toJSONString(Collections.singletonList(imgPath)));
                        imgChunk.setImageDescription(description);
                        chunkRecords.add(imgChunk);
                        totalTokens += imgChunk.getTokenCount();
                    }
                    catch (Exception e)
                    {
                        log.warn("图片处理失败 [img={}]", imgPath, e);
                    }
                }
            }
            // 5. 批量写入MySQL
            if (!chunkRecords.isEmpty())
            {
                chunkMapper.batchInsertChunks(chunkRecords);
            }

            // 6. 更新文档状态
            doc.setChunkCount(segments.size());
            doc.setStatus("2");
            doc.setErrorMessage(null);
            documentMapper.updateAiKnowledgeDocument(doc);

            // 7. 更新知识库统计
            kbMapper.updateDocumentCount(kbId);
            kbMapper.updateChunkCount(kbId);

            log.info("入库完成 [doc={}, chunks={}, tokens={}]", doc.getFileName(), segments.size(), totalTokens);

        }
        catch (Exception e)
        {
            log.error("入库失败 [doc={}]", doc.getFileName(), e);
            documentMapper.updateStatus(documentId, "3", e.getMessage());
        }
    }

    /**
     * 重建知识库索引（重新入库所有文档）
     */
    public void rebuildIndex(Long kbId)
    {
        log.info("重建索引 [kbId={}]", kbId);

        // 清除所有旧分块
        chunkMapper.deleteByKbId(kbId);

        // 删除并重建Milvus collection
        storeManager.dropCollection(kbId);

        // 重新入库所有已索引/失败的文档
        AiKnowledgeDocument query = new AiKnowledgeDocument();
        query.setKbId(kbId);
        List<AiKnowledgeDocument> docs = documentMapper.selectAiKnowledgeDocumentList(query);

        for (AiKnowledgeDocument doc : docs)
        {
            ingestDocument(kbId, doc.getId());
        }

        log.info("重建索引完成 [kbId={}, docs={}]", kbId, docs.size());
    }

    /**
     * 删除文档（Milvus + MySQL）
     */
    public void deleteDocument(Long kbId, Long documentId)
    {
        // 获取该文档的所有分块（需要milvus_id）
        List<AiKnowledgeChunk> chunks = chunkMapper.selectChunksByDocumentId(documentId);

        // 从Milvus删除
        if (!chunks.isEmpty())
        {
            List<String> milvusIds = new ArrayList<>();
            for (AiKnowledgeChunk c : chunks)
            {
                if (c.getMilvusId() != null)
                {
                    milvusIds.add(c.getMilvusId());
                }
            }
            storeManager.deleteByIds(kbId, milvusIds);
        }

        // 从MySQL删除
        chunkMapper.deleteByDocumentId(documentId);
        documentMapper.deleteAiKnowledgeDocumentById(documentId);

        // 更新统计
        kbMapper.updateDocumentCount(kbId);
        kbMapper.updateChunkCount(kbId);

        log.info("文档已删除 [kbId={}, docId={}]", kbId, documentId);
    }

    /**
     * 估算token数（粗略: 中文约2字/token, 英文约4字/token, 取中间值）
     */
    private int estimateTokens(String text)
    {
        if (text == null) return 0;
        return (int) Math.ceil(text.length() / 2.5);
    }
}