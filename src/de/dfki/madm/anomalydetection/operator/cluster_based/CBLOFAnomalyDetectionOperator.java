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

import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.cluster_based.CBLOFEvaluator;

/**
 * The CBLOF operator calculates the outlier score based on cluster-based local
 * outlier factor proposed by He et al(2003)
 * 
 * CBLOF takes as an input the data set and the cluster model that was generated
 * by a clustering algorithm. It classifies the clusters into small clusters and
 * large clusters using the parameters alpha and beta. The anomaly score is then
 * calculated based on the size of the cluster the point belongs to as well as
 * the distance to the nearest large cluster.
 * 
 * Use weighting for outlier factor based on the sizes of the clusters as
 * proposed in the original publication. Since this might lead to unexpected
 * behavior (outliers close to small clusters are not found), it can be
 * disabled and outliers scores are solely computed based on their distance to
 * the cluster center.
 * 
 * 
 * @author Mennatallah Amer
 * 
 */
public class CBLOFAnomalyDetectionOperator extends
		AbstractClusteringAnomalyDetectionOperator {

	/** alpha specifies the percentage of normal data **/
	public static String PARAMETER_ALPHA = "alpha";

	/** The minimum ratio between a normal and anomalous cluster **/
	public static String PARAMETER_BETA = "beta";

	/**
	 * The Parameter name for &quot; Uses the cluster size as a weight factor as
	 * proposed by the original publication. &quot;
	 **/
	public static String PARAMETER_WEIGHTING = "use cluster size as weighting factor";

	public CBLOFAnomalyDetectionOperator(OperatorDescription description) {
		super(description);

	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points) throws OperatorException {
		DistanceMeasure measure = getMeasureHelper().getInitializedMeasure(
				exampleSet);
		double alpha = getParameterAsDouble(PARAMETER_ALPHA) / 100;
		double beta = getParameterAsDouble(PARAMETER_BETA);
		boolean weighting = getParameterAsBoolean(PARAMETER_WEIGHTING);
		this.logNote(getName()+" alpha "+ alpha+" beta"+ beta);
		int[] belongsToCluster = getBelongsToCluster();
		double[][] centroids = getCentriods();
		int[] clusterSize = getClusterSize();
		CBLOFEvaluator evaluator = new CBLOFEvaluator(alpha, beta, measure, points,
				belongsToCluster, centroids, clusterSize,weighting);
		return evaluator.evaluate();

	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		types
				.add(new ParameterTypeDouble(
						PARAMETER_ALPHA,
						"This parameter specifies the percentage of the data set that is expected to be normal",
						0, 100, 90, false));
		types
				.add(new ParameterTypeDouble(
						PARAMETER_BETA,
						"This parameter specifies the minimum ratio between the size of a large cluster and a small cluster",
						1, Integer.MAX_VALUE, 5, false));
		types
				.add(new ParameterTypeBoolean(
						PARAMETER_WEIGHTING,
						"Uses the cluster size as a weight factor as proposed by the original publication.",
						true));
		
		
		return types;
	}

}
