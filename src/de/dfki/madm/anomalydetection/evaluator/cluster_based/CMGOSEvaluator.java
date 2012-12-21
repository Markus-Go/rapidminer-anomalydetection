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
 * Author: Markus Goldstein, Patrick Kalka
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 * 
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.evaluator.cluster_based;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;

import Jama.Matrix;

import com.rapidminer.operator.OperatorException;
import com.rapidminer.tools.RandomGenerator;
import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.Evaluator;

/**
 * The evaluator of CMGOS. This is where the algorithm
 * logic is implemented.
 * 
 * @author Patrick Kalka
 * @author Markus Goldstein
 * 
 */
public class CMGOSEvaluator implements Evaluator {

	public static final int METHOD_COV_REDUCTION = 0;
	public static final int METHOD_COV_REGULARIZE = 1;
	public static final int METHOD_COV_MCD = 2;
	
	/**
	 * The measure used to calculate the distances
	 **/
	protected DistanceMeasure measure;
	/**
	 * The points in the example set.
	 **/
	protected double[][] points;
	/**
	 * Maps each point to a cluster.
	 **/
	protected int[] belongsToCluster;
	/**
	 * The centroids of the clusters
	 **/
	protected double[][] centroids;
	/**
	 * The size of each cluster
	 **/
	protected int clusterSize[];
	/**
	 * The number of threads to be used
	 */
	protected int numberOfThreads;
	/**
	 * times to do remove outliers and recompute the COV
	 */
	private int removeRuns;
	/**
	 * The probability for normal instances
	 */
	private double probability;
	/**
	 * Number of points for covariance matrix calculation 
	 * (faster computation by sampling)
	 */
	private int cov_sampling;

	private RandomGenerator generator;
	/**
	 * Number of points to define a &quote;small cluster&quote;
	 * Small clusters are removed.
	 */
	private int minimumInstancesForCluster;
	/**
	 * Lambda for regularization method
	 */
	private double regularizedLambda;
	/**
	 * Method for cov computation
	 */
	private int red;
	/**
	 * h
	 */
	private int h;
	private int subsetPoints;
	private int fastMCDPoints;
	private int initIteration;
	CovarianceMatrix[] CovariancematrixPerCluster;

	/**
	 * Instantiates a new covariance matrix evaluator.
	 * 
	 * @param measure
	 *            the distancemeasure
	 * @param points
	 *            all datapoints
	 * @param belongsToCluster
	 *            point-cluster-association
	 * @param centroids
	 *            the centroids
	 * @param clusterSize
	 *            the cluster size
	 * @param threads
	 *            number of threads
	 * @param removeRuns
	 *            times to remove outliers and recompute
	 * @param probability
	 *            The outlier probability
	 * @param cov_sampling
	 *            Nbr of instances to use to computer cov (sampling)
	 * @param generator
	 *            Number of points for covariancematrix calculation
	 * @param minimumInstancesForCluster
	 *            Number of points to define a &quote;small cluster&quote;
	 * @param lamda
	 * @param initIteration
	 * @param fastMCDPoints
	 * @param subsetPoints
	 */
	public CMGOSEvaluator(DistanceMeasure measure, double[][] points, int[] belongsToCluster, double[][] centroids, int[] clusterSize, int threads, int removeRuns, double probability, int cov_sampling, RandomGenerator generator, int pointCountSmall, double lamda, int cov, int h, int subsetPoints, int fastMCDPoints, int initIteration) {

		this.measure = measure;
		this.points = points;
		this.belongsToCluster = belongsToCluster;
		this.clusterSize = clusterSize;
		this.centroids = centroids;
		this.numberOfThreads = threads;
		this.removeRuns = removeRuns;
		this.probability = probability;
		this.cov_sampling = cov_sampling;
		this.generator = generator;
		this.minimumInstancesForCluster = pointCountSmall;
		this.regularizedLambda = lamda;
		this.red = cov;
		this.h = h;
		this.subsetPoints = subsetPoints;
		this.fastMCDPoints = fastMCDPoints;
		this.initIteration = initIteration;
	}

	/**
	 * Reassigns instances to other clusters of cluster is too small 
	 * @param removed_cluster
	 * 	Array with removed clusters
	 * @param limit
	 * 	Minumum limit for instances
	 * @return
	 * 	Array with removed clusters 
	 */
	private boolean[] reassignPoints(boolean[] removed_cluster, double limit) {
		int clusterId = 0;
		for (double size : this.clusterSize) {
			// cluster too small according to minimum
			if (size < limit) {
				removed_cluster[clusterId] = true;
				for (int i = 0; i < this.belongsToCluster.length; i++) {
					if (this.belongsToCluster[i] == clusterId) {
						int clusterId2 = -1;
						double minDist = Integer.MAX_VALUE;
						int minId = 0;
						for (double size2 : this.clusterSize) {
							clusterId2++;
							if (clusterId2 == clusterId || size2 <= limit)
								continue;
							double dis = this.measure.calculateDistance(this.points[i], this.centroids[clusterId2]);
							if (dis < minDist) {
								minId = clusterId2;
								minDist = dis;
							}
						}
						this.belongsToCluster[i] = minId;
						this.clusterSize[minId]++;
						this.clusterSize[clusterId]--;
					}
				}
			}
			else {
				removed_cluster[clusterId] = false;
			}
			clusterId++;
		}
		return removed_cluster;
	}

	/**
	 * Main Algorithm
	 * @throws OperatorException 
	 */
	public double[] evaluate() throws OperatorException {
		// remove small clusters
		boolean[] removed_cluster = new boolean[this.centroids.length];
		if (this.minimumInstancesForCluster != -2) {
			double limit = 0.0;
			// use formula (rule of thumb)
			if (minimumInstancesForCluster == -1) {
				limit = ((1 - this.probability) * this.points.length) / (this.clusterSize.length);
			}
			// use user-input
			else {
				limit = minimumInstancesForCluster;
			}
			removed_cluster = this.reassignPoints(removed_cluster, limit);
		}

		int TotalNumberOfPoints = points.length;
		int NumberOfCluster = this.centroids.length;
		int PointDimension = this.points[0].length;

		// remove clusters with less points than dimensions
		removed_cluster = this.reassignPoints(removed_cluster, PointDimension);
		int[][] remove = new int[NumberOfCluster][PointDimension];

		// assign distance limit -1 for error
		double DistanceLimit = -1;
		ChiSquaredDistributionImpl chi = new ChiSquaredDistributionImpl(points[0].length);
		try {
			DistanceLimit = chi.inverseCumulativeProbability(this.probability);
		}
		catch (MathException e) {
			System.out.println(e);
		}

		/* compute anomaly score */
		double[] result = new double[TotalNumberOfPoints];

		int[] workBelongsToCluster = this.belongsToCluster.clone();
		int[] workClusterSize = this.clusterSize.clone();

		double[] DistanceLimitPerCluster = new double[NumberOfCluster];
		Arrays.fill(DistanceLimitPerCluster, DistanceLimit);

		this.CovariancematrixPerCluster = new CovarianceMatrix[NumberOfCluster];
		
		// in case of fastMCD make sure don't remove any outliers and recompute
		// sanity check from user interface
		if (this.red == METHOD_COV_MCD) this.removeRuns = 0;
		
		for (int rem = 0; rem <= this.removeRuns; rem++) {

			// Associate instances to a cluster
			double[][][] ClusterWithPointsAssociation = new double[NumberOfCluster][][];
			int[] nextId = new int[NumberOfCluster];
			for (int ClusterId = 0; ClusterId < NumberOfCluster; ClusterId++) {
				ClusterWithPointsAssociation[ClusterId] = new double[workClusterSize[ClusterId]][PointDimension];
			}

			for (int PointId = 0; PointId < TotalNumberOfPoints; PointId++) {
				int ClusterId = workBelongsToCluster[PointId];
				if (ClusterId < NumberOfCluster) {
					ClusterWithPointsAssociation[ClusterId][nextId[ClusterId]] = this.points[PointId];
					nextId[ClusterId]++;
				}
			}

			// Subtract mean from all
			if (rem == 0) {
				for (int ClusterId = 0; ClusterId < NumberOfCluster; ClusterId++) {
					double[] erw = new double[PointDimension];

					for (int PointId = 0; PointId < ClusterWithPointsAssociation[ClusterId].length; PointId++) {
						for (int PointAttribute = 0; PointAttribute < ClusterWithPointsAssociation[ClusterId][PointId].length; PointAttribute++) {
							erw[PointAttribute] += ClusterWithPointsAssociation[ClusterId][PointId][PointAttribute];
						}
					}
					for (int j1 = 0; j1 < erw.length; j1++) {
						erw[j1] = 1.0 / ClusterWithPointsAssociation[ClusterId].length * erw[j1];
					}

					for (int PointId = 0; PointId < ClusterWithPointsAssociation[ClusterId].length; PointId++) {
						for (int j1 = 0; j1 < ClusterWithPointsAssociation[ClusterId][PointId].length; j1++) {
							ClusterWithPointsAssociation[ClusterId][PointId][j1] -= erw[j1];
						}
					}

				}
			}

			// Calculate covariance for each cluster
			for (int ClusterId = 0; ClusterId < NumberOfCluster; ClusterId++) {
				if (workClusterSize[ClusterId] > 0) {
					double[][] data = null;
					// use all data instances
					if (this.red == METHOD_COV_MCD || this.cov_sampling == -1 || this.cov_sampling > ClusterWithPointsAssociation[ClusterId].length) {
						data = ClusterWithPointsAssociation[ClusterId];
					}
					// sample data
					else {
						data = new double[this.cov_sampling][ClusterWithPointsAssociation[ClusterId][0].length];
						int i = 0;
						for (Integer index : generator.nextIntSetWithRange(0, ClusterWithPointsAssociation[ClusterId].length, this.cov_sampling)) {
							data[i] = ClusterWithPointsAssociation[ClusterId][index];
							i++;
						}
					}
					// in the case of MCD, do it
					if (this.red == METHOD_COV_MCD) {
						// we compute h from the normal probability
						if (this.h == -1)
							this.h = (int) Math.ceil(this.probability * (float)data.length);
						CovariancematrixPerCluster[ClusterId] = fastMDC(data, this.h);
					}
					// Regularization and Reduction
					else {
						if (CovariancematrixPerCluster[ClusterId] == null || rem < this.removeRuns) {
							boolean change = false;
							int count = 0;
							// Reduction Method
							if (this.red == METHOD_COV_REDUCTION) {
								do {
									change = false;
									int ind = -1;
									// look for attribute with only one value
									for (int i = 0; i < data[0].length; i++) {
										change = true;
										ind = i;
										for (int j = 0; j < data.length; j++) {
											if (data[j][ind] != data[0][ind]) {
												change = false;
												ind = -1;
												break;
											}
										}
										if (change) break;
									}
									if (change) {
										// store which attribute to remove in which cluster
										remove[ClusterId][ind + count] = 1;
										count++;
										double[][] dataNew = new double[data.length][data[0].length - 1];
										for (int i = 0; i < data.length; i++) {
											System.arraycopy(data[i], 0, dataNew[i], 0, ind);
											System.arraycopy(data[i], ind + 1, dataNew[i], ind, data[0].length - (ind + 1));
										}
										data = dataNew;
									}
								} while (change);

								// calculate new distancelimit using new number of dimension
								chi = new ChiSquaredDistributionImpl(data[0].length);
								try {
									DistanceLimitPerCluster[ClusterId] = chi.inverseCumulativeProbability(this.probability);
								}
								catch (MathException e) {
									System.out.println(e);
								}

							}
							CovariancematrixPerCluster[ClusterId] = new CovarianceMatrix(data, numberOfThreads);
						}
					}
				}
			}

			// REGULARIZATION
			// S is the summarized covariance matrics (QDA)
			double[][] S = null;
			boolean thereisone = false;
			if (this.red == METHOD_COV_REGULARIZE) {
				int id = 0;
				for (boolean b : removed_cluster) {
					if (!b) {
						thereisone = true;
						break;
					}
					id++;
				}
				if (!thereisone) {
					throw new OperatorException("No cluster left. This is a problem. Try not to remove small clusters or reduce number");
				}
				S = new double[CovariancematrixPerCluster[id].getCovMat().length][CovariancematrixPerCluster[id].getCovMat()[0].length];
				for (int ClusterId = 0; ClusterId < NumberOfCluster; ClusterId++) {
					if (!removed_cluster[ClusterId] && CovariancematrixPerCluster[ClusterId] != null) {
						double[][] d = CovariancematrixPerCluster[ClusterId].getCovMat();
						for (int i = 0; i < d.length; i++) {
							for (int j = 0; j < d[i].length; j++) {
								S[i][j] += d[i][j];
							}
						}
					}
				}
			}

			// reset Point-association
			if (rem == this.removeRuns) {
				workClusterSize = this.clusterSize.clone();
				workBelongsToCluster = this.belongsToCluster.clone();
			}
			for (int ClusterId = 0; ClusterId < NumberOfCluster; ClusterId++) {
				if (workClusterSize[ClusterId] > 0) {
					Matrix mh = new Matrix(CovariancematrixPerCluster[ClusterId].getCovMat());
					if (this.red == METHOD_COV_REDUCTION && mh.det() == 0) {
						CovariancematrixPerCluster[ClusterId].addMinimum();
						mh = new Matrix(CovariancematrixPerCluster[ClusterId].getCovMat());
					}
					else if (this.red == METHOD_COV_REGULARIZE) {
						Matrix mS = new Matrix(S);
						mS = mS.times(this.regularizedLambda / this.points.length);
						mh = mh.times((1.0 - this.regularizedLambda));
						mh = mh.plus(mS);
					}
					// This shouldn't happen ...
					if (mh.det() == 0) {
						CovariancematrixPerCluster[ClusterId].addMinimum();
						mh = new Matrix(CovariancematrixPerCluster[ClusterId].getCovMat());
					}
					
					mh = mh.inverse();

					for (int PointId = 0; PointId < points.length; PointId++) {
						if (workBelongsToCluster[PointId] == ClusterId) {

							int sum = 0;
							for (int i : remove[ClusterId])
								sum += i;

							double[] point = new double[points[PointId].length - sum];

							int count = 0;
							for (int ind = 0; ind < remove[ClusterId].length; ind++) {
								if (remove[ClusterId][ind] == 1)
									count++;
								int newid = ind - count;
								if (newid < 0)
									continue;
								point[newid] = this.points[PointId][newid];
							}

							double mahaDist;
							if (this.red == 0)
								mahaDist = mahalanobisDistance(point, mh);
							else
								mahaDist = mahalanobisDistance(this.points[PointId], mh);
							result[PointId] = mahaDist / DistanceLimit;

							// remove association for minimum covariance
							// determinant
							if (rem != this.removeRuns && mahaDist > DistanceLimitPerCluster[ClusterId]) {
								workBelongsToCluster[PointId] = NumberOfCluster;
								workClusterSize[ClusterId]--;
							}
						}
					}
				}
			}
		}

		return result;
	}

	private boolean hasZeroVariance(double[][] data, int[] indexArray) {
		boolean ret = false;
		for (int j = 0; j < data[0].length; j++) {
			double first = data[indexArray[0]][j];
			ret = true;
			for (int i = 1; i < indexArray.length; i++) {
				if (data[indexArray[i]][j] != first) {
					ret = false;
					break;
				}
			}
			if (ret)
				break;
		}
		return ret;
	}

	private HashMap<Double, LinkedList<CovarianceMatrix>> getInit10(double[][] data, int[] indexArray, int h, int n, int p) {

		class Worker extends Thread {

			private double[][] data;
			private int[] indexArray;
			private int h;
			private int n;
			private int p;
			private int runs;
			@SuppressWarnings("unused")
			private int id;
			private HashMap<Double, LinkedList<CovarianceMatrix>> map = new HashMap<Double, LinkedList<CovarianceMatrix>>();

			public HashMap<Double, LinkedList<CovarianceMatrix>> getMap() {
				return this.map;
			}

			public Worker(double[][] data, int[] indexArray, int h, int n, int p, int runs, int id) {
				this.data = data;
				this.indexArray = indexArray;
				this.h = h;
				this.n = n;
				this.p = p;
				this.runs = runs;
				this.id = id;
			}

			public void run() {
				boolean zero = hasZeroVariance(data, indexArray);

				// repeat (say) 500 times:
				for (int run = 0; run < this.runs; run++) {
					LinkedList<double[]> list = new LinkedList<double[]>();
					boolean[] taken = new boolean[n];
					int count = 0;
					// Draw a random (p + 1)-subset J, and then compute To
					// := ave(J) and So := cov(J).
					while (count < (p + 1)) {
						for (int index : generator.nextIntSetWithRange(0, n, (p + 1))) {
							if (!taken[index]) {
								list.push(data[indexArray[index]]);
								taken[index] = true;
								count++;
							}
						}
					}
					CovarianceMatrix ret = new CovarianceMatrix(list, 1);
					Matrix mat = new Matrix(ret.getCovMat());
					if (zero) {
						ret.addMinimum();
						mat = new Matrix(ret.getCovMat());
					}

					// If det(S_0) = 0, then extend J by adding another
					// random observation, and continue adding observations
					// until det(S_0) > 0.
					while (mat.det() == 0) {
						int index;
						do {
							index = generator.nextInt(n);
						} while (taken[index]);
						taken[index] = true;
						boolean b = true;
						for (boolean t : taken)
							b &= t;
						list.push(data[indexArray[index]]);
						ret = new CovarianceMatrix(list, 1);
						if (b) {
							//all Points are taken
							ret.addMinimum();
						}
						mat = new Matrix(ret.getCovMat());
					}
					
					list = null;
					
					// carry out two C-steps
					for (int rep = 0; rep < 2; rep++) {
						ret = Cstep(ret, data, indexArray, h);
					}

					map = getSorted(map, ret, 10);
				}
			}
		}

		Worker[] wa = new Worker[this.numberOfThreads];
		int runs = (int) (this.initIteration / this.numberOfThreads);

		for (int i = 0; i < this.numberOfThreads; i++) {
			Worker w = new Worker(data, indexArray, h, n, p, runs, i);
			w.start();
			wa[i] = w;
		}
		for (int i = 0; i < this.numberOfThreads; i++) {
			try {
				wa[i].join();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		HashMap<Double, LinkedList<CovarianceMatrix>> map = new HashMap<Double, LinkedList<CovarianceMatrix>>();
		for (int i = 0; i < this.numberOfThreads; i++) {
			for (Double k : wa[i].getMap().keySet()) {
				for (CovarianceMatrix mat : wa[i].getMap().get(k))
					map = getSorted(map, mat, 10);
			}
			wa[i] = null;
		}
		wa = null;
		
		return map;
	}

	private HashMap<Double, LinkedList<CovarianceMatrix>> getSorted(HashMap<Double, LinkedList<CovarianceMatrix>> map, CovarianceMatrix ret, int count) {
		Matrix mh = new Matrix(ret.getCovMat());
		double det = mh.det();

		if (map.containsKey(det)) {
			LinkedList<CovarianceMatrix> temp = map.get(det);
			temp.push(ret);
			map.put(det, temp);
		}
		else {
			LinkedList<CovarianceMatrix> temp = new LinkedList<CovarianceMatrix>();
			temp.push(ret);
			map.put(det, temp);
		}

		if (map.keySet().size() > count) {
			ArrayList<Double> sortedList = new ArrayList<Double>();
			sortedList.addAll(map.keySet());
			Collections.sort(sortedList);
			
			map.remove(sortedList.get(sortedList.size() - 1));
			sortedList = null;
		}
		return map;
	}

	private HashMap<Double, LinkedList<CovarianceMatrix>> fast(double[][] data, int h, int n, int p) {

		class StepWorker extends Thread {
			private int id;
			private HashMap<Integer, HashMap<Double, LinkedList<CovarianceMatrix>>> map2;
			private double[][] data;
			private int[] indexArray;
			private int h_sub;
			private HashMap<Double, LinkedList<CovarianceMatrix>> retMap;
			private int anz;

			public StepWorker(int anz, HashMap<Integer, HashMap<Double, LinkedList<CovarianceMatrix>>> map, int id, double[][] data, int[] indexArray, int h_sub) {
				this.id = id;
				this.map2 = map;
				this.data = data;
				this.indexArray = indexArray;
				this.h_sub = h_sub;
				this.anz = anz;
				this.retMap = new HashMap<Double, LinkedList<CovarianceMatrix>>();
			}

			public HashMap<Double, LinkedList<CovarianceMatrix>> getMap() {
				return this.retMap;
			}

			public void run() {
				for (int id = (this.id * anz); id < ((this.id * anz) + anz); id++) {
					if (map2.containsKey(id)) {
						HashMap<Double, LinkedList<CovarianceMatrix>> map = map2.get(id);
						for (double d : map.keySet()) {
							LinkedList<CovarianceMatrix> l = map.get(d);
							for (CovarianceMatrix c : l) {
								CovarianceMatrix ret = c;
								for (int rep = 0; rep < 2; rep++)
									ret = Cstep(ret, data, indexArray, h_sub);
								retMap = getSorted(retMap, ret, 10);
							}
						}
					}
				}
			}
		}

		// construct up to five disjoint random subsets of size nsub according
		// to Section 3.3 (say, five subsets of size nsub = 300);
		double anz_subset = Math.floor(data.length / this.subsetPoints);
		double anz_points = Math.floor(data.length / anz_subset);
		boolean[] taken = new boolean[data.length];
		int merge_id = 0;

		// keep the 10 best results (Tsub, Ssub);
		HashMap<Integer, HashMap<Double, LinkedList<CovarianceMatrix>>> map2 = new HashMap<Integer, HashMap<Double, LinkedList<CovarianceMatrix>>>();
		for (int i = 0; i < anz_subset; i++) {
			int dim = (int) anz_points;
			int[] indexArray = new int[dim];
			// create sub-dataset
			for (int j = 0; j < dim; j++) {
				int index;
				do {
					index = generator.nextInt(n);
				} while (taken[index]);
				taken[index] = true;
				indexArray[j] = index;
			}
			double h_sub = Math.ceil((dim * (h / (n * 1.0))));
			HashMap<Double, LinkedList<CovarianceMatrix>> map = getInit10(data, indexArray, (int) h_sub, dim, p);

			if (!map2.containsKey(merge_id))
				map2.put(merge_id, map);
			else {
				HashMap<Double, LinkedList<CovarianceMatrix>> hilf = map2.get(merge_id);
				for (double k : map.keySet()) {
					if (!hilf.containsKey(k))
						hilf.put(k, map.get(k));
					else {
						LinkedList<CovarianceMatrix> h1 = hilf.get(k);
						h1.addAll(map.get(k));
						hilf.put(k, h1);
					}
				}
				map2.put(merge_id, hilf);
			}
			if ((i % 5) == 0 && i != 0) {
				merge_id++;
			}
		}

		// pool the subsets, yielding the merged set (say, of size nmerged =
		// 1,500);
		
		anz_subset = Math.floor(data.length / 1500.0);
		if (anz_subset <= 0)
			anz_subset = 1;
		anz_points = Math.floor(data.length / anz_subset);
		taken = new boolean[data.length];
		double h_sub = Math.ceil((anz_points * (h / (n * 1.0))));
		int dim = (int) anz_points;

		int[] indexArray = new int[dim];
		for (int j = 0; j < dim; j++) {
			int index;
			do {
				index = generator.nextInt(n);
			} while (taken[index]);
			taken[index] = true;
			indexArray[j] = index;
		}

		int anz = map2.keySet().size() % this.numberOfThreads;
		StepWorker[] wa = new StepWorker[this.numberOfThreads];
		for (int i = 0; i < this.numberOfThreads; i++) {
			wa[i] = new StepWorker(anz, map2, i, data, indexArray, (int) h_sub);
			wa[i].start();
		}
		for (int i = 0; i < this.numberOfThreads; i++) {
			try {
				wa[i].join();
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		map2 = null;
		HashMap<Double, LinkedList<CovarianceMatrix>> map3 = new HashMap<Double, LinkedList<CovarianceMatrix>>();
		for (int i = 0; i < this.numberOfThreads; i++) {
			for (Double k : wa[i].getMap().keySet()) {
				for (CovarianceMatrix mat : wa[i].getMap().get(k))
					map3 = getSorted(map3, mat, 10);
			}
		}

		// in the full dataset, repeat for the m_full best results:
		HashMap<Double, LinkedList<CovarianceMatrix>> map4 = new HashMap<Double, LinkedList<CovarianceMatrix>>();

		indexArray = new int[data.length];
		for (int i = 0; i < data.length; i++) {
			indexArray[i] = i;
		}
		for (double d : map3.keySet()) {
			LinkedList<CovarianceMatrix> l = map3.get(d);
			for (CovarianceMatrix c : l) {
				map4 = getSorted(map4, convergence(data, indexArray, c, h), 10);
			}
		}
		map3 = null;
		return map4;
	}
	
	private CovarianceMatrix convergence(double[][] data, int[] indexArray, CovarianceMatrix pre, int h) {

		Matrix pre_mat;
		double pre_det;
		Matrix post_mat;
		double post_det;
		boolean loop = true;
		pre_mat = new Matrix(pre.getCovMat());
		pre_det = pre_mat.det();
		do {
			CovarianceMatrix post = Cstep(pre, data, indexArray, h);
			post_mat = new Matrix(post.getCovMat());
			post_det = post_mat.det();

			// Repeating C-steps yields an iteration
			// process. If det(S_2) = 0 or det(S_2) =
			// det(S_1), we stop;
			if (post_det >= pre_det || post_det == 0) {
				loop = false;
			}
			else {
				pre = post;
				pre_mat = post_mat;
				pre_det = post_det;
			}
		} while (loop);

		return pre;
	}

	private CovarianceMatrix fastMDC(double[][] data, int h) {
		CovarianceMatrix ret = null;
		int n = data.length;
		// If n is small (say, n <= 600)
		int small = this.fastMCDPoints;
		int p = data[0].length;
		int low = (n + p + 1) / 2;
		// The default h is [(n + p + 1)/2], but the user may choose any integer
		// h with [(n + p + 1)/2] <= h <= n
		if (h < low || h > n) {
			h = low;
		}

		// If h = n, then the MCn location estimate T is the average of the
		// whole dataset, and the MCn scatter estimate S is its covariance
		// matrix. Report these and stop.
		if (h == n) {
			ret = new CovarianceMatrix(data, this.numberOfThreads);
		}
		else {
			// If p = 1 (univariate data), compute the MCn esti-mate (T, S) by
			// the exact algorithm of Rousseeuw and Leroy (1987, pp. 171-172) in
			// O(n log n) time; then stop.
			// if (p == 1) {
			//	ret = new CovarianceMatrix(data, 1);
			//}
			//else
			{
				if (n <= small) {
					int[] indexArray = new int[data.length];
					for (int i = 0; i < data.length; i++) {
						indexArray[i] = i;
					}
					HashMap<Double, LinkedList<CovarianceMatrix>> map = getInit10(data, indexArray, h, n, p);

					HashMap<Double, LinkedList<CovarianceMatrix>> map2 = new HashMap<Double, LinkedList<CovarianceMatrix>>();
					// for the 10 results with lowest det(S_3)
					for (LinkedList<CovarianceMatrix> covlist : map.values()) {
						for (CovarianceMatrix covmat : covlist) {
							CovarianceMatrix pre = convergence(data, indexArray, covmat, h);
							Matrix pre_mat = new Matrix(pre.getCovMat());
							double pre_det = pre_mat.det();
							if (map2.containsKey(pre_det)) {
								LinkedList<CovarianceMatrix> hilf = map2.get(pre_det);
								hilf.push(pre);
								map2.put(pre_det, hilf);
							}
							else {
								LinkedList<CovarianceMatrix> hilf = new LinkedList<CovarianceMatrix>();
								hilf.push(pre);
								map2.put(pre_det, hilf);
							}
						}
					}
					// report the solution (T, S) with lowest det(S)
					ArrayList<Double> sortedList = new ArrayList<Double>();
					sortedList.addAll(map2.keySet());
					Collections.sort(sortedList);
					ret = map2.get(sortedList.get(0)).getFirst();
				}
				else {
					HashMap<Double, LinkedList<CovarianceMatrix>> map = fast(data, h, n, p);
					ArrayList<Double> sortedList = new ArrayList<Double>();
					sortedList.addAll(map.keySet());
					Collections.sort(sortedList);
					ret = map.get(sortedList.get(0)).getFirst();
				}
			}
		}

		return ret;
	}

	public CovarianceMatrix Cstep(CovarianceMatrix covMat, double[][] data, int[] indexArray, int h) {
		HashMap<Double, LinkedList<Integer>> map = new HashMap<Double, LinkedList<Integer>>();
		double[][] newMat = new double[h][];
		Matrix mh = new Matrix(covMat.getCovMat());

		if (mh.det() == 0) {
			covMat.addMinimum();
			mh = new Matrix(covMat.getCovMat());
		}

		mh = mh.inverse();

		// Compute the distances d_old(i) for i = 1, ... , n.
		for (int index = 0; index < indexArray.length; index++) {
			double d = this.mahalanobisDistance(data[indexArray[index]], mh);
			if (map.containsKey(d)) {
				LinkedList<Integer> hilf = map.get(d);
				hilf.push(index);
				map.put(d, hilf);
			}
			else {
				LinkedList<Integer> hilf = new LinkedList<Integer>();
				hilf.push(index);
				map.put(d, hilf);
			}
		}
		// Sort these distances
		ArrayList<Double> sortedList = new ArrayList<Double>();
		sortedList.addAll(map.keySet());
		Collections.sort(sortedList);

		// take the h smallest
		int count = 0;
		Iterator<Double> iter = sortedList.iterator();
		while (iter.hasNext()) {
			Double key = iter.next();
			for (Integer i : map.get(key)) {
				newMat[count] = data[indexArray[i]];
				count++;
				if (count >= h)
					break;
			}
			if (count >= h)
				break;
		}

		return new CovarianceMatrix(newMat, this.numberOfThreads);
	}

	/**
	 * M dist2.
	 * 
	 * @param point
	 *            the mu
	 * @param centroids2
	 * @param sig_inv
	 *            the sig_inv
	 * @return the double
	 */
	private double mahalanobisDistance(double[] point, Matrix sig_inv) {
		Matrix deltaxy = new Matrix(point, point.length);
		return deltaxy.transpose().times(sig_inv).times(deltaxy).get(0, 0);
	}
}
