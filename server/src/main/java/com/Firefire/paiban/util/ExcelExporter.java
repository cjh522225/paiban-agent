package com.Firefire.paiban.util;

import com.Firefire.paiban.entity.DutySchedule;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExcelExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d");

    /**
     * 导出宿舍排班：当前周、所有宿舍楼。
     * 结构：A列宿舍楼名称(合并该楼多行)，B列日期，C列巡班，D列坐班，E列敲灯。
     * 列宽：A=7，B=7，C/D/E=30。
     */
    public byte[] exportDormitorySchedule(List<DutySchedule> schedules, String sheetName) throws IOException {
        Map<String, List<DutySchedule>> byDorm = schedules.stream()
            .collect(Collectors.groupingBy(DutySchedule::getLocationId, LinkedHashMap::new, Collectors.toList()));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);

            // 表头：A宿舍楼 B日期 C巡班 D坐班 E敲灯
            Row headerRow = sheet.createRow(0);
            String[] headers = {"宿舍楼", "日期", "巡班人员", "坐班人员", "敲灯人员"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Map.Entry<String, List<DutySchedule>> dormEntry : byDorm.entrySet()) {
                List<DutySchedule> dormSchedules = dormEntry.getValue();
                dormSchedules.sort(Comparator.comparing(DutySchedule::getDutyDate));
                Map<String, List<DutySchedule>> byDate = dormSchedules.stream()
                    .collect(Collectors.groupingBy(s -> s.getDutyDate().toString(), LinkedHashMap::new, Collectors.toList()));

                int firstRow = rowIdx;
                String dormName = dormSchedules.get(0).getLocationName();
                for (Map.Entry<String, List<DutySchedule>> dateEntry : byDate.entrySet()) {
                    Row row = sheet.createRow(rowIdx++);
                    List<DutySchedule> daySchedules = dateEntry.getValue();

                    String patrol = "";
                    String sitting = "";
                    List<String> knockLights = new ArrayList<>();
                    for (DutySchedule ds : daySchedules) {
                        String role = ds.getTimeSlot();
                        if (role != null && role.contains("巡")) patrol = ds.getUserName();
                        else if (role != null && role.contains("坐")) sitting = ds.getUserName();
                        else if (role != null && role.contains("敲")) knockLights.add(ds.getUserName());
                    }

                    row.createCell(1).setCellValue(DATE_FMT.format(daySchedules.get(0).getDutyDate()));
                    row.createCell(2).setCellValue(patrol);
                    row.createCell(3).setCellValue(sitting);
                    row.createCell(4).setCellValue(String.join("、", knockLights));
                    for (int i = 1; i < 5; i++) {
                        Cell cell = row.getCell(i);
                        if (cell != null) cell.setCellStyle(dataStyle);
                    }
                }

                // A列宿舍楼名称：该楼占一个合并单元格
                Row firstDataRow = sheet.getRow(firstRow);
                Cell dormCell = firstDataRow.getCell(0);
                if (dormCell == null) dormCell = firstDataRow.createCell(0);
                dormCell.setCellValue(dormName);
                dormCell.setCellStyle(dataStyle);
                if (rowIdx - 1 > firstRow) {
                    sheet.addMergedRegion(new CellRangeAddress(firstRow, rowIdx - 1, 0, 0));
                }
            }

            // 列宽：A=7 B=7 C/D/E=30（单位为字符）
            sheet.setColumnWidth(0, 7 * 256);
            sheet.setColumnWidth(1, 7 * 256);
            sheet.setColumnWidth(2, 30 * 256);
            sheet.setColumnWidth(3, 30 * 256);
            sheet.setColumnWidth(4, 30 * 256);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 导出办公室排班：所有办公室、当前周。
     * 结构：A列办公室名称(合并该办公室多行)，B列时间段，C~G列周一~周五，每格放4人名字。
     * 列宽：A=20，B=20，C~G=40。
     */
    public byte[] exportOfficeSchedule(List<DutySchedule> schedules, String sheetName, List<String> timeSlotOrder) throws IOException {
        String[] weekdays = {"周一", "周二", "周三", "周四", "周五"};
        Map<String, List<DutySchedule>> byOffice = schedules.stream()
            .collect(Collectors.groupingBy(DutySchedule::getLocationId, LinkedHashMap::new, Collectors.toList()));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);

            // 表头：A办公室 B时间段 C周一 D周二 E周三 F周四 G周五
            Row headerRow = sheet.createRow(0);
            String[] headers = {"办公室", "时间段", "周一", "周二", "周三", "周四", "周五"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Map.Entry<String, List<DutySchedule>> officeEntry : byOffice.entrySet()) {
                List<DutySchedule> officeSchedules = officeEntry.getValue();
                String officeName = officeSchedules.get(0).getLocationName();

                // grid: 时间段 -> 周一~周五 各一个名字列表
                Map<String, List<List<String>>> grid = new LinkedHashMap<>();
                for (String slot : timeSlotOrder) {
                    List<List<String>> days = new ArrayList<>();
                    for (int i = 0; i < 5; i++) days.add(new ArrayList<>());
                    grid.put(slot, days);
                }
                for (DutySchedule ds : officeSchedules) {
                    int dayOfWeek = ds.getDutyDate().getDayOfWeek().getValue();
                    if (dayOfWeek < 1 || dayOfWeek > 5) continue;
                    String slot = ds.getTimeSlot();
                    // 去掉空格统一匹配（数据库中可能是 "1-2节"，grid key 是 "1-2 节"）
                    for (Map.Entry<String, List<List<String>>> e : grid.entrySet()) {
                        if (e.getKey().replace(" ", "").equals(slot != null ? slot.replace(" ", "") : "")) {
                            e.getValue().get(dayOfWeek - 1).add(ds.getUserName());
                            break;
                        }
                    }
                }

                int firstRow = rowIdx;
                for (Map.Entry<String, List<List<String>>> slotEntry : grid.entrySet()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(1).setCellValue(slotEntry.getKey());
                    row.getCell(1).setCellStyle(dataStyle);
                    for (int i = 0; i < 5; i++) {
                        Cell cell = row.createCell(i + 2);
                        cell.setCellValue(String.join("、", slotEntry.getValue().get(i)));
                        cell.setCellStyle(dataStyle);
                    }
                }

                // A列办公室名称：该办公室占一个合并单元格
                Row firstDataRow = sheet.getRow(firstRow);
                Cell officeCell = firstDataRow.getCell(0);
                if (officeCell == null) officeCell = firstDataRow.createCell(0);
                officeCell.setCellValue(officeName);
                officeCell.setCellStyle(dataStyle);
                if (rowIdx - 1 > firstRow) {
                    sheet.addMergedRegion(new CellRangeAddress(firstRow, rowIdx - 1, 0, 0));
                }
            }

            // 列宽：A=20 B=20 C~G=40（单位为字符）
            sheet.setColumnWidth(0, 20 * 256);
            sheet.setColumnWidth(1, 20 * 256);
            for (int i = 2; i <= 6; i++) sheet.setColumnWidth(i, 40 * 256);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 导出值班统计：按人统计表。
     * type=dormitory 时列=姓名/巡班/坐班/敲灯/总次数；type=office 时列=姓名/值班次数。
     * counts 为 /statistics/duty-counts 接口返回的数据（每个元素含 userName、各岗位次数、totalCount）。
     * 列宽：第一列=12，其余列=12。
     */
    public byte[] exportDutyCounts(List<Map<String, Object>> counts, String type, String sheetName) throws IOException {
        boolean dormitory = "dormitory".equals(type);
        String[] headers = dormitory
            ? new String[]{"姓名", "巡班", "坐班", "敲灯", "总次数"}
            : new String[]{"姓名", "值班次数"};

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Map<String, Object> c : counts) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(str(c.get("userName")));
                if (dormitory) {
                    row.createCell(col++).setCellValue(num(c.get("巡班")));
                    row.createCell(col++).setCellValue(num(c.get("坐班")));
                    row.createCell(col++).setCellValue(num(c.get("敲灯")));
                }
                row.createCell(col).setCellValue(num(c.get("totalCount")));
                for (int i = 0; i <= col; i++) {
                    Cell cell = row.getCell(i);
                    if (cell != null) cell.setCellStyle(dataStyle);
                }
            }

            // 列宽：第一列=12，其余=12（单位为字符）
            for (int i = 0; i < headers.length; i++) sheet.setColumnWidth(i, 12 * 256);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * 导出历史明细：按楼分块，每块先一行楼名标题（加粗），下方为该楼表头+明细行，块间空一行。
     * type=dormitory 时列=日期/巡班/坐班/敲灯；type=office 时列=日期/各时段（timeSlots 传入顺序）。
     * history 为 /history/dormitory 或 /history/office 接口返回的数据结构
     * （宿舍：locationName + days[{date, patrol, sitting, knockLights}]；办公室：locationName + days[{date, slots}]）。
     * 列宽：第一列=14，其余=30。
     */
    public byte[] exportHistory(List<Map<String, Object>> history, String type, List<String> timeSlots, String sheetName) throws IOException {
        boolean dormitory = "dormitory".equals(type);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet(sheetName);
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dataStyle = createDataStyle(wb);
            CellStyle titleStyle = createHeaderStyle(wb); // 楼名标题行，复用表头加粗样式

            int rowIdx = 0;
            for (Map<String, Object> loc : history) {
                String locName = str(loc.get("locationName"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> days = (List<Map<String, Object>>) loc.get("days");

                // 楼名标题行
                Row titleRow = sheet.createRow(rowIdx++);
                titleRow.createCell(0).setCellValue(locName);
                titleRow.getCell(0).setCellStyle(titleStyle);

                // 表头行
                List<String> headers = new ArrayList<>();
                headers.add("日期");
                if (dormitory) {
                    headers.add("巡班");
                    headers.add("坐班");
                    headers.add("敲灯");
                } else {
                    headers.addAll(timeSlots);
                }
                Row headerRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers.get(i));
                    cell.setCellStyle(headerStyle);
                }

                // 明细行
                for (Map<String, Object> day : days) {
                    Row row = sheet.createRow(rowIdx++);
                    int col = 0;
                    row.createCell(col++).setCellValue(str(day.get("date")));
                    if (dormitory) {
                        row.createCell(col++).setCellValue(join(day.get("patrol")));
                        row.createCell(col++).setCellValue(join(day.get("sitting")));
                        row.createCell(col++).setCellValue(join(day.get("knockLights")));
                    } else {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> slots = (Map<String, Object>) day.get("slots");
                        for (String slot : timeSlots) {
                            row.createCell(col++).setCellValue(join(getSlotNames(slots, slot)));
                        }
                    }
                    for (int i = 0; i < col; i++) {
                        Cell cell = row.getCell(i);
                        if (cell != null) cell.setCellStyle(dataStyle);
                    }
                }

                // 块间空一行
                rowIdx++;
            }

            // 列宽：第一列=14，其余=30（单位为字符）
            sheet.setColumnWidth(0, 14 * 256);
            int numColumns = dormitory ? 4 : (1 + timeSlots.size());
            for (int i = 1; i < numColumns; i++) sheet.setColumnWidth(i, 30 * 256);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /** 空值安全转字符串 */
    private String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    /** 空值安全转数字（统计次数） */
    private double num(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 人名列表转顿号分隔字符串（空值/空列表返回空串） */
    private String join(Object v) {
        if (v == null) return "";
        if (v instanceof List<?>) {
            return ((List<?>) v).stream().map(String::valueOf).collect(Collectors.joining("、"));
        }
        return String.valueOf(v);
    }

    /** 取时段对应人员列表，兼容时段名带/不带空格两种写法（如 "1-2节" 与 "1-2 节"） */
    private Object getSlotNames(Map<String, Object> slots, String slot) {
        if (slots == null) return null;
        Object v = slots.get(slot);
        if (v != null) return v;
        String compact = slot.replace(" ", "");
        for (Map.Entry<String, Object> e : slots.entrySet()) {
            if (e.getKey() != null && e.getKey().replace(" ", "").equals(compact)) return e.getValue();
        }
        return null;
    }
}
