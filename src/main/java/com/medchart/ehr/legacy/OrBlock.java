package com.medchart.ehr.legacy;

public class OrBlock {

    public long providerId;
    public int departmentId;
    public String weekday;
    public String startTime;
    public String endTime;
    public String room;

    public OrBlock() {
    }

    public OrBlock(long providerId, int departmentId, String weekday, String startTime, String endTime, String room) {
        this.providerId = providerId;
        this.departmentId = departmentId;
        this.weekday = weekday;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
    }
}
