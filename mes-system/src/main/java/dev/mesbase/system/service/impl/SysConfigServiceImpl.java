package dev.mesbase.system.service.impl;

import dev.mesbase.common.constant.Constants;
import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.redis.CenterSettingsCache;
import dev.mesbase.common.core.text.Convert;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.utils.bean.BeanValidators;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.domain.SysConfig;
import dev.mesbase.system.mapper.SysConfigMapper;
import dev.mesbase.system.service.ISysConfigService;
import dev.mesbase.system.service.SystemPermissionService;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 参数配置 服务层实现
 *
 * @author ruoyi
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysConfigServiceImpl implements ISysConfigService {
    @Autowired
    private Validator contractValidator;
    @Autowired
    private SysConfigMapper configMapper;

    @Autowired
    private CenterSettingsCache settingsCache;
    @Autowired
    private SystemPermissionService permissions;

    @Override
    public String selectClientConfigByKey(String key) {
        if (!Set.of("sys.account.captchaOnOff", "sys.index.skinName", "sys.account.registerUser", "sys.user.avatar.maxSize")
                .contains(key)
                && !permissions.grantedPlatform(SecurityUtils.getUsername(), "system:config:query"))
            throw new FoundationException(ErrorCode.FORBIDDEN);
        return selectConfigByKey(key);
    }

    /**
     * 项目启动时，初始化参数到缓存
     */

    public void init() {
        loadingConfigCache();
    }

    /**
     * 查询参数配置信息
     *
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    @Override
    public SysConfig selectConfigById(Long configId) {
        SysConfig config = new SysConfig();
        config.setConfigId(configId);
        return configMapper.selectConfig(config);
    }

    /**
     * 根据键名查询参数配置信息
     *
     * @param configKey 参数key
     * @return 参数键值
     */
    @Override
    public String selectConfigByKey(String configKey) {
        // Account switches are security decisions: always use the database authority.
        boolean security = configKey.startsWith("sys.account.");
        String configValue = security ? "" : Convert.toStr(settingsCache.get(getCacheKey(configKey)));
        if (StringUtils.isNotEmpty(configValue)) {
            return configValue;
        }
        SysConfig config = new SysConfig();
        config.setConfigKey(configKey);
        SysConfig retConfig = configMapper.selectConfig(config);
        if (StringUtils.isNotNull(retConfig)) {
            if (!security) settingsCache.put(getCacheKey(configKey), retConfig.getConfigValue());
            return retConfig.getConfigValue();
        }
        return StringUtils.EMPTY;
    }

    /**
     * 获取验证码开关
     *
     * @return true开启，false关闭
     */
    @Override
    public boolean selectCaptchaOnOff() {
        String captchaOnOff = selectConfigByKey("sys.account.captchaOnOff");
        if (StringUtils.isEmpty(captchaOnOff)) {
            return true;
        }
        return Convert.toBool(captchaOnOff);
    }

    /**
     * 查询参数配置列表
     *
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    @Override
    public List<SysConfig> selectConfigList(SysConfig config) {
        return configMapper.selectConfigList(config);
    }

    /**
     * 新增参数配置
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int insertConfig(SysConfig config) {
        BeanValidators.validateWithException(contractValidator, config);
        int row = configMapper.insertConfig(config);
        if (row > 0) {
            settingsCache.invalidate(getCacheKey(config.getConfigKey()));
        }
        return row;
    }

    /**
     * 修改参数配置
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int updateConfig(SysConfig config) {
        BeanValidators.validateWithException(contractValidator, config);
        var previous = selectConfigById(config.getConfigId());
        if (previous == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        settingsCache.invalidate(getCacheKey(previous.getConfigKey()));
        int row = configMapper.updateConfig(config);
        if (row > 0) {
            settingsCache.invalidate(getCacheKey(config.getConfigKey()));
        }
        return row;
    }

    /**
     * 批量删除参数信息
     *
     * @param configIds 需要删除的参数ID
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void deleteConfigByIds(Long[] configIds) {
        for (Long configId : configIds) {
            SysConfig config = selectConfigById(configId);
            if (StringUtils.equals(UserConstants.YES, config.getConfigType())) {
                throw new ServiceException(String.format("内置参数【%1$s】不能删除 ", config.getConfigKey()));
            }
            configMapper.deleteConfigById(configId);
            settingsCache.invalidate(getCacheKey(config.getConfigKey()));
        }
    }

    /**
     * 加载参数缓存数据
     */
    @Override
    public void loadingConfigCache() {
        // Lazy loading avoids publishing a transaction snapshot during reset/startup.
        clearConfigCache();
    }

    /**
     * 清空参数缓存数据
     */
    @Override
    public void clearConfigCache() {
        settingsCache.clear(Constants.SYS_CONFIG_KEY);
    }

    /**
     * 重置参数缓存数据
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void resetConfigCache() {
        clearConfigCache();
        loadingConfigCache();
    }

    /**
     * 校验参数键名是否唯一
     *
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public String checkConfigKeyUnique(SysConfig config) {
        Long configId = StringUtils.isNull(config.getConfigId()) ? -1L : config.getConfigId();
        SysConfig info = configMapper.checkConfigKeyUnique(config.getConfigKey());
        if (StringUtils.isNotNull(info) && info.getConfigId().longValue() != configId.longValue()) {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 设置cache key
     *
     * @param configKey 参数键
     * @return 缓存键key
     */
    private String getCacheKey(String configKey) {
        return Constants.SYS_CONFIG_KEY + configKey;
    }
}
