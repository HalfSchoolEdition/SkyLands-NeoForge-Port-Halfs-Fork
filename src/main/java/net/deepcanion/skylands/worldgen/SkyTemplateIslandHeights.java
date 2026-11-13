package net.deepcanion.skylands.worldgen;

/**
 * Vertical positioning of NBT template *features* around procedural SkyLands.
 * This is separate from jigsaw structures and from procedural satellite geometry.
 * The immutable site sampler needs no neighboring chunk reads or extra worldgen pass.
 */
final class SkyTemplateIslandHeights {
    private SkyTemplateIslandHeights() { }

    /** The actual rock/land surface within each bundled NBT template, measured from its bottom. */
    static int visibleSurfaceOffset(String template, int height) {
        return switch (template) {
            case "cherry_isle_medium_0" -> 23;
            case "cherry_isle_medium_2" -> 12;
            case "cherry_isle_medium_snow_0" -> 15;
            case "cherry_isle_small_0" -> 23;
            case "cherry_isle_small_1" -> 12;
            case "desert_isle_large0" -> 8;
            case "desert_isle_small0" -> 6;
            case "desert_isle_small1" -> 7;
            case "desert_isle_small2" -> 6;
            case "desert_isle_small3" -> 6;
            case "desert_isle_small4" -> 15;
            case "desert_isle_small5" -> 4;
            case "isle_dropper_0" -> 0;
            case "isle_dungeon_1" -> 11;
            case "isle_dungeon_2" -> 11;
            case "isle_ice_medium_0" -> 23;
            case "isle_ice_medium_2" -> 26;
            case "isle_ice_medium_69" -> 14;
            case "isle_ice_small_0" -> 17;
            case "isle_ice_small_1" -> 30;
            case "isle_medium_0" -> 12;
            case "isle_medium_1" -> 17;
            case "isle_medium_2" -> 12;
            case "isle_medium_3" -> 15;
            case "isle_medium_4" -> 16;
            case "isle_medium_5" -> 43;
            case "isle_medium_snow_0" -> 15;
            case "isle_rare_0" -> 10;
            case "isle_rare_1" -> 8;
            case "isle_rare_3" -> 5;
            case "isle_small_0" -> 10;
            case "isle_small_1" -> 12;
            case "isle_small_2" -> 12;
            case "isle_small_4" -> 11;
            case "isle_small_5" -> 15;
            case "isle_swamp_medium_0" -> 23;
            case "isle_swamp_medium_69" -> 14;
            case "isle_swamp_small_1" -> 31;
            case "isle_swamp_small_2" -> 16;
            case "lava_isle_medium0" -> 13;
            case "lava_isle_small_0" -> 8;
            case "lava_isle_small_1" -> 8;
            case "lava_isle_small_2" -> 9;
            case "lava_isle_small_3" -> 13;
            case "lava_isle_small_4" -> 10;
            case "sstructure2" -> 6;
            case "sstructure5ver2" -> 39;
            default -> Math.max(0, Math.min(height - 1, (int)Math.round(height * .6)));
        };
    }

    /** Actual center rock height of the nearest *main* procedural island, not its Y-center. */
    static int mainSurface(long seed, int x, int z, String theme) {
        int gx = Math.floorDiv(x, SkyIslandShape.GRID);
        int gz = Math.floorDiv(z, SkyIslandShape.GRID);
        int nearestX=gx, nearestZ=gz;
        double nearestDist = Double.POSITIVE_INFINITY;
        for (int a=-1; a<=1; a++) for (int b=-1; b<=1; b++) {
            int ix=gx+a, iz=gz+b;
            if (!SkyIslandShape.hasSite(seed,ix,iz)) continue;
            double cx=SkyIslandShape.siteX(seed,ix,iz), cz=SkyIslandShape.siteZ(seed,ix,iz);
            double dist=(cx-x)*(cx-x)+(cz-z)*(cz-z);
            if (dist<nearestDist) {
                nearestDist=dist;
                nearestX=ix;
                nearestZ=iz;
            }
        }
        if (nearestDist > 340*340) {
            return switch (theme) {
                case "arid" -> 438;
                case "glaciated", "cherry" -> 433;
                case "swamp" -> 428;
                default -> 420;
            };
        }
        var islands=SkyIslandShape.sites(seed,nearestX,nearestZ,theme);
        if(islands.isEmpty()) return 425;
        var nearest=islands.get(0);
        int sx=(int)Math.round(nearest.centerX()), sz=(int)Math.round(nearest.centerZ());
        var column=nearest.column(sx,sz);
        for (int y=nearest.maxY(); y>=nearest.minY(); y--)
            if (column.solid(y)) return y;
        return (nearest.minY()+nearest.maxY())/2;
    }

    /** Stable, spatially grouped Swamp archipelagos: near, lower, upper and rare outliers. */
    private static int[] swampGroup(long seed, int x, int z) {
        final int spacing=176;
        int cellX=Math.floorDiv(x,spacing),cellZ=Math.floorDiv(z,spacing);
        double best=Double.POSITIVE_INFINITY;
        int bx=0,bz=0;
        long bestHash=0;
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            int ax=cellX+dx,az=cellZ+dz;
            long hash=SkyTerrainPatterns.mix(seed,ax,0,az,0x371af31L);
            int px=ax*spacing+36+(int)(SkyTerrainPatterns.unit(hash)*104);
            int pz=az*spacing+36+(int)(SkyTerrainPatterns.unit(
                    SkyTerrainPatterns.mix(hash,1,0,0,0x371af32L))*104);
            double distance=(px-x)*(double)(px-x)+(pz-z)*(double)(pz-z);
            if(distance<best) {best=distance;bx=px;bz=pz;bestHash=hash;}
        }
        double type=SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(bestHash,2,0,0,0x371af33L));
        int tier;
        if(type<.55) tier=0;             // close to main terrain
        else if(type<.79) tier=-46;      // lower shelf
        else if(type<.96) tier=48;       // upper shelf
        else tier=type<.98 ? -87 : 83;  // unusually distant groups
        int groupJitter=(int)Math.round(12*(SkyTerrainPatterns.unit(
                SkyTerrainPatterns.mix(bestHash,3,0,0,0x371af34L))-.5));
        return new int[]{bx,bz,tier+groupJitter};
    }

    /** Return candidate origin Y before the configured offset is added by IslandTemplateFeature. */
    static int chooseY(long seed, String theme, String template,
                       int x, int z, int originalY, int templateHeight, int offsetY,
                       int worldMinY, int worldMaxY) {
        if(theme==null) return originalY;
        int surface=visibleSurfaceOffset(template,templateHeight);
        long hash=SkyTerrainPatterns.mix(seed,x,0,z,0x371aabL ^ template.hashCode());
        int reference, variation;
        if("swamp".equals(theme)) {
            int[] group=swampGroup(seed,x,z);
            reference=mainSurface(seed,group[0],group[1],theme);
            variation=group[2]+(int)Math.round(9*(SkyTerrainPatterns.unit(hash)-.5));
        } else {
            reference=mainSurface(seed,x,z,theme);
            // Unbanded, center-biased distribution. ±120 is possible but rare.
            double u=SkyTerrainPatterns.unit(hash)*2-1;
            variation=(int)Math.round(Math.copySign(122*Math.pow(Math.abs(u),1.65),u));
        }
        int desiredSurface=reference+variation;
        int candidateY=desiredSurface-surface-offsetY;
        // Template bounds—not its surface—must fit the build range.
        int min= Math.max(worldMinY+2-offsetY,SkyIslandShape.MIN_Y-45-offsetY);
        int max= Math.min(worldMaxY-2-templateHeight-offsetY,
                SkyIslandShape.MAX_Y-templateHeight-offsetY);
        return Math.max(min,Math.min(max,candidateY));
    }
}
