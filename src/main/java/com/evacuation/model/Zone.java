package com.evacuation.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Zone {
    private String id;
    private String buildingId;
    private String name;

    @JsonProperty("zone_type")
    private String zoneType;

    @JsonProperty("max_capacity")
    private Integer maxCapacity;

    @JsonProperty("is_exit")
    private Boolean isExit;

    @JsonProperty("people_count")
    private Integer peopleCount;

    @JsonProperty("movement_speed")
    private String movementSpeed;

    @JsonProperty("time_of_day")
    private String timeOfDay;

    @JsonProperty("emergency_flag")
    private Boolean emergencyFlag;

    public Zone() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBuildingId() { return buildingId; }
    public void setBuildingId(String buildingId) { this.buildingId = buildingId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getZoneType() { return zoneType; }
    public void setZoneType(String zoneType) { this.zoneType = zoneType; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public Boolean getIsExit() { return isExit; }
    public void setIsExit(Boolean isExit) { this.isExit = isExit; }

    public Integer getPeopleCount() { return peopleCount; }
    public void setPeopleCount(Integer peopleCount) { this.peopleCount = peopleCount; }

    public String getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(String movementSpeed) { this.movementSpeed = movementSpeed; }

    public String getTimeOfDay() { return timeOfDay; }
    public void setTimeOfDay(String timeOfDay) { this.timeOfDay = timeOfDay; }

    public Boolean getEmergencyFlag() { return emergencyFlag; }
    public void setEmergencyFlag(Boolean emergencyFlag) { this.emergencyFlag = emergencyFlag; }
}
