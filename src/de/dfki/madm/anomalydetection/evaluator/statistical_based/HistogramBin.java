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
package de.dfki.madm.anomalydetection.evaluator.statistical_based;

public class HistogramBin implements Comparable<HistogramBin> {
	private double range_from;
	private double range_to;
	private int quantity;
	private int bin_num;
	private double score; 
	private double normalize;
	// The colors of the bins are saved as integers from 510 (green) to 0 (red)
	private double color;
	public HistogramBin(double from, double to,int quantity, int bin_num) {
		this.range_from = from;
		this.range_to = to;
		this.quantity = quantity;
		this.bin_num = bin_num;
	}
	
	public void set_quantity(int quantity) {
		this.quantity = quantity;	
	}
	
	public int get_quantity(){
		return this.quantity;
	}
	
	public void set_normalize(double normalize) {
		this.normalize = normalize;	
	}
	
	public double get_normalize() {
		return this.normalize;
	}
	public double get_color() {
		return this.color;
	}
	public void set_color(int color) {
		this.color = color;
	}
	public double get_height() {
		double width = (range_to - range_from);
		double height = quantity / width;
		return height;
	}
	
	public void set_bin_num(int bin_num) {
		this.bin_num = bin_num;
	}
	
	public int get_bin_num(){
		return this.bin_num;
	}
	
	public void add_quantity(int anz) {
		this.quantity  = this.quantity + anz;
	}
	
	public double get_range_from() {
		return this.range_from;
	}
	
	public double get_range_to() {
		return this.range_to;
	}
	
	public void set_range_from(double value) {
		this.range_from = value;
	}
	
	public void set_range_to(double value) {
		this.range_to = value;
	}
	
	public double get_score() {
		return this.score;
	}
	
	public void set_score(double score) {
		this.score = score;
	}
	
	/** Calculate the score (bin height/width).
	 *  A normalized bin width is used.
	 * @param max
	 */
	public void calc_score(double max){
		if(max == 0) {
			max = 1.0;
		}
		this.score = (quantity) / ((range_to - range_from) * normalize / Math.abs(max));
	}
	
	/** Normalize the score.
	 *  The "most normal bin" is set to 1. Every other bin is set relativ to that bin.
	 *  After that all scores get inverted.
	 *  Additionally the colors of the cells (when using the outlier data view) are 
	 *  calculated. We differentiate between 510 colors.
	 *  The most normal bins are green. All other bins are set relative to those bins and potentially
	 *  can become everything between green - yellow - red. 
	 * @param max_score
	 */
	public void normalize_score(double normal, double max_score, boolean log_scale) {
		this.score = this.score * normal / max_score;
		color = score * 510;
		this.score = 1.0 / this.score;
		if(log_scale) {
			this.score = Math.log10(this.score);
		}
	}
	
	/** 
	 * Debug output only.
	 */
	public void print() {
		System.out.println("Range from: " +range_from + "; Range to " + range_to + "; Quantity "+ quantity + ";score = "+ this.get_height() +"; Area = " + (range_to -range_from)*this.get_height());
	}

	@Override
	public int compareTo(HistogramBin sort) {
			if(this.score < sort.get_score()){
				return -1;
			}
			if(this.score > sort.get_score()){
				return 1;
			}
			return 0;
	}
}
