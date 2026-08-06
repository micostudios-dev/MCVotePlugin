package org.mcvote.api;

public record Vote(String serviceName, String username, String address, long timestamp) {

    public Vote {
        serviceName = serviceName == null ? "" : serviceName;
        username = username == null ? "" : username;
        address = address == null ? "" : address;
    }
}
