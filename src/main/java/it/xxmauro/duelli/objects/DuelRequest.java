package it.xxmauro.duelli.objects;

import java.util.UUID;

public class DuelRequest {

    private final UUID senderId;
    private final UUID targetId;
    private final String kitId;
    private final long timestamp;

    public DuelRequest(UUID senderId, UUID targetId, String kitId) {
        this.senderId = senderId;
        this.targetId = targetId;
        this.kitId = kitId;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getSenderId() { return senderId; }
    public UUID getTargetId() { return targetId; }
    public String getKitId() { return kitId; }
    public long getTimestamp() { return timestamp; }
    
    public boolean isExpired(int timeoutSeconds) {
        return System.currentTimeMillis() - timestamp > timeoutSeconds * 1000L;
    }
}
