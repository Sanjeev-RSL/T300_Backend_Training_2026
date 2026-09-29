package com.example.LeaveManagementDemo.controller;

import com.example.LeaveManagementDemo.dto.LeaveRequest;
import com.example.LeaveManagementDemo.dto.LeaveResponse;
import com.example.LeaveManagementDemo.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leaves")
public class LeaveManagementController {

    private final LeaveService leaveService;

    // Constructor-based Dependency Injection
    public LeaveManagementController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    /**
     * Retrieve all leave requests
     * GET /leaves
     */
    @GetMapping
    public ResponseEntity<List<LeaveResponse>> getAllLeaves() {
        List<LeaveResponse> leaves = leaveService.getAllLeaves();
        return ResponseEntity.ok(leaves);
    }

    /**
     * Retrieve a leave request by ID
     * GET /leaves/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<LeaveResponse> getLeaveById(@PathVariable Long id) {
        LeaveResponse leave = leaveService.getLeaveById(id);
        return ResponseEntity.ok(leave);
    }

    /**
     * Create a new leave request
     * POST /leaves
     */
    @PostMapping
    public ResponseEntity<LeaveResponse> createLeave(@Valid @RequestBody LeaveRequest leaveRequest) {
        LeaveResponse createdLeave = leaveService.applyLeave(leaveRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdLeave);
    }

    /**
     * Update an existing leave request
     * PUT /leaves/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<LeaveResponse> updateLeave(
            @PathVariable Long id,
            @Valid @RequestBody LeaveRequest leaveRequest) {
        LeaveResponse updatedLeave = leaveService.updateLeave(id, leaveRequest);
        return ResponseEntity.ok(updatedLeave);
    }

    /**
     * Delete a leave request
     * DELETE /leaves/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLeave(@PathVariable Long id) {
        leaveService.deleteLeave(id);
        return ResponseEntity.noContent().build();
    }
}
