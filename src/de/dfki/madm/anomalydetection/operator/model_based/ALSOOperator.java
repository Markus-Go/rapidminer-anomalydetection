package de.dfki.madm.anomalydetection.operator.model_based;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.AttributeRole;
import com.rapidminer.example.AttributeWeights;
import com.rapidminer.example.Attributes;
import com.rapidminer.example.Example;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.Statistics;
import com.rapidminer.example.Tools;
import com.rapidminer.example.set.SplittedExampleSet;
import com.rapidminer.example.table.AttributeFactory;
import com.rapidminer.example.table.DataRowFactory;
import com.rapidminer.example.table.DataRowReader;
import com.rapidminer.example.table.MemoryExampleTable;
import com.rapidminer.operator.ExecutionUnit;
import com.rapidminer.operator.Operator;
import com.rapidminer.operator.OperatorChain;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.operator.UserError;
import com.rapidminer.operator.learner.PredictionModel;
import com.rapidminer.operator.ports.InputPort;
import com.rapidminer.operator.ports.OutputPort;
import com.rapidminer.operator.ports.Port;
import com.rapidminer.operator.ports.metadata.AttributeMetaData;
import com.rapidminer.operator.ports.metadata.ExampleSetMetaData;
import com.rapidminer.operator.ports.metadata.ExampleSetPrecondition;
import com.rapidminer.operator.ports.metadata.GenerateNewMDRule;
import com.rapidminer.operator.ports.metadata.LearnerPrecondition;
import com.rapidminer.operator.ports.metadata.MetaData;
import com.rapidminer.operator.ports.metadata.PassThroughRule;
import com.rapidminer.operator.ports.metadata.PredictionModelMetaData;
import com.rapidminer.operator.ports.metadata.SimplePrecondition;
import com.rapidminer.operator.ports.metadata.SubprocessTransformRule;
import com.rapidminer.operator.preprocessing.MaterializeDataInMemory;
import com.rapidminer.parameter.ParameterType;
import com.rapidminer.parameter.ParameterTypeBoolean;
import com.rapidminer.parameter.ParameterTypeInt;
import com.rapidminer.parameter.conditions.BooleanParameterCondition;
import com.rapidminer.tools.LogService;
import com.rapidminer.tools.Ontology;
import com.rapidminer.tools.math.optimization.ec.es.ESOptimization;

/**
 * Operator performing outlier detection by round robin learning.
 * 
 * Will call an inner learner on the dataset and use that learner's 
 * predictions to compute outlier scores.
 * 
 * @author Heiko
 *
 */
public class ALSOOperator extends OperatorChain {

	/** The parameter name for number of folds */
	public static final String PARAMETER_NUMBER_OF_FOLDS = "number_of_folds";

	/**The parameter name for &quot;Specifies the number of threads for execution.&quot; Specifies that evaluation process should be performed in parallel &quot; **/
	public static final String PARAMETER_NUMBER_OF_THREADS = "number of threads";
	/** The parameter name for &quot; **/
	public static final String PARAMETER_PARALLELIZE_EVALUATION_PROCESS = "parallelize evaluation process";

	/**
	 * input port
	 */
	private InputPort exampleSetInput = getInputPorts().createPort(
			"example set", ExampleSet.class);

	/**
	 * output port
	 */
	private OutputPort exampleSetOutput = getOutputPorts().createPort(
			"example set");

	/**
	 * Original output port
	 */
	private OutputPort originalOutput= getOutputPorts().createPort("original set");

	/**
	 * Output for feature weights
	 */
	private OutputPort weightsOutput = getOutputPorts().createPort("weights");

	/**
	 * Output for outlier scores per attribute
	 */
	private OutputPort individualOutput = getOutputPorts().createPort("individual");

	/**
	 * Ports for subprocess
	 */
	private OutputPort innerExampleSource = getSubprocess(0).getInnerSources().createPort("training set");
	protected InputPort innerModelSink = getSubprocess(0).getInnerSinks().createPort("model");

	private Map<String,String> individualOutlierAttributes = new HashMap<String,String>();

	public ALSOOperator(OperatorDescription description) {
		super(description, "Model Learning");
		initialize();
	}

	public ALSOOperator(OperatorDescription description, String[] subprocessNames) {
		super(description, subprocessNames);
		initialize();
	}

	@Override
	public void transformMetaData() {
		// TODO Auto-generated method stub
		super.transformMetaData();
	}
	
	private void initialize() {
		// Adding the outlier attribute to the meta data
		getTransformer().addRule(
				new PassThroughRule(exampleSetInput, exampleSetOutput, false) {
					@Override
					public MetaData modifyMetaData(MetaData metaData) {
						if (metaData instanceof ExampleSetMetaData) {
							ExampleSetMetaData exampleSetMetaData = (ExampleSetMetaData) metaData;
							AttributeMetaData amd = new AttributeMetaData(
									Attributes.OUTLIER_NAME, Ontology.REAL,
									Attributes.OUTLIER_NAME);
							exampleSetMetaData.addAttribute(amd);
							return exampleSetMetaData;
						} else {
							return metaData;
						}

					}
				});

		// Adding one outlier attribute per attribute to the meta data at the individual output port
		getTransformer().addRule(
				new PassThroughRule(exampleSetInput, individualOutput, false) {
					@Override
					public MetaData modifyMetaData(MetaData metaData) {
						if (metaData instanceof ExampleSetMetaData) {
							individualOutlierAttributes.clear();
							ExampleSetMetaData exampleSetMetaData = (ExampleSetMetaData) metaData;
							//							ExampleSetMetaData exampleSetMetaData = (ExampleSetMetaData) metaData;
							// Hack, but this gets us the fresher version of the data
							try {
								exampleSetMetaData = new ExampleSetMetaData(exampleSetInput.getData(ExampleSet.class));
							} catch (UserError e) {
							}
							List<String> regularAttNames = new LinkedList<String>();
							for(AttributeMetaData att : exampleSetMetaData.getAllAttributes()) {
								if(!att.isSpecial())
									regularAttNames.add(att.getName());
							}
							for(String attName : regularAttNames) {
								String outlierAttributeName = "outlier score (" + attName + ")";
								AttributeMetaData amd = new AttributeMetaData(
										outlierAttributeName, Ontology.REAL);
								exampleSetMetaData.addAttribute(amd);
								individualOutlierAttributes.put(attName,outlierAttributeName);
							}

							return exampleSetMetaData;
						} else {
							return metaData;
						}

					}
				});

		getTransformer().addPassThroughRule(exampleSetInput, originalOutput);

		innerModelSink.addPrecondition(new SimplePrecondition(innerModelSink, new PredictionModelMetaData(PredictionModel.class, new ExampleSetMetaData()),true));

		// sub process stuff
		getTransformer().addRule(new PassThroughRule(exampleSetInput, innerExampleSource, false) {
			@Override
			public MetaData modifyMetaData(MetaData metaData) {
				if (metaData instanceof ExampleSetMetaData) {
					ExampleSetMetaData exampleSetMetaData = (ExampleSetMetaData) metaData;
					// fix for first run - avoid complaints of nested learner
					// set the first regular numerical attribute as label
					// preserve old label first - move to a special attribute with a very special role :-)
					if(exampleSetMetaData.getAttributeByRole(Attributes.LABEL_NAME)!=null)
						exampleSetMetaData.getAttributeByRole(Attributes.LABEL_NAME).setRole("evil_hack_role");
					for(AttributeMetaData ADM : exampleSetMetaData.getAllAttributes())
						if(!ADM.isSpecial() && ADM.isNumerical()) {
							ADM.setRole(Attributes.LABEL_NAME);
							break;
						}
					return exampleSetMetaData;
				} else {
					return metaData;
				}

			}
		});

		getTransformer().addRule(new SubprocessTransformRule(getSubprocess(0)));

		// output of weights
		getTransformer().addRule(new GenerateNewMDRule(weightsOutput, AttributeWeights.class));

		// precondition to input: only numericals; id must be present
		exampleSetInput.addPrecondition(new ExampleSetPrecondition(exampleSetInput, Ontology.NUMERICAL, Attributes.ID_NAME));
		exampleSetInput.addPrecondition(new ExampleSetPrecondition(exampleSetInput, Attributes.ALL));
	}

	@Override
	public void doWork() throws OperatorException {
		transformMetaData();
		
		final ExampleSet exampleSet = exampleSetInput.getData(ExampleSet.class);
		
		// make sure that there are at least two regular attributes
		int regularAttributes = 0;
		Iterator<AttributeRole> it = exampleSet.getAttributes().regularAttributes();
		while(it.hasNext()) {
			regularAttributes++;
			it.next();
		}
		if(regularAttributes<2)
			throw new OperatorException("RRL operator needs at least two regular attributes."); 
		

		Tools.onlyNonMissingValues(exampleSet, this.getName());
		
		int type = DataRowFactory.TYPE_DOUBLE_ARRAY;
		if (exampleSet.getExampleTable() instanceof MemoryExampleTable) {
			DataRowReader reader = exampleSet.getExampleTable()
					.getDataRowReader();
			if (reader.hasNext())
				type = reader.next().getType();
		}

		// we need a final object for multi-threading
		ExampleSet _resultSet = null;
		if (type >= 0)
			_resultSet = MaterializeDataInMemory.materializeExampleSet(
					exampleSet, type);
		else
			_resultSet = (ExampleSet) exampleSet.clone();
		final ExampleSet resultSet = _resultSet;

		ExampleSet individualResultSet = null;
		if (type >= 0)
			individualResultSet = MaterializeDataInMemory.materializeExampleSet(
					exampleSet, type);
		else
			individualResultSet = (ExampleSet) exampleSet.clone();
		
		// attribute statistics are needed later for computing the attribute weights
		resultSet.recalculateAllAttributeStatistics();

		final int numberOfFolds = getParameterAsInt(PARAMETER_NUMBER_OF_FOLDS);

		// compile list of attributes to learn
		List<String> attributesToHandle = new LinkedList<String>();

		// this method gives better metadata than the metadata delivered by the port
		ExampleSetMetaData EMD = new ExampleSetMetaData(exampleSet);

		// prepare individual outlier attributes
		individualOutlierAttributes.clear();
		for(AttributeMetaData att : EMD.getAllAttributes()) {
			if(!att.isSpecial()) {
				String outlierAttributeName = "outlier score (" + att.getName() + ")";
				individualOutlierAttributes.put(att.getName(),outlierAttributeName);			}
		}

		Iterator<Attribute> attIterator = exampleSet.getAttributes().iterator();
		while(attIterator.hasNext()) {
			Attribute att = attIterator.next();
			// ignore special attributes (id etc.)
			if(!EMD.getAttributeByName(att.getName()).isSpecial()) {
				attributesToHandle.add(att.getName());
			}
		}

		final Map<Double,Map<String,Double>> squaredDeviations = new HashMap<Double,Map<String,Double>>();
		Map<String,Double> attributeWeights = new HashMap<String,Double>();

		// for multi-threading
		int numberOfThreads = 1;
		if(getParameterAsBoolean(PARAMETER_PARALLELIZE_EVALUATION_PROCESS))
			numberOfThreads = getParameterAsInt(PARAMETER_NUMBER_OF_THREADS);

		ExecutorService ES = Executors.newFixedThreadPool(numberOfThreads);

		List<Callable<Object>> tasks = new LinkedList<Callable<Object>>();

		final Map<String,Double> rrsePerAttribute = new HashMap<String,Double>();

		// collecting errors
		final List<OperatorException> exceptions = new LinkedList<OperatorException>();
		
		int attCount = 0;
		
		for(String _att : attributesToHandle) {
			final String att = _att;
			attributeWeights.put(att, 0.0);

			// run folded learning and validation per attribute
			for (int i = 0; i < numberOfFolds; i++) {
				final int iteration = i;
				final ALSOOperator newOp = (ALSOOperator) this.cloneOperator("clone_" + att + "_" + iteration, true);
				newOp.setMyProcess(this.getExecutionUnit());
				
				// copy over macros to cloned subprocess
				Iterator<String> itMacroNames = getProcess().getMacroHandler().getDefinedMacroNames();
				while(itMacroNames.hasNext()) {
					String macroName = itMacroNames.next();
					newOp.getProcess().getMacroHandler().addMacro(macroName, getProcess().getMacroHandler().getMacro(macroName));
				}
				
				final int counter=numberOfFolds*attCount+i;
				
				Thread t = new Thread() {
					@Override
					public void run() {
						try {
							ExampleSet myExampleSet = (ExampleSet) exampleSet.clone();
							myExampleSet.recalculateAllAttributeStatistics();
							
							double sumSqDev = 0.0;
							double sumSqDevDefault = 0.0;

							// modify metadata to set new label
							// this method gives better metadata than the metadata delivered by the port
							ExampleSetMetaData MD = new ExampleSetMetaData(myExampleSet).clone();

							for(AttributeMetaData attMeta : MD.getAllAttributes()) {
								if(attMeta.getRole()!=null && attMeta.getRole().equals(Attributes.LABEL_NAME))
									attMeta.setRegular();
							}
							// set this attribute as target label
							myExampleSet.getAttributes().setLabel(myExampleSet.getAttributes().get(att));
							MD.getAttributeByName(att).setRole(Attributes.LABEL_NAME);

							OutputPort examplePort = (OutputPort) newOp.getSubprocess(0).getInnerSources().getPortByIndex(0);
							InputPort modelPort = (InputPort) newOp.getSubprocess(0).getInnerSinks().getPortByIndex(0);

							examplePort.clear(Port.CLEAR_ALL);
							modelPort.clear(Port.CLEAR_ALL);
							newOp.getSubprocess(0).clear(Port.CLEAR_ALL);

							examplePort.deliverMD(MD);

							SplittedExampleSet splittedES = new SplittedExampleSet(resultSet, numberOfFolds, 0, true, 0);
							// set this attribute as target label
							splittedES.getAttributes().setLabel(splittedES.getAttributes().get(att));

							// Learn model in subprocess
							splittedES.selectAllSubsetsBut(iteration);
							splittedES.recalculateAllAttributeStatistics();
							examplePort.deliver(splittedES);
							newOp.getSubprocess(0).execute();
							
							// Get subset of instances for validation
							splittedES.selectSingleSubset(iteration);

							// obtain and apply learned model
							PredictionModel model = (PredictionModel) modelPort.getData(PredictionModel.class);
							
							model.apply(splittedES);
							for(int i=0;i<splittedES.size();i++) {
								Example predictExample = splittedES.getExample(i);
								double actual = predictExample.getValue(splittedES.getAttributes().getLabel());
								double predicted = predictExample.getValue(splittedES.getAttributes().getPredictedLabel());
								double sqdev = (actual-predicted)*(actual-predicted);
								synchronized(squaredDeviations) {
									if(!squaredDeviations.containsKey(predictExample.getId()))
										squaredDeviations.put(predictExample.getId(),new HashMap<String,Double>());
									squaredDeviations.get(predictExample.getId()).put(att,sqdev);
								}

								double average = myExampleSet.getStatistics(myExampleSet.getAttributes().get(att), Statistics.AVERAGE);
								double sqdevDefault = (actual-average)*(actual-average);

								sumSqDev+=sqdev;
								sumSqDevDefault+=sqdevDefault;
							}
							
							double rrse = Math.sqrt(sumSqDev/sumSqDevDefault);
							
							synchronized(rrsePerAttribute) {
								if(!rrsePerAttribute.containsKey(att))
									rrsePerAttribute.put(att,rrse);
								else
									rrsePerAttribute.put(att,rrsePerAttribute.get(att) + rrse);
							}
							
							
							LogService.getRoot().log(Level.INFO,"Thread " + counter + " has finished.");
							
							// invoke garbage collection after every 25 models
							if(counter%25==0)
								System.gc();

							examplePort.clear(Port.CLEAR_ALL);
							modelPort.clear(Port.CLEAR_ALL);
							newOp.clear(Port.CLEAR_ALL);
							newOp.freeMemory();
							newOp.remove();

						} catch (OperatorException e) {
							e.printStackTrace();
							exceptions.add(e);
						}
					}
				};
				tasks.add(Executors.callable(t));
			}
			attCount++;
		}
		

		LogService.getRoot().log(Level.INFO, "Invoking " + tasks.size() + " parallel jobs");

		try {
			ES.invokeAll(tasks);
		} catch (InterruptedException e) {
			// do nothing here, the user will know why s/he interrupted
		}
		
		if(!exceptions.isEmpty())
			throw exceptions.get(0);

		LogService.getRoot().log(Level.INFO, "Parallel jobs done");
		
		boolean allWeightsZero=true;
		for(String att : attributesToHandle) {
			// weight formula as in paper
			double avgRRSE = rrsePerAttribute.get(att) / numberOfFolds;

			double weight = 1-Math.min(1,avgRRSE);
			if(Double.isNaN(avgRRSE))
				weight = 0.0;

			if(weight>0.0)
				allWeightsZero=false;
			
			LogService.getRoot().log(Level.INFO, "RRSE for attribute " + att + ": " + avgRRSE + "->" + weight);

			attributeWeights.put(att,weight);
		}

		// compute the actual scores
		Attributes individualAttributes = individualResultSet.getAttributes();
		initializeIndividualAnomalyScores(individualResultSet, individualAttributes);
		Attributes attributes = resultSet.getAttributes();
		Attribute anomalyScore1 = initializeAnomalyScore(resultSet, attributes);
		Attribute anomalyScore2 = initializeAnomalyScore(individualResultSet, individualAttributes);

		double normalizationFactor = 0.0;
		for(Double d : attributeWeights.values())
			normalizationFactor += d;

		if(!allWeightsZero) {
			int i=0;
			for(Example example : resultSet) {
				Map<String,Double> deviations = squaredDeviations.get(example.getId());
				double score = 0.0;
				for(Map.Entry<String, Double> attDev : deviations.entrySet()) {
					double attScore = attributeWeights.get(attDev.getKey()) * attDev.getValue();
					score+= attScore;
	
					individualResultSet.getExample(i).setValue(individualResultSet.getAttributes().get(individualOutlierAttributes.get(attDev.getKey())), attScore);
				}
	
				// normalize
				score = Math.sqrt(score/normalizationFactor);
				example.setValue(anomalyScore1, score);
				individualResultSet.getExample(i).setValue(anomalyScore2, score);
				i++;
			}
		} else
			LogService.getRoot().warning("All attribute weights are 0. Cannot compute outlier scores.");
		
		// deliver weights
		AttributeWeights weights = new AttributeWeights(exampleSet);
		for (Attribute attribute : exampleSet.getAttributes()) {
			double weight = attributeWeights.get(attribute.getName());
			weights.setWeight(attribute.getName(), weight);
		}

		originalOutput.deliver(exampleSet);
		exampleSetOutput.deliver(resultSet);
		weightsOutput.deliver(weights);
		individualOutput.deliver(individualResultSet);
	}


	public InputPort getExampleSetInput() {
		return exampleSetInput;
	}

	public OutputPort getExampleSetOutput() {
		return exampleSetOutput;
	}

	public OutputPort getOriginalOutput() {
		return originalOutput;
	}

	/**
	 * Initializes the outlier attribute
	 * 
	 * @param exampleSet
	 * @param attributes
	 * @return anomalyScore Attribute
	 */
	public Attribute initializeAnomalyScore(ExampleSet exampleSet,
			Attributes attributes) {
		Attribute anomalyScore = AttributeFactory.createAttribute(
				Attributes.OUTLIER_NAME, Ontology.REAL);
		exampleSet.getExampleTable().addAttribute(anomalyScore);
		attributes.setOutlier(anomalyScore);
		return anomalyScore;
	}

	/**
	 * Initializes the individual outlier attributes
	 * @param exampleSet
	 * @param attributes
	 * @return
	 */
	private void initializeIndividualAnomalyScores(ExampleSet exampleSet, Attributes attributes) {
		for(String attName : individualOutlierAttributes.values()) {
			Attribute anomalyScore = AttributeFactory.createAttribute(
					attName, Ontology.REAL);
			exampleSet.getExampleTable().addAttribute(anomalyScore);
			attributes.addRegular(anomalyScore);
		}
	}

	@Override
	public List<ParameterType> getParameterTypes() {
		List<ParameterType> types =super.getParameterTypes();

		ParameterType type = new ParameterTypeInt(PARAMETER_NUMBER_OF_FOLDS, "Number of folds", 2, Integer.MAX_VALUE, 10);
		types.add(type);
		
		types
		.add(new ParameterTypeBoolean(
				PARAMETER_PARALLELIZE_EVALUATION_PROCESS,
				"Specifies that evaluation process should be performed in parallel",
				false, false));
		type = (new ParameterTypeInt(PARAMETER_NUMBER_OF_THREADS,
				"Specifies the number of threads for execution.", 1,
				Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors(),
				false));
		type.registerDependencyCondition(new BooleanParameterCondition(this,
				PARAMETER_PARALLELIZE_EVALUATION_PROCESS, true, true));
		types.add(type);


		return types;

	}
	
	private void setMyProcess(ExecutionUnit process) {
		setEnclosingProcess(process);
	}

}
