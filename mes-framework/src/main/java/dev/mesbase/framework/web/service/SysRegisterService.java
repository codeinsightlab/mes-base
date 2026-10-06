package dev.mesbase.framework.web.service;

import dev.mesbase.common.constant.UserConstants;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.core.domain.model.RegisterBody;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.utils.PasswordPolicy;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.system.domain.SysLogininfor;
import dev.mesbase.system.service.ISysConfigService;
import dev.mesbase.system.service.ISysLogininforService;
import dev.mesbase.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** SOURCE_PRODUCT_PORT of SysRegisterService: Source switch, captcha, uniqueness and registration audit. */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysRegisterService {
    private final ISysUserService users;
    private final ISysConfigService config;
    private final ISysLogininforService audit;
    private final CaptchaService captcha;
    private final PasswordPolicy passwords;
    private final Validator validator;
    private final PlatformPersistence unit;

    public SysRegisterService(ISysUserService users, ISysConfigService config,
            ISysLogininforService audit, CaptchaService captcha, PasswordPolicy passwords,
            Validator validator, PlatformPersistence unit) {
        this.users = users;
        this.config = config;
        this.audit = audit;
        this.captcha = captcha;
        this.passwords = passwords;
        this.validator = validator;
        this.unit = unit;
    }

    public void register(RegisterBody input, HttpServletRequest request) {
        if (!"true".equals(config.selectConfigByKey("sys.account.registerUser")))
            throw new ServiceException("当前系统没有开启注册功能");
        captcha.validate(input.uuid(), input.code());
        SysUser user = new SysUser();
        user.setUserName(input.username());
        user.setNickName(input.username());
        user.setUserType("00");
        user.setSex("2");
        user.setStatus("0");
        user.setDelFlag("0");
        user.setCreateBy("REGISTER");
        if (!validator.validate(user).isEmpty()) throw new ServiceException("账号格式不符合要求");
        user.setPassword(passwords.encode(input.password()));
        unit.run(() -> {
            if (!"true".equals(config.selectConfigByKey("sys.account.registerUser")))
                throw new ServiceException("当前系统没有开启注册功能");
            if (UserConstants.NOT_UNIQUE.equals(users.checkUserNameUnique(input.username())))
                throw new ServiceException("注册账号已存在");
            if (!users.registerUser(user)) throw new ServiceException("注册失败，请联系系统管理人员");
            var log = new SysLogininfor();
            log.setUserName(user.getUserName());
            log.setActorId(user.getUserId().toString());
            log.setIpaddr(request.getRemoteAddr());
            var agent = ClientAgent.parse(request.getHeader("User-Agent"));
            log.setBrowser(agent.browser());
            log.setOs(agent.os());
            log.setStatus("0");
            log.setMsg("注册成功");
            log.setRequestId(String.valueOf(request.getAttribute("mes.requestId")));
            audit.insertLogininfor(log);
            return null;
        });
    }
}
