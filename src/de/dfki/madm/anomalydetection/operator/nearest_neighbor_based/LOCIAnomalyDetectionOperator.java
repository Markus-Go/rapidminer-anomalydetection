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

import java.util.LinkedList;
import java.util.List;

import com.rapidminer.example.Attributes;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ports.metadata.DistanceMeasurePrecondition;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.tools.math.similarity.DistanceMeasure;
import com.rapidminer.tools.math.similarity.DistanceMeasureHelper;
import com.rapidminer.tools.math.similarity.DistanceMeasures;

import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.LOCIEvaluator;



/**
 * Operator for local correlation Integral proposed by papadimitriou et al
 * (2003)
 * 
 * <p>
 * The algorithm has the following pros over other approaches. The results are
 * not highly affected by the parameters and it provides an automatic
 * statistically intuitive cut off to determine the outliers.
 * </p>
 * <p>
 * The computation of the LOCI requires the calculation of MDEF and &sigma;MDEF.
 * MDEF for a point pi at radius r refers to the deviation of the density of pi
 * to that in its average local neighborhood density. &sigma;MDEF is the
 * normalised standard deviation of the point relative to its local
 * neighborhood.
 * </p>
 * <p>
 * The original publication suggests the following flagging scheme the object
 * should be flagged as an outlier if MDEF(pi, r, &alpha;) &gt; 3
 * *&sigma;MDEF(pi, r, &alpha). The operator produces an outlier score which
 * corresponds to the maximum ratio between MDEF(pi, r, &alpha;) and
 * &sigmaMDEF(pi, r, &alpha) over all r. The higher the ratio the more outlier
 * the object is. The proposed threshold to determine outliers is 3.
 * </p>
 * 
 * @author Mennatallah Amer
 * 
 */
public class LOCIAnomalyDetectionOperator extends
		AbstractNearestNeighborBasedAnomalyDetectionOperator {

	/**
	 * The parameter name for &quot; The minimum number of neighbors in the
	 * sampling neighborhood. &quot;
	 **/
	public static String PARAMETER_N_MIN = "n min";

	/**
	 * The parameter name for &quot; The ratio of the counting neighborhood
	 * radius to the sampling neighborhood radius. &quot;
	 **/
	public static String PARAMETER_ALPHA = "alpha";

	private DistanceMeasureHelper measureHelper = new DistanceMeasureHelper(
			this);

	public LOCIAnomalyDetectionOperator(OperatorDescription description) {
		super(description);
		getExampleSetInput().addPrecondition(
				new DistanceMeasurePrecondition(getExampleSetInput(), this));
	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points, int[] weight) throws OperatorException {
		DistanceMeasure measure = measureHelper
				.getInitializedMeasure(exampleSet);
		double alpha = getParameterAsDouble(PARAMETER_ALPHA);
		int nmin = getParameterAsInt(PARAMETER_N_MIN);
		int n = points.length;
		double[] ret = {1};
		
		if (n > 1) {
			if (nmin == n) {
				this.logWarning("Setting " + PARAMETER_N_MIN + " to #Datapoints-1 because n min can't be equal #Datapoints.");
				nmin = n-1;
				//this.setParameter(PARAMETER_N_MIN, (n-1)+"");
			}
			
			LOCIEvaluator evaluator = new LOCIEvaluator(measure, alpha, nmin,
					points, weight);
			ret = evaluator.evaluate();
		}
		return ret;
	}

	public List<ParameterType> getParameterTypes() {
		LinkedList<ParameterType> types = new LinkedList<ParameterType>();
		types
				.add(new ParameterTypeDouble(
						PARAMETER_ALPHA,
						"The ratio of the counting neighborhood radius to the sampling neighborhood radius.",
						0, 1, 0.5));
		types
				.add(new ParameterTypeInt(
						PARAMETER_N_MIN,
						"The minimum number of neighbors in the sampling neighborhood.",
						1, Integer.MAX_VALUE, 20, false));

		types.addAll(DistanceMeasures.getParameterTypes(this));
		return types;

	}

}
