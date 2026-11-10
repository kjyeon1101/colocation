package com.sds.colocation.power;

import java.time.LocalDate;

public record PowerUpgradeView(
        Long id,
        Long rackId,
        String rackName,
        int beforeWatts,
        int afterWatts,
        int steps,
        long cost,
        LocalDate upgradedDate
) {

    public static PowerUpgradeView from(PowerUpgrade upgrade) {
        return new PowerUpgradeView(upgrade.getId(), upgrade.getRack().getId(), upgrade.getRackName(),
                upgrade.getBeforeWatts(), upgrade.getAfterWatts(), upgrade.getSteps(), upgrade.getCost(),
                upgrade.getUpgradedDate());
    }
}
