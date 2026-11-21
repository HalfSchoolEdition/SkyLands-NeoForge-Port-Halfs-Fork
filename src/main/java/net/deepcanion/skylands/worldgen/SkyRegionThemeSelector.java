package net.deepcanion.skylands.worldgen;

/**
 * Picks one sky biome for a Lithostitched sky-region site from sampled surface
 * biomes. A theme is eligible ONLY when at least one surface sample matches its
 * whitelist tag. Returning -1 means the site should use the ordinary surface
 * biome instead of any injected SkyLands biome.
 *
 * Plain Java helper outside the Mixin package (ordinary helpers cannot be
 * loaded from packages reserved by Sponge Mixin). It is Minecraft-independent
 * so config extremes can be regression-tested without world generation.
 */
public final class SkyRegionThemeSelector {
    // Theme order: Glaciated, Lavic, Swamp, Arid, Cherry, Golden.
    // Scores are all integers: x2 for Glaciated/Arid, x3 for rare themes,
    // and x1 for Golden (half the old base relative to other types).
    private static final int[] SCORE_FACTORS = {2, 3, 3, 2, 3, 1};
    private static final int GOLDEN = 5;

    private SkyRegionThemeSelector() {}

    public static int choose(int[] sampleScores, int[] configuredWeights, long siteHash) {
        if (sampleScores.length != SCORE_FACTORS.length ||
                configuredWeights.length != SCORE_FACTORS.length) {
            throw new IllegalArgumentException("Expected six SkyLands themes");
        }

        // Do not allow a zero-match theme to win, even when every eligible
        // theme is disabled. Equal adjusted scores retain original tag priority.
        int best = -1;
        long bestScore = 0;
        for (int theme = 0; theme < SCORE_FACTORS.length; theme++) {
            if (sampleScores[theme] <= 0 || configuredWeights[theme] <= 0) continue;
            long adjusted = (long) sampleScores[theme] * SCORE_FACTORS[theme] * configuredWeights[theme];
            if (adjusted > bestScore) {
                bestScore = adjusted;
                best = theme;
            }
        }

        if (best == GOLDEN) {
            // The Golden frequency adjustment must never create a mismatched
            // Lavic/Swamp/Cherry/etc. sky region. Prefer an eligible alternate,
            // or suppress this sky region if none exists.
            int keepThreshold = Math.min(100, (configuredWeights[GOLDEN] + 1) / 2);
            if (Math.floorMod(siteHash, 100L) >= keepThreshold) {
                int alternative = -1;
                long alternativeScore = 0;
                for (int theme = 0; theme < GOLDEN; theme++) {
                    if (sampleScores[theme] <= 0 || configuredWeights[theme] <= 0) continue;
                    long adjusted = (long) sampleScores[theme] * SCORE_FACTORS[theme] * configuredWeights[theme];
                    if (adjusted > alternativeScore) {
                        alternativeScore = adjusted;
                        alternative = theme;
                    }
                }
                best = alternative; // -1: no matching theme, no SkyLands biome.
            }
        }
        return best;
    }
}
