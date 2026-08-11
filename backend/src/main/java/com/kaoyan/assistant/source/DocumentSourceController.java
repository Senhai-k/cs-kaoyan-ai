package com.kaoyan.assistant.source;

import com.kaoyan.assistant.auth.AdminAuthService;
import com.kaoyan.assistant.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class DocumentSourceController {

    private final DocumentSourceService service;
    private final AdminAuthService authService;

    public DocumentSourceController(DocumentSourceService service, AdminAuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<DocumentSourceDto>> list(@RequestParam(required = false) Long schoolId,
                                                     @RequestParam(required = false) String auditStatus,
                                                     HttpServletRequest request) {
        return ApiResponse.success(service.list(schoolId, resolveAuditStatusFilter(auditStatus, request)));
    }

    @PostMapping
    public ApiResponse<DocumentSourceDto> create(@Valid @RequestBody DocumentSourceRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<DocumentSourceDto> update(@PathVariable Long id, @Valid @RequestBody DocumentSourceRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    private String resolveAuditStatusFilter(String auditStatus, HttpServletRequest request) {
        if ("ALL".equalsIgnoreCase(auditStatus) && !isAdminRequest(request)) {
            return null;
        }
        return auditStatus;
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        return authService.isValid(authorization.substring("Bearer ".length()));
    }
}
