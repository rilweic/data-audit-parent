package com.github.thestyleofme.data.comparison.app.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.github.thestyleofme.comparison.common.domain.entity.ComparisonJob;
import com.github.thestyleofme.data.comparison.api.dto.ComparisonJobDTO;

/**
 * <p>
 * description
 * </p>
 *
 * @author isaac 2020/10/22 11:12
 * @since 1.0.0
 */
public interface ComparisonJobService extends IService<ComparisonJob> {

    /**
     * 分页条件查询数据稽核job
     *
     * @param page             分页
     * @param comparisonJobDTO ComparisonJobDTO
     * @return IPage<PluginDTO>
     */
    IPage<ComparisonJobDTO> list(Page<ComparisonJob> page, ComparisonJobDTO comparisonJobDTO);

    /**
     * 执行数据稽核job
     *
     * @param tenantId 租户id
     * @param jobCode    jobCode
     * @param groupCode  groupCode
     */
    void execute(Long tenantId, String jobCode, String groupCode);

    /**
     * 保存数据稽核job
     *
     * @param comparisonJobDTO ComparisonJobDTO
     * @return ComparisonJobDTO
     */
    ComparisonJobDTO save(ComparisonJobDTO comparisonJobDTO);
}
