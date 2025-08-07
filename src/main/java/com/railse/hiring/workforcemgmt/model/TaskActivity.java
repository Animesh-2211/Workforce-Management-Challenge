package com.railse.hiring.workforcemgmt.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskActivity {
    private String eventType;
    private String comment;
    private Long timestamp;
    private Long userId;

    public TaskActivity(String eventType, String comment, Long userId) {
        this.eventType = eventType;
        this.comment = comment;
        this.userId = userId;
        this.timestamp = System.currentTimeMillis();
    }
}
