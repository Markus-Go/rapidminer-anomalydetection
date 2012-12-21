/*
 * RapidMiner Anomaly Detection Extension
 * 
 * Copyright (C) 2009-2012 by Deutsches Forschungszentrum fuer Kuenstliche
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
 * Author: Patrick Kalka
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 * 
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */

package de.dfki.madm.anomalydetection.evaluator.cluster_based;

import java.util.LinkedList;

import Jama.Matrix;

public class CovarianceMatrix {
	private double[][] data;
	private double[][] CovMat;
	private int dim = 0;
	private int numberOfThreads;

	public double[][] getCovMat() {
		return this.CovMat;
	}

	public void addMinimum() {
		Matrix mh = new Matrix(this.CovMat);
		Matrix m = Matrix.identity(mh.getColumnDimension(), mh.getRowDimension());
		m = m.times(0.0000000000001);
		mh.plusEquals(m);
		this.CovMat = mh.getArray();
	}

	private void doWork() {
		this.dim = this.data[0].length;
		this.CovMat = new double[dim][dim];
		this.calcCovMat();
	}
	
	public CovarianceMatrix(LinkedList<double[]> data, int numberOfThreads) {
		this.data = new double[data.size()][];
		int index = 0;
		for (double[] ar : data) {
			this.data[index] = ar;
			index++;
		}
		this.numberOfThreads = numberOfThreads;
		this.doWork();
	}

	public CovarianceMatrix(double[][] data, int numberOfThreads) {
		this.data = data;
		this.numberOfThreads = numberOfThreads;
		this.doWork();
	}

	private void calcCovMat() {
		Thread[] temp = new Thread[this.numberOfThreads];
		int count = 0;
		for (int i = 0; i < dim; i++) {
			for (int j = 0; j < dim; j++) {
				temp[count]= new worker(CovMat, i, j, data);
				temp[count].start();
				count++;
				if (count == this.numberOfThreads) {
					count = 0;
					for (int j1 = 0; j1 < this.numberOfThreads; j1++) {
						try {
							temp[j1].join();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
}

class worker extends Thread {
	double[][] CovMat;
	double[][] points;
	private int j;
	private int k;

	public worker(double[][] CovMat, int j, int k, double[][] points) {
		this.CovMat = CovMat;
		this.points = points;
		this.j = j;
		this.k = k;
	}

	@Override
	public void run() {
		double ret = 0;

		for (int i = 0; i < points.length; i++) {
			ret += (points[i][j] * points[i][k]);
		}

		ret = (1.0 / (points.length - 1)) * ret;

		synchronized (CovMat) {
			CovMat[j][k] = ret;
		}
	}
}