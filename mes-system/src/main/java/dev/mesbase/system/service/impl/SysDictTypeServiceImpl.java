package dev.mesbase.system.service.impl;

import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.domain.entity.SysDictType;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.utils.DictUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.bean.BeanValidators;
import dev.mesbase.system.mapper.SysDictDataMapper;
import dev.mesbase.system.mapper.SysDictTypeMapper;
import dev.mesbase.system.service.ISysDictTypeService;
import jakarta.validation.Validator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典 业务层处理
 *
 * @author ruoyi
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysDictTypeServiceImpl implements ISysDictTypeService {
    @Autowired
    private Validator contractValidator;
    @Autowired
    private SysDictTypeMapper dictTypeMapper;

    @Autowired
    private SysDictDataMapper dictDataMapper;

    /**
     * 项目启动时，初始化字典到缓存
     */

    public void init() {
        loadingDictCache();
    }

    /**
     * 根据条件分页查询字典类型
     *
     * @param dictType 字典类型信息
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictType> selectDictTypeList(SysDictType dictType) {
        return dictTypeMapper.selectDictTypeList(dictType);
    }

    /**
     * 根据所有字典类型
     *
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictType> selectDictTypeAll() {
        return dictTypeMapper.selectDictTypeAll();
    }

    /**
     * 根据字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictData> selectDictDataByType(String dictType) {
        List<SysDictData> dictDatas = DictUtils.getDictCache(dictType);
        if (StringUtils.isNotEmpty(dictDatas)) {
            return dictDatas;
        }
        dictDatas = dictDataMapper.selectDictDataByType(dictType);
        if (StringUtils.isNotEmpty(dictDatas)) {
            DictUtils.setDictCache(dictType, dictDatas);
            return dictDatas;
        }
        return null;
    }

    /**
     * 根据字典类型ID查询信息
     *
     * @param dictId 字典类型ID
     * @return 字典类型
     */
    @Override
    public SysDictType selectDictTypeById(Long dictId) {
        return dictTypeMapper.selectDictTypeById(dictId);
    }

    /**
     * 根据字典类型查询信息
     *
     * @param dictType 字典类型
     * @return 字典类型
     */
    @Override
    public SysDictType selectDictTypeByType(String dictType) {
        return dictTypeMapper.selectDictTypeByType(dictType);
    }

    /**
     * 批量删除字典类型信息
     *
     * @param dictIds 需要删除的字典ID
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void deleteDictTypeByIds(Long[] dictIds) {
        for (Long dictId : dictIds) {
            SysDictType dictType = selectDictTypeById(dictId);
            if (dictDataMapper.countDictDataByType(dictType.getDictType()) > 0) {
                throw new ServiceException(String.format("%1$s已分配,不能删除", dictType.getDictName()));
            }
            dictTypeMapper.deleteDictTypeById(dictId);
            DictUtils.removeDictCache(dictType.getDictType());
        }
    }

    /**
     * 加载字典缓存数据
     */
    @Override
    public void loadingDictCache() {
        clearDictCache();
    }

    /**
     * 清空字典缓存数据
     */
    @Override
    public void clearDictCache() {
        DictUtils.clearDictCache();
    }

    /**
     * 重置字典缓存数据
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void resetDictCache() {
        clearDictCache();
        loadingDictCache();
    }

    /**
     * 新增保存字典类型信息
     *
     * @param dict 字典类型信息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int insertDictType(SysDictType dict) {
        BeanValidators.validateWithException(contractValidator, dict);
        int row = dictTypeMapper.insertDictType(dict);
        if (row > 0) {
            DictUtils.removeDictCache(dict.getDictType());
        }
        return row;
    }

    /**
     * 修改保存字典类型信息
     *
     * @param dict 字典类型信息
     * @return 结果
     */
    @Override

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)

    public int updateDictType(SysDictType dict) {
        BeanValidators.validateWithException(contractValidator, dict);
        SysDictType oldDict = dictTypeMapper.selectDictTypeById(dict.getDictId());
        if (oldDict == null) throw new ServiceException("字典类型不存在");
        dictDataMapper.updateDictDataType(oldDict.getDictType(), dict.getDictType());
        int row = dictTypeMapper.updateDictType(dict);
        if (row > 0) {
            DictUtils.removeDictCache(oldDict.getDictType());
            DictUtils.removeDictCache(dict.getDictType());
        }
        return row;
    }

    /**
     * 校验字典类型称是否唯一
     *
     * @param dict 字典类型
     * @return 结果
     */
    @Override
    public String checkDictTypeUnique(SysDictType dict) {
        Long dictId = StringUtils.isNull(dict.getDictId()) ? -1L : dict.getDictId();
        SysDictType dictType = dictTypeMapper.checkDictTypeUnique(dict.getDictType());
        if (StringUtils.isNotNull(dictType) && dictType.getDictId().longValue() != dictId.longValue()) {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }
}
