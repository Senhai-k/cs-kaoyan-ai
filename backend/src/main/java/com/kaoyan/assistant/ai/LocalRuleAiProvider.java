package com.kaoyan.assistant.ai;

import com.kaoyan.assistant.school.AdjustmentInfoView;
import com.kaoyan.assistant.school.AdmissionResultInfo;
import com.kaoyan.assistant.school.ReferenceBookInfo;
import com.kaoyan.assistant.school.RetestRuleInfo;
import com.kaoyan.assistant.school.SchoolDetail;
import com.kaoyan.assistant.school.SchoolService;
import com.kaoyan.assistant.school.SchoolSummary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalRuleAiProvider implements AiProvider {

    private final SchoolService schoolService;

    public LocalRuleAiProvider(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @Override
    public AiProviderResult answer(String question) {
        List<SchoolSummary> schools = schoolService.list(null, null, null, null, null, null, null, null, null, null);
        Optional<SchoolSummary> matchedSchool = schools.stream()
                .filter(school -> question.contains(school.name()))
                .findFirst();

        String answer = matchedSchool
                .map(school -> answerForSchool(question, school))
                .orElseGet(() -> answerGeneral(question));
        Long relatedSchoolId = matchedSchool.map(SchoolSummary::id).orElse(null);
        String sourceSummary = matchedSchool
                .map(school -> "系统院校库：" + school.name())
                .orElse("系统院校库：通用规则回答");

        return new AiProviderResult(
                answer,
                relatedSchoolId,
                sourceSummary,
                List.of(sourceSummary, "当前为本地规则版 AI，结论需以官方公告为准。"),
                new AiExecutionMeta("local-rule", null, "COMPLETED", 0.35, "local_rule", 0, List.of())
        );
    }

    private String answerForSchool(String question, SchoolSummary school) {
        SchoolDetail detail = schoolService.fullDetail(school.id());
        if (detail == null) {
            return answerFromSummary(school);
        }
        if (question.contains("参考书") || question.contains("书目")) {
            return answerBooks(detail);
        }
        if (question.contains("复试") || question.contains("差额") || question.contains("权重")) {
            return answerRetest(detail);
        }
        if (question.contains("调剂")) {
            return answerAdjustment(detail);
        }
        if (question.contains("录取") || question.contains("最低分") || question.contains("平均分")) {
            return answerAdmission(detail);
        }
        if (question.contains("风险") || question.contains("难度") || question.contains("分析")) {
            return answerRisk(detail);
        }
        return answerFromDetail(detail);
    }

    private String answerRisk(SchoolDetail detail) {
        SchoolSummary school = detail.summary();
        AdmissionResultInfo latestResult = latestAdmissionResult(detail);
        RetestRuleInfo latestRetest = latestRetestRule(detail);
        boolean hasOpenAdjustment = detail.adjustmentInfos().stream().anyMatch(AdjustmentInfoView::open);
        int bookCount = detail.referenceBooks().size();
        StringBuilder risk = new StringBuilder();
        risk.append(school.name())
                .append(" 当前专业课为 ").append(displayText(school.primarySubject()))
                .append("，最近招生人数为 ").append(display(school.latestQuota()))
                .append("，最近复试线为 ").append(display(school.latestScoreLine())).append("。");

        if (school.primarySubject() == null && school.latestQuota() == null && school.latestScoreLine() == null) {
            risk.append(" 当前缺少已核验的专业级科目、招生人数和复试线，系统不做确定性风险等级判断。");
        }

        if (latestResult != null) {
            risk.append(" 最近录取最低分为 ").append(display(latestResult.lowestScore()))
                    .append("，平均分为 ").append(displayDecimal(latestResult.averageScore())).append("。");
        }
        if (latestRetest != null) {
            risk.append(" 最近复试差额比例约 ")
                    .append(displayDecimal(latestRetest.retestRatio()))
                    .append("，初试/复试权重为 ")
                    .append(display(latestRetest.initialScoreWeight()))
                    .append("/")
                    .append(display(latestRetest.retestScoreWeight()))
                    .append("。");
        }
        if (school.latestScoreLine() != null && school.latestScoreLine() >= 350) {
            risk.append(" 复试线偏高，竞争强度较大。");
        }
        if (latestRetest != null && latestRetest.retestRatio() != null && latestRetest.retestRatio() >= 1.5) {
            risk.append(" 复试差额偏大，需要重点防范复试淘汰风险。");
        }
        if (!hasOpenAdjustment) {
            risk.append(" 当前未显示开放中的调剂信息，兜底空间需要单独核实。");
        }
        if (bookCount >= 4) {
            risk.append(" 参考书目较多，专业课复习负担相对更重。");
        }
        risk.append(" 建议继续核查近三年拟录取名单、复试细则和学院官网最新公告。");
        return risk.toString();
    }

    private String answerAdmission(SchoolDetail detail) {
        SchoolSummary school = detail.summary();
        AdmissionResultInfo latestResult = latestAdmissionResult(detail);
        if (latestResult == null) {
            return school.name() + " 当前尚未录入历年录取结果，建议补充拟录取名单或复试结果公示后再做分数判断。";
        }
        return "%s 最近录取结果为：年份 %s，录取人数 %s，最低分 %s，平均分 %s，最高分 %s，复试差额比例 %s。"
                .formatted(
                        school.name(),
                        display(latestResult.year()),
                        display(latestResult.admittedCount()),
                        display(latestResult.lowestScore()),
                        displayDecimal(latestResult.averageScore()),
                        display(latestResult.highestScore()),
                        displayDecimal(latestResult.retestRatio())
                );
    }

    private String answerRetest(SchoolDetail detail) {
        SchoolSummary school = detail.summary();
        RetestRuleInfo latestRule = latestRetestRule(detail);
        if (latestRule == null) {
            return school.name() + " 当前尚未录入复试细则，建议优先核查学院官网的复试录取工作办法。";
        }
        return "%s 最近复试细则为：年份 %s，时间 %s，方式 %s，差额比例 %s，初试/复试权重 %s/%s，资格线 %s，材料要求 %s。"
                .formatted(
                        school.name(),
                        display(latestRule.year()),
                        displayText(latestRule.retestTime()),
                        displayText(latestRule.retestMethod()),
                        displayDecimal(latestRule.retestRatio()),
                        display(latestRule.initialScoreWeight()),
                        display(latestRule.retestScoreWeight()),
                        displayText(latestRule.qualificationLine()),
                        displayText(latestRule.materials())
                );
    }

    private String answerBooks(SchoolDetail detail) {
        SchoolSummary school = detail.summary();
        List<ReferenceBookInfo> books = detail.referenceBooks().stream()
                .sorted(Comparator.comparing(ReferenceBookInfo::year, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .toList();
        if (books.isEmpty()) {
            return school.name() + " 当前尚未录入参考书目，建议核查招生简章、考试大纲或学院自命题公告。";
        }
        String bookSummary = books.stream()
                .map(this::formatBook)
                .reduce((left, right) -> left + "；" + right)
                .orElse("暂无数据");
        return school.name() + " 当前可参考的专业课书目包括：" + bookSummary + "。最终以目标学院当年公告为准。";
    }

    private String answerAdjustment(SchoolDetail detail) {
        SchoolSummary school = detail.summary();
        List<AdjustmentInfoView> adjustmentInfos = detail.adjustmentInfos().stream()
                .sorted(Comparator.comparing(AdjustmentInfoView::year, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (adjustmentInfos.isEmpty()) {
            return school.name() + " 当前尚未录入调剂信息，不能据此判断是否存在调剂机会。";
        }
        AdjustmentInfoView latest = adjustmentInfos.get(0);
        String status = latest.open() ? "开放中" : "未开放";
        return "%s 最近调剂信息为：年份 %s，状态 %s，标题 %s，缺额人数 %s，申请窗口 %s，基本条件 %s。"
                .formatted(
                        school.name(),
                        display(latest.year()),
                        status,
                        displayText(latest.title()),
                        display(latest.vacancyCount()),
                        displayText(latest.applicationWindow()),
                        displayText(latest.requirements())
                );
    }

    private String answerFromDetail(SchoolDetail detail) {
        SchoolSummary school = detail.summary();
        return "%s 已收录在系统院校库中，地区为 %s%s，学校层次为 %s，专业课为 %s，最近招生人数为 %s，最近复试线为 %s。"
                .formatted(
                        school.name(),
                        school.province(),
                        school.city(),
                        school.schoolLevel(),
                        school.primarySubject(),
                        display(school.latestQuota()),
                        display(school.latestScoreLine())
                );
    }

    private String answerFromSummary(SchoolSummary school) {
        return "%s 已收录在系统院校库中，地区为 %s%s，学校层次为 %s，专业课为 %s，最近招生人数为 %s，最近复试线为 %s。"
                .formatted(
                        school.name(),
                        school.province(),
                        school.city(),
                        school.schoolLevel(),
                        school.primarySubject(),
                        display(school.latestQuota()),
                        display(school.latestScoreLine())
                );
    }

    private String answerGeneral(String question) {
        if (question.contains("408")) {
            return "408 的优势是考试范围统一、资料体系成熟，适合同时比较多所统考院校；自命题需要单独研究目标院校真题、参考书和命题风格。择校时应结合招生人数、复试线、专业课稳定性和自身复习进度判断。";
        }
        if (question.contains("择校")) {
            return "择校建议先按地区、考试科目、招生人数和复试线筛出候选院校，再分成冲、稳、保三个梯度。当前系统已支持院校筛选和对比，后续可以接入更完整的个性化推荐。";
        }
        return "当前问题暂未匹配到具体院校。我可以基于已收录的院校、考试科目、招生人数和复试线做基础说明；涉及最终报考结论时，请以学校研究生院和学院官网公告为准。";
    }

    private AdmissionResultInfo latestAdmissionResult(SchoolDetail detail) {
        return detail.admissionResults().stream()
                .max(Comparator.comparing(AdmissionResultInfo::year, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private RetestRuleInfo latestRetestRule(SchoolDetail detail) {
        return detail.retestRules().stream()
                .max(Comparator.comparing(RetestRuleInfo::year, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private String display(Integer value) {
        return value == null ? "暂无数据" : String.valueOf(value);
    }

    private String displayDecimal(Double value) {
        return value == null ? "暂无数据" : String.format("%.2f", value);
    }

    private String displayText(String value) {
        return value == null || value.isBlank() ? "暂无数据" : value;
    }

    private String formatBook(ReferenceBookInfo book) {
        StringBuilder builder = new StringBuilder();
        builder.append(displayText(book.subjectName()))
                .append("《")
                .append(displayText(book.bookTitle()))
                .append("》");
        if (book.author() != null && !book.author().isBlank()) {
            builder.append(" / ").append(book.author());
        }
        if (book.edition() != null && !book.edition().isBlank()) {
            builder.append(" / ").append(book.edition());
        }
        return builder.toString();
    }
}
