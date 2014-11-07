/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2014 by Markus Goldstein or its licensors, as applicable.
 *
 *  This is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this software. If not, see <http://www.gnu.org/licenses/.
 *
 * Author: Markus Goldstein
 * Responsible: Markus Goldstein (goldstein@ait.kyushu-u.ac.jp)
 *
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.operator.statistical_based;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

import com.rapidminer.example.*;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.UserError;
import com.rapidminer.operator.features.transformation.PCAModel;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.metadata.ExampleSetPrecondition;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeCategory;
import com.rapidminer.parameter.ParameterTypeDouble;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.conditions.EqualTypeCondition;
import com.rapidminer.tools.Ontology;
import com.rapidminer.tools.math.matrix.CovarianceMatrix;
import com.rapidminer.example.Attribute;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.set.MappedExampleSet;
import com.rapidminer.example.table.AttributeFactory;


/**
 * Computes a robust PCA-based anomaly score. For robustness, trimming of the
 * original data set based on the Mahalanobis distance is performed first. Then,
 * PCA is computed and a score is determined based on the top upper and/or lower PCs.
 * This operator follows the papers "A Novel Anomaly Detection Scheme Based on
 * Principal Component Classifier" by Shyu et al (2003) and "Robust Methods for
 * Unsupervised PCA-based Anomaly Detection" by Kwitt et al. (2006). In contrast to
 * the original publications, this operator computes a normalized score instead of
 * classifying into normal/anomalous instances.
 * 
 * Based on RM's PCA class.
 * 
 * @author Markus Goldstein
 * 
 */
public class RobustPCAOperator extends Operator {
	public static final String PARAMETER_REDUCTION_TYPE = "component_usage";
	public static final String PARAMETER_REDUCTION_TYPE_DESCRIPTION = "Select wich principal components should be used for anomaly score computation. Major PCs are typically preferred in literature.";
	public static final String[] PCS_METHODS = new String[] {
		"use all components",
		"only use major components",
		"only use minor components",
		"use major and minor components"
	};

	public static final int PCS_ALL   = 0;
	public static final int PCS_TOP   = 1;
	public static final int PCS_LOWER = 2;
	public static final int PCS_BOTH  = 3;
	
	public static final String PARAMETER_TOP_METHODS = "major_components";
	public static final String PARAMETER_TOP_METHODS_DESCRIPTION = "Select method for major principal components to use.";
	public static final String[] PCS_TOP_METHODS = new String[] {
		"use variance threshold",
		"use fixed number of components",
	};
	
	public static final int PCS_TOP_VAR   = 0;
	public static final int PCS_TOP_FIX   = 1;

	public static final String PARAMETER_LOW_METHODS = "minor_components";
	public static final String PARAMETER_LOW_METHODS_DESCRIPTION = "Select method for minor principal components to use.";
	public static final String[] PCS_LOW_METHODS = new String[] {
		"use max eigenvalue",
		"use fixed number of components",
	};

	public static final int PCS_LOW_VAL   = 0;
	public static final int PCS_LOW_FIX   = 1;

	
	/**
	 * The normal class probability; used for robustness and chi**2 dist normalization.
	 */
	public static final String PARAMETER_OUTLIER_PROBABILITY = "probability_for_normal_class";
	public static final String PARAMETER_OUTLIER_PROBABILITY_DESCRIPTION = "This is the expected probability of normal data instances. Usually it should be between 0.95 and 1.0.";

	public static final String PARAMETER_VARIANCE_THRESHOLD = "cumulative_variance";
	public static final String PARAMETER_VARIANCE_THRESHOLD_DESCRIPTION = "Cumulative variance threshold for selecting major components.";

	public static final String PARAMETER_NUMBER_OF_COMPONENTS_TOP = "number_of_major_pcs";
	public static final String PARAMETER_NUMBER_OF_COMPONENTS_TOP_DESCRIPTION = "Number of major components to keep.";
	
	public static final String PARAMETER_NUMBER_OF_COMPONENTS_LOW = "number_of_minor_pcs";
	public static final String PARAMETER_NUMBER_OF_COMPONENTS_LOW_DESCRIPTION = "Number of minor components to keep.";

	public static final String PARAMETER_VALUE_THRESHOLD = "eigenvalue_threshold_max";
	public static final String PARAMETER_VALUE_THRESHOLD_DESCRIPTION = "The maximum allowed eigenvalue for minor components taken into account.";

	private InputPort exampleSetInput = getInputPorts().createPort("example set input");

	private OutputPort exampleSetOutput = getOutputPorts().createPort("example set output");

	public RobustPCAOperator(OperatorDescription description) {
		super(description);
		exampleSetInput.addPrecondition(new ExampleSetPrecondition(exampleSetInput, Ontology.NUMERICAL));
	}

	@Override
	public void doWork() throws OperatorException {
		// check whether all attributes are numerical
		ExampleSet exampleSet = exampleSetInput.getData(ExampleSet.class);

		Tools.onlyNonMissingValues(exampleSet, "PCA");
		Tools.onlyNumericalAttributes(exampleSet, "PCA");

		// Get normal probability.
		double normProb = getParameterAsDouble(PARAMETER_OUTLIER_PROBABILITY);
		int olInst = exampleSet.size() - (int) Math.floor(exampleSet.size()*normProb);
		log("Ignoring " + olInst + " anomalyous instances for robustness.");
				
		// The robust estimate is based on removing top outliers first based on Mahalanobis distance (MD).
		// Since MD² is the same as the outlier score when using all PCs, the PCA is done twice:
		// First with all examples, second with top-outliers removed (robust)
		
		// First PCA for outlier removal
		// create covariance matrix
		Matrix covarianceMatrix = CovarianceMatrix.getCovarianceMatrix(exampleSet);

		// EigenVector and EigenValues of the covariance matrix
		EigenvalueDecomposition eigenvalueDecomposition = covarianceMatrix.eig();

		// create and deliver results
		double[] eigenvalues = eigenvalueDecomposition.getRealEigenvalues();
		Matrix eigenvectorMatrix = eigenvalueDecomposition.getV();
		double[][] eigenvectors = eigenvectorMatrix.getArray();

		PCAModel model = new PCAModel(exampleSet, eigenvalues, eigenvectors);
	
		// Perform transformation
		ExampleSet res = model.apply((ExampleSet) exampleSet.clone());

		// Compute simple list with MDs and sort according to MD.
		List<double[]> l = new LinkedList<double[]>();
		double eIdx = 0;
		for (Example example : res) {
        	double md = 0.0;
        	int aNr = 0;
        	for ( Attribute attr : example.getAttributes() ) {
        		double pcscore = example.getValue(attr);
        		md += (pcscore*pcscore)/model.getEigenvalue(aNr);
        		aNr++;
        	}
        	double[] x = {md, eIdx};
        	l.add(x);
        	eIdx++;
		}
		Collections.sort(l,new Comparator<double[]>() {
            public int compare(double[] first, double[] second) {
                return Double.compare(second[0], first[0]);
            }
        });
		// Out of the list, create array with outlier-indexes and array (mapping) with good instances. 
		Iterator<double[]> iter = l.iterator();
		int[] olMapping = new int[olInst];
		for (int i=0; i < olInst; i++) {
			olMapping[i] = (int) ((double[])iter.next())[1];
		}
		Arrays.sort(olMapping);
		int[] mapping = new int[exampleSet.size()-olInst];
		int olc = 0;
		int ctr = 0;
		for (int i = 0; i < exampleSet.size(); i++) {
			if (olc == olInst) { // Add last elements after last outlier
				mapping[ctr++] = i;
				continue;
			}
			if (olMapping[olc] != i) {
				mapping[ctr++] = i;
			}
			else {
				olc++;
			}
		}
		ExampleSet robustExampleSet =  new MappedExampleSet(exampleSet, mapping); // creates a new example set without the top outliers.

		// ---
		// Second PCA (robust)
		covarianceMatrix = CovarianceMatrix.getCovarianceMatrix(robustExampleSet);
		eigenvalueDecomposition = covarianceMatrix.eig();

		// create and deliver results
		eigenvalues = eigenvalueDecomposition.getRealEigenvalues();
		eigenvectorMatrix = eigenvalueDecomposition.getV();
		eigenvectors = eigenvectorMatrix.getArray();

		// Apply on original set
		model = new PCAModel(exampleSet, eigenvalues, eigenvectors);
		
		// Perform transformation
		res = model.apply((ExampleSet) exampleSet.clone());
		
		// Sort eigenvalues
		Arrays.sort(eigenvalues);
		ArrayUtils.reverse(eigenvalues);
		
		// if necessary reduce nbr of dimensions ...
		int reductionType = getParameterAsInt(PARAMETER_REDUCTION_TYPE);
		List<Integer> pcList = new ArrayList<Integer>();
		if (reductionType == PCS_ALL) {
			for (int i=0; i<exampleSet.getAttributes().size(); i++) {
				pcList.add(i);
			}
		}
		if (reductionType == PCS_TOP || reductionType == PCS_BOTH ) {
			//top
			switch (getParameterAsInt(PARAMETER_TOP_METHODS)) {
			case PCS_TOP_FIX:
				for (int i=0; i<getParameterAsInt(PARAMETER_NUMBER_OF_COMPONENTS_TOP); i++) {
					pcList.add(i);
				}
				break;
			case PCS_TOP_VAR:
				double var = getParameterAsDouble(PARAMETER_VARIANCE_THRESHOLD);
				boolean last = false;
				for (int i=0; i < exampleSet.getAttributes().size(); i++) {
					if (model.getCumulativeVariance(i) < var) {
						pcList.add(i);
					}
					else if (!last) { // we need to add another PC to meet the minimum requirement.
						last = true;
						pcList.add(i);
					}
				}
				break;
			}
		}
		if (reductionType == PCS_LOWER || reductionType == PCS_BOTH ) {
			//lower
			switch (getParameterAsInt(PARAMETER_LOW_METHODS)) {
			case PCS_LOW_FIX:
				for (int i=exampleSet.getAttributes().size()-getParameterAsInt(PARAMETER_NUMBER_OF_COMPONENTS_LOW); i<exampleSet.getAttributes().size(); i++) {
					pcList.add(i);
				}
				break;
			case PCS_LOW_VAL:
				double val = getParameterAsDouble(PARAMETER_VALUE_THRESHOLD);
				for (int i=0; i<eigenvalues.length; i++) {
					if (eigenvalues[i] <= val) {
						if (pcList.size() == 0) {
							pcList.add(i);
						}
						else if (pcList.get(pcList.size()-1).intValue() < i) {
							pcList.add(i);
						}
					}
				}
				break;
			}
		}
        int[] opcs = ArrayUtils.toPrimitive(pcList.toArray(new Integer[pcList.size()]));

        if (opcs.length == 0) {
        	throw new UserError(this, "Parameters thresholds are selected such that they did not match any principal component. Lower variance or increase eigenvalue threshold.");
        }
        if (opcs.length == exampleSet.getAttributes().size()) {
        	log("Using all PCs for score.");	
        }
        else {
        	log("Using following PCs for score: " + Arrays.toString(opcs));
        }
		
		// Normalize by Chi²-Dist with d degrees of freedom
		double scoreNormalizer = 1.0;
		ChiSquaredDistributionImpl chi = new ChiSquaredDistributionImpl(opcs.length);
		try {
			scoreNormalizer = chi.inverseCumulativeProbability(normProb);
		}
		catch (MathException e) {
			System.err.println(e);
		}
		log("Normalizing score with chi² cumulative propability: "+scoreNormalizer);


		// compute scores
		Attribute scoreAttr = AttributeFactory.createAttribute("outlier",Ontology.REAL);
		exampleSet.getExampleTable().addAttribute(scoreAttr);
        exampleSet.getAttributes().setOutlier(scoreAttr);
        for (int exNr = 0; exNr < exampleSet.size(); exNr++) {
        	Example orig = exampleSet.getExample(exNr);
        	Example pc = res.getExample(exNr);
        	double oscore = 0.0;
        	int aNr = 0;
            ctr = 0;
        	for ( Attribute attr : pc.getAttributes() ) {
        		if (ctr < opcs.length && opcs[ctr] != aNr) { // we skip this dimension
        			aNr++;
        			continue;
        		}
        		double pcscore = pc.getValue(attr);
        		oscore += (pcscore*pcscore)/model.getEigenvalue(aNr);
        		aNr++;
        		ctr++;
        	}
        	orig.setValue(scoreAttr, oscore/scoreNormalizer);
        }
		exampleSetOutput.deliver(exampleSet);
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> list = super.getParameterTypes();
		
		list.add(new ParameterTypeDouble(PARAMETER_OUTLIER_PROBABILITY, PARAMETER_OUTLIER_PROBABILITY_DESCRIPTION, 0, 1.0, 0.975, false));
		
		ParameterType type = new ParameterTypeCategory(PARAMETER_REDUCTION_TYPE, PARAMETER_REDUCTION_TYPE_DESCRIPTION, PCS_METHODS, PCS_ALL);
		type.setExpert(false);
		list.add(type);

		type = new ParameterTypeCategory(PARAMETER_TOP_METHODS, PARAMETER_TOP_METHODS_DESCRIPTION, PCS_TOP_METHODS, PCS_TOP_VAR);
		type.setExpert(false);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_REDUCTION_TYPE, PCS_METHODS, false, PCS_TOP, PCS_BOTH));
		list.add(type);

		type = new ParameterTypeDouble(PARAMETER_VARIANCE_THRESHOLD, PARAMETER_VARIANCE_THRESHOLD_DESCRIPTION, Double.MIN_VALUE, 1, 0.50);
		type.setExpert(false);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_REDUCTION_TYPE, PCS_METHODS, true, PCS_TOP, PCS_BOTH));
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_TOP_METHODS, PCS_TOP_METHODS, true, PCS_TOP_VAR));
		list.add(type);

		type = new ParameterTypeInt(PARAMETER_NUMBER_OF_COMPONENTS_TOP, PARAMETER_NUMBER_OF_COMPONENTS_TOP_DESCRIPTION, 1, Integer.MAX_VALUE, 1);
		type.setExpert(false);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_REDUCTION_TYPE, PCS_METHODS, true, PCS_TOP, PCS_BOTH));
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_TOP_METHODS, PCS_TOP_METHODS, true, PCS_TOP_FIX));
		list.add(type);

		type = new ParameterTypeCategory(PARAMETER_LOW_METHODS, PARAMETER_LOW_METHODS_DESCRIPTION, PCS_LOW_METHODS, PCS_LOW_VAL);
		type.setExpert(false);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_REDUCTION_TYPE, PCS_METHODS, false, PCS_LOWER, PCS_BOTH));
		list.add(type);
		
		type = new ParameterTypeDouble(PARAMETER_VALUE_THRESHOLD, PARAMETER_VALUE_THRESHOLD_DESCRIPTION, 0, Double.MAX_VALUE, 0.20);
		type.setExpert(false);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_REDUCTION_TYPE, PCS_METHODS, true, PCS_LOWER, PCS_BOTH));
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_LOW_METHODS, PCS_LOW_METHODS, true, PCS_LOW_VAL));
		list.add(type);
		
		type = new ParameterTypeInt(PARAMETER_NUMBER_OF_COMPONENTS_LOW, PARAMETER_NUMBER_OF_COMPONENTS_LOW_DESCRIPTION, 1, Integer.MAX_VALUE, 1);
		type.setExpert(false);
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_REDUCTION_TYPE, PCS_METHODS, true, PCS_LOWER, PCS_BOTH));
		type.registerDependencyCondition(new EqualTypeCondition(this, PARAMETER_LOW_METHODS, PCS_LOW_METHODS, true, PCS_LOW_FIX));
		list.add(type);

		return list;
	}
}
