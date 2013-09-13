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
 * The class the does the actual COF algorithm.
 * 
 * @author Mennatallah Amer
 * 
 */
public class COFEvaluator extends KNNEvaluator {
	private int n;
	private int k;
	private boolean newCollection;
	public COFEvaluator(KNNCollection knnCollection, 
			DistanceMeasure measure, boolean parallel, int numberOfThreads, Operator logger) {
		super(knnCollection, false, measure, parallel, numberOfThreads, logger);
	}
	public COFEvaluator(KNNCollection knnCollection, 
			DistanceMeasure measure, boolean parallel, int numberOfThreads, Operator logger,int n, int k,boolean newCollection) {
		super(knnCollection, false, measure, parallel, numberOfThreads, logger, n, k , newCollection);
		this.n = n;
		this.k = k;
		this.newCollection = newCollection;
	}
	/**
	 * 
	 * The methods implements the COF algorithm.
	 * 
	 * @return array containing the cof score.
	 */
	private double[] cof() {
		int n = getN();
		int k = getKnnCollection().getK();
		int[][] neighborIndicies = getKnnCollection()
				.getNeighBorIndiciesSoFar();
		double[][] neighborDistances = getKnnCollection()
				.getNeighBorDistanceSoFar();
		double[][] points = getKnnCollection().getPoints();
		int[] weight = getKnnCollection().getWeight();
		LinkedList<Integer>[] kdist = getKnnCollection().getKdistNeighbors();
		DistanceMeasure measure = getMeasure();

		// The array that will contain the average chaining distance
		double[] acDist = new double[n];
		double[] cof = new double[n];

		// attributes used for intermediate calculations

		// tempDistances[x] contains the minimum distance to connect the set
		// already connected to the element with index indicies[x]
		double[] tempDistances = new double[n];
		int[] indicies = new int[n];

		int j;
		int size;

		// calculating average chaining distance
		// The average chaining distance has the following formula
		// (summation from i=1 to cardinality of 2*(cardinality-i+1) * ei
		// )/(cardinality *(cardinality-1))
		for (int i = 0; i < n; i++) {

			int cardinality = weight[i] - 1;

			size = k + kdist[i].size();

			int minIndex = 0;

			for (j = 0; j < k; j++) {
				tempDistances[j] = neighborDistances[i][j];
				indicies[j] = neighborIndicies[i][j];
				cardinality += weight[neighborIndicies[i][j]];
			}

			for (int x : kdist[i]) {
				tempDistances[j] = neighborDistances[i][k - 1];
				indicies[j] = x;
				cardinality += weight[x];
				j++;
			}

			

			double summation = 0;
			// weighSofar represents (cardinality -i+1) in  the above formula
			int weightSofar = cardinality - weight[i] + 1;
      
			double denominator = cardinality * (cardinality + 1);

			for (int l = 0; l < size; l++) {
				// in case we have X duplicates of the same point then we will have
				// the weight of the current edge equal to 2*(weightSofar + (weightSofar-1)+....+
				// (weightSofar-X+1)) which is equal to the summation of i from
				// i= weightSoFar-X+1 to weightSofar which is equal  (
				// weightSOFar*(weightSoFar+1) -
				// (weighSoFar-X)*(weighSofar-X+1))  let t1
				// =weightSOfar*(weightSofar+1) and t2 =
				// (weighSofar-X)*(weighSofar-X+1) then the weight of the
				// current edge should be equal to t1-t2

				// currentweight = t1
				int currentweight = weightSofar * (weightSofar + 1);

				// weighSofar = weightSofar -X
				weightSofar -= weight[indicies[minIndex]];

				// currentweight= currentweight- t2
				currentweight -= weightSofar * (weightSofar + 1);

				summation += currentweight * tempDistances[minIndex];

				// the index of the point just added to the set
				int currentIndex = indicies[minIndex];

				// an index of -1 indicates that the point was already reached
				// and thus shouldn't need to be reached again
				indicies[minIndex] = -1;

				// This contains the index of the point that is the nearest
				// neighbor of the set from the set indicies[0..j]
				minIndex = -1;

				for (j = 0; j < size; j++) {
					if (indicies[j] == -1)
						continue;

					double temp = measure.calculateDistance(
							points[currentIndex], points[indicies[j]]);
					if (temp < tempDistances[j])
						tempDistances[j] = temp;

					if (minIndex == -1
							|| tempDistances[minIndex] > tempDistances[j]
							|| (tempDistances[minIndex] == tempDistances[j] && indicies[j] < indicies[minIndex])) {
						// assigns the nearest neighbor if non exists or if
						// point i is nearer than the current nearest neighbor,
						// in case they have the same distance ties are broken
						// by taking the earlier index

						minIndex = j;
					}

				}

			}
			acDist[i] = summation / denominator;

		}

		// calculating cof
		for (int i = 0; i < n; i++) {

			int cardinality = weight[i] - 1;
			double summation = cardinality * acDist[i];
			for (j = 0; j < k; j++) {
				int currentIndex = neighborIndicies[i][j];
				summation += weight[currentIndex] * acDist[currentIndex];
				cardinality += weight[currentIndex];
			}
			for (int x : kdist[i]) {
				summation += weight[x] * acDist[x];
				cardinality += weight[x];
			}
			cof[i] = cardinality * acDist[i] / summation;

		}

		return cof;

	}

	/**
	 * The method is called to initialize the evaluation process.
	 */
	@Override
	public double[] evaluate() {
		super.evaluate();
		double[] cof = cof();
		return cof;
	}

	@Override
	public double[] reEvaluate(int step) {
		getKnnCollection().shrink(step);
		double[] cof = cof();
		return cof;

	}

	/** Method is overridden to avoid doing extra work **/
	@Override
	protected void setAnomalyScore(int i, double[] neighBorDistanceSoFar,
			int[] neighBorIndiciesSoFar, int numberOfNeighbors) {

	}

}

class Node implements Comparable<Node> {

	int index;
	double distance;

	public Node(int index, double distance) {
		this.index = index;
		this.distance = distance;
	}

	@Override
	public int compareTo(Node arg0) {
		if (distance < arg0.distance)
			return -1;
		if (distance > arg0.distance)
			return 1;
		if (index < arg0.index)
			return -1;
		if (index > arg0.index)
			return 1;
		return 0;
	}
}
