package com.kaoyan.assistant.school;

import com.kaoyan.assistant.common.ApiResponse;
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
@RequestMapping("/api/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping
    public ApiResponse<List<SchoolSummary>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Boolean is408,
                                                 @RequestParam(required = false) String province,
                                                 @RequestParam(required = false) String schoolLevel,
                                                 @RequestParam(required = false) String degreeType,
                                                 @RequestParam(required = false) Integer minQuota,
                                                 @RequestParam(required = false) Integer maxQuota,
                                                 @RequestParam(required = false) Integer minScore,
                                                 @RequestParam(required = false) Integer maxScore,
                                                 @RequestParam(required = false) String professionalKeyword) {
        return ApiResponse.success(schoolService.list(
                keyword, is408, province, schoolLevel, degreeType,
                minQuota, maxQuota, minScore, maxScore, professionalKeyword
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<SchoolDetail> detail(@PathVariable Long id) {
        SchoolDetail school = schoolService.fullDetail(id);
        if (school == null) {
            return ApiResponse.failure(404, "school not found");
        }
        return ApiResponse.success(school);
    }

    @PostMapping
    public ApiResponse<SchoolSummary> create(@Valid @RequestBody CreateSchoolRequest request) {
        return ApiResponse.success(schoolService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SchoolSummary> update(@PathVariable Long id, @Valid @RequestBody CreateSchoolRequest request) {
        return ApiResponse.success(schoolService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        schoolService.delete(id);
        return ApiResponse.success(null);
    }
}
