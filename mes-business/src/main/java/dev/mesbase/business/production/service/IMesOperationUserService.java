package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.MesOperationUserPage;
import com.github.pagehelper.Page;
import dev.mesbase.business.production.domain.MesOperationUserCreateCommand;
import dev.mesbase.business.production.domain.MesOperationUserEntity;
import dev.mesbase.business.production.domain.MesOperationUserQueryDto;
import dev.mesbase.business.production.domain.MesOperationUserUpdateCommand;
import java.util.List;

/**
 * 工序能力人员 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesOperationUserService {
    MesOperationUserPage selectPage(MesOperationUserQueryDto query);

    MesOperationUserEntity selectById(Long id);

    MesOperationUserEntity insert(MesOperationUserCreateCommand input);

    MesOperationUserEntity updateById(Long id, MesOperationUserUpdateCommand input);

    void bindUsers(Long operationId, List<Long> userIds);

    void deleteByIds(List<Long> ids);
}

