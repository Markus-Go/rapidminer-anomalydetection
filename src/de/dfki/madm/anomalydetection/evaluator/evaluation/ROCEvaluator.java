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

package de.dfki.madm.anomalydetection.evaluator.evaluation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import com.rapidminer.operator.OperatorException;

public class ROCEvaluator {
	private class OutlierPair implements Comparable<OutlierPair> {
		private int index;
		private double outlierScore;

		public OutlierPair(int i, double o) {
			index = i;
			outlierScore = o;
		}

		@Override
		public int compareTo(OutlierPair arg0) {
			if (outlierScore > arg0.outlierScore)
				return -1;
			if (outlierScore < arg0.outlierScore)
				return 1;
			return 0;
		}
		
		@Override
		public String toString() {
			return this.outlierScore + " " + this.index;			
		}
	}

	public double auc;
	public ArrayList<Integer> out = new ArrayList<Integer>();
	private String normal = "";

	public String getNormalClass() {
		return this.normal;
	}
	public Object[][] pre = null; //prediction / recall
	/**
	 * The returned array has 2 columns denoting: false positive rate,true positive rate 
	 *  precision/recall will be stored in pre. (true positive rate==recall)
	 */
	public Object[][] evaluate(String outlierString, Object[] labels, double[] res) throws OperatorException {
		int size = res.length;
		Object[][] result;

		LinkedList<double[]> rocPoints = new LinkedList<double[]>();

		int count = 0;
		int anz_outlier = 0;
		
		int positive = 0;
		int negative = 0;
		int truePositive = 0;
		int falsePositive = 0;
		OutlierPair[] outliers = new OutlierPair[size];
		for (int j = 0; j < size; j++) {
			if (labels[j].toString().equals(outlierString)) {
				anz_outlier++;
			}
			outliers[j] = new OutlierPair(j, res[j]);
		}
		Arrays.sort(outliers);
		double Area = 0;
		double[] last = new double[] { 0, 0 };
		for (int j = 0; j < size; j++) {

			int x = outliers[j].index;

			if (count < anz_outlier) {
				this.out.add(outliers[j].index);
				count++;
			}

			if (labels[x].toString().equals(outlierString)) {
				truePositive++;
				positive++;
			} else {
				if (this.normal.equals("")) {
					this.normal = labels[x].toString();
				}
				else {
					if (!this.normal.equals(labels[x].toString()) && !outlierString.equals("")) {
						throw new OperatorException("There should be only two labels (normal & outlier). Currently found :" + outlierString + ", " + this.normal + " and " + labels[x].toString());
					}
				}
				falsePositive++;
				negative++;

			}
			if (j != size - 1 && outliers[j].outlierScore == outliers[j + 1].outlierScore)
				continue;
			Area += last[1] * ((double)falsePositive - last[0]) + (double)0.5 * ((double)falsePositive - last[0]) * ((double)truePositive - last[1]);
			rocPoints.add(new double[] { falsePositive, truePositive , truePositive*1.0/(truePositive+falsePositive), outliers[j].outlierScore});
			last[0] = falsePositive;
			last[1] = truePositive;

		}
		if (positive == 0) {
			throw new OperatorException("'" + outlierString + "' not found in the labels");
		}
		if (negative == 0) {
			throw new OperatorException("All the records are '" + outlierString + "'");
		}
		double totalArea = (double)positive * (double)negative;

		auc = Area / totalArea;
		result = new Object[rocPoints.size()][2];
		int i = 0;
		pre = new Object[rocPoints.size()][2];
		for (double[] r : rocPoints) {
			result[i][0] = r[0] / negative;
			result[i++][1] = r[1] / positive;
			}
		i=0;
		for(double[] r : rocPoints) {
			pre[i][0] = r[2]; // precision = tp /(tp+fp)
			pre[i++][1] = r[1] / positive; //recall = tp(so far) / all outlier
		}

		return result;
	}
}
