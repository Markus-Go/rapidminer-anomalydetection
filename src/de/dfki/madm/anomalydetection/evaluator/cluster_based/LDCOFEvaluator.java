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

package de.dfki.madm.anomalydetection.evaluator.cluster_based;

import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.Evaluator;

/**
 * The evaluator of LDCOF algorithm. This is where the algorithm logic is
 * implemented.
 * 
 * @author Mennatallah Amer
 * 
 */
public class LDCOFEvaluator implements Evaluator {

	/** The measure used to calculate the distances **/
	protected DistanceMeasure measure;

	/** The points in the example set. **/
	protected double[][] points;

	/** Maps each point to a cluster. **/
	protected int[] belongsToCluster;

	/** The centroids of the clusters **/
	protected double[][] centroids;

	/** The size of each cluster **/
	protected int clusterSize[];

	/** indicates which cluster is large **/
	protected boolean[] largeCluster;

	/**
	 * Constructor used when the LDCOF uses the method defined in CBLOF to
	 * divide the clusters into small and large clusters.
	 * 
	 */

	public LDCOFEvaluator(double alpha, double beta, DistanceMeasure measure,
			double[][] points, int[] belongsToCluster, double[][] centroids,
			int[] clusterSize) {

		largeCluster = CBLOFEvaluator.assignLargeClusters(clusterSize, alpha,
				beta, points.length);
		this.measure = measure;
		this.points = points;
		this.belongsToCluster = belongsToCluster;
		this.clusterSize = clusterSize;
		this.centroids = centroids;

	}

	/**
	 *Constructor used when the division into small and large clusters is based
	 * on the minimum size of the large cluster
	 * 
	 */
	public LDCOFEvaluator(double alpha, DistanceMeasure measure,
			double[][] points, int[] belongsToCluster, double[][] centroids,
			int[] clusterSize) {

		this.measure = measure;
		this.points = points;
		this.belongsToCluster = belongsToCluster;
		this.clusterSize = clusterSize;
		this.centroids = centroids;
		largeCluster = assignLargeClusters(clusterSize, alpha * points.length
				/ centroids.length);

	}

	/**
	 * The methods identifies each cluster as a small or large cluster based on
	 * the minimum large cluster size
	 * 
	 * @param clusterSize
	 *            The size of each cluster
	 * @param minimumClusterSize
	 *            The minimum size of large clusters
	 * @return
	 */
	public static boolean[] assignLargeClusters(int[] clusterSize,
			double minimumClusterSize) {
		boolean[] result = new boolean[clusterSize.length];
		for (int i = 0; i < clusterSize.length; i++) {
			result[i] = clusterSize[i] >= minimumClusterSize;
		}
		return result;

	}

	public double[] evaluate() {
		int n = points.length;
		int numberOfClusters = centroids.length;
		double[] result = new double[n];
		double[] distances = new double[n];
		int[] belongsToLargeCluster = new int[n];
		double[] summationDistances = new double[numberOfClusters];
		for (int i = 0; i < n; i++) {
			int clusterIndex = belongsToCluster[i];
			if (largeCluster[clusterIndex]) {
				// It is a large cluster
				distances[i] = measure.calculateDistance(
						centroids[clusterIndex], points[i]);
				summationDistances[clusterIndex] += distances[i];
				} else {
				// It is a small cluster
				double MinDistance = Double.MAX_VALUE;

				// search for the nearest large cluster
				for (int j = 0; j < numberOfClusters; j++) {
					if (!largeCluster[j])
						continue;
					double temp = measure.calculateDistance(centroids[j],
							points[i]);
					if (temp < MinDistance) {
						MinDistance = temp;
						clusterIndex = j;
					}
				}

				distances[i] = MinDistance;

			}

			belongsToLargeCluster[i] = clusterIndex;
		}

		for (int i = 0; i < numberOfClusters; i++)
			summationDistances[i] /= clusterSize[i];

		for (int i = 0; i < n; i++) {
			if(summationDistances[belongsToLargeCluster[i]]== 0.0) {
				result[i] = 0;
			}
			else {
				result[i] = distances[i]
						/ summationDistances[belongsToLargeCluster[i]];
			}
		
		}

		return result;
	}

}
