package com.railse.hiring.workforcemgmt.service.impl;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.railse.hiring.workforcemgmt.dto.*;
import com.railse.hiring.workforcemgmt.model.enums.Priority;
import org.springframework.stereotype.Service;

import com.railse.hiring.workforcemgmt.common.exception.ResourceNotFoundException;
import com.railse.hiring.workforcemgmt.mapper.ITaskManagementMapper;
import com.railse.hiring.workforcemgmt.model.TaskActivity;
import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.model.enums.Task;
import com.railse.hiring.workforcemgmt.model.enums.TaskStatus;
import com.railse.hiring.workforcemgmt.repository.TaskRepository;
import com.railse.hiring.workforcemgmt.service.TaskManagementService;

@Service
public class TaskManagementServiceImpl implements TaskManagementService {

    private final TaskRepository taskRepository;
    private final ITaskManagementMapper taskMapper;

    public TaskManagementServiceImpl(TaskRepository taskRepository, ITaskManagementMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
    }

    @Override
    public TaskManagementDto findTaskById(Long id) {
        TaskManagement task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        return taskMapper.modelToDto(task);
    }

    @Override
    public List<TaskManagementDto> createTasks(TaskCreateRequest createRequest) {
        List<TaskManagement> createdTasks = new ArrayList<>();
        for (TaskCreateRequest.RequestItem item : createRequest.getRequests()) {
            TaskManagement newTask = new TaskManagement();
            newTask.setReferenceId(item.getReferenceId());
            newTask.setReferenceType(item.getReferenceType());
            newTask.setTask(item.getTask());
            newTask.setAssigneeId(item.getAssigneeId());
            newTask.setPriority(item.getPriority());
            newTask.setTaskDeadlineTime(item.getTaskDeadlineTime());
            newTask.setStatus(TaskStatus.ASSIGNED);
            newTask.setDescription("New task created.");
            newTask.setCreatedAt(System.currentTimeMillis());
            newTask.getActivities().add(new TaskActivity("CREATED",
                    "Task created by user " + item.getAssigneeId(), item.getAssigneeId()));
            createdTasks.add(taskRepository.save(newTask));
        }
        return taskMapper.modelListToDtoList(createdTasks);
    }

    @Override
    public List<TaskManagementDto> updateTasks(UpdateTaskRequest updateRequest) {
        List<TaskManagement> updatedTasks = new ArrayList<>();
        for (UpdateTaskRequest.RequestItem item : updateRequest.getRequests()) {
            TaskManagement task = taskRepository.findById(item.getTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + item.getTaskId()));

            if (item.getTaskStatus() != null) {
                task.getActivities().add(new TaskActivity("STATUS_CHANGED",
                        "Status changed to " + item.getTaskStatus() + " by user " + task.getAssigneeId(),
                        task.getAssigneeId()));
                task.setStatus(item.getTaskStatus());
            }
            if (item.getDescription() != null) {
                task.getActivities().add(new TaskActivity("DESCRIPTION_CHANGED",
                        "Description updated by user " + task.getAssigneeId(), task.getAssigneeId()));
                task.setDescription(item.getDescription());
            }
            updatedTasks.add(taskRepository.save(task));
        }
        return taskMapper.modelListToDtoList(updatedTasks);
    }

    @Override
    public String assignByReference(AssignByReferenceRequest request) {
        List<Task> applicableTasks = Task.getTasksByReferenceType(request.getReferenceType());
        List<TaskManagement> existingTasks = taskRepository.findByReferenceIdAndReferenceType(request.getReferenceId(),
                request.getReferenceType());

        for (Task taskType : applicableTasks) {
            List<TaskManagement> tasksOfType = existingTasks.stream()
                    .filter(t -> t.getTask() == taskType && t.getStatus() != TaskStatus.COMPLETED)
                    .collect(Collectors.toList());

            if (!tasksOfType.isEmpty()) {
                // Keep the first one and cancel the rest
                TaskManagement primaryTask = tasksOfType.get(0);
                primaryTask.setAssigneeId(request.getAssigneeId());
                primaryTask.setStatus(TaskStatus.ASSIGNED);
                primaryTask.getActivities().add(new TaskActivity("REASSIGNED",
                        "Task reassigned to user " + request.getAssigneeId(), request.getAssigneeId()));
                taskRepository.save(primaryTask);

                for (int i = 1; i < tasksOfType.size(); i++) {
                    TaskManagement duplicate = tasksOfType.get(i);
                    duplicate.setStatus(TaskStatus.CANCELLED);// Cancel duplicates
                    duplicate.getActivities().add(new TaskActivity("CANCELLED",
                            "Task cancelled due to reassignment by user " + request.getAssigneeId(),
                            request.getAssigneeId()));
                    taskRepository.save(duplicate);
                }
            } else {
                // Create a new task if none exist
                TaskManagement newTask = new TaskManagement();
                newTask.setReferenceId(request.getReferenceId());
                newTask.setReferenceType(request.getReferenceType());
                newTask.setTask(taskType);
                newTask.setAssigneeId(request.getAssigneeId());
                newTask.setStatus(TaskStatus.ASSIGNED);
                newTask.setDescription("New task created for " + taskType);
                newTask.setTaskDeadlineTime(System.currentTimeMillis() + 86400000);
                newTask.setPriority(com.railse.hiring.workforcemgmt.model.enums.Priority.MEDIUM);
                newTask.getActivities().add(new TaskActivity("CREATED",
                        "Task created by user " + request.getAssigneeId(), request.getAssigneeId()));
                taskRepository.save(newTask);
            }
        }
        return "Tasks assigned successfully for reference " + request.getReferenceId();
    }

    @Override
    public List<TaskManagementDto> fetchTasksByDate(TaskFetchByDateRequest request) {
        List<TaskManagement> tasks = taskRepository.findByAssigneeIdIn(request.getAssigneeIds());

        // BUG #2 is here. It should filter out CANCELLED tasks but doesn't.
        List<TaskManagement> filteredTasks = tasks.stream()
                .filter(task -> task.getStatus() != TaskStatus.CANCELLED)
                .filter(task -> task.getTaskDeadlineTime() >= request.getStartDate()
                        && task.getTaskDeadlineTime() <= request.getEndDate())
                .collect(Collectors.toList());

        return taskMapper.modelListToDtoList(filteredTasks);
    }

    @Override
    public TaskManagementDto addComment(Long id, TaskCommentRequest request) {
        TaskManagement task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        task.getActivities().add(new TaskActivity("COMMENT",
                request.getComment(), request.getUserId()));
        taskRepository.save(task);
        task.getActivities().sort((a1, a2) -> Long.compare(a1.getTimestamp(), a2.getTimestamp()));
        return taskMapper.modelToDto(task);
    }

    @Override
    public TaskManagementDto updateTaskPriority(UpdateTaskPriorityRequest request) {
        TaskManagement task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + request.getTaskId()));
        task.setPriority(request.getPriority());
        task.getActivities().add(new TaskActivity("PRIORITY_CHANGED",
                "Priority changed to " + request.getPriority() + " by user " + request.getUserId(),
                request.getUserId()));
        taskRepository.save(task);
        return taskMapper.modelToDto(task);
    }

    @Override
    public List<TaskManagementDto> fetchTasksByPriority(Priority priority) {
        List<TaskManagement> tasks = taskRepository.findByPriority(priority);
        return taskMapper.modelListToDtoList(tasks);
    }

    @Override
    public List<TaskManagementDto> fetchDailyTasks(DailyTaskRequest request) {
        List<TaskManagement> tasks = taskRepository.findByAssigneeIdIn(request.getAssigneeIds());

        LocalDate targetDate = request.getDate() != null
                ? LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(request.getDate()), ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();

        long startOfDay = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endOfDay = targetDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        return filterTasksByDate(tasks, startOfDay, endOfDay);
    }
    private List<TaskManagementDto> filterTasksByDate(List<TaskManagement> tasks, long startDate, long endDate) {
        List<TaskManagement> filteredTasks = tasks.stream()
                .filter(task -> task.getStatus() != TaskStatus.CANCELLED)
                .filter(task -> {
                    return (task.getCreatedAt() >= startDate && task.getCreatedAt() <= endDate) ||
                            (task.getCreatedAt() < startDate &&
                                    (task.getStatus() == TaskStatus.ASSIGNED || task.getStatus() == TaskStatus.STARTED));
                })
                .collect(Collectors.toList());
        return taskMapper.modelListToDtoList(filteredTasks);
    }
}