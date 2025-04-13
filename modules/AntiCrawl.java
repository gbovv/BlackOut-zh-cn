package kassuk.addon.blackout.modules;

import kassuk.addon.blackout.BlackOut;
import kassuk.addon.blackout.BlackOutModule;

/**
 * @author OLEPOSSU
 */

public class AntiCrawl extends BlackOutModule {
    public AntiCrawl() {
        super(BlackOut.BLACKOUT, "防爬行", "在低矮空间时自动取消潜行与爬行（建议在1.12.2版本使用）");
    }
}
