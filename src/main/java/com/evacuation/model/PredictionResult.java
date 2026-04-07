package com.evacuation.model;

public class PredictionResult {
    private String zoneId;
    private String zoneName;
    private String congestionLevel;
    private String recommendedExit;
    private Integer peopleCount;
    private String movementSpeed;
    private Boolean emergencyFlag;

    public PredictionResult() {}

    public PredictionResult(String zoneId, String zoneName, String congestionLevel,
                            String recommendedExit, Integer peopleCount,
                            String movementSpeed, Boolean emergencyFlag) {
        this.zoneId = zoneId;
        this.zoneName = zoneName;
        this.congestionLevel = congestionLevel;
        this.recommendedExit = recommendedExit;
        this.peopleCount = peopleCount;
        this.movementSpeed = movementSpeed;
        this.emergencyFlag = emergencyFlag;
    }

    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }

    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getCongestionLevel() { return congestionLevel; }
    public void setCongestionLevel(String congestionLevel) { this.congestionLevel = congestionLevel; }

    public String getRecommendedExit() { return recommendedExit; }
    public void setRecommendedExit(String recommendedExit) { this.recommendedExit = recommendedExit; }

    public Integer getPeopleCount() { return peopleCount; }
    public void setPeopleCount(Integer peopleCount) { this.peopleCount = peopleCount; }

    public String getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(String movementSpeed) { this.movementSpeed = movementSpeed; }

    public Boolean getEmergencyFlag() { return emergencyFlag; }
    public void setEmergencyFlag(Boolean emergencyFlag) { this.emergencyFlag = emergencyFlag; }
}
