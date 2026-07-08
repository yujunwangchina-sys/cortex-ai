package com.cortex.web.controller.supplier;

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
import com.cortex.common.annotation.Log;
import com.cortex.common.core.controller.BaseController;
import com.cortex.common.core.domain.AjaxResult;
import com.cortex.common.core.page.TableDataInfo;
import com.cortex.common.enums.BusinessType;
import com.cortex.common.utils.poi.ExcelUtil;
import com.cortex.supplier.domain.AiSupplier;
import com.cortex.supplier.service.IAiSupplierService;

/**
 * AI供应商Controller
 * 
 * @author cortex
 */
@RestController
@RequestMapping("/supplier")
public class AiSupplierController extends BaseController
{
    @Autowired
    private IAiSupplierService aiSupplierService;

    /**
     * 查询AI供应商列表
     */
    @PreAuthorize("@ss.hasPermi('ai:supplier:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiSupplier aiSupplier)
    {
        startPage();
        List<AiSupplier> list = aiSupplierService.selectAiSupplierList(aiSupplier);
        return getDataTable(list);
    }

    /**
     * 导出AI供应商列表
     */
    @PreAuthorize("@ss.hasPermi('ai:supplier:export')")
    @Log(title = "AI供应商", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiSupplier aiSupplier)
    {
        List<AiSupplier> list = aiSupplierService.selectAiSupplierList(aiSupplier);
        ExcelUtil<AiSupplier> util = new ExcelUtil<AiSupplier>(AiSupplier.class);
        util.exportExcel(response, list, "AI供应商数据");
    }

    /**
     * 获取AI供应商详细信息
     */
    @PreAuthorize("@ss.hasPermi('ai:supplier:query')")
    @GetMapping(value = "/{supplierId}")
    public AjaxResult getInfo(@PathVariable("supplierId") Long supplierId)
    {
        return success(aiSupplierService.selectAiSupplierBySupplierId(supplierId));
    }

    /**
     * 新增AI供应商
     */
    @PreAuthorize("@ss.hasPermi('ai:supplier:add')")
    @Log(title = "AI供应商", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody AiSupplier aiSupplier)
    {
        if (!aiSupplierService.checkSupplierCodeUnique(aiSupplier))
        {
            return error("新增供应商'" + aiSupplier.getSupplierName() + "'失败，供应商编码已存在");
        }
        aiSupplier.setCreateBy(getUsername());
        return toAjax(aiSupplierService.insertAiSupplier(aiSupplier));
    }

    /**
     * 修改AI供应商
     */
    @PreAuthorize("@ss.hasPermi('ai:supplier:edit')")
    @Log(title = "AI供应商", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody AiSupplier aiSupplier)
    {
        if (!aiSupplierService.checkSupplierCodeUnique(aiSupplier))
        {
            return error("修改供应商'" + aiSupplier.getSupplierName() + "'失败，供应商编码已存在");
        }
        aiSupplier.setUpdateBy(getUsername());
        return toAjax(aiSupplierService.updateAiSupplier(aiSupplier));
    }

    /**
     * 删除AI供应商
     */
    @PreAuthorize("@ss.hasPermi('ai:supplier:remove')")
    @Log(title = "AI供应商", businessType = BusinessType.DELETE)
	@DeleteMapping("/{supplierIds}")
    public AjaxResult remove(@PathVariable Long[] supplierIds)
    {
        return toAjax(aiSupplierService.deleteAiSupplierBySupplierIds(supplierIds));
    }

    /**
     * 测试供应商连接
     */
    @PreAuthorize("@ss.hasPermi('ai:supplier:test')")
    @Log(title = "AI供应商", businessType = BusinessType.OTHER)
    @PostMapping("/test")
    public AjaxResult testConnection(@RequestBody AiSupplier aiSupplier)
    {
        String result = aiSupplierService.testConnection(aiSupplier);
        if (result.contains("连接成功"))
        {
            return success(result);
        }
        else
        {
            return error(result);
        }
    }
}
