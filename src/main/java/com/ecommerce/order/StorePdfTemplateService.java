package com.ecommerce.order;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StorePdfTemplateService {
    private final PdfTemplateRepository templateRepository;

    public StorePdfTemplateService(PdfTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public PdfTemplateListData list(String storeId, String type) {
        List<PdfTemplate> templates;
        if (type != null && !type.isBlank()) {
            PdfTemplateType templateType = PdfTemplateType.from(type);
            templates = templateRepository.findByStoreIdAndTypeOrderByCreatedAtDesc(storeId, templateType);
        } else {
            templates = templateRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        }
        return new PdfTemplateListData(templates.stream().map(this::toData).toList(), templates.size());
    }

    public PdfTemplateData get(String storeId, String publicTemplateId) {
        return toData(require(storeId, publicTemplateId));
    }

    public PdfTemplateData create(String storeId, String ownerPublicUserId, PdfTemplateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        String name = normalize(request.name());
        if (name == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Template name is required");
        }
        PdfTemplateType type = PdfTemplateType.from(request.type());

        PdfTemplate template = new PdfTemplate();
        template.setStoreId(storeId);
        template.setOwnerPublicUserId(ownerPublicUserId);
        template.setName(name);
        template.setType(type);
        template.setDefaultTemplate(Boolean.TRUE.equals(request.isDefault()));
        template.setSystemTemplate(false);
        template.setLayoutConfig(normalize(request.layoutConfig()));
        template.setHeaderHtml(normalize(request.headerHtml()));
        template.setFooterHtml(normalize(request.footerHtml()));
        template.setLogoUrl(normalize(request.logoUrl()));

        if (template.isDefaultTemplate()) {
            clearDefault(storeId, type);
        }

        return toData(templateRepository.save(template));
    }

    public PdfTemplateData update(String storeId, String publicTemplateId, PdfTemplateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        PdfTemplate template = require(storeId, publicTemplateId);

        if (request.name() != null && !request.name().isBlank()) {
            template.setName(request.name().trim());
        }
        if (request.layoutConfig() != null) {
            template.setLayoutConfig(request.layoutConfig());
        }
        if (request.headerHtml() != null) {
            template.setHeaderHtml(request.headerHtml());
        }
        if (request.footerHtml() != null) {
            template.setFooterHtml(request.footerHtml());
        }
        if (request.logoUrl() != null) {
            template.setLogoUrl(request.logoUrl());
        }
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefault(storeId, template.getType());
            template.setDefaultTemplate(true);
        }

        return toData(templateRepository.save(template));
    }

    public void delete(String storeId, String publicTemplateId) {
        PdfTemplate template = require(storeId, publicTemplateId);
        if (template.isSystemTemplate()) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot delete system templates");
        }
        templateRepository.delete(template);
    }

    public void seedDefaults(String storeId, String ownerPublicUserId) {
        List<PdfTemplate> existing = templateRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
        if (!existing.isEmpty()) {
            return;
        }

        Set<String> createdTypes = new HashSet<>();
        for (PdfTemplateType type : PdfTemplateType.values()) {
            for (int i = 0; i < 4; i++) {
                PdfTemplate template = new PdfTemplate();
                template.setStoreId(storeId);
                template.setOwnerPublicUserId(ownerPublicUserId);
                template.setSystemTemplate(true);
                template.setType(type);

                switch (type) {
                    case INVOICE -> applyInvoiceTemplate(template, i);
                    case SHIPPING_LABEL -> applyShippingLabelTemplate(template, i);
                    case PACKING_SLIP -> applyPackingSlipTemplate(template, i);
                }

                if (i == 0 && !createdTypes.contains(type.name())) {
                    template.setDefaultTemplate(true);
                    createdTypes.add(type.name());
                }

                templateRepository.save(template);
            }
        }
    }

    public PdfTemplate getDefaultTemplate(String storeId, PdfTemplateType type) {
        return templateRepository.findByStoreIdAndTypeAndDefaultTemplateTrue(storeId, type).orElseGet(() -> {
            List<PdfTemplate> templates = templateRepository.findByStoreIdAndTypeOrderByCreatedAtDesc(storeId, type);
            return templates.isEmpty() ? null : templates.get(0);
        });
    }

    private void applyInvoiceTemplate(PdfTemplate template, int index) {
        switch (index) {
            case 0 -> {
                template.setName("Classic Invoice");
                template.setLayoutConfig("{\"style\":\"classic\",\"font\":\"Helvetica\",\"fontSize\":10,\"accentColor\":\"#000000\",\"showBorders\":true,\"showGrid\":true}");
            }
            case 1 -> {
                template.setName("Modern Invoice");
                template.setLayoutConfig("{\"style\":\"modern\",\"font\":\"Helvetica\",\"fontSize\":10,\"accentColor\":\"#2563EB\",\"showBorders\":false,\"showGrid\":true}");
            }
            case 2 -> {
                template.setName("Compact Invoice");
                template.setLayoutConfig("{\"style\":\"compact\",\"font\":\"Helvetica\",\"fontSize\":8,\"accentColor\":\"#333333\",\"showBorders\":true,\"showGrid\":false}");
            }
            case 3 -> {
                template.setName("Detailed Invoice");
                template.setLayoutConfig("{\"style\":\"detailed\",\"font\":\"Helvetica\",\"fontSize\":9,\"accentColor\":\"#1E3A5F\",\"showBorders\":true,\"showGrid\":true,\"showTerms\":true}");
            }
        }
    }

    private void applyShippingLabelTemplate(PdfTemplate template, int index) {
        switch (index) {
            case 0 -> {
                template.setName("Standard Label");
                template.setLayoutConfig("{\"style\":\"standard\",\"width\":4,\"height\":6,\"unit\":\"in\",\"showBarcode\":true}");
            }
            case 1 -> {
                template.setName("Compact Label");
                template.setLayoutConfig("{\"style\":\"compact\",\"width\":4,\"height\":4,\"unit\":\"in\",\"showBarcode\":true}");
            }
            case 2 -> {
                template.setName("Full Label");
                template.setLayoutConfig("{\"style\":\"full\",\"width\":6,\"height\":8,\"unit\":\"in\",\"showBarcode\":true,\"showSummary\":true}");
            }
            case 3 -> {
                template.setName("Return Label");
                template.setLayoutConfig("{\"style\":\"return\",\"width\":4,\"height\":6,\"unit\":\"in\",\"showBarcode\":true,\"showReturnAddress\":true}");
            }
        }
    }

    private void applyPackingSlipTemplate(PdfTemplate template, int index) {
        switch (index) {
            case 0 -> {
                template.setName("Standard Packing Slip");
                template.setLayoutConfig("{\"style\":\"standard\",\"accentColor\":\"#0F172A\",\"showPrices\":false,\"showSku\":true,\"showBarcode\":false}");
            }
            case 1 -> {
                template.setName("Priced Packing Slip");
                template.setLayoutConfig("{\"style\":\"priced\",\"accentColor\":\"#0F172A\",\"showPrices\":true,\"showSku\":true,\"showBarcode\":false}");
            }
            case 2 -> {
                template.setName("Compact Packing Card");
                template.setLayoutConfig("{\"style\":\"compact\",\"accentColor\":\"#334155\",\"showPrices\":false,\"showSku\":false,\"showBarcode\":false}");
            }
            case 3 -> {
                template.setName("Gift Packing Slip");
                template.setLayoutConfig("{\"style\":\"gift\",\"accentColor\":\"#7C3AED\",\"showPrices\":false,\"showSku\":false,\"showBarcode\":false,\"showGiftNote\":true}");
            }
        }
    }

    private void clearDefault(String storeId, PdfTemplateType type) {
        templateRepository.findByStoreIdAndTypeOrderByCreatedAtDesc(storeId, type).stream()
                .filter(PdfTemplate::isDefaultTemplate)
                .forEach(t -> {
                    t.setDefaultTemplate(false);
                    templateRepository.save(t);
                });
    }

    private PdfTemplate require(String storeId, String publicTemplateId) {
        return templateRepository.findByStoreIdAndPublicTemplateId(storeId, publicTemplateId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Template not found"));
    }

    private PdfTemplateData toData(PdfTemplate t) {
        return new PdfTemplateData(
                t.getPublicTemplateId(),
                t.getName(),
                t.getType().apiValue(),
                t.isDefaultTemplate(),
                t.isSystemTemplate(),
                t.getLayoutConfig(),
                t.getHeaderHtml(),
                t.getFooterHtml(),
                t.getLogoUrl(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
