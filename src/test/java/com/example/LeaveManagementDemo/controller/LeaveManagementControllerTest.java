package com.example.LeaveManagementDemo.controller;

import com.example.LeaveManagementDemo.dto.LeaveRequest;
import com.example.LeaveManagementDemo.dto.LeaveResponse;
import com.example.LeaveManagementDemo.exception.GlobalExceptionHandler;
import com.example.LeaveManagementDemo.exception.InvalidLeaveDateException;
import com.example.LeaveManagementDemo.exception.LeaveNotFoundException;
import com.example.LeaveManagementDemo.model.LeaveStatus;
import com.example.LeaveManagementDemo.model.LeaveType;
import com.example.LeaveManagementDemo.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class LeaveManagementControllerTest {

    private MockMvc mockMvc;

    @Mock
    private LeaveService leaveService;

    @InjectMocks
    private LeaveManagementController leaveManagementController;

    private LeaveResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(leaveManagementController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleResponse = LeaveResponse.builder()
                .id(1L)
                .employeeId(101L)
                .leaveType(LeaveType.PAID_LEAVE)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 5))
                .reason("Attending family wedding")
                .status(LeaveStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("GET /leaves - Returns 200 OK with list of leaves")
    void testGetAllLeaves_ReturnsOk() throws Exception {
        LeaveResponse secondResponse = LeaveResponse.builder()
                .id(2L)
                .employeeId(102L)
                .leaveType(LeaveType.SICK_LEAVE)
                .startDate(LocalDate.of(2026, 10, 10))
                .endDate(LocalDate.of(2026, 10, 12))
                .reason("Medical illness")
                .status(LeaveStatus.APPROVED)
                .build();

        when(leaveService.getAllLeaves()).thenReturn(Arrays.asList(sampleResponse, secondResponse));

        mockMvc.perform(get("/leaves")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].employeeId", is(101)))
                .andExpect(jsonPath("$[0].leaveType", is("PAID_LEAVE")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].leaveType", is("SICK_LEAVE")));

        verify(leaveService, times(1)).getAllLeaves();
    }

    @Test
    @DisplayName("GET /leaves - Returns 200 OK with empty list when no leaves exist")
    void testGetAllLeaves_Empty_ReturnsOk() throws Exception {
        when(leaveService.getAllLeaves()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/leaves")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(leaveService, times(1)).getAllLeaves();
    }

    @Test
    @DisplayName("GET /leaves/{id} - Returns 200 OK with leave response when found")
    void testGetLeaveById_Success() throws Exception {
        when(leaveService.getLeaveById(1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/leaves/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.employeeId", is(101)))
                .andExpect(jsonPath("$.leaveType", is("PAID_LEAVE")))
                .andExpect(jsonPath("$.reason", is("Attending family wedding")))
                .andExpect(jsonPath("$.status", is("PENDING")));

        verify(leaveService, times(1)).getLeaveById(1L);
    }

    @Test
    @DisplayName("GET /leaves/{id} - Returns 404 NOT FOUND when ID does not exist")
    void testGetLeaveById_NotFound() throws Exception {
        when(leaveService.getLeaveById(999L)).thenThrow(new LeaveNotFoundException("Leave request not found with ID: 999"));

        mockMvc.perform(get("/leaves/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("Leave request not found with ID: 999")));

        verify(leaveService, times(1)).getLeaveById(999L);
    }

    @Test
    @DisplayName("POST /leaves - Returns 201 CREATED with created leave response")
    void testCreateLeave_Success() throws Exception {
        when(leaveService.applyLeave(any(LeaveRequest.class))).thenReturn(sampleResponse);

        String jsonPayload = """
                {
                    "employeeId": 101,
                    "leaveType": "PAID_LEAVE",
                    "startDate": "2026-10-01",
                    "endDate": "2026-10-05",
                    "reason": "Attending family wedding"
                }
                """;

        mockMvc.perform(post("/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.employeeId", is(101)))
                .andExpect(jsonPath("$.leaveType", is("PAID_LEAVE")))
                .andExpect(jsonPath("$.status", is("PENDING")));

        verify(leaveService, times(1)).applyLeave(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("POST /leaves - Returns 400 BAD REQUEST when request validation fails")
    void testCreateLeave_ValidationFailure() throws Exception {
        String invalidJson = """
                {
                    "employeeId": null,
                    "leaveType": null,
                    "startDate": null,
                    "endDate": null,
                    "reason": ""
                }
                """;

        mockMvc.perform(post("/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.validationErrors", notNullValue()));

        verify(leaveService, never()).applyLeave(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("POST /leaves - Returns 400 BAD REQUEST when dates are invalid")
    void testCreateLeave_InvalidDates() throws Exception {
        when(leaveService.applyLeave(any(LeaveRequest.class)))
                .thenThrow(new InvalidLeaveDateException("End date cannot be before start date"));

        String jsonPayload = """
                {
                    "employeeId": 101,
                    "leaveType": "PAID_LEAVE",
                    "startDate": "2026-10-10",
                    "endDate": "2026-10-02",
                    "reason": "Attending family wedding"
                }
                """;

        mockMvc.perform(post("/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.message", containsString("cannot be before start date")));

        verify(leaveService, times(1)).applyLeave(any(LeaveRequest.class));
    }

    @Test
    @DisplayName("PUT /leaves/{id} - Returns 200 OK with updated leave response")
    void testUpdateLeave_Success() throws Exception {
        LeaveResponse updatedResponse = LeaveResponse.builder()
                .id(1L)
                .employeeId(101L)
                .leaveType(LeaveType.CASUAL_LEAVE)
                .startDate(LocalDate.of(2026, 10, 2))
                .endDate(LocalDate.of(2026, 10, 6))
                .reason("Updated reason")
                .status(LeaveStatus.APPROVED)
                .build();

        when(leaveService.updateLeave(eq(1L), any(LeaveRequest.class))).thenReturn(updatedResponse);

        String jsonPayload = """
                {
                    "employeeId": 101,
                    "leaveType": "CASUAL_LEAVE",
                    "startDate": "2026-10-02",
                    "endDate": "2026-10-06",
                    "reason": "Updated reason"
                }
                """;

        mockMvc.perform(put("/leaves/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.leaveType", is("CASUAL_LEAVE")))
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.reason", is("Updated reason")));

        verify(leaveService, times(1)).updateLeave(eq(1L), any(LeaveRequest.class));
    }

    @Test
    @DisplayName("PUT /leaves/{id} - Returns 404 NOT FOUND when updating non-existent ID")
    void testUpdateLeave_NotFound() throws Exception {
        when(leaveService.updateLeave(eq(999L), any(LeaveRequest.class)))
                .thenThrow(new LeaveNotFoundException("Leave request not found with ID: 999"));

        String jsonPayload = """
                {
                    "employeeId": 101,
                    "leaveType": "PAID_LEAVE",
                    "startDate": "2026-10-01",
                    "endDate": "2026-10-05",
                    "reason": "Attending family wedding"
                }
                """;

        mockMvc.perform(put("/leaves/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Leave request not found with ID: 999")));

        verify(leaveService, times(1)).updateLeave(eq(999L), any(LeaveRequest.class));
    }

    @Test
    @DisplayName("DELETE /leaves/{id} - Returns 204 NO CONTENT on successful deletion")
    void testDeleteLeave_Success() throws Exception {
        doNothing().when(leaveService).deleteLeave(1L);

        mockMvc.perform(delete("/leaves/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(leaveService, times(1)).deleteLeave(1L);
    }

    @Test
    @DisplayName("DELETE /leaves/{id} - Returns 404 NOT FOUND when deleting non-existent ID")
    void testDeleteLeave_NotFound() throws Exception {
        doThrow(new LeaveNotFoundException("Leave request not found with ID: 999"))
                .when(leaveService).deleteLeave(999L);

        mockMvc.perform(delete("/leaves/999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", containsString("Leave request not found with ID: 999")));

        verify(leaveService, times(1)).deleteLeave(999L);
    }
}
