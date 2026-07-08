package com.cortex.web.controller.knowledge;

import java.util.List;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.knowledge.rag.SearchResult;
import com.cortex.knowledge.rag.KnowledgeSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库检索Controller
 *
 * @author cortex
 */
@RestController
@RequestMapping("/knowledge/search")
public class KnowledgeSearchController extends BaseController
{
    @Autowired
    private KnowledgeSearchService searchService;

    /**
     * 检索测试
     */
    @PostMapping("/test")
    public AjaxResult test(@RequestBody SearchRequest req)
    {
        List<SearchResult> results = searchService.search(
                req.getKbId(), req.getQuery(), req.getTopK(), req.getMinScore(), req.getMetadataFilter());
        return success(results);
    }

    public static class SearchRequest
    {
        private Long kbId;
        private String query;
        private Integer topK;
        private Double minScore;
        private String metadataFilter;

        public Long getKbId() { return kbId; }
        public void setKbId(Long kbId) { this.kbId = kbId; }
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public Integer getTopK() { return topK; }
        public void setTopK(Integer topK) { this.topK = topK; }
        public Double getMinScore() { return minScore; }
        public void setMinScore(Double minScore) { this.minScore = minScore; }
        public String getMetadataFilter() { return metadataFilter; }
        public void setMetadataFilter(String metadataFilter) { this.metadataFilter = metadataFilter; }
    }
}