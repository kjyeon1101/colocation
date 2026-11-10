package com.sds.colocation.allocation;

import java.time.LocalDate;

public record ServerView(
        Long id,
        String hostname,
        ServerType type,
        int startUnit,
        int sizeUnits,
        int powerWatts,
        ServerStatus status,
        LocalDate installedDate
) {

    public static ServerView from(Server server) {
        return new ServerView(server.getId(), server.getHostname(), server.getType(), server.getStartUnit(),
                server.getSizeUnits(), server.getPowerWatts(), server.getStatus(), server.getInstalledDate());
    }
}
