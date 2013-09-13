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
package de.dfki.madm.anomalydetection.operator.nearest_neighbor_based;

import com.rapidminer.example.Attributes;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNCollection;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNCollectionModel;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.LOFEvaluator;


import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * The LOF anomaly detection calculates the anomaly score according to the local
 * outlier factor algorithm proposed by Breunig et al[1999;2000].
 * </p>
 * 
 * <p>
 * LOF is one of the earliest local density based approaches proposed. There are
 * several steps in the calculation of the LOF. The initial step involves
 * getting the nearest neighbors set.The definition of the k-distance employed
 * is the one proposed in the original paper in order to handle duplicates. The
 * definition states that the k-distance(p) has at least k neighbors with
 * distinct spatial coordinates that have a distance less than or equal it and
 * at most k-1 of such neighbors with distance strictly less than it. The
 * reachability distance (reach-dist(p,o)) is the maximum of the distance
 * between point p and o and the k-distance(o). The local reachability is the
 * inverse of the average reachability distance over the nearest neighborhood
 * set. Finally the LOF is calculated as the average of the ratio of the local
 * reachability density over the neighborhood set. The values of the LOF
 * oscillates with the change in the size of the neighborhood. Thus a range is
 * defined for the size of the neighborhood. The maximum LOF over that range is
 * taken as the final LOF score.
 * </p>
 * <p>
 * A normal instance has an outlier value of approximately 1, while outliers
 * have values greater than 1.
 * </p>
 * 
 * @author Mennatallah Amer
 * 
 */
public class LOFAnomalyDetectionOperator extends KNNAnomalyDetectionOperator {

	public static String PARAMETER_MINIMUM_K = "k_min (MinPtsLB)";
	public static String PARAMETER_MAXIMUM_K = "k_max (MinPtsUB)";

	public LOFAnomalyDetectionOperator(OperatorDescription description) {
		super(description);
	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points, int[] weight) throws OperatorException {
		DistanceMeasure measure = getMeasureHelper().getInitializedMeasure(
				exampleSet);
		
		int n = points.length;
		int minK = getParameterAsInt(PARAMETER_MINIMUM_K);
		int maxK = getParameterAsInt(PARAMETER_MAXIMUM_K);
		double[] ret = {1};

		if (n > 1) {
			if (maxK >= n) {
				this.logWarning("Setting " + PARAMETER_MAXIMUM_K + " to "+ (n-1) + " because there cannot be more neighbors than data points.");
				maxK = n-1;
				//this.setParameter(PARAMETER_MAXIMUM_K, maxK+"");
			}
			if (maxK < minK) {
				this.logWarning("Setting " + PARAMETER_MINIMUM_K + " to "+ maxK + " to make UpperBound at least as large as LowerBound.");
				minK = maxK;
				//this.setParameter(PARAMETER_MINIMUM_K, minK+"");
			}
			boolean parallel = getParameterAsBoolean(PARAMETER_PARALLELIZE_EVALUATION_PROCESS);
			int numberOfThreads = getParameterAsInt(PARAMETER_NUMBER_OF_THREADS);
			
			readModel(n,maxK,points,weight,measure);
			//KNNCollection knnCollection = new KNNCollection(n, maxK, points, weight);
			LOFEvaluator evaluator = new LOFEvaluator(minK, knnCollection, 
					measure,parallel, numberOfThreads, this, n,  maxK ,  newCollection);
			ret = evaluator.evaluate();
			if(newCollection) {
				model = new KNNCollectionModel(exampleSet,knnCollection,measure);
			}
			else {
				model = new KNNCollectionModel(exampleSet,modelInput.getData(KNNCollectionModel.class).get(),measure);
			}
			modelOutput.deliver(model);
			knnCollection = null;
			
		}
		return ret;
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		types.get(0).setKey(PARAMETER_MINIMUM_K);
		types.get(0).setDescription("The lower bound of MinPts");
		types.remove(1);

		types.add(1, new ParameterTypeInt(PARAMETER_MAXIMUM_K,
				"The upper bound of the MinPts ", 1, Integer.MAX_VALUE, 20,
				false));

		return types;
	}

}
