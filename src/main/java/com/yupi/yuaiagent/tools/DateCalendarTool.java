package com.yupi.yuaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 日期/纪念日计算工具：帮助用户管理恋爱中的重要日期。
 */
@Component
@Slf4j
public class DateCalendarTool {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 节日名称（硬编码）。
     * 说明：七夕为农历节日，公历日期每年不同；这里用近似日期并在结果中提示用户以当年公历为准。
     */
    private static final Map<MonthDay, String> FESTIVALS = Map.ofEntries(
            Map.entry(MonthDay.of(2, 14), "情人节"),
            Map.entry(MonthDay.of(3, 14), "白色情人节"),
            Map.entry(MonthDay.of(5, 20), "520"),
            Map.entry(MonthDay.of(8, 10), "七夕（农历节日，公历每年不同，此处为近似日期）"),
            Map.entry(MonthDay.of(12, 24), "平安夜"),
            Map.entry(MonthDay.of(12, 25), "圣诞节"),
            Map.entry(MonthDay.of(12, 31), "跨年夜"),
            Map.entry(MonthDay.of(1, 1), "元旦" )
    );

    private static final Map<MonthDay, String> FESTIVAL_TIPS = Map.ofEntries(
            Map.entry(MonthDay.of(2, 14), "一束花 + 手写卡片 + 提前订好餐厅/电影，仪式感最重要"),
            Map.entry(MonthDay.of(3, 14), "甜品/巧克力回礼，搭配一张‘想对你说的话’小纸条"),
            Map.entry(MonthDay.of(5, 20), "可以准备‘520’主题小礼物：香水/口红/情侣配饰，适合表白或重温承诺"),
            Map.entry(MonthDay.of(8, 10), "更适合中式浪漫：汉服拍照、江边夜景、星空露营或一份有故事的礼物"),
            Map.entry(MonthDay.of(12, 24), "围巾/手套等保暖礼物 + 热饮约会，适合逛街看灯光"),
            Map.entry(MonthDay.of(12, 25), "圣诞氛围约会：逛市集、拍照、交换礼物（建议选对方‘常用且想要’的）"),
            Map.entry(MonthDay.of(12, 31), "安排跨年倒计时：预留交通/排队时间，准备小烟花/拍立得留念"),
            Map.entry(MonthDay.of(1, 1), "新年第一天适合做‘今年的恋爱计划’，一起写愿望清单" )
    );

    private static final int[] MILESTONES = {100, 200, 365, 520, 1000};

    @Tool(description = "计算恋爱纪念日天数，提醒即将到来的重要日期节点")
    public String calculateAnniversary(
            @ToolParam(description = "纪念日日期，格式yyyy-MM-dd") String dateStr
    ) {
        long startMs = System.currentTimeMillis();
        log.info("[DateCalendarTool] calculateAnniversary start, dateStr={}", dateStr);

        try {
            LocalDate startDate = parseDate(dateStr);
            if (startDate == null) {
                log.warn("[DateCalendarTool] calculateAnniversary invalid dateStr={}", dateStr);
                return "请输入正确的纪念日日期，格式为 yyyy-MM-dd（例如：2023-08-13）。";
            }

            LocalDate today = LocalDate.now();
            long daysSince = ChronoUnit.DAYS.between(startDate, today);

            // 下一个周年纪念日（按月日计算）
            MonthDay md = MonthDay.from(startDate);
            LocalDate nextAnniversary = resolveMonthDayInYear(md, today.getYear());
            if (nextAnniversary.isBefore(today)) {
                nextAnniversary = resolveMonthDayInYear(md, today.getYear() + 1);
            }
            long daysToNextAnniversary = ChronoUnit.DAYS.between(today, nextAnniversary);

            StringBuilder sb = new StringBuilder();
            sb.append("纪念日：").append(startDate.format(DATE_FMT)).append("\n");
            if (daysSince >= 0) {
                sb.append("已经在一起：").append(daysSince).append(" 天\n");
            } else {
                sb.append("距离纪念日还有：").append(-daysSince).append(" 天（还未到达该日期）\n");
            }
            sb.append("下一个周年纪念日：").append(nextAnniversary.format(DATE_FMT))
                    .append("（还有 ").append(daysToNextAnniversary).append(" 天）\n");

            sb.append("\n重要天数节点：\n");
            for (int m : MILESTONES) {
                LocalDate milestoneDate = startDate.plusDays(m);
                long diff = ChronoUnit.DAYS.between(today, milestoneDate);
                if (diff > 0) {
                    sb.append("- ").append(m).append(" 天：").append(milestoneDate.format(DATE_FMT))
                            .append("（还有 ").append(diff).append(" 天）\n");
                } else if (diff == 0) {
                    sb.append("- ").append(m).append(" 天：今天就是 ").append(m).append(" 天！可以安排一个小庆祝～\n");
                } else {
                    sb.append("- ").append(m).append(" 天：").append(milestoneDate.format(DATE_FMT))
                            .append("（已过 ").append(-diff).append(" 天）\n");
                }
            }

            // 给出“下一个即将到来的节点”
            LocalDate nextMilestoneDate = null;
            int nextMilestone = -1;
            for (int m : MILESTONES) {
                LocalDate d = startDate.plusDays(m);
                if (!d.isBefore(today)) {
                    nextMilestoneDate = d;
                    nextMilestone = m;
                    break;
                }
            }
            if (nextMilestoneDate != null) {
                sb.append("\n最近一个即将到来的节点：").append(nextMilestone)
                        .append(" 天（").append(nextMilestoneDate.format(DATE_FMT)).append("）")
                        .append("，还有 ").append(ChronoUnit.DAYS.between(today, nextMilestoneDate)).append(" 天。\n");
            }

            sb.append("\n小建议：可以提前 7 天开始准备（订位/礼物/行程），避免临近手忙脚乱。\n");

            log.info("[DateCalendarTool] calculateAnniversary done, date={}, daysSince={}, nextAnniversary={}, daysToNextAnniversary={}, costMs={}",
                    startDate, daysSince, nextAnniversary, daysToNextAnniversary, System.currentTimeMillis() - startMs);
            return sb.toString();
        } catch (Exception e) {
            log.error("[DateCalendarTool] calculateAnniversary error, dateStr={}", dateStr, e);
            return "计算纪念日信息失败，请稍后再试。";
        }
    }

    @Tool(description = "查询即将到来的情侣相关节日和纪念日，提供送礼和庆祝建议")
    public String getUpcomingFestivals(
            @ToolParam(description = "查询未来多少天内的节日，默认30") int days
    ) {
        long startMs = System.currentTimeMillis();
        int window = days <= 0 ? 30 : Math.min(days, 366);
        log.info("[DateCalendarTool] getUpcomingFestivals start, days={}, window={}", days, window);

        try {
            LocalDate today = LocalDate.now();
            LocalDate end = today.plusDays(window);

            List<FestivalHit> hits = new ArrayList<>();
            for (Map.Entry<MonthDay, String> entry : FESTIVALS.entrySet()) {
                MonthDay md = entry.getKey();
                String name = entry.getValue();

                LocalDate dThisYear = resolveMonthDayInYear(md, today.getYear());
                LocalDate candidate = dThisYear.isBefore(today) ? resolveMonthDayInYear(md, today.getYear() + 1) : dThisYear;
                if (!candidate.isAfter(end)) {
                    long countdown = ChronoUnit.DAYS.between(today, candidate);
                    String tip = FESTIVAL_TIPS.getOrDefault(md, "准备一份贴合对方兴趣的小礼物，再配合一段走心表达。");
                    hits.add(new FestivalHit(name, candidate, countdown, tip));
                }
            }
            hits.sort(Comparator.comparingLong(FestivalHit::countdown));

            StringBuilder sb = new StringBuilder();
            sb.append("未来 ").append(window).append(" 天内的情侣相关节日：\n");
            if (hits.isEmpty()) {
                sb.append("- 暂无（你可以把窗口调大一些，比如 90 天）\n");
                log.info("[DateCalendarTool] getUpcomingFestivals done, hitCount=0, costMs={}", System.currentTimeMillis() - startMs);
                return sb.toString();
            }

            for (FestivalHit hit : hits) {
                sb.append("- ").append(hit.name()).append("：").append(hit.date().format(DATE_FMT))
                        .append("（倒计时 ").append(hit.countdown()).append(" 天）\n")
                        .append("  送礼/庆祝建议：").append(hit.tip()).append("\n");
            }

            sb.append("\n备注：‘七夕’为农历节日，公历日期每年不同，建议以当年日历/官方信息为准。\n");
            log.info("[DateCalendarTool] getUpcomingFestivals done, hitCount={}, costMs={}", hits.size(), System.currentTimeMillis() - startMs);
            return sb.toString();
        } catch (Exception e) {
            log.error("[DateCalendarTool] getUpcomingFestivals error, days={}", days, e);
            return "查询节日失败，请稍后再试。";
        }
    }

    @Tool(description = "根据指定日期分析是否适合约会，给出时间安排建议")
    public String suggestDateByDate(
            @ToolParam(description = "日期，格式yyyy-MM-dd") String dateStr
    ) {
        long startMs = System.currentTimeMillis();
        log.info("[DateCalendarTool] suggestDateByDate start, dateStr={}", dateStr);

        try {
            LocalDate date = parseDate(dateStr);
            if (date == null) {
                log.warn("[DateCalendarTool] suggestDateByDate invalid dateStr={}", dateStr);
                return "请输入正确的日期，格式为 yyyy-MM-dd（例如：2026-05-20）。";
            }

            DayOfWeek dow = date.getDayOfWeek();
            boolean weekend = (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY);
            MonthDay md = MonthDay.from(date);
            boolean isFestival = FESTIVALS.containsKey(md);

            StringBuilder sb = new StringBuilder();
            sb.append("日期：").append(date.format(DATE_FMT)).append("（").append(toChineseWeekday(dow)).append("）\n");
            if (isFestival) {
                sb.append("当天是：").append(FESTIVALS.get(md)).append("\n");
            }

            if (weekend) {
                sb.append("判断：周末更适合安排‘长时段’约会。\n");
                sb.append("建议：下午开始（14:00 左右）→ 逛街/展览/公园 → 晚餐 → 夜景散步/电影。\n");
            } else {
                sb.append("判断：工作日更适合安排‘轻量’约会。\n");
                sb.append("建议：下班后（19:00 左右）→ 一顿舒适的晚餐/咖啡 → 适度散步（30-60 分钟）→ 早些回家休息。\n");
            }

            if (isFestival) {
                sb.append("加成建议：节日当天建议提前订位，并准备一个‘可留存’的小礼物（拍立得/手写信/定制小物）。\n");
            }

            sb.append("提示：本工具仅识别部分情侣相关节日，不包含法定节假日调休信息；如需精确节假日，请结合当年放假安排。\n");

            log.info("[DateCalendarTool] suggestDateByDate done, date={}, weekend={}, isFestival={}, costMs={}",
                    date, weekend, isFestival, System.currentTimeMillis() - startMs);
            return sb.toString();
        } catch (Exception e) {
            log.error("[DateCalendarTool] suggestDateByDate error, dateStr={}", dateStr, e);
            return "分析约会日期失败，请稍后再试。";
        }
    }

    private static LocalDate parseDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate resolveMonthDayInYear(MonthDay md, int year) {
        try {
            return md.atYear(year);
        } catch (DateTimeException e) {
            // 处理 2/29 这种在非闰年无效的日期：回退到 2/28
            if (md.getMonthValue() == 2 && md.getDayOfMonth() == 29 && !Year.isLeap(year)) {
                return LocalDate.of(year, 2, 28);
            }
            // 兜底：使用当月最后一天
            LocalDate firstDay = LocalDate.of(year, md.getMonthValue(), 1);
            return firstDay.withDayOfMonth(firstDay.lengthOfMonth());
        }
    }

    private static String toChineseWeekday(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private record FestivalHit(String name, LocalDate date, long countdown, String tip) {
    }
}
