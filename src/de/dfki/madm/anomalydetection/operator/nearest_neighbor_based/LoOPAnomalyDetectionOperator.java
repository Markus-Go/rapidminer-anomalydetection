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
import com.rapidminer.operator.UserError;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeDouble;

import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNCollection;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNCollectionModel;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.LoOPEvaluator;

import java.util.Arrays;
import java.util.List;

/**
 * 
 * The operator calculates the outlier score based on Local outlier probablity,
 * proposed by Kriegel et al [2009].
 * 
 * LoOP score represents the probability that the object is a local density
 * outlier. The algorithm combines the benefits of local density approaches
 * being that it doesn't assume any distribution for the data as well as the
 * mathematical concepts of the statistical methods. The Fact that the score is
 * a probability facilitates comparisons. LoOP is also based on the nearest
 * neighbors set. The definition of the k-distance used is the same as the one
 * proposed by Breunig et al [1999; 2000] to handle duplicates.
 * 
 * @author Mennatallah Amer
 * 
 */

public class LoOPAnomalyDetectionOperator extends KNNAnomalyDetectionOperator {
	/**
	 * The parameter name for &quot; The normalization factor. The results are
	 * weakly affected by this factor. &quot;
	 **/
	public static String PARAMETER_LAMBDA = "normalization factor";

	public LoOPAnomalyDetectionOperator(OperatorDescription description) {
		super(description);

	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points, int[] weight) throws OperatorException {

		DistanceMeasure measure = getMeasureHelper().getInitializedMeasure(
				exampleSet);

		int n = points.length;
		double lamda = getParameterAsDouble(PARAMETER_LAMBDA);
		int k = getParameterAsInt(PARAMETER_K);
		double[] ret = {1};
		
		if (exampleSet.size() < 3) {
			throw new UserError(this, 142, k);
		}
		
		if (n > 1) {
			if (k >= n) {
				this.logWarning("Setting " + KNNAnomalyDetectionOperator.PARAMETER_K + " to #Datapoints-1.");
				k = n-1;
				//this.setParameter(KNNAnomalyDetectionOperator.PARAMETER_K, (n-1)+"");
			}
			boolean parallel = getParameterAsBoolean(PARAMETER_PARALLELIZE_EVALUATION_PROCESS);
			int numberOfThreads = getParameterAsInt(PARAMETER_NUMBER_OF_THREADS);
	
			readModel(n,k,points,weight,measure);
			LoOPEvaluator evaluator = new LoOPEvaluator(knnCollection, 
					measure, lamda,parallel, numberOfThreads, this,n,k,newCollection);
			
			ret = evaluator.evaluate();
			model = new KNNCollectionModel(exampleSet,knnCollection,measure);
			modelOutput.deliver(model);
			knnCollection = null;
		}
		return ret;
	}

	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		types.remove(1);
		types
				.add(
						1,
						new ParameterTypeDouble(
								PARAMETER_LAMBDA,
								"The normalization factor. The results are weakly affected by this factor. ",
								1, 3, 3, true));

		return types;
	}

}
