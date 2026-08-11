package com.kaoyan.assistant.college;

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
@RequestMapping("/api/colleges")
public class CollegeController {

    private final CollegeService collegeService;

    public CollegeController(CollegeService collegeService) {
        this.collegeService = collegeService;
    }

    @GetMapping
    public ApiResponse<List<CollegeDto>> list(@RequestParam(required = false) Long schoolId) {
        return ApiResponse.success(collegeService.list(schoolId));
    }

    @PostMapping
    public ApiResponse<CollegeDto> create(@Valid @RequestBody CollegeRequest request) {
        return ApiResponse.success(collegeService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<CollegeDto> update(@PathVariable Long id, @Valid @RequestBody CollegeRequest request) {
        return ApiResponse.success(collegeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        collegeService.delete(id);
        return ApiResponse.success(null);
    }
}
