package com.kaoyan.assistant.compare;

import java.util.List;

public record CompareResult(List<CompareSchoolItem> schools, List<String> riskTips) {
}
