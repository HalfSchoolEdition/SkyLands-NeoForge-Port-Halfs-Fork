package net.deepcanion.skylands.worldgen;

/** Pure, deterministic asymmetric rock profiles, shared by Golden boulders. */
public final class SkyBoulderGeometry {
    private SkyBoulderGeometry() { }

    public static boolean solid(long seed,int cx,int cy,int cz,int radius,int height,
                                int x,int y,int z) {
        double angle=SkyTerrainPatterns.unit(SkyTerrainPatterns.mix(seed,0,0,0,0x8a6c3L))*Math.PI*2;
        double ca=Math.cos(angle),sa=Math.sin(angle);
        double dx=x-cx,dz=z-cz;
        double rx=dx*ca+dz*sa,rz=-dx*sa+dz*ca;
        double axisX=radius*(.76+.43*SkyTerrainPatterns.unit(
                SkyTerrainPatterns.mix(seed,1,0,0,0x8a6c3L)));
        double axisZ=radius*(.74+.40*SkyTerrainPatterns.unit(
                SkyTerrainPatterns.mix(seed,2,0,0,0x8a6c3L)));
        double ny=(y-cy+.12*dx-.08*dz)/(double)height;
        double norm=(rx*rx)/(axisX*axisX)+(rz*rz)/(axisZ*axisZ)+ny*ny;
        // Three independent scales of erosion plus angular lobes rather than
        // the former almost-perfect single noisy ellipsoid.
        double coarse=SkyTerrainPatterns.noise3(seed,x,y,z,7,0x61be1L)-.5;
        double medium=SkyTerrainPatterns.noise3(seed,x,y,z,4,0x61be2L)-.5;
        double fine=SkyTerrainPatterns.noise3(seed,x,y,z,2,0x61be3L)-.5;
        double theta=Math.atan2(rz,rx);
        double lobes=.13*Math.sin(3*theta+angle)*Math.cos(ny*1.6)
                +.10*Math.sin(5*theta-angle*.7);
        double edge=1.0+.50*coarse+.28*medium+.10*fine+lobes;
        return norm<edge;
    }
}
