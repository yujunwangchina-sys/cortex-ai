package com.ruoyi.web.controller.supplier;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.supplier.domain.AiModel;
import com.ruoyi.supplier.service.IAiModelService;

/**
 * AI模型Controller
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/supplier/model")
public class AiModelController extends BaseController
{
    @Autowired
    private IAiModelService aiModelService;

    /**
     * 查询AI模型列表
     */
    @PreAuthorize("@ss.hasPermi('supplier:model:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiModel aiModel)
    {
        startPage();
        List<AiModel> list = aiModelService.selectAiModelList(aiModel);
        return getDataTable(list);
    }

    /**
     * 根据供应商ID查询模型列表
     */
    @PreAuthorize("@ss.hasPermi('supplier:model:query')")
    @GetMapping("/listBySupplier/{supplierId}")
    public AjaxResult listBySupplier(@PathVariable("supplierId") Long supplierId)
    {
        List<AiModel> list = aiModelService.selectAiModelListBySupplierId(supplierId);
        return success(list);
    }

    /**
     * 导出AI模型列表
     */
    @PreAuthorize("@ss.hasPermi('supplier:model:export')")
    @Log(title = "AI模型", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiModel aiModel)
    {
        List<AiModel> list = aiModelService.selectAiModelList(aiModel);
        ExcelUtil<AiModel> util = new ExcelUtil<AiModel>(AiModel.class);
        util.exportExcel(response, list, "AI模型数据");
    }

    /**
     * 获取AI模型详细信息
     */
    @PreAuthorize("@ss.hasPermi('supplier:model:query')")
    @GetMapping(value = "/{modelId}")
    public AjaxResult getInfo(@PathVariable("modelId") Long modelId)
    {
        return success(aiModelService.selectAiModelByModelId(modelId));
    }

    /**
     * 新增AI模型
     */
    @PreAuthorize("@ss.hasPermi('supplier:model:add')")
    @Log(title = "AI模型", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AiModel aiModel)
    {
        aiModel.setCreateBy(getUsername());
        return toAjax(aiModelService.insertAiModel(aiModel));
    }

    /**
     * 修改AI模型
     */
    @PreAuthorize("@ss.hasPermi('supplier:model:edit')")
    @Log(title = "AI模型", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AiModel aiModel)
    {
        aiModel.setUpdateBy(getUsername());
        return toAjax(aiModelService.updateAiModel(aiModel));
    }

    /**
     * 删除AI模型
     */
    @PreAuthorize("@ss.hasPermi('supplier:model:remove')")
    @Log(title = "AI模型", businessType = BusinessType.DELETE)
	@DeleteMapping("/{modelIds}")
    public AjaxResult remove(@PathVariable Long[] modelIds)
    {
        return toAjax(aiModelService.deleteAiModelByModelIds(modelIds));
    }
}
