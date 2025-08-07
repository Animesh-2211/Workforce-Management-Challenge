package com.railse.hiring.workforcemgmt.dto;
import lombok.Data;
@Data
public class TaskCommentRequest {
    private String comment;
    private Long userId;
}