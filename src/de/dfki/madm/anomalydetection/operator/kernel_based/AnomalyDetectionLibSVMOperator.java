/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2013 by Deutsches Forschungszentrum fuer
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
 * Author: Mennatallah Amer (menna.amer@gmail.com)
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 *
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */

package de.dfki.madm.anomalydetection.operator.kernel_based;

import java.util.List;

import anomalydetection_libsvm.svm_model;
import anomalydetection_libsvm.svm_parameter;

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

import de.dfki.madm.anomalydetection.evaluator.Evaluator;
import de.dfki.madm.anomalydetection.evaluator.kernel_based.AnomalyDetectionLibSVMEvaluator;

import de.dfki.madm.anomalydetection.operator.AbstractAnomalyDetectionOperator;

public class AnomalyDetectionLibSVMOperator extends
		AbstractAnomalyDetectionOperator {
	/**
	 * The parameter name for &quot;One-class SVM for unsupervised anomaly
	 * detection: one-class, robust one-class and eta one-class&quot;
	 */
	public static final String PARAMETER_SVM_TYPE = "svm_type";

	/** The parameter name for &quot;The type of the kernel functions&quot; */
	public static final String PARAMETER_KERNEL_TYPE = "svm_kernel_type";

	/**
	 * The parameter name for &quot;The degree for a polynomial kernel
	 * function.&quot;
	 */
	public static final String PARAMETER_DEGREE = "degree";

	/**
	 * The parameter name for &quot;Automatic tuning of gamma parameter using
	 * the heuristics defined in Evangelista, P. F., Embrechts, M. J., &
	 * Szymanski, B. K. (2007). Some Properties of the Gaussian Kernel for One
	 * Class Learning, 4668, 269--278.,&quot;
	 */
	public static final String PARAMETER_GAMMA_TUNING = "automatic gamma tuning";

	/**
	 * The parameter name for &quot;The parameter gamma for polynomial, rbf, and
	 * sigmoid kernel functions (0 means 1/#attributes).&quot;
	 */
	public static final String PARAMETER_GAMMA = "gamma";

	/**
	 * The parameter name for &quot;The parameter coef0 for polynomial and
	 * sigmoid kernel functions.&quot;
	 */
	public static final String PARAMETER_COEF0 = "coef0";

	/**
	 * The parameter name for &quot;The parameter nu for one-class svm. It 
	 * represents the lower bound on the number of support vectors and the
	 *  upper bound on the number of outliers.&quot;
	 */
	public static final String PARAMETER_NU = "nu";

	/**
	 * The parameter name for &quot;The parameter lambda for 
	 * robust one-class SVM.&quot;
	 */
	public static final String PARAMETER_LAMBDA = "lambda";

	/**
	 * The parameter name for &quot;The parameter beta for eta one-class SVM. 
	 * It represents the ratio of expected outliers that will be not
	 *  considered when training the SVM.&quot;
	 */
	public static final String PARAMETER_BETA = "beta";

	/** The parameter name for &quot;Cache size in Megabyte.&quot; */
	public static final String PARAMETER_CACHE_SIZE = "cache_size";

	/** The parameter name for &quot;Tolerance of termination criterion.&quot; */
	public static final String PARAMETER_EPSILON = "epsilon";

	/**
	 * The parameter name for &quot;Whether to use the shrinking
	 * heuristics.&quot;
	 */
	public static final String PARAMETER_SHRINKING = "shrinking";

	/** The different kernel types implemented by the LibSVM package. */
	public static final String[] KERNEL_TYPES = { "linear", "poly", "rbf",
			"sigmoid", "precomputed" };

	public static final String[] SVM_TYPES = { "one-class", "robust one-class",
			"eta one-class" };

	public static final int SVM_TYPE_ONE_CLASS = 0;
	public static final int SVM_TYPE_ROBUST_ONE_CLASS = 1;
	public static final int SVM_TYPE_ROBUST_ONE_CLASS_ETA = 2;

	/**
	 * Values to allow logging the number of bound and non-bound 
	 * support vectors
	 */

	private NumberOfSupportVectorsValue nSVValue;
	private NumberOfSupportVectorsValue nBSVValue;

	public AnomalyDetectionLibSVMOperator(OperatorDescription description) {
		super(description);
		nSVValue = new NumberOfSupportVectorsValue("nSV",
				"number of support Vectors");
		nBSVValue = new NumberOfSupportVectorsValue("nBSV",
				"number of bound support Vectors");
		addValue(nSVValue);
		addValue(nBSVValue);
	}

	/**
	 * The operator parameters are used to create the LIBSVM parameter object
	 */
	private svm_parameter getSVMParameters(ExampleSet exampleSet)
			throws OperatorException {
		svm_parameter params = new svm_parameter();

		switch (getParameterAsInt(PARAMETER_SVM_TYPE)) {
		case SVM_TYPE_ONE_CLASS:
			params.svm_type = svm_parameter.ONE_CLASS;
			params.nu = getParameterAsDouble(PARAMETER_NU);
			break;
		case SVM_TYPE_ROBUST_ONE_CLASS:
			params.svm_type = svm_parameter.ROBUST_ONE_CLASS;
			params.lambda = getParameterAsDouble(PARAMETER_LAMBDA);
			break;
		case SVM_TYPE_ROBUST_ONE_CLASS_ETA:
			params.svm_type = svm_parameter.ETA_ONE_CLASS;
			params.nu = getParameterAsDouble(PARAMETER_BETA);
			break;
		}

		params.kernel_type = getParameterAsInt(PARAMETER_KERNEL_TYPE);
		params.degree = getParameterAsInt(PARAMETER_DEGREE);
		params.gamma = getParameterAsDouble(PARAMETER_GAMMA);
		if (params.gamma == 0)
			params.gamma = 1.0 / exampleSet.size();
		params.coef0 = getParameterAsDouble(PARAMETER_COEF0);
		params.cache_size = getParameterAsInt(PARAMETER_CACHE_SIZE);
		params.eps = getParameterAsDouble(PARAMETER_EPSILON);
		params.shrinking = getParameterAsBoolean(PARAMETER_SHRINKING) ? 1 : 0;
		params.probability = 1;
		return params;
	}

	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points) throws OperatorException {
		svm_parameter params = getSVMParameters(exampleSet);
		boolean gammaTuning = getParameterAsBoolean(PARAMETER_GAMMA_TUNING);

		Evaluator evaluator = new AnomalyDetectionLibSVMEvaluator(points,
				params, gammaTuning);

		double[] result = evaluator.evaluate();
		svm_model model = ((AnomalyDetectionLibSVMEvaluator) evaluator)
				.getModel();
		nSVValue.setNumberofSupportVectors(model.l);
		nBSVValue.setNumberofSupportVectors(model.nBSV);
		
		if (gammaTuning) {
			logNote("Tuned gamma value " + params.gamma);
		}
		
		return result;
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();

		ParameterType type = new ParameterTypeCategory(
				PARAMETER_SVM_TYPE,
				"One-class SVM for unsupervised anomaly detection: one-class, robust one-class and eta one-class",
				SVM_TYPES, 0);
		type.setExpert(false);
		types.add(type);
		type = new ParameterTypeCategory(PARAMETER_KERNEL_TYPE,
				"The type of the kernel functions", KERNEL_TYPES, 2);
		type.setExpert(false);
		types.add(type);
		type = new ParameterTypeInt(PARAMETER_DEGREE,
				"The degree for a polynomial kernel function.", 1,
				Integer.MAX_VALUE, 3, false);
		type.registerDependencyCondition(new EqualTypeCondition(this,
				PARAMETER_KERNEL_TYPE, KERNEL_TYPES, false, 1));
		types.add(type);

		type = new ParameterTypeBoolean(
				PARAMETER_GAMMA_TUNING,
				"Automatic tuning of gamma parameter using the heuristics defined in Evangelista, P. F., Embrechts, M. J., & Szymanski, B. K. (2007). Some Properties of the Gaussian Kernel for One Class Learning, 4668, 269--278.",
				true);
		type.registerDependencyCondition(new EqualTypeCondition(this,
				PARAMETER_KERNEL_TYPE, KERNEL_TYPES, false, 2));
		types.add(type);

		type = new ParameterTypeDouble(
				PARAMETER_GAMMA,
				"The parameter gamma for polynomial, rbf, and sigmoid kernel functions (0 means 1/#examples).",
				0.0, Double.POSITIVE_INFINITY, 0.0d);
		type.registerDependencyCondition(new EqualTypeCondition(this,
				PARAMETER_KERNEL_TYPE, KERNEL_TYPES, false, 2));
		type.registerDependencyCondition(new BooleanParameterCondition(this,
				PARAMETER_GAMMA_TUNING, false, false));
		types.add(type);

		type = new ParameterTypeDouble(
				PARAMETER_COEF0,
				"The parameter coef0 for polynomial and sigmoid kernel functions.",
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0);
		type.registerDependencyCondition(new EqualTypeCondition(this,
				PARAMETER_KERNEL_TYPE, KERNEL_TYPES, false, 1, 4));
		types.add(type);

		type = new ParameterTypeDouble(
				PARAMETER_NU,
				"The parameter nu for one-class svm. It represents the lower bound on the number of support vectors and the upper bound on the number of outliers. ",
				0.0d, 1.0d, 0.5d);

		type.registerDependencyCondition(new EqualTypeCondition(this,
				PARAMETER_SVM_TYPE, SVM_TYPES, false, SVM_TYPE_ONE_CLASS));
		types.add(type);
		
		type = new ParameterTypeDouble(
				PARAMETER_BETA,
				"The parameter beta for eta one-class SVM. It represents the ratio of expected outliers that will be not considered when training the SVM. ",
				0.0d, 1.0d, 0.5d);

		type.registerDependencyCondition(new EqualTypeCondition(this,
				PARAMETER_SVM_TYPE, SVM_TYPES, false,
				SVM_TYPE_ROBUST_ONE_CLASS_ETA));
		types.add(type);

		type = new ParameterTypeDouble(PARAMETER_LAMBDA,
				"The parameter lambda for  robust one-class svm", 0.0d,
				Double.POSITIVE_INFINITY, 0.001);
		type.registerDependencyCondition(new EqualTypeCondition(this,
				PARAMETER_SVM_TYPE, SVM_TYPES, false, SVM_TYPE_ROBUST_ONE_CLASS));
		types.add(type);

		types.add(new ParameterTypeDouble(PARAMETER_EPSILON,
				"Tolerance of termination criterion.",
				Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.001));

		types.add(new ParameterTypeInt(PARAMETER_CACHE_SIZE,
				"Cache size in Megabyte.", 0, Integer.MAX_VALUE, 80));

		types.add(new ParameterTypeBoolean(PARAMETER_SHRINKING,
				"Whether to use the shrinking heuristics.", true));

		return types;
	}

}
