/*
 * RapidMiner Anomaly Detection Extension
 * 
 * Copyright (C) 2009-2013 by Deutsches Forschungszentrum fuer Kuenstliche
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

package de.dfki.madm.anomalydetection.operator.evaluation;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.ExampleSetFactory;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.example.table.NominalMapping;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ValueDouble;
import com.rapidminer.operator.performance.MultiClassificationPerformance;
import com.rapidminer.operator.performance.PerformanceCriterion;
import com.rapidminer.operator.performance.PerformanceVector;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.metadata.GenerateNewMDRule;
import com.rapidminer.operator.ports.metadata.MetaData;
import com.rapidminer.operator.ports.metadata.PassThroughOrGenerateRule;
import com.rapidminer.operator.ports.metadata.SimplePrecondition;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeString;
import com.rapidminer.tools.Ontology;

import de.dfki.madm.anomalydetection.evaluator.evaluation.ROCEvaluator;

public class ROCOperator extends Operator {

	private InputPort exampleSetInput = getInputPorts().createPort("example set", ExampleSet.class);
	private OutputPort exampleSetOutput = getOutputPorts().createPort("example set");
	private OutputPort rocExampleSet = getOutputPorts().createPort("roc set");
	private OutputPort aucOutput = getOutputPorts().createPort("auc");
	private OutputPort preOutput = getOutputPorts().createPort("pre");
	
	private InputPort performanceInput = getInputPorts().createPort("performance");
	private OutputPort performanceOutput = getOutputPorts().createPort("performance");
	
	public static String PARAMETER_LABEL = "label value for outliers";
	public double auc;
	private PerformanceVector currentPerformanceVector =null;

	private void preSetOutlierLable() throws OperatorException {
		HashMap<Object, Integer> count = new HashMap<Object, Integer>();
		double exampleSize = exampleSetInput.getData(ExampleSet.class).size();

		for (Object s : initializeLabels(exampleSetInput.getData(ExampleSet.class))) {
			int val = 0;
			if (count.containsKey(s))
				val = count.get(s);
			val++;
			count.put(s, val);
		}

		Object less = null;
		for (Object k : count.keySet()) {
			if (less == null || count.get(less) > count.get(k)) {
				less = k;
			}
		}
		if (!less.equals(getParameterAsString(PARAMETER_LABEL))) {
			this.logWarning("Specified outlier class " + getParameterAsString(PARAMETER_LABEL) + " is not smallest class (" + (count.get(less) / exampleSize) + "%)");
		}
		else {
			this.logNote("Outlier class " + less + " is with " + (count.get(less) / exampleSize) + "% ok");
		}
	}
	/**
     * Adds the performance criteria as plottable values
     */
    public void addPerformanceValue(final String name, String description) {
        addValue(new ValueDouble(name, description) {
            @Override
            public double getDoubleValue() {
                if (currentPerformanceVector == null)
                    return Double.NaN;
                PerformanceCriterion c = currentPerformanceVector.getCriterion(name);

                if (c != null) {
                    return c.getAverage();
                } else {
                    return Double.NaN;
                }
            }
        });
    }
	private List<PerformanceCriterion> getCriteria() {
		List<PerformanceCriterion> performanceCriteria = new LinkedList<PerformanceCriterion>();
		performanceCriteria.add( new MultiClassificationPerformance(MultiClassificationPerformance.ACCURACY));
		performanceCriteria.add(new ROCPerformanceVector("AUC", auc));
		return performanceCriteria;
	}


	public ROCOperator(OperatorDescription description) {
		super(description);
		performanceInput.addPrecondition(new SimplePrecondition(performanceInput, new MetaData(PerformanceVector.class), false));
		getTransformer().addRule(new PassThroughOrGenerateRule(performanceInput, performanceOutput, new MetaData(PerformanceVector.class)));
		getTransformer().addRule(new GenerateNewMDRule(performanceOutput, PerformanceVector.class));
		getTransformer().addPassThroughRule(exampleSetInput, exampleSetOutput);
		getTransformer().addGenerationRule(rocExampleSet, ExampleSet.class);
		List<PerformanceCriterion> criteria = getCriteria();
        for (PerformanceCriterion criterion : criteria) {
            addPerformanceValue(criterion.getName(), criterion.getDescription());
        }
		
		addValue(new ValueDouble("performance", "The last performance (main criterion).") {
	            @Override
	            public double getDoubleValue() {
	                if (currentPerformanceVector != null)
	                    return currentPerformanceVector.getMainCriterion().getAverage();
	                else
	                    return Double.NaN;
	            }
	        });
		
	}

	@Override
	public void doWork() throws OperatorException {
		currentPerformanceVector = performanceInput.getDataOrNull(PerformanceVector.class);
		if (currentPerformanceVector == null) {            
			currentPerformanceVector = new PerformanceVector();
		}
		
		PerformanceCriterion perCrit;
		preSetOutlierLable();
		ExampleSet exampleSet = exampleSetInput.getData(ExampleSet.class);
		Object[] labels = initializeLabels(exampleSet);

		double[] outliers = getOutliers(exampleSet);

		ROCEvaluator evaluator = new ROCEvaluator();
		String outlierString = getParameterAsString(PARAMETER_LABEL);
		ExampleSet roc = (ExampleSet) ExampleSetFactory.createExampleSet(evaluator.evaluate(outlierString, labels, outliers));
		ExampleSet pre = (ExampleSet) ExampleSetFactory.createExampleSet(evaluator.pre);
		String norm = evaluator.getNormalClass();
		roc.getAttributes().get("att1").setName("false_positive_rate");
		roc.getAttributes().get("att2").setName("true_positive_rate");
		pre.getAttributes().get("att1").setName("precision");
		pre.getAttributes().get("att2").setName("recall");
		rocExampleSet.deliver(roc);
		preOutput.deliver(pre);
		auc = evaluator.auc;
		Object[][] auc_ = { { auc } };
		Object[] labels2 = { "AUC" };
		ExampleSet a = (ExampleSet) ExampleSetFactory.createExampleSet(auc_, labels2);
		
		perCrit = new ROCPerformanceVector("AUC", auc);
		currentPerformanceVector.addCriterion(perCrit);
		currentPerformanceVector.setMainCriterionName("AUC");
		
		Attribute pediction;
		int i;
		pediction = AttributeFactory.createAttribute("prediction", Ontology.NOMINAL);
		exampleSet.getExampleTable().addAttribute(pediction);
		exampleSet.getAttributes().setPredictedLabel(pediction);
		i = 0;
		String s = "";
		for (Example example : exampleSet) {
			if (evaluator.out.contains(i))
				s = getParameterAsString(PARAMETER_LABEL);
			else 
				s = norm;
			example.setValue(pediction, s);
			i++;
		}
		
		MultiClassificationPerformance test = new MultiClassificationPerformance(MultiClassificationPerformance.ACCURACY);
		test.startCounting(exampleSet, false);
		for (Example e : exampleSet)
			test.countExample(e);
		currentPerformanceVector.addCriterion(test);
		
		exampleSetOutput.deliver(exampleSet);
		performanceOutput.deliver(currentPerformanceVector);
		aucOutput.deliver(a);
	}

	public double[] getOutliers(ExampleSet exampleSet) {
		double[] outliers = new double[exampleSet.size()];
		int currentExample = 0;
		Attribute attribute = exampleSet.getAttributes().getOutlier();
		for (Example example : exampleSet) {
			outliers[currentExample++] = example.getValue(attribute);
		}
		return outliers;

	}

	public Object[] initializeLabels(ExampleSet exampleSet) {
		Attribute label = exampleSet.getAttributes().getLabel();
		Object[] labels = new Object[exampleSet.size()];
		int currentExample = 0;
		if (label.isNumerical())
			for (Example example : exampleSet) {
				labels[currentExample++] = example.getValue(label);
			}
		else {
			NominalMapping nominalMapping = label.getMapping();
			for (Example example : exampleSet) {
				labels[currentExample++] = nominalMapping.mapIndex((int) (example.getValue(label)));
			}

		}

		return labels;

	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types = super.getParameterTypes();
		types.add(new ParameterTypeString(PARAMETER_LABEL, "The value that define the anomalous class for the attribute with the role \"label\". There should only be one label for the normal class and one label for the outlier class, if multiple labels exist, please rename them first.", "", false));
		return types;
	}

}
