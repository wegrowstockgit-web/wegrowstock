package com.invsys.api;

import com.invsys.api.dto.BomLineResponse;
import com.invsys.api.dto.BomResponse;
import com.invsys.domain.Bom;
import com.invsys.domain.BomLine;
import com.invsys.domain.BomOutput;
import com.invsys.repository.BomRepository;
import com.invsys.service.ManufacturingDtoMapper;
import com.invsys.service.ManufacturingService;
import com.invsys.tenancy.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manufacturing")
@PreAuthorize("hasAnyRole('OWNER','ADMIN','WAREHOUSE_MANAGER')")
public class BomController {

    private final BomRepository bomRepository;
    private final ManufacturingService manufacturingService;
    private final ManufacturingDtoMapper dtoMapper;

    public BomController(BomRepository bomRepository,
                         ManufacturingService manufacturingService,
                         ManufacturingDtoMapper dtoMapper) {
        this.bomRepository = bomRepository;
        this.manufacturingService = manufacturingService;
        this.dtoMapper = dtoMapper;
    }

    @GetMapping("/boms")
    public List<BomResponse> listBoms() {
        return bomRepository.findByTenantIdOrderByNameAsc(TenantContext.requireTenantId()).stream()
                .map(dtoMapper::toBomResponse)
                .toList();
    }

    @GetMapping("/boms/{id}")
    public BomResponse getBom(@PathVariable UUID id) {
        Bom bom = bomRepository.findById(id).orElseThrow();
        return dtoMapper.toBomResponse(bom);
    }

    @PostMapping("/boms")
    public BomResponse createBom(@Valid @RequestBody CreateBomRequest request) {
        List<ManufacturingService.BomLineInput> lines = request.lines().stream()
                .map(l -> new ManufacturingService.BomLineInput(l.componentVariantId(), l.quantityRequired()))
                .toList();
        return dtoMapper.toBomResponse(
                manufacturingService.createBom(
                        request.parentVariantId(),
                        request.name(),
                        lines,
                        Boolean.TRUE.equals(request.autoAssemble())));
    }

    @PostMapping("/boms/{id}/lines")
    public BomLineResponse addLine(@PathVariable UUID id, @Valid @RequestBody AddBomLineRequest request) {
        BomLine line = manufacturingService.addBomLine(id, request.componentVariantId(), request.quantityRequired());
        return dtoMapper.toBomLineResponse(line);
    }

    @GetMapping("/boms/{id}/outputs")
    public List<BomOutputResponse> listOutputs(@PathVariable UUID id) {
        return manufacturingService.listBomOutputs(id).stream()
                .map(o -> new BomOutputResponse(
                        o.getId(), o.getBomId(), o.getVariantId(), o.getOutputType(),
                        o.getAllocationRatio(), o.getQtyPerBatch()))
                .toList();
    }

    @PostMapping("/boms/{id}/outputs")
    public BomOutputResponse addOutput(@PathVariable UUID id, @Valid @RequestBody AddBomOutputRequest request) {
        BomOutput output = manufacturingService.addBomOutput(
                id, request.variantId(), request.outputType(), request.allocationRatio(), request.qtyPerBatch());
        return new BomOutputResponse(
                output.getId(), output.getBomId(), output.getVariantId(), output.getOutputType(),
                output.getAllocationRatio(), output.getQtyPerBatch());
    }

    public record CreateBomRequest(
            @NotNull UUID parentVariantId,
            @NotBlank String name,
            Boolean autoAssemble,
            @NotNull List<CreateBomLineRequest> lines
    ) {
    }

    public record CreateBomLineRequest(
            @NotNull UUID componentVariantId,
            @NotNull @Positive BigDecimal quantityRequired
    ) {
    }

    public record AddBomLineRequest(
            @NotNull UUID componentVariantId,
            @NotNull @Positive BigDecimal quantityRequired
    ) {
    }

    public record AddBomOutputRequest(
            @NotNull UUID variantId,
            @NotBlank String outputType,
            @NotNull BigDecimal allocationRatio,
            BigDecimal qtyPerBatch
    ) {
    }

    public record BomOutputResponse(
            UUID id,
            UUID bomId,
            UUID variantId,
            String outputType,
            BigDecimal allocationRatio,
            BigDecimal qtyPerBatch
    ) {
    }
}
