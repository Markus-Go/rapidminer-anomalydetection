/*
 * RapidMiner Anomaly Detection Extension
 * 
 * Copyright (C) 2009-2012 by Deutsches Forschungszentrum fuer Kuenstliche
 * Intelligenz GmbH or its licensors, as applicable.
 * 
 * This is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this software. If not, see <http://www.gnu.org/licenses/.
 * 
 * Author: Patrick Kalka, Markus Goldstein
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 * 
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.operator.cluster_based;

import java.util.LinkedList;
import java.util.List;

import com.rapidminer.example.Attributes;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeCategory;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.conditions.BooleanParameterCondition;
import com.rapidminer.parameter.conditions.EqualTypeCondition;
import com.rapidminer.tools.RandomGenerator;
import com.rapidminer.tools.math.similarity.DistanceMeasure;
import com.rapidminer.tools.math.similarity.DistanceMeasures;

import de.dfki.madm.anomalydetection.evaluator.cluster_based.CMGOSEvaluator;

/**
 * The Operator for Covariance Matrix anomaly score.
 * 
 * @author Patrick Kalka
 * @author Markus Goldstein
 */
public class CMGOSAnomalyDetectionOperator extends AbstractClusteringAnomalyDetectionOperator {

	
	public static final String[] COV = new String[] {
		"Reduction",
		"Regularisation",
		"MCD"
	};
	
	public static final int METHOD_COV_REDUCTION = 0;
	public static final int METHOD_COV_REGULARIZE = 1;
	public static final int METHOD_COV_MCD = 2;
	

	/**
	 * 
	 **/
	public static final String PARAMETER_LAMBDA = "lambda";
	public static final String PARAMETER_LAMBDA_DESCRIPTION = "Lambda for regularization (see Friedmann). A lambda of 0.0 menas QDA (each cluster has its own covariance) and a lambda of 1.0 means LDA (a global covariance matrix).";

	/**
	 * 
	 **/
	public static final String PARAMETER_COVARIANCE = "covariance estimation";
	public static final String PARAMETER_COVARIANCE_DESCRIPTION = "The algorithm to estimate the covariance matrics. Reduction is the simplest method whereas the other two are more complex. Details can be found in the papers (see Operator description).";
	/**
	 * 
	 **/
	public static final String PARAMETER_H = "h (non-outlier instances)";
	public static final String PARAMETER_H_DESCRIPTION = "This parameter specifies the number of samples for fastMCD/MCD to be used for a computation (non-outliers). If set to -1 it is automatically computed according to the 'probability for normal class'. Friedmann et al recommend to use 75% of the examples as a good estimate. The upper bound is the numer of examples and the lower bound is (number of examples * dimensions +1)/2. Values exceeding these limits will be replaced by the limit. ";
	/**
	 * 
	 **/
	public static final String PARAMETER_POINTS_SUBSET = "instances in a subset";
	public static final String PARAMETER_POINTS_SUBSET_DESCRIPTION = "Defines the number of instances in a (random) subset used in fastMCD. Friedmann recommends to have at most 5 subsets, which menas this number should not be lower then (number of examples / 5).";
	/**
	 * 
	 **/
	public static final String PARAMETER_FMCD = "threshold for fastMCD";
	public static final String PARAMETER_FMCD_DESCRIPTION = "If the number of examples in the dataset exceeds the threshold, fastMCD will be applied instead of MCD (complete search). Not recommended to be higher than 600 due to computational issues.";
	/**
	 * 
	 **/
	public static final String PARAMETER_RUN = "iterations";
	public static final String PARAMETER_RUN_DESCRIPTION = "Numer of iterations for computing the MCD. 100-500 might be a good choice.";

	/**
	 * The parameter name for &quot;Specifies the number of threads for
	 * execution.&quot; Specifies that evaluation process should be performed in
	 * parallel &quot;
	 **/
	public static final String PARAMETER_NUMBER_OF_THREADS = "number of threads";
	public static final String PARAMETER_NUMBER_OF_THREADS_DESCRIPTION = "The number of threads for the computation";
	/**
	 * The number of times outlier should be removed for minimum covariance
	 * determinant
	 */
	public static final String PARAMETER_NUMBER_OF_REMOVE = "times to remove outlier";
	public static final String PARAMETER_NUMBER_OF_REMOVE_DESCRIPTION = "The number of times outlier should be removed for minimum covariance determinant";
	/**
	 * The normal class probability
	 */
	public static final String PARAMETER_OUTLIER_PROBABILITY = "probability for normal class";
	public static final String PARAMETER_OUTLIER_PROBABILITY_DESCRIPTION = "This is the expected probability of normal data instances. Usually it should be between 0.95 and 1.0 to make sense.";
	/**
	 * Boolean to use a given number of points to calculate the covariance matrix
	 */
	public static final String PARAMETER_LIMIT_COVARIANCE_POINTS = "limit computations";
	public static final String PARAMETER_LIMIT_COVARIANCE_POINTS_DESCRIPTION = "Limit the number of instances to calculate the covariance matrix. Should be used for very large clusters. The sampling of the instances is a random choice.";
	/**
	 * Number of points for covariance matrix calculation
	 */
	public static final String PARAMETER_NUMBER_COVARIANCE_POINTS = "maximum";
	public static final String PARAMETER_NUMBER_COVARIANCE_POINTS_DESCRIPTION = "Maximum number of instances for covariance matrix calculation";
	/**
	 * Boolean for parallelization
	 */
	public static final String PARAMETER_PARALLELIZE_EVALUATION_PROCESS = "parallelize evaluation process";
	public static final String PARAMETER_PARALLELIZE_EVALUATION_PROCESS_DESCRIPTION = "Specifies that evaluation process should be performed in parallel";
	/**
	 * Boolean to remove small cluster
	 */
	public static final String PARAMETER_REMOVE_CLUSTER = "remove small clusters";
	public static final String PARAMETER_REMOVE_CLUSTER_DESCRIPTION = "Too small clusters can negatively influence the result. If ticked, a rule of thumb is applied to remove clusters with less than ((1 - 'normal probability') * nbr of datapoints) / nbr of clusters. These points will be assigned to the nearest large cluster";
	/**
	 * Number of points to define a &quote;small cluster&quote;
	 */
	public static final String PARAMETER_NUMBER_POINTS_SMALL_CLUSTER = "minimum";
	public static final String PARAMETER_NUMBER_POINTS_SMALL_CLUSTER_DESCRIPTION = "Minimum of instances in a cluster. Smaller clusters will be removed and instances assigned to the next large cluster.";
	
	public static final String PARAMETER_NUMBER_POINTS_MAN = "minimum number of instances";
	public static final String PARAMETER_NUMBER_POINTS_MAN_DESCRIPTION = "Set the minimum number of points for a cluster manually.";

	
	public CMGOSAnomalyDetectionOperator(OperatorDescription description) {
		super(description);
	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes, double[][] points) throws OperatorException {
		DistanceMeasure measure = getMeasureHelper().getInitializedMeasure(exampleSet);

		int parallel = 1;
		if (getParameterAsBoolean(PARAMETER_PARALLELIZE_EVALUATION_PROCESS))
			parallel = getParameterAsInt(PARAMETER_NUMBER_OF_THREADS);

		int rem_cluster_anz = -2;
		if (getParameterAsBoolean(PARAMETER_REMOVE_CLUSTER))
			if (getParameterAsBoolean(PARAMETER_NUMBER_POINTS_MAN))
				rem_cluster_anz = getParameterAsInt(PARAMETER_NUMBER_POINTS_SMALL_CLUSTER);
			else
				rem_cluster_anz = -1;
	
		
		int[] belongsToCluster = getBelongsToCluster();
		double[][] centroids = getCentriods();
		int[] clusterSize = getClusterSize();
		int variancePoints = -1;
		if (getParameterAsBoolean(PARAMETER_LIMIT_COVARIANCE_POINTS))
			variancePoints = getParameterAsInt(PARAMETER_NUMBER_COVARIANCE_POINTS);
		if (getParameterAsBoolean(PARAMETER_LIMIT_COVARIANCE_POINTS+"_"))
			variancePoints = getParameterAsInt(PARAMETER_NUMBER_COVARIANCE_POINTS+"_");

		RandomGenerator generator = RandomGenerator.getRandomGenerator(this);
		CMGOSEvaluator evaluator = new CMGOSEvaluator(measure, points, belongsToCluster, centroids, clusterSize, parallel, getParameterAsInt(PARAMETER_NUMBER_OF_REMOVE), getParameterAsDouble(PARAMETER_OUTLIER_PROBABILITY), variancePoints, generator, rem_cluster_anz, getParameterAsDouble(PARAMETER_LAMBDA), getParameterAsInt(PARAMETER_COVARIANCE), getParameterAsInt(PARAMETER_H), getParameterAsInt(PARAMETER_POINTS_SUBSET), getParameterAsInt(PARAMETER_FMCD), getParameterAsInt(PARAMETER_RUN));

		double[] e = evaluator.evaluate();
			return e;
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = new LinkedList<ParameterType>();

		types.add(new ParameterTypeDouble(PARAMETER_OUTLIER_PROBABILITY, PARAMETER_OUTLIER_PROBABILITY_DESCRIPTION, 0, 1.0, 0.975, false));

		types.add(new ParameterTypeBoolean(PARAMETER_REMOVE_CLUSTER, PARAMETER_REMOVE_CLUSTER_DESCRIPTION, false, false));
		ParameterTypeBoolean type3 = (new ParameterTypeBoolean(PARAMETER_NUMBER_POINTS_MAN, PARAMETER_NUMBER_POINTS_MAN_DESCRIPTION, false, false));
		type3.registerDependencyCondition(new BooleanParameterCondition(this, PARAMETER_REMOVE_CLUSTER, true, true));
		types.add(type3);
		
		ParameterTypeInt type = (new ParameterTypeInt(PARAMETER_NUMBER_POINTS_SMALL_CLUSTER, PARAMETER_NUMBER_POINTS_SMALL_CLUSTER_DESCRIPTION, 0, Integer.MAX_VALUE, 0, false));
		type.registerDependencyCondition(new BooleanParameterCondition(this, PARAMETER_NUMBER_POINTS_MAN, true, true));
		types.add(type);

		types.add(new ParameterTypeCategory(PARAMETER_COVARIANCE, PARAMETER_COVARIANCE_DESCRIPTION, COV, 0, false));
		
		ParameterTypeInt type2 = new ParameterTypeInt(PARAMETER_NUMBER_OF_REMOVE, PARAMETER_NUMBER_OF_REMOVE_DESCRIPTION, 0, Integer.MAX_VALUE, 1, false);
		type2.registerDependencyCondition(new EqualTypeCondition(getParameterHandler(), PARAMETER_COVARIANCE, COV, false, METHOD_COV_REDUCTION, METHOD_COV_REGULARIZE));
		types.add(type2);

		type3 = (new ParameterTypeBoolean(PARAMETER_LIMIT_COVARIANCE_POINTS, PARAMETER_LIMIT_COVARIANCE_POINTS_DESCRIPTION, false, false));
		type3.registerDependencyCondition(new EqualTypeCondition(getParameterHandler(), PARAMETER_COVARIANCE, COV, false, 1));
		types.add(type3);
		type = (new ParameterTypeInt(PARAMETER_NUMBER_COVARIANCE_POINTS, PARAMETER_NUMBER_COVARIANCE_POINTS_DESCRIPTION, 1, Integer.MAX_VALUE, 1000, false));
		type.registerDependencyCondition(new BooleanParameterCondition(this, PARAMETER_LIMIT_COVARIANCE_POINTS, true, true));
		types.add(type);
		type3 = (new ParameterTypeBoolean(PARAMETER_LIMIT_COVARIANCE_POINTS+"_", PARAMETER_LIMIT_COVARIANCE_POINTS_DESCRIPTION, false, false));
		type3.registerDependencyCondition(new EqualTypeCondition(getParameterHandler(), PARAMETER_COVARIANCE, COV, false, 0));
		types.add(type3);
		type = (new ParameterTypeInt(PARAMETER_NUMBER_COVARIANCE_POINTS+"_", PARAMETER_NUMBER_COVARIANCE_POINTS_DESCRIPTION, 1, Integer.MAX_VALUE, 1000, false));
		type.registerDependencyCondition(new BooleanParameterCondition(this, PARAMETER_LIMIT_COVARIANCE_POINTS+"_", true, true));
		types.add(type);
		type = (new ParameterTypeInt(PARAMETER_H, PARAMETER_H_DESCRIPTION, 0, Integer.MAX_VALUE, -1, false));
		type.registerDependencyCondition(new EqualTypeCondition(getParameterHandler(), PARAMETER_COVARIANCE, COV, false, METHOD_COV_MCD));
		types.add(type);
		type = (new ParameterTypeInt(PARAMETER_RUN, PARAMETER_RUN_DESCRIPTION, 0, Integer.MAX_VALUE, 500, false));
		type.registerDependencyCondition(new EqualTypeCondition(getParameterHandler(), PARAMETER_COVARIANCE, COV, false, METHOD_COV_MCD));
		types.add(type);
		type = (new ParameterTypeInt(PARAMETER_FMCD, PARAMETER_FMCD_DESCRIPTION, 0, Integer.MAX_VALUE, 600, false));
		type.registerDependencyCondition(new EqualTypeCondition(getParameterHandler(), PARAMETER_COVARIANCE, COV, false, METHOD_COV_MCD));
		types.add(type);
		type = (new ParameterTypeInt(PARAMETER_POINTS_SUBSET, PARAMETER_POINTS_SUBSET_DESCRIPTION, 0, Integer.MAX_VALUE, 300, false));
		type.registerDependencyCondition(new EqualTypeCondition(getParameterHandler(), PARAMETER_COVARIANCE, COV, false, METHOD_COV_MCD));
		types.add(type);
		
		ParameterTypeDouble type1 = (new ParameterTypeDouble(PARAMETER_LAMBDA, PARAMETER_LAMBDA_DESCRIPTION, 0, 1, 0.1, false));
		type1.registerDependencyCondition(new EqualTypeCondition(getParameterHandler(), PARAMETER_COVARIANCE, COV, false, METHOD_COV_REGULARIZE));
		types.add(type1);

		List<ParameterType> distancetypes = DistanceMeasures.getParameterTypes(this);
		types.addAll(distancetypes);
		
		types.add(new ParameterTypeBoolean(PARAMETER_PARALLELIZE_EVALUATION_PROCESS, PARAMETER_PARALLELIZE_EVALUATION_PROCESS_DESCRIPTION, false, false));
		type = (new ParameterTypeInt(PARAMETER_NUMBER_OF_THREADS, PARAMETER_NUMBER_OF_THREADS_DESCRIPTION, 1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(), false));
		type.registerDependencyCondition(new BooleanParameterCondition(this, PARAMETER_PARALLELIZE_EVALUATION_PROCESS, true, true));
		types.add(type);
		
		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));
		return types;
	}

}
