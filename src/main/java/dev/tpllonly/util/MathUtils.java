/*
 * MIT License
 *
 * Copyright (c) 2022 BuildTheEarth.net team
 *
 * Adapted from TerraPlusPlus (net.buildtheearth.terraplusplus.util.MathUtils), MIT licensed.
 */
package dev.tpllonly.util;

/**
 * The maths the dymaxion projection needs: conversions between geographic, spherical and
 * Cartesian coordinates, plus the ZYZ rotation matrices used to swing an icosahedron face onto
 * the reference triangle.
 */
public final class MathUtils {

    /**
     * Square root of 3.
     */
    public static final double ROOT3 = Math.sqrt(3);

    /**
     * Two times pi.
     */
    public static final double TAU = 2 * Math.PI;

    private MathUtils() {
    }

    /**
     * Converts geographic coordinates to spherical coordinates on a sphere of radius 1.
     *
     * @param geo - geographic coordinates as a double array of length 2, {longitude, latitude}, in degrees
     * @return the corresponding spherical coordinates in radians: {longitude, colatitude}
     */
    public static double[] geo2Spherical(double[] geo) {
        double lambda = Math.toRadians(geo[0]);
        double phi = Math.toRadians(90 - geo[1]);
        return new double[]{ lambda, phi };
    }

    /**
     * Converts spherical coordinates on a sphere of radius 1 back to geographic coordinates.
     *
     * @param spherical - spherical coordinates in radians as a double array of length 2: {longitude, colatitude}
     * @return the corresponding geographic coordinates in degrees: {longitude, latitude}
     */
    public static double[] spherical2Geo(double[] spherical) {
        double lon = Math.toDegrees(spherical[0]);
        double lat = 90 - Math.toDegrees(spherical[1]);
        return new double[]{ lon, lat };
    }

    /**
     * Converts spherical coordinates to Cartesian coordinates on a sphere of radius 1.
     *
     * @param spherical - spherical coordinates in radians as a double array of length 2: {longitude, colatitude}
     * @return the corresponding Cartesian coordinates: {x, y, z}
     */
    public static double[] spherical2Cartesian(double[] spherical) {
        double sinphi = Math.sin(spherical[1]);
        double x = sinphi * Math.cos(spherical[0]);
        double y = sinphi * Math.sin(spherical[0]);
        double z = Math.cos(spherical[1]);
        return new double[]{ x, y, z };
    }

    /**
     * Converts Cartesian coordinates to spherical coordinates on a sphere of radius 1.
     *
     * @param cartesian - Cartesian coordinates as double array of length 3: {x, y, z}
     * @return the spherical coordinates of the corresponding normalized vector: {longitude, colatitude} in radians
     */
    public static double[] cartesian2Spherical(double[] cartesian) {
        double lambda = Math.atan2(cartesian[1], cartesian[0]);
        double phi = Math.atan2(Math.sqrt(cartesian[0] * cartesian[0] + cartesian[1] * cartesian[1]), cartesian[2]);
        return new double[]{ lambda, phi };
    }

    /**
     * Produces the matrix of a ZYZ Euler rotation by the given angles.
     */
    public static double[][] produceZYZRotationMatrix(double a, double b, double c) {
        double sina = Math.sin(a), cosa = Math.cos(a);
        double sinb = Math.sin(b), cosb = Math.cos(b);
        double sinc = Math.sin(c), cosc = Math.cos(c);

        double[][] mat = new double[3][3];
        mat[0][0] = cosa * cosb * cosc - sinc * sina;
        mat[0][1] = -sina * cosb * cosc - sinc * cosa;
        mat[0][2] = cosc * sinb;
        mat[1][0] = sinc * cosb * cosa + cosc * sina;
        mat[1][1] = cosc * cosa - sinc * cosb * sina;
        mat[1][2] = sinc * sinb;
        mat[2][0] = -sinb * cosa;
        mat[2][1] = sinb * sina;
        mat[2][2] = cosb;
        return mat;
    }

    /**
     * Matrix-vector product for a 3x3 matrix and a 3x1 vector.
     */
    public static double[] matVecProdD(double[][] m, double[] v) {
        double[] r = new double[v.length];
        for (int i = 0; i < r.length; i++) {
            for (int j = 0; j < m[i].length; j++) {
                r[i] += m[i][j] * v[j];
            }
        }
        return r;
    }
}
