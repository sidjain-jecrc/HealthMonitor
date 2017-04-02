package mcgroup16.asu.com.group16.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import mcgroup16.asu.com.group16.R;
import mcgroup16.asu.com.group16.model.Row;
import mcgroup16.asu.com.group16.utility.DatabaseUtil;

public class DataCollectActivity extends AppCompatActivity implements SensorEventListener {

    private final String TAG = DataCollectActivity.this.getClass().getSimpleName();
    private SensorManager sensorManager = null;
    private Sensor accelerometer = null;
    private double[] sensorData = null;
    private ArrayList<Double> trainingArray;
    private int activityLabel;
    private static final String RADIO_RUN_OPTION = "Run";
    private static final String RADIO_WALK_OPTION = "Walk";
    private static final int LABEL_RUN = 1;
    private static final int LABEL_WALK = 2;
    private static final int LABEL_EAT = 3;
    private Button btnCollectData = null;
    private Button btnTrainModel = null;
    private Button btnCollectTest = null;
    private Button btnCheckData = null;
    private Button btnCheckTest = null;
    private String trainOrTestFile = null;

    // handler related declarations
    private static final int HAS_FINISHED = 1;
    private Handler insertHandle = null;

    // Database utility related declarations
    private String DB_NAME = null;
    private String TABLE_NAME = "training_table";
    private DatabaseUtil dbHelper = null;

    // file handling declarations
    FileOutputStream outputStream = null;
    BufferedWriter bw = null;
    BufferedReader br = null;
    File trainingFile = null;
    String row = null;
    FileInputStream fin = null;
    String trainFileName = "train";
    String testFileName = "test";
    String modelFileName = "model";
    String predcitFileName = "output";

    //SVM files
    private String storagePath;
    private String appDataPath;
    private String appDataTrainingPath;
    private String appDataModelPath;
    private String appDataTestPath;
    private String appDataPredictPath;
    //Temp Acceleration values
    private TextView xText = null;
    private TextView yText = null;
    private TextView zText = null;
    //Native methods
    static {
        System.loadLibrary("jnilibsvm");
    }

    //private native void jniSvmTrain(String cmd);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collect);
        xText = (TextView) findViewById(R.id.x_val);
        yText = (TextView) findViewById(R.id.y_val);
        zText = (TextView) findViewById(R.id.z_val);
        initDataPaths();
        createFolders();
        initiateAccelerometer();
        //Test linear-acceleration

        DB_NAME = getIntent().getStringExtra("EXTRA_DB_NAME");

        // DB handler instance initialization
        dbHelper = new DatabaseUtil(this, DB_NAME);
        dbHelper.createTable(TABLE_NAME);

        btnCollectData = (Button) findViewById(R.id.btn_collect_data);
        btnCollectData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                RadioGroup radioGroupDataChoice = (RadioGroup) findViewById(R.id.radio_data_choice);
                int choice = radioGroupDataChoice.getCheckedRadioButtonId();
                RadioButton radioButtonChoice = (RadioButton) findViewById(choice);
                String choiceOption = radioButtonChoice.getText().toString();

                if (choiceOption.equals("Train")) {
                    trainOrTestFile = trainFileName;
                } else {
                    trainOrTestFile = testFileName;
                }

                RadioGroup radioGroupCollectData = (RadioGroup) findViewById(R.id.radio_collect_data);
                int selectedId = radioGroupCollectData.getCheckedRadioButtonId();
                RadioButton radioButtonCollect = (RadioButton) findViewById(selectedId);
                String collectOption = radioButtonCollect.getText().toString();

                if (collectOption.equals(RADIO_RUN_OPTION)) {
                    activityLabel = LABEL_RUN;
                } else if (collectOption.equals(RADIO_WALK_OPTION)) {
                    activityLabel = LABEL_WALK;
                } else {
                    activityLabel = LABEL_EAT;
                }

                // file handling operations prior to starting collecting data
                FileWriter fw = null;
                try {
                    File dataFile = new File(appDataPath, trainOrTestFile);
                    if (!dataFile.exists()) {
                        dataFile.createNewFile();
                    }
                    fw = new FileWriter(dataFile.getAbsoluteFile(), true);
                    bw = new BufferedWriter(fw);
                } catch (IOException e) {
                    Toast.makeText(getApplicationContext(), "Error:" + e, Toast.LENGTH_SHORT).show();
                }
                trainingArray = new ArrayList<Double>();
                insertHandle = new Handler();
                insertHandle.post(insertIntoTrainTestArray);
                Toast.makeText(getApplicationContext(), "Data collection started", Toast.LENGTH_SHORT).show();
            }
        });

        btnTrainModel = (Button) findViewById(R.id.btn_train);
        btnTrainModel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                svmTrain();
                Toast.makeText(getApplicationContext(), "Training completed", Toast.LENGTH_SHORT).show();
            }
        });

        btnCheckData = (Button) findViewById(R.id.btn_check_data);
        btnCheckData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {

                    RadioGroup radioGroupDataChoice = (RadioGroup) findViewById(R.id.radio_data_choice);
                    int choice = radioGroupDataChoice.getCheckedRadioButtonId();
                    RadioButton radioButtonChoice = (RadioButton) findViewById(choice);
                    String choiceOption = radioButtonChoice.getText().toString();

                    if (choiceOption.equals("Train")) {
                        trainOrTestFile = trainFileName;
                    } else {
                        trainOrTestFile = testFileName;
                    }

                    char actLabel = '\0';
                    RadioGroup radioGroupCollectData = (RadioGroup) findViewById(R.id.radio_collect_data);
                    int selectedId = radioGroupCollectData.getCheckedRadioButtonId();
                    RadioButton radioButtonCollect = (RadioButton) findViewById(selectedId);
                    String collectOption = radioButtonCollect.getText().toString();
                    if (collectOption.equals(RADIO_RUN_OPTION)) {
                        actLabel = '1';
                    } else if (collectOption.equals(RADIO_WALK_OPTION)) {
                        actLabel = '2';
                    } else {
                        actLabel = '3';
                    }

                    File trainFile = new File(appDataPath, trainOrTestFile);
                    br = new BufferedReader(new FileReader(trainFile));
                    String readLine = "";
                    int dataCount = 0;
                    while ((readLine = br.readLine()) != null) {
                        char label = readLine.charAt(0);
                        if (label == actLabel) {
                            dataCount++;
                        }
                    }
                    Toast.makeText(getApplicationContext(), "Data count for " + actLabel + " is: " + dataCount, Toast.LENGTH_SHORT).show();

                } catch (IOException e) {
                    Log.e(TAG, "Error: " + e);
                }
            }
        });

        Button btnPredict = (Button) findViewById(R.id.btnPredict);
        btnPredict.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent moveToPredictActivity = new Intent(getApplicationContext(), PredictActivity.class);
                moveToPredictActivity.putExtra("EXTRA_DB_NAME", DB_NAME);
                moveToPredictActivity.putExtra("EXTRA_FROM_ACTIVITY", "Collect");
                startActivity(moveToPredictActivity);
            }
        });
    }

    private Runnable insertIntoTrainTestArray = new Runnable() {
        @Override
        public void run() {
            if (trainingArray.size() < 150) {
                trainingArray.add(sensorData[0]);
                trainingArray.add(sensorData[1]);
                trainingArray.add(sensorData[2]);
                insertHandle.postDelayed(this, 100);

            } else if (trainingArray.size() == 150) {
                insertHandle.removeCallbacksAndMessages(null);
                row = String.valueOf(activityLabel);
                double[] averageByAxes = new double[3];
                averageByAxes[0] = 0.0;
                averageByAxes[1] = 0.0;
                averageByAxes[2] = 0.0;
                //average of x,y,z acceleration values
                for (int i = 0; i < 150; i++) {
                    if(i%3 == 0)
                        averageByAxes[0] += trainingArray.get(i);
                    if(i%3 == 1)
                        averageByAxes[1] += trainingArray.get(i);
                    if(i%3 == 2)
                        averageByAxes[2] += trainingArray.get(i);
                    //row += " " + (i + 1) + ":" + trainingArray.get(i);
                }
                averageByAxes[0]/=50;
                averageByAxes[1]/=50;
                averageByAxes[2]/=50;
                row += " " + (1) + ":" + averageByAxes[0]+" "+(2) + ":" + averageByAxes[1]+" "+(3) + ":" + averageByAxes[2];
                Log.e("DataCollection", row);
                try {
                    bw.write(row);
                    bw.newLine();
                } catch (IOException e) {
                    Log.e(TAG, "Error: " + e);
                } finally {
                    try {
                        bw.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error: " + e);
                    }
                }
                // adding row to database
                Row databaseRow = new Row(trainingArray, String.valueOf(activityLabel));
                dbHelper.addRow(databaseRow, TABLE_NAME);
            }
        }
    };

    private void initiateAccelerometer() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorData = new double[4];
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private double[] bigRoundOff(double[] sensorData) {
        double[] tempData = new double[sensorData.length - 1];
        for (int i = 0; i < sensorData.length - 1; i++) {
            BigDecimal bigDecimal = new BigDecimal(sensorData[i]);
            bigDecimal = bigDecimal.setScale(4, BigDecimal.ROUND_HALF_UP);
            tempData[i] = bigDecimal.doubleValue();
        }
        return tempData;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        sensorData[0] = sensorEvent.values[0];
        sensorData[1] = sensorEvent.values[1];
        sensorData[2] = sensorEvent.values[2];
        sensorData[3] = sensorEvent.timestamp;
        xText.setText(String.valueOf(sensorEvent.values[0]));
        yText.setText(String.valueOf(sensorEvent.values[1]));
        zText.setText(String.valueOf(sensorEvent.values[2]));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this, accelerometer);
    }

    private void initDataPaths() {
        storagePath = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/";
        appDataPath = storagePath + "libsvm";
        appDataTrainingPath = appDataPath + "/" + trainFileName;
        appDataTestPath = appDataPath + "/" + testFileName;
        appDataModelPath = appDataPath + "/" + modelFileName;
        appDataPredictPath = appDataPath + "/" + predcitFileName;
    }

    private void svmTrain() {
        String svmOptions = "-t 2 ";
        //jniSvmTrain(svmOptions+appDataTrainingPath+" "+appDataModelPath+" ");
    }

    private void createFolders() {
        File folder = new File(appDataPath);
        if (folder.exists()) {
            removeDirectory(folder);
        }
        boolean created = folder.mkdir();
        int i = 1;
    }

    private void removeDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null && files.length > 0) {
                for (File aFile : files) {
                    removeDirectory(aFile);
                }
            }
            dir.delete();
        } else {
            dir.delete();
        }
    }

}
