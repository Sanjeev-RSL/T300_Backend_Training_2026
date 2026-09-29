package com.example.LeaveManagementDemo.repository;

import com.example.LeaveManagementDemo.model.LeaveInfo;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class LeaveRepository {

    private final Map<Long, LeaveInfo> storage = new ConcurrentHashMap<>();

    public LeaveInfo save(LeaveInfo leaveInfo) {
        storage.put(leaveInfo.getId(), leaveInfo);
        return leaveInfo;
    }

    public Optional<LeaveInfo> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    public List<LeaveInfo> findAll() {
        return new ArrayList<>(storage.values());
    }

    public boolean deleteById(Long id) {
        return storage.remove(id) != null;
    }

    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    public void clear() {
        storage.clear();
    }
}
