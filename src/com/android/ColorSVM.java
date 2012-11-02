package com.android;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import android.graphics.Typeface;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ColorSVM {

	public static final int COLOR_READING_LENGTH = 4;
	public static final int COLOR_TRAINING_WINDOW_LENGTH = 10;
	public static final int COLOR_SENSING_WINDOW_LENGTH = 5;
	public static final int COLOR_READING_STDV_THRESHOLD = 15;
	SensingFork sensingFork = null;
	FileWriter trainFile = null;
	public static int whatFood;

	private boolean trainCompleted = false;

	Queue<int[]> readingsQueue=new LinkedList<int[]>();
	int[] colorReadingsSum = new int[COLOR_READING_LENGTH];
	double[] colorReadingsAvg = new double[COLOR_READING_LENGTH];

	public ColorSVM(SensingFork sensingFork, FileWriter vFile) {
		this.sensingFork = sensingFork;
		this.trainFile = vFile;
	}

	public void fileClose() {
		try {
			trainFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean getTrainCompleted() {
		return trainCompleted;
	}

	// While steady-state of forking, only recording stable readings.  
	public boolean forkReadingCheck(int state, int foodType, int[] readings, int windowLength, boolean isTrain) {
		if(state != 2) {
			bufferClean();
			return false;
		}

		for(int i=0; i<COLOR_READING_LENGTH; i++) {
			colorReadingsSum[i] += readings[i];
		}

		readingsQueue.add(readings.clone());

		// Calculate standard deviations 
		double[] stdvs = new double[COLOR_READING_LENGTH];
		if(readingsQueue.size() >= windowLength) {

			for(int i=0; i<COLOR_READING_LENGTH; i++) {
				colorReadingsAvg[i] = colorReadingsSum[i]/windowLength;
			}

			Iterator<int[]> iter = readingsQueue.iterator();
			while(iter.hasNext())
	        {
				int[] iteratorValues = iter.next();
				for(int i=0; i<COLOR_READING_LENGTH; i++) {
					stdvs[i] += Math.pow(colorReadingsAvg[i]-iteratorValues[i], 2);
				}

	        }

			if((Math.sqrt(stdvs[0]/windowLength)<COLOR_READING_STDV_THRESHOLD)&&
			   (Math.sqrt(stdvs[1]/windowLength)<COLOR_READING_STDV_THRESHOLD)&&
			   (Math.sqrt(stdvs[2]/windowLength)<COLOR_READING_STDV_THRESHOLD)&&
			   (Math.sqrt(stdvs[3]/windowLength)<COLOR_READING_STDV_THRESHOLD)) {

				if(isTrain) {
					iter = readingsQueue.iterator();
					while(iter.hasNext())
			        {
						int[] iteratorValues = iter.next();
						forkReadingsToSvmFile(state, foodType, iteratorValues, trainFile);

			        }

					Log.i("CheckingQ*", readingsQueue.size()+ " " + Math.sqrt(stdvs[0]/windowLength) + " " 
							+ Math.sqrt(stdvs[1]/windowLength)+ " " + Math.sqrt(stdvs[2]/windowLength) + " " 
							+ Math.sqrt(stdvs[3]/windowLength));
				}
				else { // For testing
					Log.i("TestingQ*", readingsQueue.size()+ " " + Math.sqrt(stdvs[0]/windowLength) + " " 
							+ Math.sqrt(stdvs[1]/windowLength)+ " " + Math.sqrt(stdvs[2]/windowLength) + " " 
							+ Math.sqrt(stdvs[3]/windowLength));

					classify(readingsClassifyParser(readingsQueue), foodType);

				}

				bufferClean();
				return true;
			}
			else
				Log.i("CheckingQ", readingsQueue.size()+ " " + Math.sqrt(stdvs[0]/windowLength) + " " 
					+ Math.sqrt(stdvs[1]/windowLength)+ " " + Math.sqrt(stdvs[2]/windowLength) + " " 
					+ Math.sqrt(stdvs[3]/windowLength));

			// Remove head of queue
			int[] head = readingsQueue.poll();
			for(int i=0; i<COLOR_READING_LENGTH; i++) {
				colorReadingsSum[i] -= head[i];
			}

		}

		return false;
	}

	public void bufferClean() {
		readingsQueue.clear();
		colorReadingsSum = new int[COLOR_READING_LENGTH]; 
		colorReadingsAvg = new double[COLOR_READING_LENGTH];
	}

	private void forkReadingsToSvmFile(int state, int foodType, int [] readings, FileWriter vFile) {
		String readingSvm = "";
		String readingDiff = "";
		String readingRank = "";
		int labelCount = COLOR_READING_LENGTH+1;
		readingRank += foodType;

		readingRank +=" " + "1:"+ addFeature(readings);
		
		String rt = "";
    	for(int i=0; i<readings.length;i++)
    		rt += readings[i]+" ";
    	Log.i("MESSAGE_READ1", rt);
		
		readings[0] = (int) (readings[0]/(readings[3]*0.001862570141-0.1061637462)) ;
		readings[1] = (int) (readings[1]/(readings[3]*0.001736886984-0.03003948353));
		readings[2] = (int) (readings[2]/(readings[3]*0.001244840228+0.2551565263));
		
		for(int i=0; i<COLOR_READING_LENGTH-1;i++) {
			readingSvm += " " + (i+2) + ":" + readings[i];
			for(int j=i+1; j<COLOR_READING_LENGTH-1;j++) {
				readingDiff += " " + labelCount + ":" + (readings[i] - readings[j]);
				labelCount++;
			}
    	}

		try {
			vFile.write(readingRank + readingSvm + readingDiff + "\n");
		} catch (IOException e) {
			e.printStackTrace();
		}

    	Log.i("MESSAGE_READ", readingRank + readingSvm + readingDiff + " --- " + state);

		return;
	}

	float[][] readingsClassifyParser(Queue<int[]> readingsQueue){
		float[][] results = new float[5][7];
		int count = 0;
		Iterator<int[]> iter = readingsQueue.iterator();
		while(iter.hasNext())
		{
			int resulCount = 4;
			int[] readings = iter.next();
			results[count][0] = addFeature(readings);
			//Log.i("MESSAGE_READ", results[count][0] + "");
			readings[0] = (int) (readings[0]/(readings[3]*0.001862570141-0.1061637462)) ;
			readings[1] = (int) (readings[1]/(readings[3]*0.001736886984-0.03003948353));
			readings[2] = (int) (readings[2]/(readings[3]*0.001244840228+0.2551565263));
			
			/*String rt = "";
        	for(int i=0; i<readings.length;i++)
        		rt += readings[i]+" ";
        	Log.i("-classfy-", rt);*/
			
			
			for(int i=0; i<COLOR_READING_LENGTH-1;i++) {
				results[count][i+1] = readings[i];
				//Log.i("MESSAGE_READ1", results[count][i+1] + "");
				for(int j=i+1; j<COLOR_READING_LENGTH-1;j++) {
					results[count][resulCount] = (readings[i] - readings[j]);
					//Log.i("MESSAGE_READ2", results[count][resulCount] + "");

					resulCount++;
				}
				
			}
			String rt = "";
        	for(int k=0; k<7;k++)
        		rt += results[count][k]+" ";
        	Log.i("-classfy-", rt);
        	
			count++;
	    }
		return results;
	}

	public int addFeature(int[] readings){
		if(readings[0]>=readings[1] && readings[1]>=readings[2])
			return 1;
		else if(readings[0]>=readings[2] && readings[2]>readings[1])
			return 2;
		else if(readings[1]>readings[0] && readings[0]>=readings[2])
			return 3;
		else if(readings[2]>readings[0] && readings[0]>=readings[1])
			return 4;
		else if(readings[1]>=readings[2] && readings[2]>readings[0])
			return 5;
		else if(readings[2]>readings[1] && readings[1]>readings[0])
			return 6;
		else 
			return 0;
	}

	public void train() {
    	// Svm training
    	int kernelType = 1; // polynomial function
    	int isProb = 0;
    	int cost = 2; // Cost
    	float gamma = 0.25f; // Gamma
    	String trainingFileLoc = Environment.getExternalStorageDirectory()+"/SensingFork/FoodSVM.dat";
    	String modelFileLoc = Environment.getExternalStorageDirectory()+"/SensingFork/model";
    	if (sensingFork.parseTrainClassifierNative(trainingFileLoc, kernelType, cost, gamma, isProb,
    			modelFileLoc) == -1) {
    		Log.d(sensingFork.TAG, "training err");
    		sensingFork.finish();
    	}
    	
    	trainCompleted = true;
    	//
    }

	public int[] classify(float[][] values, int foodType) {
        // Svm classification
        /*float[][] values = {
        				{44, 24, 18, 31, 20, 26, 13, 6, -7, -13 },
        				{373, 336, 246, 427, 37, 127, -54, 90, -91, -181 },
                        {230, 84, 45, 148, 146, 185, 82, 39, -64, -103},
                        {54, 29, 27, 49, 25, 27, 5, 2, -20, -22 },
                        {343, 283, 169, 360, 60, 174, -17, 114, -77, -191},
        };*/

        int[][] indices = {
        				{1,2,3,4,5,6,7},
        				{1,2,3,4,5,6,7},
        				{1,2,3,4,5,6,7},
        				{1,2,3,4,5,6,7},
        				{1,2,3,4,5,6,7}
        };
        int[] groundTruth = {1,2,3,4,5};
        int[] labels = new int[5];
        double[] probs = new double[5];
        int isProb = 0; // Not probability prediction
        String modelFileLoc = Environment.getExternalStorageDirectory()+"/SensingFork/model";
        int[] count = new int[foodType-1];

        if (callSVM(values, indices, groundTruth, isProb, modelFileLoc, labels, probs) != 0) {
                Log.d(sensingFork. TAG, "Classification is incorrect");
        }
        else {
        	String m = "";
        	for (int l : labels){ 
        		m += l + ", ";
        		count[l-1]++;
        	}
        	int max = 0;
        	
        	for (int i=0; i< count.length-1;i++){
        		if(count[i]<count[i+1]){
        			max = i+1;
        		}
        	}
       
        	Log.i("answer", max+" "+count[max]);
        	
        	TextView sensingText = (TextView)sensingFork.findViewById(R.id.sensing);
        	sensingText.setVisibility(View.VISIBLE);
        	sensingText.setText("Food "+ (max+1) );
        	sensingText.setTypeface(Typeface.createFromAsset(sensingFork.getAssets(), "fonts/SCHOOLA.TTF"));
        	new CountDownTimer(2000,1000){
        		public void onFinish(){
                	TextView sensingText = (TextView)sensingFork.findViewById(R.id.sensing);
        			sensingText.setVisibility(View.INVISIBLE);
        		}
        		
        		public void onTick(long millisUntilFinished){
    				
    			}
        	}.start();
        	
        	//Toast.makeText(sensingFork, "Classification is done, the result is food" + m, 3000).show();
        }
        return labels;
    }
    
    /**
     * classify generate labels for features.
     * Return:
     * 	-1: Error
     * 	0: Correct
     */
	public int callSVM(float values[][], int indices[][], int groundTruth[], int isProb, String modelFile,
    		int labels[], double probs[]) {
    	// SVM type
    	final int C_SVC = 0;
    	final int NU_SVC = 1;
    	final int ONE_CLASS_SVM = 2;
    	final int EPSILON_SVR = 3;
    	final int NU_SVR = 4;
    	
    	// For accuracy calculation
    	int correct = 0;
    	int total = 0;
    	float error = 0;
    	float sump = 0, sumt = 0, sumpp = 0, sumtt = 0, sumpt = 0;
    	float MSE, SCC, accuracy;  	

    	int num = values.length;
    	int svm_type = C_SVC;
    	if (num != indices.length)
    		return -1;
    	// If isProb is true, you need to pass in a real double array for probability array
        int r = sensingFork.parseDoClassificationNative(values, indices, isProb, modelFile, labels, probs);
        
        // Calculate accuracy
        if (groundTruth != null) {
        	if (groundTruth.length != indices.length) {
        		return -1;
        	}
        	for (int i = 0; i < num; i++) {
            	int predict_label = labels[i];
            	int target_label = groundTruth[i];
            	if(predict_label == target_label)
            		++correct;
    	        error += (predict_label-target_label)*(predict_label-target_label);
    	        sump += predict_label;
    	        sumt += target_label;
    	        sumpp += predict_label*predict_label;
    	        sumtt += target_label*target_label;
    	        sumpt += predict_label*target_label;
    	        ++total;
            }
            
        	if (svm_type==NU_SVR || svm_type==EPSILON_SVR)
        	{
        		MSE = error/total; // Mean square error
        		SCC = ((total*sumpt-sump*sumt)*(total*sumpt-sump*sumt)) / ((total*sumpp-sump*sump)*(total*sumtt-sumt*sumt)); // Squared correlation coefficient
        	}
        	accuracy = (float)correct/total*100;
            Log.d(sensingFork.TAG, "Classification accuracy is " + accuracy);
        }       
        
        return r;
    }
    
}