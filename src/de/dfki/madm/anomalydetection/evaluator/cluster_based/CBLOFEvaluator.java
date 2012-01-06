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
 * The class that has the actual implementation of the CBLOF.
 * 
 * @author Mennatallah Amer
 * 
 */
public class CBLOFEvaluator implements Evaluator {
	
	

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

	/**
	 * boolean indicating that the cluster size as a weight factor as proposed
	 * by the original publication.
	 **/
	protected boolean weighting;
	
	/** indicates which cluster is large**/
	
	protected  boolean [] largeCluster;

	public CBLOFEvaluator(double alpha, double beta, DistanceMeasure measure,
			double[][] points, int[] belongsToCluster, double[][] centroids,
			int clusterSize[], boolean weighting) {
		
		this.measure = measure;
		this.points = points;
		this.belongsToCluster = belongsToCluster;
		this.centroids = centroids;
		this.clusterSize = clusterSize;
		this.weighting = weighting;
		largeCluster= assignLargeClusters(clusterSize, alpha, beta, points.length);

	}

	
	public static boolean[] assignLargeClusters(int clusterSize[], double alpha, double beta, int n ) {
		ClusterOrder[] clusterOrders=ClusterOrder.getOrderedClusters(clusterSize); 
		int numberOfClusters = clusterOrders.length;
		int numberOfLargeClusters = numberOfClusters;
		boolean [] result = new boolean[numberOfClusters];
		int sizeSofar = 0;
		double sizeNormal= alpha*n;
		// searches for the last large cluster according the parameters alpha
		// and beta using the definition defined be He et al (2003)
		for (int i = 0; i < numberOfClusters; i++) {
			sizeSofar += clusterOrders[i].getClusterSize();
			if (numberOfLargeClusters == numberOfClusters) {
				if (sizeSofar >= sizeNormal)
					numberOfLargeClusters = i;
				else {
					if (i != numberOfClusters - 1) {
						if (clusterOrders[i].getClusterSize() / clusterOrders[i+1].getClusterSize() >= beta)
							numberOfLargeClusters = i;
					}
				}
			}
			else break;
		}
		for (int i=0; i< numberOfClusters; i++)
			result[clusterOrders[i].getClusterIndex()]= i<=numberOfLargeClusters;
		
		return result;
	}

	/**
	 * The method the computes CBLOF
	 * 
	 * @param weighting
	 * 
	 * 
	 * @return The array containing the cblof scores.
	 * 
	 */
	public double[] evaluate() {
		int n = points.length;
		
		double[] cblof = new double[n];
		
		int numberOfClusters= centroids.length;
		
		// calculates cblof
		for (int i = 0; i < n; i++) {
			int clusterIndex = belongsToCluster[i];
			if (largeCluster[clusterIndex]) {
				// It is a large cluster
				cblof[i] = measure.calculateDistance(centroids[clusterIndex],
						points[i]);
				if (weighting)
					cblof[i] *= clusterSize[clusterIndex];
			} else {
				// It is a small cluster

				double MinDistance = Double.MAX_VALUE;

				// search for the nearest large cluster
				for (int j = 0; j <numberOfClusters; j++) {
					if(!largeCluster[j])
						continue;
					double temp = measure.calculateDistance(centroids[j],
							points[i]);
					if (temp < MinDistance)
						MinDistance = temp;
				}

				cblof[i] = MinDistance;
				if (weighting)
					cblof[i] *= clusterSize[clusterIndex];
			}

		}

		return cblof;
	}

}
