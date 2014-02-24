/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2012 by Deutsches Forschungszentrum fuer
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
 * Author: Johann Gebhardt
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 *
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.operator.statistical_based;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import com.rapidminer.example.*;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.MetaDataChangeListener;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.metadata.AttributeMetaData;
import com.rapidminer.operator.ports.metadata.ExampleSetMetaData;
import com.rapidminer.operator.ports.metadata.MetaData;
import com.rapidminer.operator.ports.metadata.ModelMetaData;
import com.rapidminer.parameter.MetaDataProvider;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeAttribute;
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeCategory;
import com.rapidminer.parameter.ParameterTypeList;
import com.rapidminer.parameter.ParameterTypeString;
import com.rapidminer.parameter.ParameterTypeStringCategory;
import com.rapidminer.parameter.ParameterTypeTupel;
import com.rapidminer.parameter.UndefinedParameterError;
import com.rapidminer.parameter.conditions.BooleanParameterCondition;
import com.rapidminer.parameter.conditions.EqualStringCondition;

import de.dfki.madm.anomalydetection.evaluator.statistical_based.HistogramEvaluator;

/**
 * The HBOS operator calculates the outlier score based on univariate histograms
 * 
 * It calculates a separate histogram for every column in the data. 
 * There are two modes, one with a static and one with a dynamic bandwidth. In 
 * the static mode every bin has the same binwidth. In the dynamic mode the 
 * bindwidth can vary, but you can specify a minimum number of datapoints that 
 * fall in every bin. The default values for either the number of bins or the 
 * minimum of datapoints per bin is the square root of the number of datapoints.
 * 
 * The outlier score is calculated by setting the most normal bin to one and 
 * calculating the relative difference in bin high for all other bins. The score
 * is inverted, so that anomalies have a higher score. 
 * 
 * It is also possible to apply a logarithmic scaling to the score (log_scale).
 * 
 * @author Johann Gebhardt, Markus Goldstein
 * 
 */
public class HistogramOperator extends Operator {
	private boolean  outlier_color;
	private static final String PARAMETER_LOG_SCALE = "use log scale (better precision)";
	private static final String PARAMETER_PROPERTIES_LIST = "histogram properties";
	private static String[] CONDITION_NAMES = new String[] { "all", "single"};
	private static final String PARAMETER_FILTER_TYPE = "parameter mode";
	private static final String PARAMETER_BIN_INFO ="number of bins";
	private static final String PARAMETER_MODE="select mode";
	private static final String PARAMETER_COLUMN_PROPERTIES = "column properties";
	private static final String PARAMETER_ATTRIBUTE_NAME = "attribute name";
	private static final String PARAMETER_RANKED_MODE = "ranked mode";
	
    private MetaDataProvider metaDataProvider;
	/**
	 * input port getAllAttributeNames
	 */
	private InputPort exampleSetInput = getInputPorts().createPort(	"example set", ExampleSet.class);
	
	/**
	 * output port
	 */
	private OutputPort exampleSetOutput = getOutputPorts().createPort("example set");
	private OutputPort outlierExampleSetOutput = getOutputPorts().createPort("outlier example set");
	boolean defaultlist = false;
	MetaDataChangeListener l = new MetaDataChangeListener() {
		@Override
		/* fill properties list with initial default values
		 * if the currently used parameter list contains different attribute name 
		 * than the current exampleSet input
		 */
		public void informMetaDataChanged(MetaData newMetadata) {
			if(newMetadata != null){
				/* fetch the currently saved attribute names
				 * 
				 */
				List<String[]> current_list = new LinkedList<String[]>();
				try {
					current_list = getParameterList(PARAMETER_PROPERTIES_LIST);
				} catch (UndefinedParameterError e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				List<String> current_list_names = new LinkedList<String>();
				for(String[] m :current_list) {
					current_list_names.add(m[0]);
				}
				/* create the default list
				 * 
				 */
				if(newMetadata instanceof ExampleSetMetaData){
					ExampleSetMetaData emd = (ExampleSetMetaData) newMetadata;
					Vector<String> names = getRegularAttributeNames();
					List<String[]> list =  new LinkedList<String[]>();
					for (String name:names){
						String[] map=new String[2];
						map[0] = name;
						if(emd.getAttributeByName(name).isNominal()) {
							map[1] = "fixed binwidth.nominal";	
						}
						else {
							map[1] = "fixed binwidth.-1";
						}
						list.add(map);
					}
					/*
					 * override the current list with the default names if the attributes differ
					 */
					boolean help = false;
					for(String[] m :list) {
						if(current_list_names.contains(m[0])){
							continue;
						}
						else {
							help = true;
							break;
						}
					}
					if(help) {
						setParameter(PARAMETER_PROPERTIES_LIST,ParameterTypeList.transformList2String(list));
					}
				}
			}
		}
	};
	
	public HistogramOperator(OperatorDescription description) {
		super(description);
		this.metaDataProvider = new MetaDataProvider() {
			@Override
			public MetaData getMetaData() {
				if (exampleSetInput != null) {
					return exampleSetInput.getMetaData();
				} else {
					return null;
				}
			}
			@Override
			public void addMetaDataChangeListener(MetaDataChangeListener l) {
				exampleSetInput.registerMetaDataChangeListener(l);
			}
			@Override
			public void removeMetaDataChangeListener(MetaDataChangeListener l) {
				exampleSetInput.removeMetaDataChangeListener(l);
				
			}   	
		};
		this.metaDataProvider.addMetaDataChangeListener(l);
	}
	
	public void doWork() throws OperatorException {
		// Only use color coded output if necessary to compute (speed)
		 if(outlierExampleSetOutput.isConnected()) {
			 outlier_color = true;
		 }
		 else {
			 outlier_color = false;
		 }
		 boolean log_scale = getParameterAsBoolean(PARAMETER_LOG_SCALE);
		 boolean ranked = getParameterAsBoolean(PARAMETER_RANKED_MODE);
		 String parameter_mode =getParameterAsString(PARAMETER_FILTER_TYPE);
		 int bin_width = getParameterAsInt(PARAMETER_BIN_INFO);
		 String mode2 = getParameterAsString(PARAMETER_MODE);
		 ExampleSet exampleSet = exampleSetInput.getData(ExampleSet.class);
	     List<String[]> list = getParameterList(PARAMETER_PROPERTIES_LIST);
		 HashMap<String,Integer> bin_info = new HashMap<String,Integer>();
		 HashMap<String,String> mode = new HashMap<String,String>();
		 if(parameter_mode.equals("all")){
			 for(Attribute att : exampleSet.getAttributes()) {
				 mode.put(att.getName(),mode2);
				 if(att.isNominal()){
					 bin_info.put(att.getName(),1);
				 }
				 else {
					 bin_info.put(att.getName(), bin_width);
				 }
			 }
		 }
		 else{
			 for(String[] m : list){
				 String[] splitArray = m[1].split("(?<!\\\\)\\.");
				 splitArray[1]= splitArray[1].replace("\\", ""); 
				 mode.put(m[0], splitArray[0]);
				 if(splitArray[1].equals("nominal")){
					 bin_info.put(m[0],1);
				 }
				 else  {
					 bin_info.put(m[0],Integer.parseInt(splitArray[1]));
				 }
			 }
		 }
		HistogramEvaluator evaluator = new HistogramEvaluator(this);
		ExampleSet eS = evaluator.evaluate(exampleSet,log_scale,ranked,bin_info,mode,outlier_color);
		exampleSetOutput.deliver(eS);
		if(outlier_color){
			OutlierExampleSet oES = new OutlierExampleSet(eS,evaluator.colors);
			outlierExampleSetOutput.deliver(oES);
		}
	
	}

	public List<ParameterType> getParameterTypes() {
		LinkedList<ParameterType> types = new LinkedList<ParameterType>();
		String[] mode = new String[2];
		mode[0] = "fixed binwidth";
		mode[1] = "dynamic binwidth";
		ParameterTypeString type_int= new ParameterTypeString(PARAMETER_BIN_INFO,"Specifies the number of bins. " +
				"When using static binwidth the binwidth is set to (range of values)/(number of bins)."+
				"When using dynamic binwidth the minimum number of bins is set to (number of examples)/(number of bins)." +
				"In this case it is possible that there are less bins than specified if some bins contain more than the minimum number of values. Set to -1 for default value (sqrt(N)).","-1");
		ParameterTypeStringCategory type_category  = new ParameterTypeStringCategory(PARAMETER_MODE,"Select dynamic or fixed binwidth mode",mode,"fixed binwidth");
		type_category.setEditable(false);
		ParameterTypeList typeList = new ParameterTypeList(PARAMETER_PROPERTIES_LIST, "properties for every column - select mode and number of bins for every column (set binwidth to -1 for default value or to nominal for categorical data)",
				new ParameterTypeAttribute(PARAMETER_ATTRIBUTE_NAME, "The index of the column whose properties should be changed.",getExampleSetInputPort()),
				new ParameterTypeTupel(PARAMETER_COLUMN_PROPERTIES, "properties", 
						 									type_category,
						 									type_int));
		ParameterType type2 = new ParameterTypeCategory(PARAMETER_FILTER_TYPE, "switch between setting the histogram properties for all columns at once or individual for every column", CONDITION_NAMES, 0);
		type2.setExpert(false);
		types.add(type2);	
		typeList.registerDependencyCondition(new EqualStringCondition(this,PARAMETER_FILTER_TYPE,false,"single"));
		types.add(typeList);
		types.add(type_int);
		type_int.registerDependencyCondition(new EqualStringCondition(this,PARAMETER_FILTER_TYPE,false,"all"));
		types.add(type_category);
		type_category.registerDependencyCondition(new EqualStringCondition(this,PARAMETER_FILTER_TYPE,false,"all"));
		types.add(new ParameterTypeBoolean(PARAMETER_RANKED_MODE,"rank bins and use the rank of a bin as its score",false,false));
		ParameterTypeBoolean type = new ParameterTypeBoolean(PARAMETER_LOG_SCALE,"set to true for a logarithmic scaled score",true,false);
		type.registerDependencyCondition(new BooleanParameterCondition(this, PARAMETER_RANKED_MODE, false, false));
		types.add(type);
		 
		return types;
	}
	public InputPort getExampleSetInputPort() {
		return exampleSetInput;
	}
	/*
	 * return all regular attribute names
	 */
	public Vector<String> getRegularAttributeNames() {
        Vector<String> names = new Vector<String>();
        Vector<String> regularNames = new Vector<String>();
        MetaData metaData = getMetaData();
        if (metaData != null) {
            if (metaData instanceof ExampleSetMetaData) {
                ExampleSetMetaData emd = (ExampleSetMetaData) metaData;
                for (AttributeMetaData amd : emd.getAllAttributes()) {
                	
                    if (true) {
                        if (amd.isSpecial())
                            names.add(amd.getName());
                        else
                            regularNames.add(amd.getName());
                    }

                }
            } else if (metaData instanceof ModelMetaData) {
                ModelMetaData mmd = (ModelMetaData) metaData;
                if (mmd != null) {
                    ExampleSetMetaData emd = mmd.getTrainingSetMetaData();
                    if (emd != null) {
                        for (AttributeMetaData amd : emd.getAllAttributes()) {
                        	System.out.println("test");
                            if (true)
                                if (amd.isSpecial())
                                    names.add(amd.getName());
                                else
                                    regularNames.add(amd.getName());
                        }
                    }
                }
            }
        }
        Collections.sort(names);
        Collections.sort(regularNames);
        names.addAll(regularNames);

        return regularNames;
    }
	
	public MetaData getMetaData() {
		MetaData metaData = null;
	    if (metaDataProvider != null) {
	       	metaData = metaDataProvider.getMetaData();
	    }
		return metaData;
	}
}
