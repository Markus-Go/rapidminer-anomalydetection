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
package de.dfki.madm.anomalydetection.operator.cluster_based;

import java.util.List;

import com.rapidminer.example.Attributes;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.conditions.BooleanParameterCondition;
import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.cluster_based.LDCOFEvaluator;

/**
 * The Operator for LDCOF algorithm.
 * 
 * 
 * @author Mennatallah Amer
 * 
 */
public class LDCOFAnomalyDetectionOperator extends
		AbstractClusteringAnomalyDetectionOperator {
	/** The parameter name for &quot; The division into large and small clusters will be implemented in a manner similar to CBLOF. &quot;**/
	public static String PARAMETER_LIKE_CBLOF = "divide clusters like cblof";

	/** alpha specifies the percentage of normal data **/
	public static String PARAMETER_ALPHA = "alpha";

	/** The minimum ratio between a normal and anomalous cluster **/
	public static String PARAMETER_BETA = "beta";

	/**
	 * Parameter name for gamma &quot; ratio between the maximum size of small
	 * clusters and the average cluster size &quot;
	 **/
	public static String PARAMETER_GAMMA = "gamma";

	public LDCOFAnomalyDetectionOperator(OperatorDescription description) {
		super(description);
	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points) throws OperatorException {
		DistanceMeasure measure = getMeasureHelper().getInitializedMeasure(
				exampleSet);

		int[] belongsToCluster = getBelongsToCluster();
		double[][] centroids = getCentriods();
		int[] clusterSize = getClusterSize();
		LDCOFEvaluator evaluator;
		if (getParameterAsBoolean(PARAMETER_LIKE_CBLOF)) {
			double beta = getParameterAsDouble(PARAMETER_BETA);
			double alpha = getParameterAsDouble(PARAMETER_ALPHA) / 100;
			this.logNote(getName() + " alpha " + alpha + " beta" + beta);
			evaluator = new LDCOFEvaluator(alpha, beta, measure, points,
					belongsToCluster, centroids, clusterSize);
		} else {
			double percentage = getParameterAsDouble(PARAMETER_GAMMA);
			evaluator = new LDCOFEvaluator(percentage, measure, points,
					belongsToCluster, centroids, clusterSize);
		}
		double[] e = evaluator.evaluate();		
		return e;

	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		
		/**/
		types
				.add(new ParameterTypeBoolean(
						PARAMETER_LIKE_CBLOF,
						"The division into large and small clusters will be implemented in a manner similar to CBLOF.",
						false, false));
		ParameterType type = new ParameterTypeDouble(PARAMETER_ALPHA,
				"percentage of normal data", 0, 100, 90);
		type.registerDependencyCondition(new BooleanParameterCondition(this,
				PARAMETER_LIKE_CBLOF, true, true));
		types.add(type);
		type = new ParameterTypeDouble(PARAMETER_BETA,
				"the minimum ratio between large and small clusters", 1,
				Integer.MAX_VALUE, 5);
		type.registerDependencyCondition(new BooleanParameterCondition(this,
				PARAMETER_LIKE_CBLOF, true, true));
		types.add(type);
		type = new ParameterTypeDouble(
				PARAMETER_GAMMA,
				"ratio between the maximum size of small clusters and the average cluster size",
				0, 1, 0.1);
		type.registerDependencyCondition(new BooleanParameterCondition(this,PARAMETER_LIKE_CBLOF, true, false));

		types.add(type);
/**/
		return types;
	}

}
