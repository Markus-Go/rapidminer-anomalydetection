package de.dfki.madm.anomalydetection.operator.kernel_based;

import com.rapidminer.operator.Value;

public class NumberOfSupportVectorsValue  extends Value{

	private int numberofSupportVectors;
	public NumberOfSupportVectorsValue(String key, String description) {
		super(key, description);
		
	}

	public void setNumberofSupportVectors(int numberofSupportVectors) {
		this.numberofSupportVectors = numberofSupportVectors;
	}


	public Object getValue() {
		return new Integer(numberofSupportVectors);
	}


	public boolean isNominal() {
		return true;
	}

}
