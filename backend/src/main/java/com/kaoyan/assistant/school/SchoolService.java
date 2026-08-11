package com.kaoyan.assistant.school;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SchoolService {

    private final SchoolRepository schoolRepository;

    public SchoolService(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    public List<SchoolSummary> list(String keyword, Boolean is408, String province,
                                    String schoolLevel, String degreeType, Integer minQuota,
                                    Integer maxQuota, Integer minScore, Integer maxScore,
                                    String professionalKeyword) {
        return schoolRepository.findSummaries(
                keyword, is408, province, schoolLevel, degreeType,
                minQuota, maxQuota, minScore, maxScore, professionalKeyword
        );
    }

    public SchoolSummary detail(Long id) {
        return schoolRepository.findSummaryById(id);
    }

    public SchoolDetail fullDetail(Long id) {
        return schoolRepository.findDetailById(id);
    }

    public SchoolSummary create(CreateSchoolRequest request) {
        Long id = schoolRepository.create(request);
        return schoolRepository.findSummaryById(id);
    }

    public SchoolSummary update(Long id, CreateSchoolRequest request) {
        schoolRepository.update(id, request);
        return schoolRepository.findSummaryById(id);
    }

    public void delete(Long id) {
        schoolRepository.delete(id);
    }
}
