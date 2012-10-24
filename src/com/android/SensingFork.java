package com.android;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


@TargetApi(5)
public class SensingFork extends Activity {
	
	public static final String TAG = "Libsvm";
	
    // svm native
    private native int trainClassifierNative(String trainingFile, int kernelType,
    		int cost, float gamma, int isProb, String modelFile);
    private native int doClassificationNative(float values[][], int indices[][],
    		int isProb, String modelFile, int labels[], double probs[]);
    
   // Load the native library
    static {
        try {
            System.loadLibrary("signal");
            //Log.e(TAG, "Native library signal is loaded");
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Hey, could not load native library signal");
        }
    }
    
    public int parseTrainClassifierNative(String trainingFileLoc, int kernelType, int cost, 
    		float gamma, int isProb, String modelFileLoc) {
    	return trainClassifierNative(trainingFileLoc, kernelType, cost, gamma, isProb,	modelFileLoc);
    }
    
    public int parseDoClassificationNative(float[][] values, int[][] indices, int isProb, String modelFile, 
    		int[] labels, double[] probs) {
    	return doClassificationNative(values, indices, isProb, modelFile, labels,probs);
    }
	
	public SoundPool soundPool = new SoundPool(8, AudioManager.STREAM_MUSIC,0);
	private int id1,id2,id3,id4,id5,id6,id7,id8;
	
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
	
    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    
    public byte[] tmp = null;
    
    // Layout Views
    private ListView mConversationView;
    //private EditText mOutEditText;
    //private Button mSendButton;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothChatService mChatService = null;
    
    private TextView numText = null;
    public int now_view = 0 ;
    
    ColorSVM colorSVM = null;
    private boolean isNotReading = true;
	
    File vSDCard = null;
    File vPath = null;
    FileWriter vFile = null;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set up the window layout
        setContentView(R.layout.begin_panda);
        
        
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
              
        id1 = soundPool.load(this, R.raw.track001, 1);
        id2 = soundPool.load(this, R.raw.track002, 1);
        id3 = soundPool.load(this, R.raw.track003, 1);
        id4 = soundPool.load(this, R.raw.track004, 1);
        id5 = soundPool.load(this, R.raw.track005, 1);
        id6 = soundPool.load(this, R.raw.track006, 1);
        id7 = soundPool.load(this, R.raw.track012, 1);
        id8 = soundPool.load(this, R.raw.kirarara01, 1);
        

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // SD card access
        if( Environment.getExternalStorageState().equals(Environment.MEDIA_REMOVED) )
            return;
        else
        {
           vSDCard = Environment.getExternalStorageDirectory();
        }
         
        vPath = new File( vSDCard + "/SensingFork" );
        if( !vPath.exists() )
           vPath.mkdirs();
         
        try {
			vFile = new FileWriter( vPath + "/FoodSVM.dat", false);
		} catch (IOException e) {
			e.printStackTrace();
		}
        

        colorSVM = new ColorSVM(this, vFile);
        
        start();
        
        
    }
    @Override
    public void onStart() {
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mChatService == null) setupChat();
        }
    }
    
    @Override
    public synchronized void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
              // Start the Bluetooth chat services
              mChatService.start();
            }
        }
    }
    
    @Override
    public synchronized void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }
    
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) mChatService.stop();
    }

  
    private void setupChat() {

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        //mConversationView = (ListView) findViewById(R.id.input);
        //mConversationView.setAdapter(mConversationArrayAdapter);
        
        /*mStopButton = (Button) findViewById(R.id.stop);
        mStopButton.setOnClickListener( new OnClickListener(){
        	//public void onClick(View v){
        		
        	}
        });*/
        
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);

        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }
    
	
	boolean isFoodSensed = false;
	int sensingNoFoodCount = 0;
	int trainingNoFoodCount = 5;
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                case BluetoothChatService.STATE_CONNECTED:
                    mConversationArrayAdapter.clear();
                    break;
                }
                break;
                
            case MESSAGE_READ:
            	int state = 0;
            	int[] readings = (int[]) msg.obj;
            	state = detectMovement(readings);
            	String rt = "";
            	for(int i=0; i<readings.length;i++)
            		rt += readings[i]+" ";
            	Log.i("-State-", state +" "+rt);
            	if(now_view == 3) {
            		//ImageButton nextButton = (ImageButton)findViewById(R.id.next1);
            		//nextButton.setEnabled(true);
            		if(state == 2) {
            			if(trainingNoFoodCount >= 5) {
                			trainingNoFoodCount = 0;
                			three_second();
                		}
            			else
            				trainingNoFoodCount = 0;
            		}
            		else if(state < 2) {
            			trainingNoFoodCount++;
            		}
            	}
            	
            	
            	if(isNotReading)
            		break;
            	
            	if(now_view == 7) 
            		break;
            	
            	// Sensing phase
            	if(now_view == 8) {
            		Log.i("-State-", state +"");
            		if(state == 2) {
            			sensingNoFoodCount = 0;
            			
            			if(isFoodSensed)
            				break;
            			
                		
                		if(colorSVM.forkReadingCheck(state, foodType, readings, colorSVM.COLOR_SENSING_WINDOW_LENGTH, false)) {
                			Log.e("Sensing", " ------ ");
                			isFoodSensed = true;
                		}
                		
                		
            		}
            		else if(state < 2) {
            			sensingNoFoodCount++;
            			colorSVM.bufferClean();
            			if(sensingNoFoodCount >= 5) {
            				isFoodSensed = false;
            			}
            		}
            		
            		break;
            	}
            	
            	// Training phase
            	if(colorSVM.forkReadingCheck(state, foodType, readings, colorSVM.COLOR_TRAINING_WINDOW_LENGTH, true)) {
            		isNotReading = true;
            		treeSecTimer.cancel();
            		completed();
            	}
            	   	
            	
            	Log.i("State", state +"");
            	
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                break;
            	
            }
            
            	
        }
    };
    
	int yes = 0;
    int count = 0;
    int fin=0;
    int x=0,y=0,z=0,x1=0,y1=0,z1=0,x2=0,y2=0,z2=0;
    int t,r;
    int predict=0;
    boolean sound=true;
    int accum=0;
    int zero_count=0;
    int one_count=0;
    
    private int detectMovement(int[] num){
    	   	
    	if(yes==0){
    		yes = 1;
    		x=num[4];y=num[5];z=num[6];
    		x1=0;y1=0;z1=0;
    	}
    	else{
    		x2=x1; y2=y1; z2=z1;
    		x1=x;  y1=y;  z1=z;
    	}
	
    	x=num[4];
    	y=num[5];
    	z=num[6];
    	r=num[10];
    	t=num[11];
    	
    	if(t>200)
    		one_count++;
    	
    	if(t<150)
    		zero_count++;
    	
    	if(zero_count>=3){
    		sound = true;
    		zero_count = 0;
    	}
    	//Log.i("Sound", String.valueOf(t) +" "+ String.valueOf(sound));
    	
    	if(sound==true && one_count>=3){
    		
    		sound = false;
    		one_count = 0;
    		if( t >=200 && t <250)
    			soundPool.play(id1, 1, 1, 0, 0, 1);
    		else if(t>=250 && t<300)
    			soundPool.play(id2, 1, 1, 0, 0, 1);
    		else if(t>=300 && t<350)
    			soundPool.play(id3, 1, 1, 0, 0, 1);
    		else if(t>=350 && t<400)
    			soundPool.play(id4, 1, 1, 0, 0, 1);
    		else if(t>=400 && t<450)
    			soundPool.play(id5, 1, 1, 0, 0, 1);
    		else if(t>=450 && t<500)
    			soundPool.play(id6, 1, 1, 0, 0, 1);
    		else if(t>=500 && t<550)
    			soundPool.play(id7, 1, 1, 0, 0, 1);
    		else if(t>=550 && t<600)
    			soundPool.play(id8, 1, 1, 0, 0, 1);
    		else
    			sound = true;
    		 
    	}
    	
    	
	
    	double mean_x=(x+x1+x2)/3.0;
    	double mean_y=(y+y1+y2)/3.0;
    	double mean_z=(z+z1+z2)/3.0;
    	double Vx = ( Math.pow(x-mean_x,2)+Math.pow(x1-mean_x,2)+Math.pow(x2-mean_x,2));
    	double Vy = ( Math.pow(y-mean_y,2)+Math.pow(y1-mean_y,2)+Math.pow(y2-mean_y,2));
    	double Vz = ( Math.pow(z-mean_z,2)+Math.pow(z1-mean_z,2)+Math.pow(z2-mean_z,2));
    	
    	//Log.i("Variance", Vx +" "+Vy+" "+Vz );
	
    	if(count>=0){
    		count = 0;
    		if(t>200)
    			predict=3;
    		else if(r>10)
    			predict=2;
    		else if( Vx>5 || Vy>5 || Vz>5)
    			predict=1;
    		else
    			predict=0;
    	}
    	else{
    		if(t>200){
    			if(fin==3)
    				count++;
    			else
    				count=0;
    			fin=3;
    		}
    		else if(r>10){
    			if(fin==2)
    				count++;
    			else
    				count=0;
    			fin=2;
    		}
    		else if(Vx>10 || Vy>10 || Vz>10){
    			if(fin==1)
    				count++;
    			else
    				count=0;
    			fin = 1;
    		}
    		else{
    			if(fin==0)
    				count++;
    			else
    				count=0;
    			fin = 0;
			}	
    	}
    	return predict;
    }
    
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mChatService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupChat();
            } else {
                // User did not enable Bluetooth or an error occured
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) { 
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
        return true;
    }
    
    
    public void onBackPressed() {   // Back to last page. 
    	Log.i("now",now_view+"");
    	switch(now_view){
    	case 1:
    		finish();
    		break;
    	case 2:
    		now_view = 1;
    		start();
    		break;
    	case 3:
    		now_view = 2;
    		how_many();
    		break;
    	//case 3: poke_food();break;
    	//case 4: three_second();break;
    	case 7:
    		finish();
    		break;
    	default:
    		finish();
    		break;    	
    	}
    	return;
    }
    
    
    private void start(){
    	 
    	 //setContentView(R.layout.begin_panda);
    	 
    	 now_view=1;
    	 
         ImageButton beginButton = (ImageButton)findViewById(R.id.imageButton1);
    	 beginButton.setOnClickListener(new View.OnClickListener() {
       	 public void onClick(View v) {
       		    how_many();
       			Log.i("Button", "beginButton");		
			 }
		 });
 		
    }
    private int food_num = 1;
    private int foodType = 1;
   
    private void how_many(){
    	
    	setContentView(R.layout.how_many);
    	
    	now_view=2;
    	
    	TextView numText = (TextView)findViewById(R.id.text_number);
    	numText.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/SCHOOLA.TTF"));
    	//Log.i("Num",String.valueOf(food_num));
    	
    	ImageButton minusButton = (ImageButton)findViewById(R.id.minus);
    	ImageButton plusButton = (ImageButton)findViewById(R.id.plus);
    	ImageButton okButton = (ImageButton)findViewById(R.id.ok_button);
    	
        minusButton.setOnClickListener(new View.OnClickListener() {
         	public void onClick(View v) {
         		TextView numText = (TextView)findViewById(R.id.text_number);
         		food_num--;
         		if(food_num <1)
         			food_num = 1;
         		numText.setText(String.valueOf(food_num));
         		numText.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/SCHOOLA.TTF"));
         		//Log.i("Num",String.valueOf(food_num));
 			}
 		});
        
        plusButton.setOnClickListener(new View.OnClickListener() {
        	//Message msg = mHandler.obtainMessage(MESSAGE_NUM_CHANGE);
         	public void onClick(View v) {
         		TextView numText = (TextView)findViewById(R.id.text_number);
         		food_num++;;
         		//Log.i("Num",String.valueOf(food_num));
         		numText.setText(String.valueOf(food_num));
         		numText.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/SCHOOLA.TTF"));
 			}				
 		});
        //Log.i("Num",String.valueOf(num));
        okButton.setOnClickListener(new View.OnClickListener() {
         	public void onClick(View v) {
         		poke_food();
 			}
 		});
	
    }
    
    private void poke_food(){
    	setContentView(R.layout.poke_food);
    	//ImageButton nextButton = (ImageButton)findViewById(R.id.next1);
		//nextButton.setEnabled(false);
		
    	now_view=3;
    	
    	TextView numText = (TextView)findViewById(R.id.food_count);
    	
    	numText.setText(foodType+"");
    	numText.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/SCHOOLA.TTF"));
    	
    	
    	/*nextButton.setOnClickListener(new View.OnClickListener(){
    		
    		public void onClick(View v){
    			
    			three_second();	
    		}
    	});*/
    			
    }
    

	CountDownTimer treeSecTimer = null;
	
    private void three_second(){
    	setContentView(R.layout.three_second);
    	
    	now_view=4;

    	isNotReading = false;
    	
		treeSecTimer = new CountDownTimer(100000,100){
			
			public void onTick(long millisUntilFinished){
				
				TextView countdown = (TextView)findViewById(R.id.countdown);
				countdown.setText("Sensing...");
				//countdown.setText( millisUntilFinished/1000+"");
				countdown.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/SCHOOLA.TTF"));
				
			}
			
			public void onFinish(){
				
				completed();
			}
			
			
		}.start();
    			
    }
    
    private void completed(){
    	
    	now_view=5;
    	isNotReading = true;
    	
    	foodType++;
    	
    	if(food_num < foodType){
    		colorSVM.fileClose();
    		Thread trainThread = new Thread(new Runnable() {
     			public void run() {
     				colorSVM.train();
     				Log.e("Completed", "--- Train Thread is done ---");
     			}
     		});
     		trainThread.start();
     		processing();
     		return;
    	}

    	setContentView(R.layout.completed);
    	
		new CountDownTimer(1000,1000){
			
			public void onFinish(){
				poke_food();
			}
			public void onTick(long millisUntilFinished){
				
			}
		}.start();

    }
    
    private void processing(){
    	setContentView(R.layout.processing);
    	
    	TextView processingText = (TextView)findViewById(R.id.processingText);
    	processingText.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/SCHOOLA.TTF"));
    	
    	now_view=6;
    	
    	new CountDownTimer(100,100){
    		public void onFinish(){
    			if(!colorSVM.getTrainCompleted()) {
    				this.start();
    			}
    			else
    				letsEat();
    		}
    		
    		public void onTick(long millisUntilFinished){
				
			}
    	}.start();
    }
    
    
    private void letsEat(){
    	Toast.makeText(this, "Training is done", 2000).show();
    	setContentView(R.layout.lets_eat);
      	 
    	now_view=7;
      	 
    	ImageButton eatButton = (ImageButton)findViewById(R.id.eating);
    	eatButton.setOnClickListener(new View.OnClickListener() {
      		public void onClick(View v) {
      			sensing();
   			}
   		});
    }
    
    private void sensing(){
    	setContentView(R.layout.sensing);
    	now_view=8;
    	
    	isNotReading = false;
    	
    	TextView sensingText = (TextView)findViewById(R.id.sensing);
    	sensingText.setTypeface(Typeface.createFromAsset(getAssets(), "fonts/SCHOOLA.TTF"));
    	
    	    	
    	//sensingText.setText("Food type is " + 1 + " !");
    	
    }
    
}

