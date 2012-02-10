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

import java.util.Arrays;


import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.Evaluator;

/**
 * This is the implementation of LOCI (Fast Outlier Detection Using the Local
 * Coorelation Integeral) proposed by S. papdimitriou el al (2003)
 * 
 * @author Mennatallah Amer
 * 
 */
public class LOCIEvaluator implements Evaluator {
	private DistanceMeasure measure;

	private double[][] points;
	private int n;
	private double alpha = 0.5;
	
	private int nmin;

	int [] weight;
	public LOCIEvaluator(DistanceMeasure measure, double alpha, int nmin , double[][] points, int []weight) {
		n = points.length;
		this.points = points;
		this.nmin= nmin;
		this.alpha= alpha;
		this.measure = measure;
		this.weight=weight;
	}

	private int find(int start, int end, double distance,
			DistancePair[] criticalDistances) {
		int low = start;
		int high = end-1;
		while (low < high) {
			int mid = (low + high+1) >> 1;
			if (criticalDistances[mid].distance <= distance) {
				low = mid;
			} else
				high = mid - 1;
		}

		return criticalDistances[low].cardinality;

	}

	public double[] evaluate() {
		// the result will contain MDEF/ SIgmaMDEF the higher this ratio is the
		// more outling the result is.Lower than or equal 3 is not considered an
		// outlier
		double[] result = new double[n];
		DistancePair[][] criticalDistances = new DistancePair[n][2 * n];
		int secondDimension = 2 * n;
		// preprocessing
		for (int i = 0; i < n; i++) {
			int firstIndex = i << 1;
			int secondIndex = firstIndex + 1;
			int current = secondIndex + 1;
			// cardinality -2 means that there actually a point
			criticalDistances[i][firstIndex] = new DistancePair(0, -2, i);
			// cardinality -1 means that there is no point just alpha critical
			// distance
			criticalDistances[i][secondIndex] = new DistancePair(0, -1, -1);
			for (int j = i + 1; j < n; j++) {
				// draw back this assumes that the distance measure is symmetric
				double currentDistance = measure.calculateDistance(points[i],
						points[j]);
				double alphaCurrentDistance = currentDistance/ alpha;
				
				criticalDistances[i][current++] = new DistancePair(
						currentDistance, -2, j);
				criticalDistances[i][current++] = new DistancePair(
						alphaCurrentDistance, -1, -1);

				criticalDistances[j][firstIndex] = new DistancePair(
						currentDistance, -2, i);
				
				criticalDistances[j][secondIndex] = new DistancePair(
						alphaCurrentDistance, -1, -1);

			}
			Arrays.sort(criticalDistances[i]);
			int cardinality = 0;

			for (int j = 0; j < secondDimension; j++) {

				if (criticalDistances[i][j].cardinality == -2) {
					cardinality+=weight[criticalDistances[i][j].index];
				}
				criticalDistances[i][j].cardinality = cardinality;
			}

		}
		// computation of MDEF
		for (int i = 0; i < n; i++) {
			result[i]=0;
			for (int j = 0; j < secondDimension; j++) {
				if(  criticalDistances[i][j].cardinality< nmin)
					continue;
				if (j != secondDimension - 1
						&& criticalDistances[i][j].distance == criticalDistances[i][j + 1].distance)
					continue;
			
				// alpha r distance
				double alphaR = criticalDistances[i][j].distance * alpha;
				
				int nPR = criticalDistances[i][j].cardinality;

				int nPRAlpha = find(0, secondDimension, alphaR,
						criticalDistances[i]);

				
				
				double summationNPRALpha = 0.0;
				// this is the loop I should try to vanish 
				for (int k = 0; k <= j; k++) {
					int index = criticalDistances[i][k].index;
					if (index == -1)
						continue;
					int currentNRPAlpa = find(0, secondDimension, alphaR,
							criticalDistances[index]);
					summationNPRALpha += currentNRPAlpa;

				}                                                                                                                                                                                                                                          
				double nHatPRAlpha = summationNPRALpha * 1.0 / nPR;
				
				double squaredNPRAlpha = 0.0;
				
				for (int k = 0; k <= j; k++) {
					int index = criticalDistances[i][k].index;
					if (index == -1)
						continue;
					int currentNRPAlpa = find(0, secondDimension, alphaR,
							criticalDistances[index]);
					double delta = currentNRPAlpa - nHatPRAlpha;
					
					squaredNPRAlpha += delta*delta;
					

				}       
			
				double sigmaPRAlpha = Math.sqrt(squaredNPRAlpha
						/ nPR);
				
				double MDEF = 1.0 - nPRAlpha / nHatPRAlpha;
				double sigmaMDEF = sigmaPRAlpha / nHatPRAlpha;
				double currentRes;
				if (sigmaMDEF==0)
					currentRes= 0;
				else currentRes= MDEF / sigmaMDEF;
				if(currentRes> result[i])
				result[i] =currentRes;
				
			}
			
		}

		return result;
	}

	
}

class DistancePair implements Comparable<DistancePair> {
	double distance;
	int cardinality;
	int index;

	public DistancePair(double distance, int cardinality, int index) {
		this.distance = distance;
		this.cardinality = cardinality;
		this.index = index;
	}

	@Override
	public int compareTo(DistancePair arg0) {
		if (distance < arg0.distance)
			return -1;
		if (distance > arg0.distance)
			return 1;
		return cardinality - arg0.cardinality;
	}

}
