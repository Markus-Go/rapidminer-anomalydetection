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

import java.util.Arrays;
import java.util.List;

import com.rapidminer.example.Attributes;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.COFEvaluator;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNCollection;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNCollectionModel;


/**
 * The operator calculates the outlier score based on Connectivity Based Outlier
 * Factor , proposed by Tang el al(2002)
 * 
 * The COF is a modification of LOF algorithm (Breunig el al, 2000) proposed in
 * order to handle outliers deviating from low density patterns.
 * 
 * 
 * @author Mennatallah Amer
 * 
 */
public class COFAnomalyDetectionOperator extends KNNAnomalyDetectionOperator {
	
	

	public COFAnomalyDetectionOperator(OperatorDescription description) {
		super(description);
	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points, int[] weight) throws OperatorException {

		DistanceMeasure measure = getMeasureHelper().getInitializedMeasure(
				exampleSet);

		int n = points.length;

		int k = getParameterAsInt(PARAMETER_K);
		double[] ret = {1};
		
		if (n > 1) {
			if (k >= n) {
				this.logWarning("Setting " + KNNAnomalyDetectionOperator.PARAMETER_K + " to #Datapoints-1.");
				k = n-1;
				//this.setParameter(KNNAnomalyDetectionOperator.PARAMETER_K, (n-1)+"");
			}
			
			boolean parallel = getParameterAsBoolean(PARAMETER_PARALLELIZE_EVALUATION_PROCESS);
			int numberOfThreads = getParameterAsInt(PARAMETER_NUMBER_OF_THREADS);
	
			readModel(n,k,points,weight,measure);
			COFEvaluator evaluator = new COFEvaluator(knnCollection, measure,parallel, numberOfThreads, this,n,k,newCollection);
			ret = evaluator.evaluate();
			model = new KNNCollectionModel(exampleSet,knnCollection,measure);
			modelOutput.deliver(model);
			knnCollection = null;		
		}
		return ret;

	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		ParameterType  type=types.get(1);
		types.remove(type);
		return types;
	}

}
