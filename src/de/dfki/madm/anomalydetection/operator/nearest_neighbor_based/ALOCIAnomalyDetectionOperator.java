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
 * Author: Ahmed Elsawy (ahmed.nagah.elsawy@gmail.com)
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
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.conditions.BooleanParameterCondition;
import com.rapidminer.tools.math.similarity.DistanceMeasure;
import com.rapidminer.tools.math.similarity.DistanceMeasureHelper;
import com.rapidminer.tools.math.similarity.DistanceMeasures;
import com.rapidminer.tools.RandomGenerator;
import de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based.ALOCIEvaluator;

public class ALOCIAnomalyDetectionOperator extends
		AbstractNearestNeighborBasedAnomalyDetectionOperator {

	public static String PARAMETER_LEVEL_DIFFERENCE = "difference of levels L";
	public static String PARAMETER_TREE_DEPTH = "tree depth (levels)";
	public static String PARAMETER_GRIDS_NUM = "number of grids";
	public static String PARAMETER_NMIN = "n min";
	public static String PARAMETER_PARALLELIZE_EVALUATION_PROCESS = "parallelize evaluation process";
	public static String PARAMETER_NUMBER_OF_THREADS = "number of threads";
	
	public ALOCIAnomalyDetectionOperator(OperatorDescription description) {
		super(description);	
		getExampleSetInput().addPrecondition(
				new DistanceMeasurePrecondition(getExampleSetInput(), this));

	}
	private DistanceMeasureHelper measureHelper = new DistanceMeasureHelper(
			this);
	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points, int[] weight) throws OperatorException {
		
		DistanceMeasure measure = measureHelper.getInitializedMeasure(exampleSet);

		int alpha = getParameterAsInt(PARAMETER_LEVEL_DIFFERENCE);
		int level = getParameterAsInt(PARAMETER_TREE_DEPTH);
		int grids = getParameterAsInt(PARAMETER_GRIDS_NUM);
		int nmin  = getParameterAsInt(PARAMETER_NMIN);
		
		boolean parallelizeProcess = getParameterAsBoolean(PARAMETER_PARALLELIZE_EVALUATION_PROCESS);
		int numberOfThreads = getParameterAsInt(PARAMETER_NUMBER_OF_THREADS);
		
		ALOCIEvaluator evaluator = new ALOCIEvaluator(measure,points,grids,level,alpha,nmin,RandomGenerator.getRandomGenerator(this), parallelizeProcess, numberOfThreads);
		double[] result = evaluator.evaluate();
		return result;
	}

	public List<ParameterType> getParameterTypes() {
		LinkedList<ParameterType> types = new LinkedList<ParameterType>();
		types.add(new ParameterTypeInt(
						PARAMETER_LEVEL_DIFFERENCE,
						"The difference in number of levels between sampling & counting, &alpha = 2 ^ -L",
						1, Integer.MAX_VALUE,4,false));
		types.add(new ParameterTypeInt(
						PARAMETER_TREE_DEPTH,
						"Number of levels in the quadtree",
						1, Integer.MAX_VALUE,10,false));
		types.add(new ParameterTypeInt(
						PARAMETER_GRIDS_NUM,
						"Total number of different grids",
						1,Integer.MAX_VALUE,20,false));
		
		types.add(new ParameterTypeInt(
						PARAMETER_NMIN,
						"The minimum number of neighbors in the sampling neighborhood.",
						1,Integer.MAX_VALUE,20,false));

		types.addAll(RandomGenerator.getRandomGeneratorParameters(this));
		types.add(new ParameterTypeBoolean(
						PARAMETER_PARALLELIZE_EVALUATION_PROCESS,
						"Specifies that evaluation process should be performed in parallel.",
						false,false));		

		ParameterType numberOfThreadsType = new ParameterTypeInt(
							PARAMETER_NUMBER_OF_THREADS,
							"Specifies the number of threads for execution.",
							1,Integer.MAX_VALUE,Runtime.getRuntime().availableProcessors(),false);		
		
		numberOfThreadsType.registerDependencyCondition(new BooleanParameterCondition(this,
				PARAMETER_PARALLELIZE_EVALUATION_PROCESS, true, true));

		types.add(numberOfThreadsType);
		
		types.addAll(DistanceMeasures.getParameterTypes(this));
		return types;

	}

}
