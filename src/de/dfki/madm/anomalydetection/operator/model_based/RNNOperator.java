package de.dfki.madm.anomalydetection.operator.model_based;

import org.encogx.engine.network.activation.ActivationSigmoid;
import org.encogx.engine.network.activation.ActivationStep;
import org.encogx.engine.network.activation.ActivationTANH;
import org.encogx.neural.data.NeuralDataSet;
import org.encogx.neural.data.basic.BasicNeuralDataSet;
import org.encogx.neural.error.ErrorFunction;
import org.encogx.neural.error.LinearErrorFunction;
import org.encogx.neural.networks.BasicNetwork;
import org.encogx.neural.networks.layers.BasicLayer;
import org.encogx.neural.networks.training.propagation.back.Backpropagation;

import com.rapidminer.example.Attributes;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.operator.OperatorDescription;
import com.rapidminer.operator.OperatorException;

import de.dfki.madm.anomalydetection.operator.AbstractAnomalyDetectionOperator;

public class RNNOperator extends AbstractAnomalyDetectionOperator {

	public RNNOperator(OperatorDescription description) {
		super(description);
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public double[] doWork(ExampleSet exampleSet, Attributes attributes,
			double[][] points) throws OperatorException {
		double[] result = new double[exampleSet.size()];

		BasicNetwork network = new BasicNetwork();
		// Grˆﬂe des Problems (i.e., Anzahl Features)
		int inputOutputSize = points[0].length;
		// Hidden Layer gem‰ﬂ Table 3 im Paper
		int hidden1 = 35;
		int hidden2 = 3;
		int hidden3 = 35;
		// lt. Paper werden offenbar keine Bias-Neuronen verwendet
		network.addLayer(new BasicLayer(new ActivationTANH(),false,inputOutputSize));
		network.addLayer(new BasicLayer(new ActivationTANH(),false,hidden1));
		// ungef‰hre Approximation dessen, was im Paper steht
		network.addLayer(new BasicLayer(new ActivationStep(0,0.5,1),false,hidden2));
		network.addLayer(new BasicLayer(new ActivationTANH(),false,hidden3));
		// output-layer: sigmoid activation function
		network.addLayer(new BasicLayer(new ActivationSigmoid(),false,inputOutputSize));
		network.getStructure().finalizeStructure();
		network.reset();

		// Netzwerk trainieren
		// in und out sind dieselben Daten
		NeuralDataSet trainingSet = new BasicNeuralDataSet(points, points);
		Backpropagation train = new Backpropagation(network, trainingSet);
		// das sollte der Fehlerfunktion im Paper entsprechen
		train.setErrorFunction(new LinearErrorFunction());
		
		double lastError = Double.MAX_VALUE;
		// initiale Learning rate gem‰ﬂ Table 1
		train.setLearningRate(0.001);
		double learningMax = 0.02;
		double betae = 1.005;
		double betar = 0.98;
		
		// Anzahl Iterationen gem‰ﬂ Table 3
		for(int i=0;i<1000;i++) {
			train.iteration();
			
			// adaptive learning rate wie in Formel (6)
			if(train.getError()>lastError*1.01)
				train.setLearningRate(train.getLearningRate()*betar);
			if(train.getError()<lastError && train.getLearningRate()<learningMax)
				train.setLearningRate(train.getLearningRate()*betae);
			
			lastError = train.getError();
		}
		train.finishTraining();
		
		// apply network
		ErrorFunction function = new LinearErrorFunction();
		for(int i=0;i<exampleSet.size();i++) {
			double[] example = points[i];
			double[] predictedExample = new double[example.length];
			double[] error = new double[example.length];
			network.compute(example, predictedExample);
			// TODO check exact error function
			function.calculateError(example, predictedExample, error);
			double overallError = 0.0;
			for(double d : error)
				overallError += d*d;
			result[i] = overallError / error.length;
		}

		return result;
	}


}
