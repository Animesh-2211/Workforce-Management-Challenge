package com.railse.hiring.workforcemgmt.repository;

import java.util.List;
import java.util.Optional;

import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.model.enums.Priority;

public interface TaskRepository {
    Optional<TaskManagement> findById(Long id);

    TaskManagement save(TaskManagement task);

    List<TaskManagement> findAll();

    List<TaskManagement> findByReferenceIdAndReferenceType(Long referenceId,
            com.railse.hiring.workforcemgmt.common.model.enums.ReferenceType referenceType);

    List<TaskManagement> findByAssigneeIdIn(List<Long> assigneeIds);

    public List<TaskManagement> findByPriority(Priority priority);
}
