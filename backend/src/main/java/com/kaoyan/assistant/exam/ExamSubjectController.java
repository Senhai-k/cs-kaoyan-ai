package com.kaoyan.assistant.exam;

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
@RequestMapping("/api/exam-subjects")
public class ExamSubjectController {

    private final ExamSubjectService service;

    public ExamSubjectController(ExamSubjectService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ExamSubjectDto>> list(@RequestParam(required = false) Long majorId) {
        return ApiResponse.success(service.list(majorId));
    }

    @PostMapping
    public ApiResponse<ExamSubjectDto> create(@Valid @RequestBody ExamSubjectRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExamSubjectDto> update(@PathVariable Long id, @Valid @RequestBody ExamSubjectRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
