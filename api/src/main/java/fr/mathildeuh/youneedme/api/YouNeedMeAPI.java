package fr.mathildeuh.youneedme.api;

import fr.mathildeuh.youneedme.api.auctionhouse.AuctionHouseService;
import fr.mathildeuh.youneedme.api.economy.EconomyService;
import fr.mathildeuh.youneedme.api.homes.HomeService;
import fr.mathildeuh.youneedme.api.kits.KitService;
import fr.mathildeuh.youneedme.api.moderation.ModerationService;
import fr.mathildeuh.youneedme.api.nickname.NicknameService;
import fr.mathildeuh.youneedme.api.scheduler.SchedulerAdapter;
import fr.mathildeuh.youneedme.api.scoreboard.ScoreboardService;
import fr.mathildeuh.youneedme.api.shop.ShopService;
import fr.mathildeuh.youneedme.api.storage.DataStorage;
import fr.mathildeuh.youneedme.api.warps.WarpService;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Convenience static facade over every YouNeedMe service.
 *
 * <p>Every service is also independently registered on Bukkit's {@link
 * org.bukkit.plugin.ServicesManager}, which remains the canonical, mockable/replaceable way to
 * obtain them (exactly how Vault or LuckPerms expose themselves). This facade only saves the
 * boilerplate of looking each one up by hand; it never holds state of its own.
 */
public final class YouNeedMeAPI {

    private YouNeedMeAPI() {}

    /**
     * Looks up a YouNeedMe service on the Bukkit {@link org.bukkit.plugin.ServicesManager}.
     *
     * @param type the service interface to resolve
     * @return the registered provider, or empty if YouNeedMe (or the module backing that
     *     service) isn't loaded
     */
    public static <T> Optional<T> service(Class<T> type) {
        RegisteredServiceProvider<T> registration = Bukkit.getServicesManager().getRegistration(type);
        return registration == null ? Optional.empty() : Optional.of(registration.getProvider());
    }

    public static Optional<EconomyService> economy() {
        return service(EconomyService.class);
    }

    public static Optional<HomeService> homes() {
        return service(HomeService.class);
    }

    public static Optional<WarpService> warps() {
        return service(WarpService.class);
    }

    public static Optional<KitService> kits() {
        return service(KitService.class);
    }

    public static Optional<AuctionHouseService> auctionHouse() {
        return service(AuctionHouseService.class);
    }

    public static Optional<ShopService> shop() {
        return service(ShopService.class);
    }

    public static Optional<ModerationService> moderation() {
        return service(ModerationService.class);
    }

    public static Optional<ScoreboardService> scoreboard() {
        return service(ScoreboardService.class);
    }

    public static Optional<NicknameService> nicknames() {
        return service(NicknameService.class);
    }

    public static Optional<DataStorage> storage() {
        return service(DataStorage.class);
    }

    public static Optional<SchedulerAdapter> scheduler() {
        return service(SchedulerAdapter.class);
    }
}
