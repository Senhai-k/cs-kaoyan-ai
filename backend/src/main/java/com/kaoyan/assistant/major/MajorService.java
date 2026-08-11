package com.kaoyan.assistant.major;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MajorService {

    private final MajorRepository majorRepository;

    public MajorService(MajorRepository majorRepository) {
        this.majorRepository = majorRepository;
    }

    public List<MajorDto> list(Long schoolId, Long collegeId) {
        return majorRepository.findAll(schoolId, collegeId);
    }

    public MajorDto create(MajorRequest request) {
        Long id = majorRepository.create(request);
        return majorRepository.findById(id);
    }

    public MajorDto update(Long id, MajorRequest request) {
        majorRepository.update(id, request);
        return majorRepository.findById(id);
    }

    public void delete(Long id) {
        majorRepository.delete(id);
    }
}
