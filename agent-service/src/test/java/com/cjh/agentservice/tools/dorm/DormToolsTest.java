package com.cjh.agentservice.tools.dorm;

import com.cjh.agentservice.business.BusinessApiException;
import com.cjh.agentservice.demo.DemoDormClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DormToolsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DormTools tools = new DormTools(new DemoDormClient(mapper), "demo-token", mapper);

    @Test
    void exposesExactlyEightReadOnlyTools() {
        long toolCount = Arrays.stream(DormTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .count();
        assertThat(toolCount).isEqualTo(8);
    }

    @Test
    void dormOverviewReturnsStatistics() {
        String result = tools.dormOverview();
        assertThat(result).contains("\"buildingCount\":3").contains("\"totalBedCount\":12")
                .contains("\"freeBedCount\":6").contains("\"floorCount\":4").contains("occupancyRate");
        assertNoSensitive(result);
    }

    @Test
    void availableBedsFiltersByBuildingAndExcludesRepairRooms() {
        String result = tools.availableBeds("演示1号楼", null, null, null);
        assertThat(result).contains("演示1号楼").contains("\"roomNumber\":\"101\"").contains("\"roomNumber\":\"102\"");
        assertThat(result).doesNotContain("演示2号楼").doesNotContain("演示3号楼").doesNotContain("维修");
        assertNoSensitive(result);
    }

    @Test
    void availableBedsSupportsAliasAndFloorFilter() {
        String result = tools.availableBeds("演示南区", "1楼", null, null);
        assertThat(result).contains("演示3号楼").contains("\"roomNumber\":\"101\"").contains("\"availableBeds\":2");
        assertThatThrownBy(() -> tools.availableBeds("演示3号楼", "9楼", null, null))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("没有找到楼层");
    }

    @Test
    void availableBedsRequiresBuildingWhenFloorGiven() {
        assertThatThrownBy(() -> tools.availableBeds(null, "2楼", null, null))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("请同时提供楼栋名称");
    }

    @Test
    void studentDormResolvesByNameAndCode() {
        String byName = tools.studentDorm("演示学生甲");
        assertThat(byName).contains("\"studentCode\":\"20260001\"").contains("演示1号楼")
                .contains("\"roomNumber\":\"101\"").contains("\"bedNumber\":\"1\"").contains("在宿");
        String byCode = tools.studentDorm("20260005");
        assertThat(byCode).contains("演示学生戊").contains("演示2号楼").contains("\"bedNumber\":\"2\"");
        assertNoSensitive(byName + byCode);
    }

    @Test
    void studentDormMarksNotLodgingStudent() {
        String result = tools.studentDorm("演示学生庚");
        assertThat(result).contains("未住宿");
    }

    @Test
    void roomOccupancyReturnsOccupantsAndFreeBedNumbers() {
        String result = tools.roomOccupancy("演示1号楼", "101");
        assertThat(result).contains("演示学生甲").contains("演示学生乙").contains("演示学生丙")
                .contains("\"availableBeds\":1").contains("\"availableBedNumbers\":[\"4\"]")
                .contains("\"bedCount\":4");
        assertNoSensitive(result);
    }

    @Test
    void counselorScopeGroupsBuildingsAndFiltersByName() {
        String all = tools.counselorScope(null);
        assertThat(all).contains("演示辅导员A").contains("演示辅导员B").contains("演示1号楼")
                .contains("演示2号楼").contains("\"totalStudents\":4").contains("\"totalStudents\":2");
        String one = tools.counselorScope("演示辅导员B");
        assertThat(one).contains("演示辅导员B").doesNotContain("演示辅导员A");
        assertNoSensitive(all + one);
    }

    @Test
    void counselorScopeUnknownNameFails() {
        assertThatThrownBy(() -> tools.counselorScope("不存在的辅导员"))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("没有找到辅导员");
    }

    @Test
    void violationStatsAggregatesAndFilters() {
        String all = tools.violationStats(null, null, null);
        assertThat(all).contains("\"total\":3").contains("晚归").contains("使用违规电器");
        String byStudent = tools.violationStats("演示学生乙", null, null);
        assertThat(byStudent).contains("\"total\":2");
        String byBuilding = tools.violationStats(null, "演示2号楼", null);
        assertThat(byBuilding).contains("\"total\":1").contains("演示学生丁");
        assertNoSensitive(all);
    }

    @Test
    void buildingListResolvesByAliasAndIncludesRooms() {
        String all = tools.buildingList(null, null);
        assertThat(all).contains("\"buildingCount\":3").contains("演示1号楼").contains("演示3号楼")
                .contains("\"floorCount\":2").contains("\"roomNumber\":\"101\"");
        String alias = tools.buildingList("演示南区", false);
        assertThat(alias).contains("演示3号楼").doesNotContain("\"rooms\"");
        assertNoSensitive(all + alias);
    }

    @Test
    void unknownBuildingFails() {
        assertThatThrownBy(() -> tools.availableBeds("不存在的楼", null, null, null))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("没有找到楼栋");
        assertThatThrownBy(() -> tools.roomOccupancy("不存在的楼", "101"))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("没有找到楼栋");
        assertThatThrownBy(() -> tools.buildingList("不存在的楼", null))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("没有找到楼栋");
    }

    @Test
    void roomOccupancyUnknownRoomFails() {
        assertThatThrownBy(() -> tools.roomOccupancy("演示1号楼", "999"))
                .isInstanceOf(BusinessApiException.class)
                .hasMessageContaining("没有找到房间");
    }

    @Test
    void userLookupReturnsStudentsWithoutSensitiveFields() {
        String result = tools.userLookup("演示学生");
        assertThat(result).contains("\"count\":7").contains("演示学生甲").contains("20260001")
                .contains("演示计算机1班").contains("在宿");
        JsonNode node;
        try {
            node = mapper.readTree(result);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(node.path("data").path("students").isArray()).isTrue();
        assertNoSensitive(result);
    }

    @Test
    void everyToolReturnsParseableJson() {
        List<String> outputs = List.of(
                tools.dormOverview(),
                tools.availableBeds(null, null, null, null),
                tools.studentDorm("演示学生丁"),
                tools.roomOccupancy("演示2号楼", "101"),
                tools.counselorScope(null),
                tools.violationStats(null, null, null),
                tools.buildingList(null, null),
                tools.userLookup("20260007"));
        for (String output : outputs) {
            try {
                assertThat(mapper.readTree(output).path("source").asText()).isNotBlank();
            } catch (Exception e) {
                throw new AssertionError("工具输出不是合法 JSON：" + output, e);
            }
            assertNoSensitive(output);
        }
    }

    private void assertNoSensitive(String result) {
        assertThat(result).doesNotContain("password")
                .doesNotContain("phone")
                .doesNotContain("1380000")
                .doesNotContain("1390000")
                .doesNotContain("photoUrl")
                .doesNotContain("demo-password");
    }
}
