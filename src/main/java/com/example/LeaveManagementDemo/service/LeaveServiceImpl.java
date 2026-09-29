package com.example.LeaveManagementDemo.service;

import com.example.LeaveManagementDemo.dto.LeaveRequest;
import com.example.LeaveManagementDemo.dto.LeaveResponse;
import com.example.LeaveManagementDemo.exception.InvalidLeaveDateException;
import com.example.LeaveManagementDemo.exception.LeaveNotFoundException;
import com.example.LeaveManagementDemo.model.LeaveInfo;
import com.example.LeaveManagementDemo.model.LeaveStatus;
import com.example.LeaveManagementDemo.repository.LeaveRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class LeaveServiceImpl implements LeaveService {

    private final LeaveRepository leaveRepository;
    private final AtomicLong counter;

    public LeaveServiceImpl(LeaveRepository leaveRepository) {
        this.leaveRepository = leaveRepository;
        this.counter = new AtomicLong(0);
    }

    @Override
    public LeaveResponse applyLeave(LeaveRequest leaveRequest) {
        validateLeaveDates(leaveRequest.getStartDate(), leaveRequest.getEndDate());

        Long requestId = counter.incrementAndGet();

        LeaveInfo leaveInfo = LeaveInfo.builder()
                .id(requestId)
                .employeeId(leaveRequest.getEmployeeId())
                .leaveType(leaveRequest.getLeaveType())
                .startDate(leaveRequest.getStartDate())
                .endDate(leaveRequest.getEndDate())
                .reason(leaveRequest.getReason())
                .status(LeaveStatus.PENDING) // Default to PENDING on new leave submission
                .build();

        LeaveInfo saved = leaveRepository.save(leaveInfo);
        return mapToResponse(saved);
    }

    @Override
    public LeaveResponse getLeaveById(Long id) {
        LeaveInfo leaveInfo = leaveRepository.findById(id)
                .orElseThrow(() -> new LeaveNotFoundException("Leave request not found with ID: " + id));
        return mapToResponse(leaveInfo);
    }

    @Override
    public List<LeaveResponse> getAllLeaves() {
        return leaveRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public LeaveResponse updateLeave(Long id, LeaveRequest leaveRequest) {
        LeaveInfo existing = leaveRepository.findById(id)
                .orElseThrow(() -> new LeaveNotFoundException("Leave request not found with ID: " + id));

        validateLeaveDates(leaveRequest.getStartDate(), leaveRequest.getEndDate());

        existing.setEmployeeId(leaveRequest.getEmployeeId());
        existing.setLeaveType(leaveRequest.getLeaveType());
        existing.setStartDate(leaveRequest.getStartDate());
        existing.setEndDate(leaveRequest.getEndDate());
        existing.setReason(leaveRequest.getReason());
        // Status remains preserved unless updated through administrative workflow

        LeaveInfo updated = leaveRepository.save(existing);
        return mapToResponse(updated);
    }

    @Override
    public void deleteLeave(Long id) {
        if (!leaveRepository.existsById(id)) {
            throw new LeaveNotFoundException("Leave request not found with ID: " + id);
        }
        leaveRepository.deleteById(id);
    }

    private void validateLeaveDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidLeaveDateException("End date (" + endDate + ") cannot be before start date (" + startDate + ")");
        }
    }

    private LeaveResponse mapToResponse(LeaveInfo leaveInfo) {
        return LeaveResponse.builder()
                .id(leaveInfo.getId())
                .employeeId(leaveInfo.getEmployeeId())
                .leaveType(leaveInfo.getLeaveType())
                .startDate(leaveInfo.getStartDate())
                .endDate(leaveInfo.getEndDate())
                .reason(leaveInfo.getReason())
                .status(leaveInfo.getStatus())
                .build();
    }
}
