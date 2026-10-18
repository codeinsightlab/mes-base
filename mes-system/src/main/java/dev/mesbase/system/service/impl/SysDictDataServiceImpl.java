package dev.mesbase.system.service.impl;

import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.utils.DictUtils;
import dev.mesbase.common.utils.bean.BeanValidators;
import dev.mesbase.system.mapper.SysDictDataMapper;
import dev.mesbase.system.service.ISysDictDataService;
import jakarta.validation.Validator;
import dev.mesbase.common.exception.ServiceException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典 业务层处理
 *
 * @author ktg
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysDictDataServiceImpl implements ISysDictDataService {
    @Autowired
    private Validator contractValidator;
    @Autowired
    private SysDictDataMapper dictDataMapper;

    /**
     * 根据条件分页查询字典数据
     *
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictData> selectDictDataList(SysDictData dictData) {
        return dictDataMapper.selectDictDataList(dictData);
    }

    /**
     * 根据字典类型和字典键值查询字典数据信息
     *
     * @param dictType  字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    @Override
    public String selectDictLabel(String dictType, String dictValue) {
        return dictDataMapper.selectDictLabel(dictType, dictValue);
    }

    /**
     * 根据字典数据ID查询信息
     *
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    @Override
    public SysDictData selectDictDataById(Long dictCode) {
        return dictDataMapper.selectDictDataById(dictCode);
    }

    /**
     * 批量删除字典数据信息
     *
     * @param dictCodes 需要删除的字典数据ID
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void deleteDictDataByIds(Long[] dictCodes) {
        for (Long dictCode : dictCodes) {
            SysDictData data = selectDictDataById(dictCode);
            dictDataMapper.deleteDictDataById(dictCode);
            DictUtils.removeDictCache(data.getDictType());
        }
    }

    /**
     * 新增保存字典数据信息
     *
     * @param data 字典数据信息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int insertDictData(SysDictData data) {
        BeanValidators.validateWithException(contractValidator, data);
        int row = dictDataMapper.insertDictData(data);
        if (row > 0) {
            DictUtils.removeDictCache(data.getDictType());
        }
        return row;
    }

    /**
     * 修改保存字典数据信息
     *
     * @param data 字典数据信息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int updateDictData(SysDictData data) {
        BeanValidators.validateWithException(contractValidator, data);
        var previous = dictDataMapper.selectDictDataById(data.getDictCode());
        if (previous == null) throw new ServiceException("字典数据不存在");
        int row = dictDataMapper.updateDictData(data);
        if (row > 0) DictUtils.removeDictCache(previous.getDictType());
        if (row > 0) {
            DictUtils.removeDictCache(data.getDictType());
        }
        return row;
    }
}
