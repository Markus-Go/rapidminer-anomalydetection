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

package de.dfki.madm.anomalydetection.evaluator.kernel_based;



import de.dfki.madm.anomalydetection.evaluator.Evaluator;
import anomalydetection_libsvm.Svm;
import anomalydetection_libsvm.svm_model;
import anomalydetection_libsvm.svm_node;
import anomalydetection_libsvm.svm_parameter;
import anomalydetection_libsvm.svm_problem;


public class AnomalyDetectionLibSVMEvaluator implements Evaluator {

	/**
	 * svm_nodes for ExampleSet
	 */
	private svm_node[][] values;
	/**
	 *  parameters of SVM
	 */
	private svm_parameter params;

	/**
	 * Boolean that determines whether gamma should be automatically tuned
	 */
	private boolean automatic_gamma_learning;
	/**
	 * The learned SVM model.
	 */
	private svm_model model;

	private svm_node[][] formSVMNodes(double[][] values) {
		svm_node[][] result = new svm_node[values.length][values[0].length];
		for (int i = 0; i < values.length; i++) {
			for (int j = 0; j < values[0].length; j++) {
				result[i][j] = new svm_node();
				result[i][j].value = values[i][j];
				result[i][j].index = j;
			}
		}
		return result;
	}

	public AnomalyDetectionLibSVMEvaluator(svm_parameter params,
			boolean automatic_gamma_learning) {
		this.params = params;
		this.automatic_gamma_learning = automatic_gamma_learning;
	}

	public AnomalyDetectionLibSVMEvaluator(double[][] values,
			svm_parameter params, boolean automatic_gamma_learning) {
		this(params, automatic_gamma_learning);
		this.values = formSVMNodes(values);

	}

	public AnomalyDetectionLibSVMEvaluator(svm_node[][] values,
			svm_parameter params, boolean automatic_gamma_learning) {
		this(params, automatic_gamma_learning);
		this.values = values;
	}

	protected void setValues(svm_node[][] values) {
		this.values = values;
	}

	protected void setValues(double[][] values) {
		this.values = formSVMNodes(values);
	}

	protected void setGamma(double gamma) {
		this.params.gamma = gamma;
	}

	/**
	 * Anomaly Score is calculated according to the value 
	 * of the decision function where it is normalized the maximum confidence. 
	 *  	score(i) = maxConfidence - d(i)/ Math.abs(maxConfidence) 
	 * @param model
	 * @param trainingSet
	 * @param testSet
	 * @return
	 */
	private double[] computeAnomalyScore(svm_model model,
			svm_node[][] trainingSet, svm_node[][] testSet) {
		double maxConfidence = Integer.MIN_VALUE;
		double[] prob = new double[1];
		for (int i = 0; i < trainingSet.length; i++) {
			Svm.svm_predict_values(model, trainingSet[i], prob);
			if (prob[0] > maxConfidence)
				maxConfidence = prob[0];
		}
		double[] result = new double[testSet.length];
		maxConfidence = Math.abs(maxConfidence);
		model.max_confidence = maxConfidence;

		for (int i = 0; i < testSet.length; i++) {
			Svm.svm_predict_values(model, trainingSet[i], prob);
			result[i] = (maxConfidence - prob[0]) / Math.abs(maxConfidence);
			
		}
		return result;
	}

	@Override
	public double[] evaluate() {
		int l = values.length;
		double[] results;
		svm_problem problem;
		double[] labels;
		if (params.kernel_type == svm_parameter.RBF && automatic_gamma_learning) {
			params.gamma = RBF_Kernel.learnGamma(values);
		}

		labels = new double[l];
		problem = new svm_problem();
		problem.l = l;
		problem.x = values;
		problem.y = labels;
		model = Svm.svm_train(problem, params);
		results = computeAnomalyScore(model, values, values);
		return results;
	}

	public svm_model getModel() {
		return model;
	}

}
