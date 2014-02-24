/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2014 by Deutsches Forschungszentrum fuer
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

import java.util.Arrays;
import java.util.HashMap;


import com.rapidminer.example.*;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;

public class OutlierColorJoin extends Operator{
	private InputPort outlierExampleSetInput = getInputPorts().createPort(	"outlier example set", OutlierExampleSet.class);
	private InputPort exampleSetInput = getInputPorts().createPort(	"example set", ExampleSet.class);
	private OutputPort outlierExampleSetOutput = getOutputPorts().createPort("outlier example set");
	
	public OutlierColorJoin(OperatorDescription description) {
		super(description);
	}
	public void doWork() throws OperatorException {
		ExampleSet exampleSet = exampleSetInput.getData(ExampleSet.class);
		OutlierExampleSet outlierExampleSet = outlierExampleSetInput.getData(OutlierExampleSet.class);
		Attribute eId = exampleSet.getAttributes().getId();
		Attribute oEId = outlierExampleSet.getAttributes().getId();
		// check if both exampleSets have an ID and both IDs have the same name.
		try {
			if(!eId.getName().equals(oEId.getName())) {
				throw new OperatorException("The Id Attributes of the ExampleSets do not match.");
				
			}
		}
		catch(NullPointerException E) {
			throw new OperatorException("At least one of the ExampleSets does not contain an ID Attribute");
			
		}
		combineExampleSets(exampleSet,outlierExampleSet);
		outlierExampleSetOutput.deliver(combineExampleSets(exampleSet,outlierExampleSet));
	}
	/* Create a new OutlierExampleSet containing the data from the input ExampleSet and
	 * the color values from the OutlierExampleSet.
	 */
	public OutlierExampleSet combineExampleSets(ExampleSet exampleSet,OutlierExampleSet outlierExampleSet) throws OperatorException {
		double currentId =0;
		Attributes exampleSetAttributes = exampleSet.getAttributes();
		Attributes outlierExampleSetAttributes = outlierExampleSet.getAttributes();
		//init color map
		HashMap<Integer,int[]> eColor = new HashMap<Integer,int[]>(); 
		for(Attribute att : exampleSetAttributes) {
			int[] colors = new int[exampleSet.size()];
			Arrays.fill(colors,-1);
			eColor.put(att.getTableIndex(), colors);
		}
		//for(int x = 0; x< exampleSet.size();x++) {
		int x = 0;
		outlierExampleSet.remapIds();
		
	    for(Example e : exampleSet) {
			currentId = e.getId();
			int[] i = outlierExampleSet.getExampleIndicesFromId(currentId);
			try {
				if(i.length > 1 ) {
					throw new OperatorException("Ids are not unique.");
				}
			}
			catch (NullPointerException E){
				this.logNote("Data Row with id " +currentId+ " not found in the OutlierExampleSet");
			}
				for(Attribute att: exampleSetAttributes) {
					for(Attribute outAtt : outlierExampleSetAttributes){
						// find attribute with the same name
						if(att.getName() == outAtt.getName()) {
							//save color
							int[] colors = eColor.get(att.getTableIndex());
							
							try {
							colors[x] = outlierExampleSet.colors.get(att.getTableIndex())[i[0]];
							}
							catch (NullPointerException E ){
								colors[x] = -1;
								this.logNote("Attribute "+ att.getName() + " not found in the OutlierExampleSet");
							}
							eColor.put(att.getTableIndex(), colors);
						}
					}
					
				}
			x++;
		}
		return new OutlierExampleSet(exampleSet,eColor);
		
	}
}
