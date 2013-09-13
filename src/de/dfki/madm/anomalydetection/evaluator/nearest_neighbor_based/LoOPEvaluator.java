/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2011 by Deutsches Forschungszentrum fuer
 *  Kuenstliche Intelligenz GmbH or its licensors, as applicable.
 *
 *  This is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this software. If not, see <http://www.gnu.org/licenses/.
 *
 * Author: Mennatallah Amer (mennatallah.amer@student.guc.edu.eg)
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 *
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based;

import java.util.LinkedList;

import com.rapidminer.operator.Operator;
import com.rapidminer.tools.math.similarity.DistanceMeasure;

/**
 * The class that implements LoOP algorithm.
 * 
 * @author Mennatallah Amer
 * @author Markus Goldstein
 * 
 */
public class LoOPEvaluator extends KNNEvaluator {

	private double lambda;
	private int n;
	private int k;
	private boolean newCollection;
	public LoOPEvaluator(KNNCollection knnCollection,
			DistanceMeasure measure, double lambda, boolean parallel, int numberOfThreads, Operator logger) {
		super(knnCollection, false, measure, parallel, numberOfThreads, logger);
		this.lambda = lambda;
	}
	public LoOPEvaluator(KNNCollection knnCollection,
			DistanceMeasure measure, double lambda, boolean parallel, int numberOfThreads, Operator logger,int n , int k , boolean newCollection) {
		super(knnCollection, false, measure, parallel, numberOfThreads, logger,n ,k, newCollection);
		this.lambda = lambda;
		this.n = n;
		this.k = k;
		this.newCollection = newCollection;
	}
	/** The method is overridden to avoid doing extra computation **/
	@Override
	protected void setAnomalyScore(int i, double[] neighBorDistanceSoFar,
			int[] neighBorIndiciesSoFar, int numberOfNeighbors) {

	}

	@Override
	public double[] evaluate() {
		super.evaluate();
		double[] res = LoOp();
		return res;
	}
	@Override
	public double[] reEvaluate(int step) {
		getKnnCollection().shrink(step);
		double[] res = LoOp();
		return res;
	}

	/**
	 * The implementation of LoOp
	 * 
	 * @return An array containing LoOP values.
	 */
	private double[] LoOp() {
		int n = getN();
		int[][] neighborIndicies = getKnnCollection()
				.getNeighBorIndiciesSoFar();
		double[][] neighborDistances = getKnnCollection()
				.getNeighBorDistanceSoFar();
		int[] number = getKnnCollection().getNumberOfNeighborsSoFar();
		int[] weight = getKnnCollection().getWeight();
		LinkedList<Integer>[] kdistNeighbor = getKnnCollection()
				.getKdistNeighbors();

		double[] pdist = new double[n];
		double sqrt2 = Math.sqrt(2.0);

		// calcualating pdist
		for (int i = 0; i < n; i++) {
			int end = number[i];
			int cardinality = weight[i] - 1;
			double squaredSum = 0.0;
			int start = 0;
			for (int j = start; j < end; j++) {
				int size = weight[neighborIndicies[i][j]];
				double dist = neighborDistances[i][j];
				cardinality += size;
				squaredSum += size * dist * dist;

			}

			for (int currentIndex : kdistNeighbor[i]) {
				squaredSum += weight[currentIndex]
						* neighborDistances[i][end - 1]
						* neighborDistances[i][end - 1];
				cardinality += weight[currentIndex];
			}
			pdist[i] = lambda * Math.sqrt(squaredSum / cardinality);

		}

		double[] PLOF = new double[n];

		// calculating plof
		double sumSequaredPLOF = 0.0;
		for (int i = 0; i < n; i++) {
			int start = 0;
			int end = number[i];
			int cardinality = weight[i] - 1;
			double sumPDist = cardinality * pdist[i];

			for (int j = start; j < end; j++) {
				int size = weight[neighborIndicies[i][j]];
				cardinality += size;
				sumPDist += size * pdist[neighborIndicies[i][j]];
			}

			for (int currentIndex : kdistNeighbor[i]) {
				sumPDist += weight[currentIndex] * pdist[currentIndex];
				cardinality += weight[currentIndex];
			}

			PLOF[i] = cardinality * pdist[i] / sumPDist - 1;
			sumSequaredPLOF += PLOF[i] * PLOF[i];

		}
		double mean = sumSequaredPLOF / n;
		double nPLOF = lambda * Math.sqrt(mean) * sqrt2;

		double[] LoOp = new double[n];
		for (int i = 0; i < n; i++) {
			LoOp[i] = Math.max(0, this.erf(PLOF[i] / nPLOF));
		}
		return LoOp;

	}
	
	/**
	 * This is the implementation of Gaussian Error Function.
	 * We want the result as precise as possible, due to the 
	 * fact that minor errors can change the ranking.
	 * 
	 * That's why I expand the Taylor series in a loop up to 10000.
	 * Theoretically, you should be able in Java to compute up to x=26
	 * before hitting infinity. In Practice, things get imprecise for x>4
	 * 
	 * Due to this, I
	 * - return 1 for x > 5.5
	 * - use Chebyshev approximation for 3 < x < 5.5
	 * - expand Taylor series for x <= 3
	 * 
	 */
	private double erf(double x) {
		// Return early if value out of precision interval
		if (x >= 6.0) {return (double)1.0;}
		if (x <= -6.0) {return (double)-1.0;}
		
		if (Math.abs(x) > 3.0) {
			// According to Numerical Recipies in C, 6.2, pp 221
			// Approximation from Chebyshev correct up to 1.2 * 10 ^ -7
			double t = 1.0 / ( 1.0 + 0.5*Math.abs(x) );
			double res = 1- t * Math.exp(-Math.abs(x)*Math.abs(x) - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 + 
					t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-0.82215223 + t * 0.17087277)))))))));
			return x >= 0.0 ? res : -res;
		}
		
		// expand Taylor series up to 1,000 summands
		// precision should be up to 10 ^ -11
		double zz = x * x;
		double z = Math.abs(x);
		double res = 0.0;
		double mult = 1.0;
		
		for (int n = 0; n < 1000; n++) {
			double diff = (z * mult) / (double)(2*n+1); 
			res += diff;
			if (Math.abs(diff) < 0.0000000000001) {
				break;
			}
			mult *= -zz / (double)(n + 1.0);
			assert (mult == Double.POSITIVE_INFINITY);
		}
		res *= ((double)2.0 / Math.sqrt(Math.PI));
		return x >= 0.0 ? res : -res;
	}
}