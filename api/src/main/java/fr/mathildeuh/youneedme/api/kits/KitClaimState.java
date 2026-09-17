package fr.mathildeuh.youneedme.api.kits;

/** A player's claim history for one kit. */
public record KitClaimState(String kitId, long claimCount, long lastClaimedAt) {

    public static final KitClaimState NEVER_CLAIMED = new KitClaimState("", 0, 0);
}
