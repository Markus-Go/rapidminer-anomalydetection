/*
 *  RapidMiner Anomaly Detection Extension
 *
 *  Copyright (C) 2009-2011 by Deutsches Forschungszentrum fuer
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
 * Author: Mennatallah Amer (mennatallah.amer@student.guc.edu.eg)
 * Responsible: Markus Goldstein (Markus.Goldstein@dfki.de)
 *
 * URL: http://madm.dfki.de/rapidminer/anomalydetection
 */
package de.dfki.madm.anomalydetection.evaluator.nearest_neighbor_based;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import com.rapidminer.operator.Operator;
import com.rapidminer.tools.math.similarity.DistanceMeasure;

import de.dfki.madm.anomalydetection.evaluator.Evaluator;

/**
 * This class implements the KNN algorithm.
 * 
 * @author Mennatallah Amer
 * 
 */

public class KNNEvaluator implements Evaluator {
	/**
	 * Threads for parallel evaluation of KNN without the need for
	 * synchronization.
	 * 
	 */
	private class KNNThread implements Runnable {

		private int start, end;
		private CyclicBarrier barrier;
		private Operator logger;

		public KNNThread(int start, int end, CyclicBarrier barrier, Operator logger) {

			this.start = start;
			this.end = end;
			this.barrier = barrier;
			this.logger = logger;
		}

		@Override
		public void run() {
			if (logger != null)
				this.logger.logNote("Thread " + start + " " + end + " started!");
			for (int i = start; i < end; i++) {
				for (int j = 0; j < n; j++) {
					if (i == j)
						continue;
					if(newCollection){
					double currentDistance = measure.calculateDistance(
							knnCollection.getPoints()[i], knnCollection
									.getPoints()[j]);
					knnCollection.updateNearestNeighbors(i, j, currentDistance);
					}
				}
			}
			if (logger != null)
				logger.logNote("Thread " + start + " " + end + " finished!");
			try {
				barrier.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			}

		}

	}

	/**
	 * 
	 * This class implements the threads with the need for synchronization.
	 * 
	 */
	private class KNNThreadSynchronized implements Runnable {

		private int start, end;
		private CyclicBarrier barrier;
		private Object[] locks;

		public KNNThreadSynchronized(int start, int end, CyclicBarrier barrier,
				Object[] locks) {
			this.start = start;
			this.end = end;
			this.barrier = barrier;
			this.locks = locks;
		}

		@Override
		public void run() {
			if (logger != null)
				logger.logNote("Thread " + start + " " + end + " started!");
			for (int i = start; i <= end; i++) {
				for (int j = 0; j <= i; j++) {
					if(newCollection){
					double currentDistance = measure.calculateDistance(
							knnCollection.getPoints()[i], knnCollection
									.getPoints()[j]);
					synchronized (locks[i]) {
						knnCollection.updateNearestNeighbors(i, j,
								currentDistance);
					}
					synchronized (locks[j]) {
						knnCollection.updateNearestNeighbors(j, i,
								currentDistance);
					}
					}
				}

			}
			if (logger != null)
				logger.logNote("Thread " + start + " " + end + " finished!");
			try {
				barrier.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (BrokenBarrierException e) {
				e.printStackTrace();
			}

		}

	}

	private KNNCollection knnCollection;
	private boolean kth;
	private DistanceMeasure measure;
	private int n, k;
	private double[] res;
	private Operator logger;
	protected boolean parallel;
	protected int numberOfThreads;
	boolean newCollection = false;
	public KNNEvaluator(KNNCollection knnCollection, boolean kth,
			DistanceMeasure measure, boolean parallel, int numberOfThreads, Operator logger) {
		this.knnCollection = knnCollection;
		this.measure = measure;
		this.kth = kth;
		n = knnCollection.getN();
		k = knnCollection.getK();
		this.parallel = parallel;
		this.numberOfThreads = numberOfThreads;
		this.logger = logger;
		res = new double[n];
	}
	public KNNEvaluator(KNNCollection knnCollection, boolean kth,
			DistanceMeasure measure, boolean parallel, int numberOfThreads, Operator logger,int n, int k,boolean newCollection) {
		this.knnCollection = knnCollection;
		this.measure = measure;
		this.kth = kth;
		this.n = knnCollection.getN();
		this.k = knnCollection.getK();
		this.parallel = parallel;
		this.numberOfThreads = numberOfThreads;
		this.logger = logger;
		res = new double[n];
		this.newCollection = newCollection;
	}

	/**
	 * To start the evaluation process.
	 * 
	 * @param parallel
	 *            Specifies whether the evaluation should be done in parallel.
	 * @param numberOfThreads
	 *            number of threads that should implement the process
	 */
	public double[] evaluate() {
		long start = System.currentTimeMillis();
		if (parallel)
			KNNParallel();
		else
			KNNSeq();
		if (logger != null)
			logger.logNote("Time " + (System.currentTimeMillis() - start));
		return res;
	}

	public KNNCollection getKnnCollection() {
		return knnCollection;
	}

	public DistanceMeasure getMeasure() {
		return measure;
	}

	public int getN() {
		return n;
	}

	/**
	 * The method that initializes and starts the threads for parallel
	 * evaluation.
	 * 
	 * @param numberOfThreads
	 */
	private void KNNParallel() {
		ThreadGroup threadGroup = new ThreadGroup("Knn Thread Group");

		// barrier so that when all the threads are done the main thread
		// continues.
		CyclicBarrier barrier = new CyclicBarrier(numberOfThreads + 1,new Runnable() {
		public void run() { 
		    for(int i = 0; i<n;i++){
			    setAnomalyScore(i, knnCollection.getNeighBorDistanceSoFar()[i],
						    knnCollection.getNeighBorIndiciesSoFar()[i],
						    knnCollection.getNumberOfNeighborsSoFar()[i]);
		    }
		  }
		});

		if (knnCollection.getPoints()[0].length < 32) {
			// number of elements that each thread should handle
			int elementsPerThread = n / numberOfThreads;

			int start = 0;

			// initializing the threads
			for (int i = 1; i < numberOfThreads; i++) {

				int end = start + elementsPerThread;
				new Thread(threadGroup, new KNNThread(start, end, barrier, logger))
						.start();
				start = end;

			}

			new Thread(threadGroup, new KNNThread(start, n, barrier, logger)).start();
		} else {
			// number of operations that each thread should handle
			long numberOfoperations = (1L * n * (n - 1))
					/ (2 * numberOfThreads);

			Object[] locks = new Object[n];
			for (int i = 0; i < n; i++) {
				locks[i] = new Object();
			}

			int start = 0;
			int numberOfOperationsSofar = 0;
			// int threadsSofar = 0;
			// initializing the threads
			for (int i = 0; i < n; i++) {
				numberOfOperationsSofar += i;
				if (numberOfOperationsSofar < numberOfoperations)
					continue;
				numberOfOperationsSofar = 0;
				// threadsSofar++;
				new Thread(threadGroup, new KNNThreadSynchronized(start, i,
						barrier, locks)).start();
				start = i + 1;

			}
			if (start != n)
				new Thread(threadGroup, new KNNThreadSynchronized(start, n - 1,
						barrier, locks)).start();
		}
		// The main thread is waiting for the threads to finish
		try {
			barrier.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (BrokenBarrierException e) {
			e.printStackTrace();
		}

	}

	private void KNNSeq() {
		for (int i = 0; i < n; i++) {
			for (int j = i + 1; j <n; j++) {
				if(newCollection) {
					double currentDistance = measure.calculateDistance(
							knnCollection.getPoints()[i],
							knnCollection.getPoints()[j]);
					knnCollection.updateNearestNeighbors(i, j, currentDistance);
					knnCollection.updateNearestNeighbors(j, i, currentDistance);
				}
			}
			setAnomalyScore(i, knnCollection.getNeighBorDistanceSoFar()[i],
					knnCollection.getNeighBorIndiciesSoFar()[i], knnCollection
							.getNumberOfNeighborsSoFar()[i]);

		}

	}

	/**
	 * Method called in case the neighborhood size k is reduced by step in order
	 * to recalculate the anomaly score.
	 * 
	 * @param step
	 *            The neighborhood size is reduced by step.
	 * @return
	 */
	public double[] reEvaluate(int step) {
		k -= step;
		knnCollection.shrink(step);
		res = new double[n];
		for (int i = 0; i < n; i++) {
			double sum = 0;
			int elementsSoFar = getKnnCollection().getWeight()[i] - 1;
			int j = 0;
			for (; j < getKnnCollection().getNumberOfNeighborsSoFar()[i]
					&& elementsSoFar < k; j++) {
				int noOfElements = Math.min(
						knnCollection.getWeight()[knnCollection
								.getNeighBorIndiciesSoFar()[i][j]], k
								- elementsSoFar);
				sum += noOfElements
						* knnCollection.getNeighBorDistanceSoFar()[i][j];
				elementsSoFar += noOfElements;
			}

			if (kth)
				// If parameter k only is set then the anomaly score is set to
				// the distance of the kth neighbor.
				if (j == 0)
					res[i] = 0;
				else
					res[i] = knnCollection.getNeighBorDistanceSoFar()[i][j - 1];
			else
				// else it is set to the average
				res[i] = sum / elementsSoFar;
		}
		return res;
	}

	/**
	 * Sets the anomaly score of the example.
	 * 
	 * @param example
	 * @param neighBorDistanceSoFar
	 * @param neighBorIndiciesSoFar
	 * @param numberOfNeighbors
	 */
	protected void setAnomalyScore(int i, double[] neighBorDistanceSoFar,
			int[] neighBorIndiciesSoFar, int numberOfNeighbors) {
		// if there are no neighbors
		if (numberOfNeighbors == 0) {
			res[i] = 0.0; // Double.NaN;
		} else {
			double sum = 0;
			int elementsSoFar = getKnnCollection().getWeight()[i] - 1;
			int j = 0;
			for (; j < numberOfNeighbors && elementsSoFar < k; j++) {
				int noOfElements = Math.min(
						knnCollection.getWeight()[neighBorIndiciesSoFar[j]], k
								- elementsSoFar);
				sum += noOfElements * neighBorDistanceSoFar[j];
				elementsSoFar += noOfElements;
			}

			if (kth)
				// If parameter k only is set then the anomaly score is set to
				// the distance of the kth neighbor.
				if (j == 0)
					res[i] = 0;
				else
					res[i] = neighBorDistanceSoFar[j - 1];
			else
				// else it is set to the average
				res[i] = sum / elementsSoFar;

		}
	}

}
