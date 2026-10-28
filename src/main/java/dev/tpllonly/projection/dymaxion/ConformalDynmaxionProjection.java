/*
 * tpll-only &mdash; standalone /tpll and /tpllcalc for modern Fabric.
 *
 * Portions of this file are derived from TerraPlusPlus
 * (https://github.com/BuildTheEarth/terraplusplus), Copyright (c) 2020 BuildTheEarth.net,
 * distributed under the MIT license.
 */
package dev.tpllonly.projection.dymaxion;

import dev.tpllonly.util.MathUtils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The conformal Dymaxion projection: the same icosahedron net as {@link DymaxionProjection}, but
 * with a per-triangle distortion field applied so that angles are (very nearly) preserved.
 *
 * <p>The field comes from a 256&nbsp;x&nbsp;256 grid of offsets per triangle, shipped as
 * {@value #VECTOR_FIELD_RESOURCE}. Projecting a point means solving, with Newton's method, for the
 * grid position whose interpolated offset lands on it &mdash; which is why the field is stored
 * inverted (it maps the conformal space back to the plain Dymaxion one).
 *
 * @see BTEDymaxionProjection, which wraps this in the layout BuildTheEarth uses
 */
public class ConformalDynmaxionProjection extends DymaxionProjection {

	/**
	 * Where the per-triangle vector field is loaded from: big-endian IEEE-754 doubles, an (x, y)
	 * pair for each of the (256 + 1) x (256 + 1) grid points a triangle is sampled at, minus the
	 * ones that fall outside it.
	 */
	private static final String VECTOR_FIELD_RESOURCE = "conformal.bin";

	/**
	 * The field is stored at a scale of 1.147... map units per unit; this brings it back to
	 * projection units.
	 */
	protected static final double VECTOR_SCALE_FACTOR = 1.0d / 1.1473979730192934d;

	/**
	 * How many segments a triangle's side is cut into when the distortion field is sampled.
	 */
	protected static final int SIDE_LENGTH = 256;

	/**
	 * The vector field is the same for every instance, so it is read from disk once and shared.
	 */
	private static InvertableVectorField sharedInverse;

	protected final InvertableVectorField inverse = sharedInverse();

	private static synchronized InvertableVectorField sharedInverse() {
		if (sharedInverse == null) {
			sharedInverse = loadVectorField();
		}
		return sharedInverse;
	}

	private static InvertableVectorField loadVectorField() {
		double[][] vx = new double[SIDE_LENGTH + 1][];
		double[][] vy = new double[SIDE_LENGTH + 1][];
		for (int i = 0; i <= SIDE_LENGTH; i++) {
			vx[i] = new double[SIDE_LENGTH + 1 - i];
			vy[i] = new double[SIDE_LENGTH + 1 - i];
		}

		// The offsets were generated as big-endian IEEE-754 doubles, (vx, vy) per grid point.
		try (DataInputStream in = new DataInputStream(ConformalDynmaxionProjection.class.getResourceAsStream(VECTOR_FIELD_RESOURCE))) {
			if (in == null) {
				throw new IllegalStateException("Missing projection data resource " + VECTOR_FIELD_RESOURCE);
			}

			for (int v = 0; v < SIDE_LENGTH + 1; v++) {
				for (int u = 0; u < SIDE_LENGTH + 1 - v; u++) {
					vx[u][v] = in.readDouble() * VECTOR_SCALE_FACTOR;
					vy[u][v] = in.readDouble() * VECTOR_SCALE_FACTOR;
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read " + VECTOR_FIELD_RESOURCE, e);
		}

		return new InvertableVectorField(vx, vy);
	}

	@Override
	protected double[] triangleTransform(double[] vec) {
		double[] c = super.triangleTransform(vec);

		double x = c[0];
		double y = c[1];

		c[0] /= ARC;
		c[1] /= ARC;

		c[0] += 0.5;
		c[1] += MathUtils.ROOT3 / 6;

		//use another interpolated vector to have a really good guess before using Newton's method
		//Note: foward was removed for now, will need to be added back if this improvement is ever re-implemented
		//c = forward.getInterpolatedVector(c[0], c[1]);
		//c = inverse.applyNewtonsMethod(x, y, c[0]/ARC + 0.5, c[1]/ARC + ROOT3/6, 1);

		//just use newtons method: slower
		c = this.inverse.applyNewtonsMethod(x, y, c[0], c[1], 5);//c[0]/ARC + 0.5, c[1]/ARC + ROOT3/6

		c[0] -= 0.5;
		c[1] -= MathUtils.ROOT3 / 6;

		c[0] *= ARC;
		c[1] *= ARC;

		return c;
	}

	@Override
	protected double[] inverseTriangleTransform(double x, double y) {

		x /= ARC;
		y /= ARC;

		x += 0.5;
		y += MathUtils.ROOT3 / 6;

		double[] c = this.inverse.getInterpolatedVector(x, y);
		return super.inverseTriangleTransform(c[0], c[1]);
	}

	@Override
	public double metersPerUnit() {
		return (40075017.0d / (2.0d * Math.PI)) / VECTOR_SCALE_FACTOR;
	}

	@Override
	public String toString() {
		return "Conformal Dymaxion";
	}

	/**
	 * The distortion field of a single triangle, and the bilinear (barycentric) interpolation used
	 * to read it at any point inside that triangle.
	 */
	private static class InvertableVectorField {
		private final double[][] vx;
		private final double[][] vy;

		public InvertableVectorField(double[][] vx, double[][] vy) {
			this.vx = vx;
			this.vy = vy;
		}

		public double[] getInterpolatedVector(double x, double y) {
			//scale up triangle to be triangleSize across
			x *= SIDE_LENGTH;
			y *= SIDE_LENGTH;

			//convert to triangle units
			double v = 2 * y / MathUtils.ROOT3;
			double u = x - v * 0.5;

			int u1 = (int) u;
			int v1 = (int) v;

			if (u1 < 0) {
				u1 = 0;
			} else if (u1 >= SIDE_LENGTH) {
				u1 = SIDE_LENGTH - 1;
			}

			if (v1 < 0) {
				v1 = 0;
			} else if (v1 >= SIDE_LENGTH - u1) {
				v1 = SIDE_LENGTH - u1 - 1;
			}

			double valx1;
			double valy1;
			double valx2;
			double valy2;
			double valx3;
			double valy3;
			double y3;
			double x3;

			double flip = 1;

			if (y < -MathUtils.ROOT3 * (x - u1 - v1 - 1) || v1 == SIDE_LENGTH - u1 - 1) {
				valx1 = this.vx[u1][v1];
				valy1 = this.vy[u1][v1];
				valx2 = this.vx[u1][v1 + 1];
				valy2 = this.vy[u1][v1 + 1];
				valx3 = this.vx[u1 + 1][v1];
				valy3 = this.vy[u1 + 1][v1];

				y3 = 0.5 * MathUtils.ROOT3 * v1;
				x3 = (u1 + 1) + 0.5 * v1;
			} else {
				valx1 = this.vx[u1][v1 + 1];
				valy1 = this.vy[u1][v1 + 1];
				valx2 = this.vx[u1 + 1][v1];
				valy2 = this.vy[u1 + 1][v1];
				valx3 = this.vx[u1 + 1][v1 + 1];
				valy3 = this.vy[u1 + 1][v1 + 1];

				flip = -1;
				y = -y;

				y3 = -(0.5 * MathUtils.ROOT3 * (v1 + 1));
				x3 = (u1 + 1) + 0.5 * (v1 + 1);
			}

			//TODO: not sure if weights are right (but weirdly mirrors stuff so there may be simplifcation yet)
			double w1 = -(y - y3) / MathUtils.ROOT3 - (x - x3);
			double w2 = 2 * (y - y3) / MathUtils.ROOT3;
			double w3 = 1 - w1 - w2;

			return new double[]{ valx1 * w1 + valx2 * w2 + valx3 * w3, valy1 * w1 + valy2 * w2 + valy3 * w3,
					(valx3 - valx1) * SIDE_LENGTH, SIDE_LENGTH * flip * (2 * valx2 - valx1 - valx3) / MathUtils.ROOT3,
					(valy3 - valy1) * SIDE_LENGTH, SIDE_LENGTH * flip * (2 * valy2 - valy1 - valy3) / MathUtils.ROOT3 };
		}

		public double[] applyNewtonsMethod(double expectedf, double expectedg, double xest, double yest, int iter) {
			for (int i = 0; i < iter; i++) {
				double[] c = this.getInterpolatedVector(xest, yest);

				double f = c[0] - expectedf;
				double g = c[1] - expectedg;
				double dfdx = c[2];
				double dfdy = c[3];
				double dgdx = c[4];
				double dgdy = c[5];

				double determinant = 1 / (dfdx * dgdy - dfdy * dgdx);

				xest -= determinant * (dgdy * f - dfdy * g);
				yest -= determinant * (-dgdx * f + dfdx * g);
			}

			return new double[]{ xest, yest };
		}
	}
}