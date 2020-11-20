package com.github.thestyleofme.comparison.common.infra.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.github.thestyleofme.comparison.common.domain.ColMapping;
import com.github.thestyleofme.comparison.common.domain.JobEnv;
import com.github.thestyleofme.comparison.common.domain.PrestoInfo;
import com.github.thestyleofme.comparison.common.infra.constants.PrestoConstant;
import com.github.thestyleofme.comparison.common.infra.exceptions.HandlerException;
import com.github.thestyleofme.plugin.core.infra.utils.BeanUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * @author siqi.hou
 * @date 2020-11-19 15:13
 */
@Slf4j
public class SqlGeneratorUtil {

    public static String generateSql(PrestoInfo prestoInfo, JobEnv jobEnv) {
        String sql;
        String sourcePk = jobEnv.getSourcePk();
        String targetPk = jobEnv.getTargetPk();
        String sourceIndex = jobEnv.getSourceIndex();
        String targetIndex = jobEnv.getTargetIndex();
        // 如果有指定主键
        if (!StringUtils.isEmpty(sourcePk) && !StringUtils.isEmpty(targetPk)) {
            sql = createSqlByPk(sourcePk, targetPk, prestoInfo, jobEnv);
        } else if (!StringUtils.isEmpty(sourceIndex) && !StringUtils.isEmpty(targetIndex)) {
            sql = createSqlByIndex(sourceIndex, targetIndex, prestoInfo, jobEnv);
        } else {
            throw new HandlerException("hdsp.xadt.error.presto.not_support");
        }
        return sql;
    }

    private static String createSqlByPk(String sourcePk,
                                        String targetPk,
                                        PrestoInfo prestoInfo,
                                        JobEnv jobEnv) {
        StringBuilder builder = new StringBuilder();
        String sourceTable = String.format(PrestoConstant.SqlConstant.TABLE_FT, prestoInfo.getSourcePrestoCatalog(),
                prestoInfo.getSourceSchema(), prestoInfo.getSourceTable());
        String targetTable = String.format(PrestoConstant.SqlConstant.TABLE_FT, prestoInfo.getTargetPrestoCatalog(),
                prestoInfo.getTargetSchema(), prestoInfo.getTargetTable());

        // 获取列的映射关系
        List<Map<String, Object>> colMapping = jobEnv.getColMapping();
        List<ColMapping> colMappingList = colMapping.stream()
                .map(map -> BeanUtils.map2Bean(map, ColMapping.class))
                .collect(Collectors.toList());
        /*
        AB都有的数据
        例：
        `select a.* from devmysql.hdsp_test.resume as a join devmysql.hdsp_test.resume_bak  as b
        ON a.id = b.id WHERE a.id = b.id and a.name = b.name and a.sex = b.sex and a.phone = b
        .call and a.address = b.address and a.education = b.education and a.state = b.state;`
        */
        StringBuilder equalsBuilder = new StringBuilder();
        for (ColMapping mapping : colMappingList) {
            equalsBuilder.append(String.format(PrestoConstant.SqlConstant.EQUAL, mapping.getSourceCol(), mapping.getTargetCol()));
        }
        builder.append(String.format(PrestoConstant.SqlConstant.ALL_HAVE_SQL_PK, sourceTable, targetTable, sourcePk, targetPk,
                equalsBuilder.toString()))
                .append(PrestoConstant.SqlConstant.LINE_END);
        /*
         A有B无
         `select a.* from devmysql.hdsp_test.resume  as a left join devmysql.hdsp_test.resume_bak  as b ON a.id = b.id WHERE b.id is null;`
         */
        createUniqueDataSqlByPk(builder, sourceTable, targetTable, sourcePk, targetPk);
        /*
          A无B有
          `select a.* from devmysql.hdsp_test.resume_bak  as a left join devmysql.hdsp_test.resume  as b ON a.id = b.id WHERE b.id is null  ;`
         */
        createUniqueDataSqlByPk(builder, targetTable, sourceTable, targetPk, sourcePk);

        /*
          AB主键或唯一索引相同，部分字段不一样
          `select a.* from devmysql.hdsp_test.resume  as a left join devmysql.hdsp_test.resume_bak  as b
          ON a.id = b.id
          WHERE a.id != b.id or a.name != b.name or a.sex != b.sex or a.phone != b.call or a.address != b.address
          or a.education != b.education or a.state != b.state or 1=2  ;`
         */
        StringBuilder whereBuilder = new StringBuilder();
        for (ColMapping mapping : colMappingList) {
            whereBuilder.append(String.format(PrestoConstant.SqlConstant.NOT_EQUAL, mapping.getSourceCol(), mapping.getTargetCol()));
        }
        whereBuilder.append(PrestoConstant.SqlConstant.OR_END);
        builder.append(String.format(PrestoConstant.SqlConstant.ANY_NOT_IN_SQL_PK, sourceTable, targetTable, sourcePk, targetPk,
                whereBuilder.toString()))
                .append(PrestoConstant.SqlConstant.LINE_END);

        log.debug("==> presto create sql by primary key: {}", builder.toString());
        return builder.toString();
    }

    public static void createUniqueDataSqlByPk(StringBuilder builder, String tableA, String tableB, String pkA,
                                               String pkB) {
        builder.append(String.format(PrestoConstant.SqlConstant.LEFT_HAVE_SQL_PK, tableA, tableB, pkA, pkB, pkB))
                .append(PrestoConstant.SqlConstant.LINE_END);
    }

    private static String createSqlByIndex(String sourceIndex,
                                           String targetIndex,
                                           PrestoInfo prestoInfo,
                                           JobEnv jobEnv) {
        //获取索引的列
        String[] sourceIndexArray = sourceIndex.split(StringPool.COMMA);
        String[] targetIndexArray = targetIndex.split(StringPool.COMMA);
        if (sourceIndexArray.length != targetIndexArray.length) {
            throw new HandlerException("hdsp.xadt.error.presto.index_length.not_equals");
        }
        StringBuilder builder = new StringBuilder();
        String sourceTable = String.format(PrestoConstant.SqlConstant.TABLE_FT, prestoInfo.getSourcePrestoCatalog(),
                prestoInfo.getSourceSchema(), prestoInfo.getSourceTable());
        String targetTable = String.format(PrestoConstant.SqlConstant.TABLE_FT, prestoInfo.getTargetPrestoCatalog(),
                prestoInfo.getTargetSchema(), prestoInfo.getTargetTable());

        // 获取列的映射关系
        List<Map<String, Object>> colMapping = jobEnv.getColMapping();
        List<ColMapping> colMappingList = colMapping.stream()
                .map(map -> BeanUtils.map2Bean(map, ColMapping.class))
                .collect(Collectors.toList());
        // 获取除去索引外的所有列
        List<ColMapping> colList = colMappingList.stream().filter(col -> {
            for (int i = 0; i < sourceIndexArray.length; i++) {
                if (sourceIndexArray[i].equalsIgnoreCase(col.getSourceCol())
                        && targetIndexArray[i].equalsIgnoreCase(col.getTargetCol())) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        // 创建on语句 AB型
        StringBuilder onConditionBuilder = new StringBuilder();
        for (int i = 0; i < sourceIndexArray.length; i++) {
            onConditionBuilder.append(String.format(PrestoConstant.SqlConstant.ON_EQUAL, sourceIndexArray[i], targetIndexArray[i])).append(PrestoConstant.SqlConstant.AND);
        }
        onConditionBuilder.delete(onConditionBuilder.lastIndexOf(PrestoConstant.SqlConstant.AND), onConditionBuilder.length());
        String onConditionAB = onConditionBuilder.toString();
        // 创建on语句 AB型
        onConditionBuilder.setLength(0);
        for (int i = 0; i < sourceIndexArray.length; i++) {
            onConditionBuilder.append(String.format(PrestoConstant.SqlConstant.ON_EQUAL, targetIndexArray[i], sourceIndexArray[i])).append(PrestoConstant.SqlConstant.AND);
        }
        onConditionBuilder.delete(onConditionBuilder.lastIndexOf(PrestoConstant.SqlConstant.AND), onConditionBuilder.length());
        String onConditionBA = onConditionBuilder.toString();

        /*
        AB都有的数据
        例：
        `select a.* from devmysql.hdsp_test.resume as a join devmysql.hdsp_test.resume_bak  as b
        ON a.name = b.name and a.phone = b.call WHERE a.id = b.id and a.sex = b.sex and a.address = b.address
        and a.education = b.education and a.state = b.state;`
        */
        StringBuilder equalsBuilder = new StringBuilder();
        for (ColMapping mapping : colList) {
            equalsBuilder.append(String.format(PrestoConstant.SqlConstant.EQUAL, mapping.getSourceCol(), mapping.getTargetCol()));
        }
        builder.append(String.format(PrestoConstant.SqlConstant.ALL_HAVE_SQL_INDEX, sourceTable, targetTable, onConditionAB, equalsBuilder.toString()))
                .append(PrestoConstant.SqlConstant.LINE_END);
        /*
         A有B无
         `select a.* from devmysql.hdsp_test.resume as a left join devmysql.hdsp_test.resume_bak  as b ON a.name = b.name and a.phone = b.call where b.name is null and b.call is null;`
         */
        StringBuilder whereCondition1 = new StringBuilder();
        for (String idx : targetIndexArray) {
            whereCondition1.append(String.format(PrestoConstant.SqlConstant.IS_NULL, idx)).append(PrestoConstant.SqlConstant.AND);
        }
        whereCondition1.delete(whereCondition1.lastIndexOf(PrestoConstant.SqlConstant.AND), whereCondition1.length());
        builder.append(String.format(PrestoConstant.SqlConstant.LEFT_HAVE_SQL_INDEX, sourceTable, targetTable, onConditionAB,
                whereCondition1.toString()))
                .append(PrestoConstant.SqlConstant.LINE_END);
        /*
         B有A无
         `select a.* from devmysql.hdsp_test.resume_bak as a left join devmysql.hdsp_test.resume  as b ON a.name = b.name and a.call = b.phone where b.name is null and b.phone is null;`
         */
        StringBuilder whereCondition2 = new StringBuilder();
        for (String idx : sourceIndexArray) {
            whereCondition2.append(String.format(PrestoConstant.SqlConstant.IS_NULL, idx)).append(PrestoConstant.SqlConstant.AND);
        }
        whereCondition2.delete(whereCondition2.lastIndexOf(PrestoConstant.SqlConstant.AND), whereCondition2.length());
        builder.append(String.format(PrestoConstant.SqlConstant.LEFT_HAVE_SQL_INDEX, targetTable, sourceTable, onConditionBA,
                whereCondition2.toString()))
                .append(PrestoConstant.SqlConstant.LINE_END);

        /*
          AB唯一索引相同，部分字段不一样
          `select a.* from devmysql.hdsp_test.resume as a left join devmysql.hdsp_test.resume_bak  as b
          ON a.name = b.name and a.phone = b.call
          WHERE a.id != b.id or a.sex != b.sex or a.address != b.address
          or a.education != b.education or a.state != b.state or 1=2  ;`
         */
        StringBuilder whereBuilder = new StringBuilder();
        for (ColMapping mapping : colList) {
            whereBuilder.append(String.format(PrestoConstant.SqlConstant.NOT_EQUAL, mapping.getSourceCol(), mapping.getTargetCol()));
        }
        whereBuilder.append(PrestoConstant.SqlConstant.OR_END);
        builder.append(String.format(PrestoConstant.SqlConstant.ANY_NOT_IN_SQL_INDEX, sourceTable, targetTable, onConditionAB,
                whereBuilder.toString()))
                .append(PrestoConstant.SqlConstant.LINE_END);

        log.debug("==> presto create sql by index: {}", builder.toString());
        return builder.toString();
    }

//    private String createColSql(List<ColMapping> columns){
//    }

}
