package com.sds.colocation.rack;

import java.time.LocalDate;

public record RackInstallationView(
        Long id,
        Long rackId,
        String rackName,
        int totalUnits,
        int powerWatts,
        long cost,
        LocalDate installedDate
) {

    public static RackInstallationView from(RackInstallation installation) {
        return new RackInstallationView(installation.getId(), installation.getRack().getId(),
                installation.getRackName(), installation.getTotalUnits(),
                installation.getPowerWatts(), installation.getCost(), installation.getInstalledDate());
    }
}
