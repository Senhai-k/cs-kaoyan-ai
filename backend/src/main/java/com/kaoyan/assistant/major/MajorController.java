package com.kaoyan.assistant.major;

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
@RequestMapping("/api/majors")
public class MajorController {

    private final MajorService majorService;

    public MajorController(MajorService majorService) {
        this.majorService = majorService;
    }

    @GetMapping
    public ApiResponse<List<MajorDto>> list(@RequestParam(required = false) Long schoolId,
                                            @RequestParam(required = false) Long collegeId) {
        return ApiResponse.success(majorService.list(schoolId, collegeId));
    }

    @PostMapping
    public ApiResponse<MajorDto> create(@Valid @RequestBody MajorRequest request) {
        return ApiResponse.success(majorService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MajorDto> update(@PathVariable Long id, @Valid @RequestBody MajorRequest request) {
        return ApiResponse.success(majorService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        majorService.delete(id);
        return ApiResponse.success(null);
    }
}
