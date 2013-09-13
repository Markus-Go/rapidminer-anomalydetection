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
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.metadata.DistanceMeasurePrecondition;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.conditions.BooleanParameterCondition;
import com.rapidminer.tools.math.similarity.DistanceMeasure;
import com.rapidminer.tools.math.similarity.DistanceMeasureHelper;
import com.rapidminer.tools.math.similarity.DistanceMeasures;

import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNCollection;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNCollectionModel;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.KNNEvaluator;

/**
 * 
 * This operator calculates the anomaly score based on the K Nearest Neighbors
 * implementation. The outlier score is by default the average of the distance
 * to the nearest neighbors, it can be set to the distance to the kth nearest
 * neighbor which is similar to the algorithm proposed by Ramaswamy et al [2000]
 * by setting the corresponding parameter. The outlier score is calculated
 * according to the measure type selected. The higher the outlier the more
 * anomalous the instance is.
 * 
 * 
 * @author Mennatallah Amer
 * 
 */
public class KNNAnomalyDetectionOperator extends
		AbstractNearestNeighborBasedAnomalyDetectionOperator {
	
	/** The parameter name for &quot; This parameter defines the number of neighbours to be considered &quot;**/
	public static final String PARAMETER_K = "k";
	
	/** The parameter name for &quot;Sets the anomaly score to the kth-neighbor-distance like the algorithm proposed by Ramaswamy et al (2000)&quot;**/
	public static final String PARAMETER_KTH_NEIGHBOR_DISTANCE = "use k-th neighbor distance only (no average)";
	
	/**The parameter name for &quot;Specifies the number of threads for execution.&quot; Specifies that evaluation process should be performed in parallel &quot; **/
	public static final String PARAMETER_NUMBER_OF_THREADS = "number of threads";
	/** The parameter name for &quot; **/
	public static final String PARAMETER_PARALLELIZE_EVALUATION_PROCESS = "parallelize evaluation process";
	public OutputPort modelOutput = getOutputPorts().createPort("model");
	public InputPort modelInput = getInputPorts().createPort("model");
	
	private DistanceMeasureHelper measureHelper = new DistanceMeasureHelper(this);
	protected KNNCollection knnCollection = null;
	protected boolean newCollection = false;
	protected KNNCollectionModel model = null;
	
	public KNNAnomalyDetectionOperator(OperatorDescription description) {
		super(description);
		getExampleSetInput().addPrecondition(
				new DistanceMeasurePrecondition(getExampleSetInput(), this));
	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points, int[] weight) throws OperatorException {
		DistanceMeasure measure = measureHelper
				.getInitializedMeasure(exampleSet);
		int n = points.length;
		int k = getParameterAsInt(PARAMETER_K);
		boolean kth = getParameterAsBoolean(PARAMETER_KTH_NEIGHBOR_DISTANCE );
		boolean parallel = getParameterAsBoolean(PARAMETER_PARALLELIZE_EVALUATION_PROCESS);
		int numberOfThreads = getParameterAsInt(PARAMETER_NUMBER_OF_THREADS);
		double[] ret = {1};
		
		if (n > 1) {
			if (k >= n) {
				this.logWarning("Setting " + KNNAnomalyDetectionOperator.PARAMETER_K + " to #Datapoints-1.");
				k = n-1;
				//this.setParameter(KNNAnomalyDetectionOperator.PARAMETER_K, (n-1) + "");
			}
			readModel(n,k,points,weight,measure);
			KNNEvaluator evaluator = new KNNEvaluator(knnCollection, kth, measure, parallel, numberOfThreads, this,n,k,newCollection);
			ret = evaluator.evaluate();
			model = new KNNCollectionModel(exampleSet,knnCollection,measure);
			modelOutput.deliver(model);
			knnCollection = null;
		}
		return ret;
	}

	public DistanceMeasureHelper getMeasureHelper() {
		return measureHelper;
	}
	public void readModel(int n, int k, double[][] points,int[] weight,DistanceMeasure measure) throws OperatorException {
		if(modelInput.isConnected()){
			KNNCollectionModel input;
			input = modelInput.getData(KNNCollectionModel.class);
			knnCollection = input.get();
			newCollection = false;
			if(k>knnCollection.getK() || !Arrays.deepEquals(knnCollection.getPoints(),points) ||!measure.getClass().toString().equals(input.measure.getClass().toString()) ){
				if(k>knnCollection.getK()) {
					this.logNote("Model at input port can not be used (k too small).");
				}
				else {
					this.logNote("Model at input port can not be used (Model andExampleSet not matching).");
				}
				knnCollection = new KNNCollection(n, k, points, weight);
				newCollection = true;
				
			}
			else{
				this.logNote(" Model at input port used for speeding up the operator.");
			}
			if(k<knnCollection.getK()){
				knnCollection = KNNCollection.clone(knnCollection);
				knnCollection.shrink(knnCollection.getK()-k);
			}
			}
	else {
		knnCollection = new KNNCollection(n, k, points, weight);
		newCollection = true;
	}
		
}
	

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types =super.getParameterTypes();
		types
				.add(new ParameterTypeInt(
						PARAMETER_K,
						"This parameter defines the number of neighbours to be considered",
						1, Integer.MAX_VALUE, 10, false));
		ParameterType type = new ParameterTypeBoolean(
				PARAMETER_KTH_NEIGHBOR_DISTANCE ,
				"Sets the anomaly score to the kth-neighbor-distance like the algorithm proposed by Ramaswamy et al (2000) ",
				false, false);

		types.add(type);
		
		types.addAll(DistanceMeasures.getParameterTypes(this));

		types
				.add(new ParameterTypeBoolean(
						PARAMETER_PARALLELIZE_EVALUATION_PROCESS,
						"Specifies that evaluation process should be performed in parallel",
						false, false));
		type = (new ParameterTypeInt(PARAMETER_NUMBER_OF_THREADS,
				"Specifies the number of threads for execution.", 1,
				Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(),
				false));
		type.registerDependencyCondition(new BooleanParameterCondition(this,
				PARAMETER_PARALLELIZE_EVALUATION_PROCESS, true, true));
		types.add(type);

		return types;

	}

}
