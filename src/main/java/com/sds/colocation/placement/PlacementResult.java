package com.sds.colocation.placement;

import com.sds.colocation.allocation.Server;

/**
 * @param duplicated 이미 처리된 요청 키로 다시 들어온 요청이면 true
 */
public record PlacementResult(
        Long serverId,
        Long allocationId,
        Long rackId,
        String rackName,
        String hostname,
        int startUnit,
        int sizeUnits,
        boolean duplicated
) {

    public static PlacementResult of(Server server, boolean duplicated) {
        return new PlacementResult(
                server.getId(),
                server.getAllocation().getId(),
                server.getAllocation().getRack().getId(),
                server.getAllocation().getRack().getName(),
                server.getHostname(),
                server.getStartUnit(),
                server.getSizeUnits(),
                duplicated
        );
    }
}
