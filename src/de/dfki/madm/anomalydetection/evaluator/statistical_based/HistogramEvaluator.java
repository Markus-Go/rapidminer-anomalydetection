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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.Attributes;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.tools.Ontology;
import com.rapidminer.operator.Operator;

/**
 * The class that has the actual implementation of the HBOS.
 * 
 * @author Johann Gebhardt, Markus Goldstein
 * 
 */
public class HistogramEvaluator {
	private Operator logger;
	public HistogramEvaluator(Operator logger) {
		this.logger = logger;
	}
	static int number_of_features; 
	public HashMap<Integer,int[]> colors;
	private ArrayList<HistogramBin>[] histogram;
	@SuppressWarnings("unchecked")
	public ExampleSet evaluate (ExampleSet exampleSet, boolean log_scale, boolean ranked, HashMap<String,Integer> bin_info_help, HashMap<String,String> mode_help,boolean outlier_color) {
	
		if(ranked) {
			log_scale = false;
		}
		
		// maximum score is set to this value (1 is recommended)
		// if too big, final scores can be too large by multiplication
		double normal = 1.0;
		
		// Get number of atttributes
		Attributes attributes = exampleSet.getAttributes();
		
		number_of_features = bin_info_help.size();
		
		// match properties to the attributes 
		int[] bin_info = new int[number_of_features];
		String[] mode = new String[number_of_features];
		int count_attributes = 0;
		boolean[] nominal = new boolean[number_of_features];
		for(String att : bin_info_help.keySet()) {
			Attribute a = attributes.get(att);
			if (a == null) {
				this.logger.logError("Attribute name not found. Please check histogram properties!");
				return null;
			}
			bin_info[count_attributes] = (int) bin_info_help.get(att);
			mode[count_attributes] = mode_help.get(att);
			nominal[count_attributes] = a.isNominal();
			count_attributes++;
		}
		
		// calculate standard bin size if needed
		int items = exampleSet.size();
		for(int i = 0; i < bin_info.length;i++){
			if(bin_info[i] == -1.0) {
				bin_info[i] = (int) Math.round(Math.sqrt(items));
			}
		}
		// Initialize map to store colors if needed
		// The columns are specified by the table index of their attribute
		if(outlier_color) {
			colors = new HashMap<Integer,int[]>();
			for(Attribute att : attributes){
				colors.put(att.getTableIndex(),new int[items]);
			}
		}
		
		// initialize histogram, one histogram for every dimension
		// list of bins for every histogram
		histogram = new ArrayList[number_of_features];
		for(int i = 0; i < number_of_features; i++) {
			histogram[i] = new ArrayList<HistogramBin>();
		}
		
		// read data from example set, save maximum value for every row (needed to normalize bin width)
		double[][] data = new double[items][number_of_features];
		int line = 0;
		int row = 0;
		
		double[] maximum_value = new double[number_of_features];
		for (Example example : exampleSet) {
			row = 0;
			for (String att : bin_info_help.keySet()) { 
				Attribute a = attributes.get(att);
				data[line][row] = example.getValue(a);
				if(line == 0) {
					maximum_value[row] = example.getValue(a);
				}
				if(example.getValue(a)>maximum_value[row]){
					maximum_value[row] = example.getValue(a);
				}
				row++;
			}
			line++;
		}
		// sort data
		data = sort_2dim_array(data, items, number_of_features);
		
		// create histograms
		for (int i = 0; i < number_of_features; i++) {
			int last = 0;
			double bin_start = data[0][i];
			if(mode[i].equals("dynamic binwidth")){
				// For nominal values every value gets its own bin. Rapidminer handels nominal values as intergers => binwidth 1
				if (nominal[i]) {
					while(last<data.length-1){
						last = createDynamicHistogram(histogram,data,last,1,i,true);
					}
				}
				else {
					int length = data.length;
					int binwidth = bin_info[i];
					while(last<data.length-1){
						int values_per_bin = (int) Math.floor(data.length/bin_info[i]);
						last = createDynamicHistogram(histogram,data,last,values_per_bin,i,false);
						if(binwidth > 1) {
						length = length - histogram[i].get(histogram[i].size()-1).get_quantity();
						binwidth = binwidth -1;
						values_per_bin = (int) Math.floor(length/binwidth);
						}
					}
				}
			}
			else {
				int count_bins = 0;
				double binwidth = (data[items-1][i]- data[0][i]) / bin_info[i];
				if(nominal[i] || binwidth == 0) {
					binwidth = 1.0;
				}
				while(last<data.length) {
					if(count_bins == bin_info[i]-1) {
						last = createStaticHistogram(histogram,data,last,binwidth,i,bin_start,true);
					}
					else {
						last = createStaticHistogram(histogram,data,last,binwidth,i,bin_start,false);
					}
					bin_start = bin_start+binwidth;
					count_bins++;
				}
			}
		}
		
		/** calculate score using normalized bin width
		 *  bin width is normalized to the number of datapoints
		 *  save maximum score for every data dimension (needed to normalize score)
		 */
		double[] max_score = new double[number_of_features];
		for (int i = 0; i < histogram.length; i++) {
			ArrayList<HistogramBin> tmp = histogram[i];
			tmp.get(0).set_normalize(items);
			tmp.get(0).calc_score(maximum_value[i]);
			max_score[i] = tmp.get(0).get_score(); 
			for(int x = 1; x < tmp.size(); x++){
				tmp.get(x).set_normalize(items);
				tmp.get(x).calc_score(maximum_value[i]);
				if(tmp.get(x).get_score() > max_score[i]){
					max_score[i] = tmp.get(x).get_score();
				}
			}
		}	
		
		/** Normalize score
		 *  to the value specified in variable normal
		 *  calculate logarithm if loc_scale = true
		 */
		for (int i = 0; i < histogram.length;i++) {
			ArrayList<HistogramBin> tmp = histogram[i];
			for(int x = 0; x < tmp.size();x++){
				tmp.get(x).normalize_score(normal,max_score[i],log_scale);
			}
		}
	    
		
		/** Ranked modus. Sort histogram by score.
		 *  Rank bins => new score
		 *  also the color of the bins is set linearly 
		 *  from green (rank 1) to red (max rank)
		 */
		if (ranked) {
			for (int i = 0; i < histogram.length;i++) {
				ArrayList<HistogramBin> tmp = histogram[i];
				HistogramBin[] test = new HistogramBin[tmp.size()];
						tmp.toArray(test);
				java.util.Arrays.sort(test);
				int count = 1;
				for(int x = 0; x < tmp.size();x++){
					test[x].set_score(count);
					test[x].set_color(102-(int)(x*102/(tmp.size()-1)));
					count++;
				}
				tmp = new ArrayList<HistogramBin>(Arrays.asList(test));
			}	
		}
		
			
		  // print some information about the histogram, only used for debugging
		  /*double[] test = new double[number_of_features];
		  count_attributes = 0;
		  for (String attr : bin_info_help.keySet()) {
		  		ArrayList<HistogramBin> tmp = histogram[count_attributes];
		  		System.out.println("Attribute " + attr + " " + count_attributes);
		  		for(int x = 0; x < tmp.size();x++) {
		  			System.out.println("Range from: " +tmp.get(x).get_range_from() + "; Range to " + tmp.get(x).get_range_to() + "; Quantity "+ tmp.get(x).get_quantity() + ";score = "+ tmp.get(x).get_score()+"; Color = "+tmp.get(x).get_color() +"; Area = " + ((tmp.get(x).get_range_to() -tmp.get(x).get_range_from()))*tmp.get(x).get_height() );
		  			test[count_attributes] = test[count_attributes] + ((tmp.get(x).get_range_to() -tmp.get(x).get_range_from()))*tmp.get(x).get_height();
		  		}
		  		count_attributes++;
		  	}
		 	for(int i = 0; i < number_of_features;i++){
		 		System.out.println(test[i]);
		 	}
		*/
		double value = 0.0;
		Attribute score = AttributeFactory.createAttribute("score",Ontology.REAL);
		exampleSet.getExampleTable().addAttribute(score);
        exampleSet.getAttributes().setOutlier(score);
        int count_examples = 0;
        for (Example example : exampleSet) {
			value = 1.0;
			if(log_scale) {
				value = 0.0;
			}
			if(ranked) {
				value = 0.0;
			}
			row = 0;
			for (String att : bin_info_help.keySet()) {
				Attribute a = attributes.get(att);
				
				// Get score and color
				double[] tmp = get_score(histogram[row],example.getValue(a),row);
				if(log_scale) {
					value =  value +tmp[0];
					if(outlier_color) {
						colors.get(a.getTableIndex())[count_examples] = (int) tmp[1];
					}
					row++;
				}
				else if(ranked) {
					value =  value +tmp[0];
					if(outlier_color){
						colors.get(a.getTableIndex())[count_examples] = (int) tmp[1];
					}
					row++;
				}
				else {
					value =  value * tmp[0];
					if(outlier_color) {
						colors.get(a.getTableIndex())[count_examples] = (int) tmp[1];
					}
					row++;
				}
			}
			count_examples++;
			example.setValue(score,value);
			// count_test++;
		}  
		return exampleSet;
	}
	
	/** Creates a histogram (list of bins) for every dimension.
	 *  Read through the sorted data and insert up to n values into the current bin.
	 *  Continue to put instances into the bin until a new value is there.
	 *  Additionally every time a new value is found, it checks if there are 
	 *  more than n of that value. If there are more than n values a new bin is created.
	 *  @param histogram_array
	 *  @param data
	 *  @param first
	 *  @param n
	 *  @param row
	 */
	
	public static int createDynamicHistogram(ArrayList<HistogramBin>[] histogram_array, double[][] data, int first, int n, int feature, boolean nominal) {
		
		int last = first;
		int end = 0;
		// create new bin
		HistogramBin bin = new HistogramBin(data[first][feature],0,0,0);
		// check if an end of the file is near
		if(first+n < data.length) {
			end = (int) (first + n);
		}
		else {
			end = data.length;
		}
		// the first value always goes to the bin
		bin.add_quantity(1);
		/*
		 * for every other value check if it is the same as the last value
		 * if so put it into the bin
		 * if not check if there are more than n of that value => open new bin
		 * 													   or continue putting the value into the bin
		 */
		for(int i = first+1; i < end; i++) {
			if(data[i][feature] == data[last][feature]) {
				bin.add_quantity(1);
				last++;
			}
			else {
				if(check_amount(data, i, n, feature)){
					break;
				}
				else {
					bin.add_quantity(1);
					last++;
				}
			}
		}
		/*
		 * continue to put values in the bin until a new value arrives
		 */
		for(int i = last+1;i < data.length;i++) {
			if(data[i][feature] == data[last][feature]){
				bin.add_quantity(1);
				last++;
			}
			else {
				break;
			}
		}
		// adjust range of the bins
		if (last+1 < data.length) {
			bin.set_range_to(data[last+1][feature]);
		}
		else {
			if(nominal) {
				bin.set_range_to(data[data.length-1][feature]+1);
			}
			else {
				bin.set_range_to(data[data.length-1][feature]);
			}
		}
		
		// save bin 
		if (bin.get_range_to() - bin.get_range_from() > 0) {
			histogram_array[feature].add(bin);
		}
		else if (histogram_array[feature].size() == 0) {
			bin.set_range_to(bin.get_range_to() +1);
			histogram_array[feature].add(bin);
		}
		else {
			// if the bin would have length of zero we merge it with previous bin
			// This can happen at the end of the histogram.
			HistogramBin lb = histogram_array[feature].get(histogram_array[feature].size()-1);
			lb.add_quantity(bin.get_quantity());
			lb.set_range_to(bin.get_range_to());
		}
		
		/*
		 * if end of that file isn't reached start over with the last unused value as first value
		 */
		return last+1;
	}
	/** Create histogram with static binWidth
	 *  @param histogram_array
	 *  @param data
	 *  @param first
	 *  @param binWidth
	 *  @param feature
	 *  @param binStart
	 */
	public static int createStaticHistogram(ArrayList<HistogramBin>[] histogram_array, double[][] data, int first, double binWidth, int feature, double binStart,boolean last_bin){
		HistogramBin bin = new HistogramBin(binStart,binStart+binWidth,0,0);
		if(last_bin ){
			bin = new HistogramBin(binStart,data[data.length-1][feature],0,0);
			}
		
		int last = first-1;
		for(int i = first; i < data.length&&data[i][feature] <= bin.get_range_to(); i++) {
			bin.add_quantity(1);
			last = i;
		}
		histogram_array[feature].add(bin);
		return last+1;
		/*if(last < data.length - 1) { 
			createStaticHistogram(histogram_array,data,last+1,binWidth,feature,binStart+binWidth);
		}*/
	}
	
	/** Sort the rows of an multidimensional array independently.
	 * @param data
	 * @param items
	 * @param number_of_features
	 * @return
	 */
	public static double[][] sort_2dim_array(double[][] data, int items, int number_of_features) {
		double[] tmp = new double[items];
		for(int x = 0; x < number_of_features;x++){
			for(int i = 0; i < items; i++){
				tmp[i] = data[i][x];
				}
			java.util.Arrays.sort(tmp);
			for(int i = 0; i < items;i++){
				data[i][x]= tmp[i];
			}
		}
		return data;
	}
	
	/**	Returns the score and the color of the bin in which a given value is.
	 * @param histogram
	 * @param value
	 * @return
	 */
	public static double[] get_score(ArrayList<HistogramBin> histogram, double value,int row){
		double score = 0;
		double color = 0;
		double[] ret = new double[2];
		for(int x = 0; x < histogram.size()-1;x++){
			if(value >= histogram.get(x).get_range_from() && value < histogram.get(x).get_range_to()) {
				score = histogram.get(x).get_score();
				color = histogram.get(x).get_color();
				ret[0] = score;
				ret[1] = color;
				return ret;
			}
		}
		// "last" bin => different borders
		int x = histogram.size()-1;
		if(value >= histogram.get(x).get_range_from() && value <= histogram.get(x).get_range_to()) {
			score = histogram.get(x).get_score();
			color = histogram.get(x).get_color();
			ret[0] = score;
			ret[1] = color;
			return ret;
		}
		ret[0] = score;
		ret[1] = color;
		return ret;
	}
	
	/** Check if there are more than n points of a given value.
	 * @param data
	 * @param first_occurrence 
	 * @param n
	 * @param row
	 * @return
	 */
	public static boolean check_amount(double[][] data, int first_occurrence ,int n, int row) {
		if((first_occurrence + n) < data.length) {
			if(data[first_occurrence][row]==data[first_occurrence +n][row]) {
				return true;
			}
			else {
				return false;
			}
		}
		else {
			return false;
		}
	}
}


