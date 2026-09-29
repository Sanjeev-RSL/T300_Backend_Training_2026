package com.example.LeaveManagementDemo.service;

import com.example.LeaveManagementDemo.dto.LeaveRequest;
import com.example.LeaveManagementDemo.dto.LeaveResponse;

import java.util.List;

public interface LeaveService {

    LeaveResponse applyLeave(LeaveRequest leaveRequest);

    LeaveResponse getLeaveById(Long id);

    List<LeaveResponse> getAllLeaves();

    LeaveResponse updateLeave(Long id, LeaveRequest leaveRequest);

    void deleteLeave(Long id);
}
