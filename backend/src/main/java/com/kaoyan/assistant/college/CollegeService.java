package com.kaoyan.assistant.college;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CollegeService {

    private final CollegeRepository collegeRepository;

    public CollegeService(CollegeRepository collegeRepository) {
        this.collegeRepository = collegeRepository;
    }

    public List<CollegeDto> list(Long schoolId) {
        return collegeRepository.findAll(schoolId);
    }

    public CollegeDto create(CollegeRequest request) {
        Long id = collegeRepository.create(request);
        return collegeRepository.findById(id);
    }

    public CollegeDto update(Long id, CollegeRequest request) {
        collegeRepository.update(id, request);
        return collegeRepository.findById(id);
    }

    public void delete(Long id) {
        collegeRepository.delete(id);
    }
}
