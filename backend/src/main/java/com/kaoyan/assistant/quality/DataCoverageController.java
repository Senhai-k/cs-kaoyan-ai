package com.kaoyan.assistant.quality;

import com.kaoyan.assistant.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.kaoyan.assistant.auth.AdminAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@RestController
@RequestMapping("/api/data-coverage")
public class DataCoverageController {

    private final DataCoverageService dataCoverageService;
    private final AdminAuthService authService;

    public DataCoverageController(DataCoverageService dataCoverageService, AdminAuthService authService) {
        this.dataCoverageService = dataCoverageService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<DataCoverageReport> report() {
        return ApiResponse.success(dataCoverageService.report());
    }

    @GetMapping("/tasks")
    public ApiResponse<List<DataCollectionTask>> tasks(@RequestParam(defaultValue = "20") int limit,
                                                       @RequestParam(defaultValue = "ACTIVE") String status) {
        return ApiResponse.success(dataCoverageService.collectionTasks(limit, status));
    }

    @PutMapping("/tasks/{schoolId}")
    public ApiResponse<DataCollectionTask> updateTask(@PathVariable Long schoolId,
                                                      @Valid @RequestBody DataCollectionTaskUpdateRequest request,
                                                      HttpServletRequest httpRequest) {
        return ApiResponse.success(dataCoverageService.updateTask(schoolId, request, operator(httpRequest)));
    }

    @PostMapping("/tasks/{schoolId}/targets")
    public ApiResponse<DataCollectionTarget> createTarget(@PathVariable Long schoolId,
                                                          @Valid @RequestBody DataCollectionTargetRequest request,
                                                          HttpServletRequest httpRequest) {
        return ApiResponse.success(dataCoverageService.createTarget(schoolId, request, operator(httpRequest)));
    }

    @PutMapping("/tasks/{schoolId}/targets/{targetId}")
    public ApiResponse<DataCollectionTarget> updateTarget(@PathVariable Long schoolId,
                                                          @PathVariable Long targetId,
                                                          @Valid @RequestBody DataCollectionTargetRequest request,
                                                          HttpServletRequest httpRequest) {
        return ApiResponse.success(dataCoverageService.updateTarget(
                schoolId, targetId, request, operator(httpRequest)
        ));
    }

    @DeleteMapping("/tasks/{schoolId}/targets/{targetId}")
    public ApiResponse<Void> deleteTarget(@PathVariable Long schoolId,
                                          @PathVariable Long targetId,
                                          HttpServletRequest httpRequest) {
        dataCoverageService.deleteTarget(schoolId, targetId, operator(httpRequest));
        return ApiResponse.success(null);
    }

    @PostMapping("/tasks/{schoolId}/targets/{targetId}/discover-links")
    public ApiResponse<List<OfficialLinkCandidate>> discoverLinks(@PathVariable Long schoolId,
                                                                  @PathVariable Long targetId) {
        return ApiResponse.success(dataCoverageService.discoverOfficialLinks(schoolId, targetId));
    }

    @PostMapping("/tasks/{schoolId}/targets/{targetId}/accept-link")
    public ApiResponse<DataCollectionTarget> acceptLink(@PathVariable Long schoolId,
                                                        @PathVariable Long targetId,
                                                        @Valid @RequestBody OfficialLinkCandidateAcceptRequest request,
                                                        HttpServletRequest httpRequest) {
        return ApiResponse.success(dataCoverageService.acceptOfficialLink(
                schoolId, targetId, request, operator(httpRequest)
        ));
    }

    private String operator(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "system";
        }
        String username = authService.usernameFor(authorization.substring("Bearer ".length()));
        return username == null ? "system" : username;
    }
}
