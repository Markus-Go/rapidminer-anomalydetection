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
 * The class that implements INFLO algorithm.
 * 
 * @author Mennatallah Amer
 *
 */
public class INFLOEvaluator extends KNNEvaluator {


	private boolean newCollection;
	public INFLOEvaluator(KNNCollection knnCollection,
			DistanceMeasure measure, boolean parallel, int numberOfThreads, Operator logger) {
		super(knnCollection, false, measure, parallel, numberOfThreads, logger);
	}
	public INFLOEvaluator(KNNCollection knnCollection,
			DistanceMeasure measure, boolean parallel, int numberOfThreads, Operator logger,int n, int k , boolean newCollection) {
		super(knnCollection, false, measure, parallel, numberOfThreads, logger,n,k,newCollection);
		this.newCollection = newCollection;
	}

	public double [] evaluate() {

		super.evaluate();
		double[] inflo = inflo();
		return inflo;

	}
	@Override
	public double[] reEvaluate(int step) {
		getKnnCollection().shrink(step);
		double[] inflo = inflo();
		return inflo;
		
	}

	private double[] inflo() {
		int[][] neighbors = getKnnCollection().getNeighBorIndiciesSoFar();
		double[][] distances = getKnnCollection().getNeighBorDistanceSoFar();
		int[] neighborNumbers = getKnnCollection().getNumberOfNeighborsSoFar();
		int[] weight = getKnnCollection().getWeight();
		LinkedList<Integer> [] kdistNeighbors = getKnnCollection().getKdistNeighbors();
		int n = getN();
		double[] inflo = new double[n];
		
		// for intermediate work 
		int[] cardinality = new int[n];
		double[] summationDensities = new double[n];

		for (int i = 0; i < n; i++) {
			int end = neighborNumbers[i];
			double kdist = distances[i][end - 1];
			
			cardinality[i]+= weight[i]-1;
			summationDensities[i]+= (weight[i]-1)* 1/kdist;
			for (int j = 0; j < end; j++) {
				int currentIndex = neighbors[i][j];
				int currentWeight = weight[currentIndex];
				cardinality[i] += currentWeight;
				double currentDistance = distances[i][j];
				double currentKdist = distances[currentIndex][neighborNumbers[currentIndex] - 1];

				summationDensities[i] += currentWeight * 1.0 / currentKdist;
				if (currentDistance > currentKdist) {
					cardinality[currentIndex] += weight[i];
					summationDensities[currentIndex] += weight[i] * 1.0 / kdist;

				}
			}

			for(int currentIndex: kdistNeighbors[i])
			{
				int currentWeight= weight[currentIndex];
				cardinality[i] += currentWeight;
				double currentDistance = distances[i][neighborNumbers[i]-1];
				double currentKdist = distances[currentIndex][neighborNumbers[currentIndex] - 1];

				summationDensities[i] += currentWeight* 1.0 / currentKdist;
				if (currentDistance > currentKdist) {
					cardinality[currentIndex] += weight[i];
					summationDensities[currentIndex] +=  weight[i]* 1.0 / kdist;

				}
				
			}
		}

		for (int i = 0; i < n; i++) {
			int end = neighborNumbers[i];
			double kdist = distances[i][end - 1];
			inflo[i] = summationDensities[i] * kdist / cardinality[i];

		}

		return inflo;

	}

	@Override
	protected void setAnomalyScore(int i, double[] neighBorDistanceSoFar,
			int[] neighBorIndiciesSoFar, int numberOfNeighbors) {

	}

}
