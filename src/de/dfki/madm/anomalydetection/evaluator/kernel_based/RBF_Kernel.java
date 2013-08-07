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

package de.dfki.madm.anomalydetection.evaluator.kernel_based;


import anomalydetection_libsvm.svm_node;
/**
 *  Class that contains methods that are related the RBF-Kernel
 * 
 * @author Mennatallah Amer
 *
 */

public class RBF_Kernel {
	private static boolean debug =false;
	static double eps = Math.pow(10,-12);
	
	public static  double estimateInitialAlpha(svm_node[][] values){
		int n = values.length;
		int m = values[0].length;
		double [] mean = new double[m];
		for(int i=0; i< n ; i++) {
			for(int j=0; j< m ; j++) {
				mean[j]+=values[i][j].value;
			}
		}
		for(int j=0; j< m ; j++) {
			mean[j]= mean[j]/n;
		}
		double alpha =0;
		double temp = 0;
		for(int i=0; i< n ; i++) {
			for(int j=0; j< m ; j++) {
				temp = mean[j]- values[i][j].value;
				alpha+= temp*temp;
			}
		}
		alpha= Math.sqrt(alpha/n);
		return alpha;
	}
	
	/**
	 *  Computes the kernel function e^(-|x-y|^2 / alpha^2)
	 * @param x
	 * @param y
	 * @param alpha
	 * @return a double array 'k' of size two: 
	 * 		k[0] is the value of the kernel function, 
	 * 		k[1] is the differential of the kernel function relative to alpha 
	 */
	private static double[] k_function(svm_node[] x, svm_node[] y, double alpha) {
		double sum = 0;
		int xlen = x.length;
		int ylen = y.length;
		int i = 0;
		int j = 0;
		while (i < xlen && j < ylen) {
			if (x[i].index == y[j].index) {
				double d = x[i++].value - y[j++].value;
				sum += d * d;
			} else if (x[i].index > y[j].index) {
				sum += y[j].value * y[j].value;
				++j;
			} else {
				sum += x[i].value * x[i].value;
				++i;
			}
		}

		while (i < xlen) {
			sum += x[i].value * x[i].value;
			++i;
		}

		while (j < ylen) {
			sum += y[j].value * y[j].value;
			++j;
		}
 
		double k_value = Math.exp(-sum/(alpha*alpha));
		return new double[]{k_value, sum*k_value/(alpha*alpha*alpha)};
	}
	
	public static  double learnGamma(svm_node[][] values){
		return learnGamma(values, estimateInitialAlpha(values));
	}
	
	/**
	 * Perform gradient ascent to maximize J= s^2 / (k_avg +eps)  
	 * where s^2= sum_i_0_{l-1}(sum_j_{i+1}_{l-1}((k(i,j)-k_avg)^2))/(number-1)
	 * k_avg = sum_i_0_{l-1}(sum_j_{i+1}_{l-1}(k(i,j))/ number
	 * dJ/d_alpha = sum_i_0_{l-1}(sum_j_{i+1}_{l-1}(
	 * 						2 * (k(i,j) - k_avg) * (k(i,j)' - k_avg') * (k_avg + eps)
	 * 					-
	 * 						k_avg' * (k(i,j) - k_avg) ^ 2
	 * 				)/
	 * 					 (number-1) * (k_avg + eps) ^ 2 
	 * @param values input values 
	 * @param eps 
	 * @return
	 */
	public static  double learnGamma(svm_node[][] values,  double initialAlpha){
		int l= values.length; // size of dataset 
		int number= l*(l-1)/2; // number of non-diagonal kernel elements
		double alpha = initialAlpha; // standard deviation of gaussian 
		double learning_rate= initialAlpha*initialAlpha; // learning rate of gradient ascent 
		double eps_conv = Math.pow(10,-3); // to test for convergence 
		double eps = Math.pow(10,-12);
		double lastValue = 0;
		for(int f=0; f<100 ; f++){
			double [] k;
			double k_avg = 0; // Average of non-diagonal entries in 
			double k_d_avg = 0; // differential of k_avg relative to alpha
			for(int i=0; i< l ; i++) {
				for(int j= i+1; j< l ; j++) {
					k = RBF_Kernel.k_function(values[i], values[j], alpha);
					k_avg += k[0];
					k_d_avg += k[1];
				}
			}
			
			k_avg /= number;
			k_d_avg /= number;
		
			double diff = 0.0; // gradient of maximization objective
			double s_2=0.0; // variance of non-diagonal 
			for(int i=0; i < l; i++) {
				for(int j = i+1; j< l ; j++) {
					k = RBF_Kernel.k_function(values[i], values[j], alpha);
					s_2+=(k[0]-k_avg)*(k[0]-k_avg);
					diff+=2*(k[0]-k_avg)*(k[1]-k_d_avg)*(k_avg+eps)-(k[0]-k_avg)*(k[0]-k_avg)* k_d_avg;
				}
			}
			diff/= (number-1)*(k_avg+ eps)*(k_avg+eps);
			s_2/= (number-1);
			
			
			double temp = learning_rate* diff;
			if(f==0){
				while(alpha+ temp <0)
				{
					learning_rate/=3;
					temp/=3;
				}
			}
			else {
				// reduce the learning rate because it is too large.
				while(temp* lastValue < 0){
					learning_rate/=3;
					temp = learning_rate * diff;
				}
				while(alpha+ temp <0)
				{
					learning_rate/=3;
					temp/=3;
				}
			}
			
			lastValue = temp; 
			alpha += temp;
			
			if(debug) {
				System.out.println("maximized value at iteration "+ f+" "+ s_2/(k_avg+eps));
				System.out.println("After iteration "+f+ " alpha = "+ alpha+ " gamma ="+ (1.0/(alpha*alpha)));
			}
			
			if(Math.abs(temp) < eps_conv){
				break;
			}
		
		}
		if(debug)
			System.out.println("Returned Gamma "+ (1.0/(alpha*alpha)));
		return 1.0/(alpha*alpha);
	}
	
	public static double computeOptimizationObjective(svm_node[][] values, double eps, double gamma){
		double alpha = Math.sqrt(1.0/gamma);
		double k_avg=0.0;
		double s2=0.0;
		int l = values.length;
		int number = l*(l-1)/2;
		double [] k;
		for(int i=0; i< l ; i++) {
			for(int j=i+1; j < l; j++) {
				k = k_function(values[i], values[j], alpha);
				k_avg+=k[0];
			}
		}
		k_avg/=number;
		for(int i=0; i < l; i++) {
			for(int j = i + 1; j <l; j++) {
				k= k_function(values[i], values[j], alpha);
				s2+=(k[0]-k_avg)*(k[0]-k_avg);
			}
		}
		s2/=(number-1);
		return s2/(k_avg+ eps);
	}
	
	

}
