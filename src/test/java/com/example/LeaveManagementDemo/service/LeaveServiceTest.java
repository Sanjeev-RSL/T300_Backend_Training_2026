package com.example.LeaveManagementDemo.service;

import com.example.LeaveManagementDemo.dto.LeaveRequest;
import com.example.LeaveManagementDemo.dto.LeaveResponse;
import com.example.LeaveManagementDemo.exception.InvalidLeaveDateException;
import com.example.LeaveManagementDemo.exception.LeaveNotFoundException;
import com.example.LeaveManagementDemo.model.LeaveInfo;
import com.example.LeaveManagementDemo.model.LeaveStatus;
import com.example.LeaveManagementDemo.model.LeaveType;
import com.example.LeaveManagementDemo.repository.LeaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRepository leaveRepository;

    @InjectMocks
    private LeaveServiceImpl leaveService;

    private LeaveRequest sampleRequest;
    private LeaveInfo sampleLeaveInfo;

    @BeforeEach
    void setUp() {
        sampleRequest = LeaveRequest.builder()
                .employeeId(101L)
                .leaveType(LeaveType.PAID_LEAVE)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 5))
                .reason("Attending family wedding")
                .build();

        sampleLeaveInfo = LeaveInfo.builder()
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
    @DisplayName("applyLeave - Success: generates ID, sets status to PENDING, and saves leave request")
    void testApplyLeave_Success() {
        when(leaveRepository.save(any(LeaveInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveResponse response = leaveService.applyLeave(sampleRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(sampleRequest.getEmployeeId(), response.getEmployeeId());
        assertEquals(sampleRequest.getLeaveType(), response.getLeaveType());
        assertEquals(sampleRequest.getStartDate(), response.getStartDate());
        assertEquals(sampleRequest.getEndDate(), response.getEndDate());
        assertEquals(sampleRequest.getReason(), response.getReason());
        assertEquals(LeaveStatus.PENDING, response.getStatus());

        verify(leaveRepository, times(1)).save(any(LeaveInfo.class));
    }

    @Test
    @DisplayName("applyLeave - Failure: throws InvalidLeaveDateException when endDate is before startDate")
    void testApplyLeave_EndDateBeforeStartDate_ThrowsException() {
        sampleRequest.setStartDate(LocalDate.of(2026, 10, 10));
        sampleRequest.setEndDate(LocalDate.of(2026, 10, 5));

        InvalidLeaveDateException exception = assertThrows(InvalidLeaveDateException.class, () -> {
            leaveService.applyLeave(sampleRequest);
        });

        assertTrue(exception.getMessage().contains("cannot be before start date"));
        verify(leaveRepository, never()).save(any(LeaveInfo.class));
    }

    @Test
    @DisplayName("getLeaveById - Success: returns existing leave response")
    void testGetLeaveById_Found() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(sampleLeaveInfo));

        LeaveResponse response = leaveService.getLeaveById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(101L, response.getEmployeeId());
        verify(leaveRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getLeaveById - Failure: throws LeaveNotFoundException when ID does not exist")
    void testGetLeaveById_NotFound_ThrowsException() {
        when(leaveRepository.findById(999L)).thenReturn(Optional.empty());

        LeaveNotFoundException exception = assertThrows(LeaveNotFoundException.class, () -> {
            leaveService.getLeaveById(999L);
        });

        assertTrue(exception.getMessage().contains("Leave request not found with ID: 999"));
        verify(leaveRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("getAllLeaves - Success: returns all leaves in repository")
    void testGetAllLeaves_Success() {
        LeaveInfo second = LeaveInfo.builder()
                .id(2L)
                .employeeId(102L)
                .leaveType(LeaveType.SICK_LEAVE)
                .startDate(LocalDate.of(2026, 10, 15))
                .endDate(LocalDate.of(2026, 10, 16))
                .reason("Medical checkup")
                .status(LeaveStatus.APPROVED)
                .build();

        when(leaveRepository.findAll()).thenReturn(Arrays.asList(sampleLeaveInfo, second));

        List<LeaveResponse> leaves = leaveService.getAllLeaves();

        assertNotNull(leaves);
        assertEquals(2, leaves.size());
        assertEquals(1L, leaves.get(0).getId());
        assertEquals(2L, leaves.get(1).getId());
        verify(leaveRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getAllLeaves - Success: returns empty list when no leaves exist")
    void testGetAllLeaves_Empty() {
        when(leaveRepository.findAll()).thenReturn(Collections.emptyList());

        List<LeaveResponse> leaves = leaveService.getAllLeaves();

        assertNotNull(leaves);
        assertTrue(leaves.isEmpty());
        verify(leaveRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("updateLeave - Success: updates fields and saves updated leave")
    void testUpdateLeave_Success() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(sampleLeaveInfo));
        when(leaveRepository.save(any(LeaveInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequest updateRequest = LeaveRequest.builder()
                .employeeId(101L)
                .leaveType(LeaveType.UNPAID_LEAVE)
                .startDate(LocalDate.of(2026, 10, 2))
                .endDate(LocalDate.of(2026, 10, 6))
                .reason("Updated reason: urgent personal matter")
                .build();

        LeaveResponse response = leaveService.updateLeave(1L, updateRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(LeaveType.UNPAID_LEAVE, response.getLeaveType());
        assertEquals(LeaveStatus.PENDING, response.getStatus()); // Status preserved from existing record
        assertEquals("Updated reason: urgent personal matter", response.getReason());
        verify(leaveRepository, times(1)).findById(1L);
        verify(leaveRepository, times(1)).save(sampleLeaveInfo);
    }

    @Test
    @DisplayName("updateLeave - Failure: throws LeaveNotFoundException when updating non-existent leave")
    void testUpdateLeave_NotFound_ThrowsException() {
        when(leaveRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(LeaveNotFoundException.class, () -> {
            leaveService.updateLeave(999L, sampleRequest);
        });

        verify(leaveRepository, times(1)).findById(999L);
        verify(leaveRepository, never()).save(any(LeaveInfo.class));
    }

    @Test
    @DisplayName("updateLeave - Failure: throws InvalidLeaveDateException when updating with invalid dates")
    void testUpdateLeave_InvalidDates_ThrowsException() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(sampleLeaveInfo));

        LeaveRequest invalidDatesRequest = LeaveRequest.builder()
                .employeeId(101L)
                .leaveType(LeaveType.PAID_LEAVE)
                .startDate(LocalDate.of(2026, 10, 10))
                .endDate(LocalDate.of(2026, 10, 2))
                .reason("Invalid dates")
                .build();

        assertThrows(InvalidLeaveDateException.class, () -> {
            leaveService.updateLeave(1L, invalidDatesRequest);
        });

        verify(leaveRepository, times(1)).findById(1L);
        verify(leaveRepository, never()).save(any(LeaveInfo.class));
    }

    @Test
    @DisplayName("deleteLeave - Success: deletes existing leave")
    void testDeleteLeave_Success() {
        when(leaveRepository.existsById(1L)).thenReturn(true);

        assertDoesNotThrow(() -> leaveService.deleteLeave(1L));

        verify(leaveRepository, times(1)).existsById(1L);
        verify(leaveRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("deleteLeave - Failure: throws LeaveNotFoundException when deleting non-existent ID")
    void testDeleteLeave_NotFound_ThrowsException() {
        when(leaveRepository.existsById(999L)).thenReturn(false);

        LeaveNotFoundException exception = assertThrows(LeaveNotFoundException.class, () -> {
            leaveService.deleteLeave(999L);
        });

        assertTrue(exception.getMessage().contains("Leave request not found with ID: 999"));
        verify(leaveRepository, times(1)).existsById(999L);
        verify(leaveRepository, never()).deleteById(anyLong());
    }
}
