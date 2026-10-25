package dev.mesbase.business.sales.controller;

import dev.mesbase.business.sales.controller.vo.MesCustomerPageVo;
import dev.mesbase.business.sales.controller.vo.MesCustomerVo;
import dev.mesbase.business.sales.domain.MesCustomerEntity;
import dev.mesbase.business.sales.domain.MesCustomerQueryDto;
import dev.mesbase.business.sales.domain.CustomerCreateRequest;
import dev.mesbase.business.sales.domain.CustomerUpdateRequest;
import dev.mesbase.business.sales.service.IMesCustomerService;
import dev.mesbase.business.sales.service.CustomerManagementService;
import dev.mesbase.common.utils.bean.BeanConversions;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售客户 HTTP 接口，响应转换显式发生在 Web 边界。
 */
@RestController
@RequestMapping("/api/sales/mes-customer")
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class MesCustomerController {
    private final IMesCustomerService service;
    private final CustomerManagementService management;

    public MesCustomerController(IMesCustomerService service, CustomerManagementService management) {
        this.service = service;
        this.management = management;
    }

    @GetMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesCustomerPermissions.LIST)
    public MesCustomerPageVo list(@Valid @ModelAttribute MesCustomerQueryDto query) {
        return BeanConversions.convertPage(service.selectPage(query), MesCustomerPageVo.class);
    }

    @GetMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesCustomerPermissions.QUERY)
    public MesCustomerVo detail(@PathVariable Long id) {
        return toView(service.selectById(id));
    }

    @PostMapping
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesCustomerPermissions.CREATE)
    public MesCustomerVo create(@Valid @RequestBody CustomerCreateRequest input) {
        return toView(management.create(input));
    }

    @PutMapping("/{id}")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesCustomerPermissions.UPDATE)
    public MesCustomerVo update(@PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest input) {
        return toView(management.update(id, input));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED,
            permission = MesCustomerPermissions.DELETE)
    public void delete(@RequestBody
            @NotEmpty @Size(max = 100)
            List<@NotNull Long> ids) {
        management.delete(ids);
    }


    private static MesCustomerVo toView(MesCustomerEntity row) {
        return BeanConversions.convert(row, MesCustomerVo.class);
    }
}

