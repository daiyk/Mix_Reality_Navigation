// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package com.microsoft.sampleandroid;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import com.google.ar.sceneform.ux.TransformableNode;
import com.microsoft.azure.spatialanchors.AnchorLocateCriteria;
import com.microsoft.azure.spatialanchors.AnchorLocatedEvent;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.microsoft.azure.spatialanchors.CloudSpatialException;
import com.microsoft.azure.spatialanchors.LocateAnchorStatus;
import com.microsoft.azure.spatialanchors.LocateAnchorsCompletedEvent;
import com.microsoft.azure.spatialanchors.NearAnchorCriteria;
import com.microsoft.azure.spatialanchors.SessionUpdatedEvent;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.vecmath.Vector3d;

public class MapBuildingActivity extends AppCompatActivity
{
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 100;
    private String anchorID;
    private final ConcurrentHashMap<String, AnchorVisual> anchorVisuals = new ConcurrentHashMap<>();
    private boolean basicDemo = true;
    private AzureSpatialAnchorsManager cloudAnchorManager;
    private DemoStep currentDemoStep = DemoStep.Start;
    private boolean enoughDataForSaving;
    //change this later
    private static final int numberOfNearbyAnchors = 3;

    private final Object progressLock = new Object();
    private final Object renderLock = new Object();
    private int saveCount = 0;
    private int anchorFound = 0;
    private boolean finish = false;
    private Vector3 lastAnchorPos = new Vector3();
    private Pose lastAnchorPose;

    // Materials
    private static Material failedColor;
    private static Material foundColor;
    private static Material readyColor;
    private static Material savedColor;

    // UI Elements
    private ArFragment arFragment;
    private Button actionButton;
    private Button backButton;
    private TextView scanProgressText;
    private ArSceneView sceneView;
    private TextView statusText;

    private Pose current_anchorPose;
    private Button finishButton;
    private AnchorMap anchorMap;
    private ArrayList<String> AnchorNames = new ArrayList<String>();
    private ArrayList<String> AnchorNames_major = new ArrayList<String>();
    private String prev_anchorName = null;
    private String curr_anchorName= null;
    private boolean spinner_selected = false;

    private EditText nameInput;
    private Button submitButton;
    private Button newBranchButton;
    private LinearLayout mylinearLayout;
    private LinearLayout mylinearLayout_buttons;
    private String anchorName;
    private Spinner spinner_branch;

    private final LinkedHashMap<String,String> anchorNamesIdentifier = new LinkedHashMap<>();
    public void exitDemoClicked(View v) {
        synchronized (renderLock) {
            destroySession();
            finish();
        }
    }

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapping);

        basicDemo = getIntent().getBooleanExtra("BasicDemo", true);

        arFragment = (ArFragment)getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.setOnTapArPlaneListener(this::onTapArPlaneListener);

        sceneView = arFragment.getArSceneView();
        Scene scene = sceneView.getScene();


        scene.addOnUpdateListener(frameTime -> {
            if (cloudAnchorManager != null) {
                // Pass frames to Spatial Anchors for processing.
                cloudAnchorManager.update(sceneView.getArFrame());
            }
        });

        backButton = findViewById(R.id.backButton);
        statusText = findViewById(R.id.statusText);
        scanProgressText = findViewById(R.id.scanProgressText);
        actionButton = findViewById(R.id.actionButton);
        actionButton.setOnClickListener((View v) -> advanceDemo());

        mylinearLayout_buttons = findViewById(R.id.linearLayout_buttons);
        finishButton = findViewById(R.id.finishButton);
        finishButton.setOnClickListener((View v) -> onClick_finish());
        newBranchButton = findViewById(R.id.newBranchButton);
        newBranchButton.setOnClickListener((View v) -> onClick_branch());
        spinner_branch = findViewById(R.id.spinner_branch);

        nameInput = findViewById(R.id.editText);
        submitButton = findViewById(R.id.submitButton);
        mylinearLayout = findViewById(R.id.linearLayout_input);
        submitButton.setOnClickListener((View v) -> onClick_submit());

        anchorMap = new AnchorMap();



        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(material -> failedColor = material);

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.GREEN))
                .thenAccept(material -> savedColor = material);

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.YELLOW))
                .thenAccept(material -> {
                    readyColor = material;
                    foundColor = material;
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroySession();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ArFragment of Sceneform automatically requests the camera permission before creating the AR session,
        // so we don't need to request the camera permission explicitly.
        // This will cause onResume to be called again after the user responds to the permission request.
        if (!SceneformHelper.hasCameraPermission(this)) {
            return;
        }

        if (sceneView != null && sceneView.getSession() == null) {
            SceneformHelper.setupSessionForSceneView(this, sceneView);
        }

        if (AzureSpatialAnchorsManager.SpatialAnchorsAccountId.equals("Set me") || AzureSpatialAnchorsManager.SpatialAnchorsAccountKey.equals("Set me")) {
            Toast.makeText(this, "\"Set SpatialAnchorsAccountId and SpatialAnchorsAccountKey in AzureSpatialAnchorsManager.java\"", Toast.LENGTH_LONG)
                    .show();

            finish();
        }

        if (currentDemoStep == DemoStep.Start) {
            startDemo();
        }
    }

    private void advanceDemo() {
        switch (currentDemoStep) {
            case SaveCloudAnchor:
                AnchorVisual visual = anchorVisuals.get("");
                if (visual == null) {
                    return;
                }

                if (!enoughDataForSaving) {
                    return;
                }

                // Hide the back button until we're done
                runOnUiThread(() -> backButton.setVisibility(View.GONE));

                setupLocalCloudAnchor(visual);

                cloudAnchorManager.createAnchorAsync(visual.getCloudAnchor())
                        .thenAccept(this::anchorSaveSuccess)
                        .exceptionally(thrown -> {
                            thrown.printStackTrace();
                            String exceptionMessage = thrown.toString();
                            Throwable t = thrown.getCause();
                            if (t instanceof CloudSpatialException) {
                                exceptionMessage = (((CloudSpatialException) t).getErrorCode().toString());
                            }

                            anchorSaveFailed(exceptionMessage);
                            return null;
                        });

                synchronized (progressLock) {
                    runOnUiThread(() -> {
                        scanProgressText.setVisibility(View.GONE);
                        scanProgressText.setText("");
                        actionButton.setVisibility(View.INVISIBLE);
                        statusText.setText("Saving cloud anchor...");
                    });
                    currentDemoStep = DemoStep.SavingCloudAnchor;
                }

                break;

            case CreateAnotherLocalAnchor:
                runOnUiThread(() -> {
                    statusText.setText("Tap a surface to create next anchor");
                    actionButton.setVisibility(View.INVISIBLE);
                    mylinearLayout_buttons.setVisibility(View.INVISIBLE);
                });
                currentDemoStep = DemoStep.CreateLocalAnchor;

                break;

//            case AddtoMap:
//
//                runOnUiThread(() -> {
//                    statusText.setText("Anchor info added to Map");
//                    actionButton.setVisibility(View.VISIBLE);
//                });
//                currentDemoStep = DemoStep.CreateAnotherLocalAnchor;
//                break;

            case SaveMap:
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

                    // Permission is not granted
                    // Should we show an explanation?

                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_READ_CONTACTS);

                    // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                    // app-defined int constant. The callback method gets the
                    // result of the request.

                } else {
                    FileManager file = new FileManager();
                    file.saveMap("test",anchorMap);
                    currentDemoStep = DemoStep.End;
                    Toast.makeText(this, "save map",Toast.LENGTH_LONG).show();
                    // Permission has already been granted
                    runOnUiThread(() -> {
                        statusText.setText("");
                        backButton.setVisibility(View.VISIBLE);
                    });
                }
                break;

            case End:
                for (AnchorVisual toDeleteVisual : anchorVisuals.values()) {
                    cloudAnchorManager.deleteAnchorAsync(toDeleteVisual.getCloudAnchor());
                }
                //set arrow invisible
                destroySession();

                runOnUiThread(() -> {
                    actionButton.setText("Restart");
                    statusText.setText("");
                    backButton.setVisibility(View.VISIBLE);
                });

                currentDemoStep = DemoStep.Restart;

                break;

            case Restart:
                startDemo();
                break;
        }
    }

    //当saveCloudAnchor步骤成功时调用，重置createLocalAnchor或者开始新的query步骤
    private void anchorSaveSuccess(CloudSpatialAnchor result) {
        saveCount++;

        Log.d("ASADemo:", "created anchor: " + anchorID);
        AnchorVisual visual = anchorVisuals.get("");
        current_anchorPose = visual.getLocalAnchor().getPose();
        anchorID = result.getIdentifier();

        runOnUiThread(() -> {
            statusText.setText("");
            //Here we can click Submit button
            mylinearLayout.setVisibility(View.VISIBLE);
            actionButton.setVisibility(View.INVISIBLE);
//            finishButton.setVisibility(View.VISIBLE);
        });
        //pos of the last anchor, which is used to compute transformation

        //储存完毕并且把临时anchor删除
        visual.setColor(savedColor);
        anchorVisuals.put(anchorID, visual);
        anchorVisuals.remove("");


        // Need to create more anchors for nearby demo
//        currentDemoStep = DemoStep.CreateAnotherLocalAnchor;
//        advanceDemo();
    }

    private void anchorSaveFailed(String message) {
        runOnUiThread(() -> statusText.setText(message));
        AnchorVisual visual = anchorVisuals.get("");
        visual.setColor(failedColor);
    }

    private void clearVisuals() {
        for (AnchorVisual visual : anchorVisuals.values()) {
            visual.destroy();
        }

        anchorVisuals.clear();
    }

    private Anchor createAnchor(HitResult hitResult) {
        AnchorVisual visual = new AnchorVisual(hitResult.createAnchor());
        visual.setColor(readyColor);
        visual.render(arFragment);
        anchorVisuals.put("", visual);

        runOnUiThread(() -> {
            scanProgressText.setVisibility(View.VISIBLE);
            if (enoughDataForSaving) {
                statusText.setText("Ready to save");
                actionButton.setText("Save cloud anchor");
                actionButton.setVisibility(View.VISIBLE);
            }
            else {
                statusText.setText("Move around the anchor");
            }
        });

        currentDemoStep = DemoStep.SaveCloudAnchor;

        return visual.getLocalAnchor();
    }

    private void destroySession() {
        if (cloudAnchorManager != null) {
            cloudAnchorManager.stop();
            cloudAnchorManager = null;
        }

        clearVisuals();
    }

    // 当criteria定义的寻找目标全部完成时调用，不然一直调用onAnchorLocated（）

    private void onSessionUpdated(SessionUpdatedEvent args) {
        float progress = args.getStatus().getRecommendedForCreateProgress();
        enoughDataForSaving = progress >= 1.0;
        synchronized (progressLock) {
            if (currentDemoStep == DemoStep.SaveCloudAnchor) {
                DecimalFormat decimalFormat = new DecimalFormat("00");
                runOnUiThread(() -> {
                    String progressMessage = "Scan progress is " + decimalFormat.format(Math.min(1.0f, progress) * 100) + "%";
                    scanProgressText.setText(progressMessage);
                });

                if (enoughDataForSaving && actionButton.getVisibility() != View.VISIBLE) {
                    // Enable the save button
                    runOnUiThread(() -> {
                        statusText.setText("Ready to save");
                        actionButton.setText("Save cloud anchor");
                        actionButton.setVisibility(View.VISIBLE);
                    });
                    currentDemoStep = DemoStep.SaveCloudAnchor;
                }
            }
        }
    }

    private void onTapArPlaneListener(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        if (currentDemoStep == DemoStep.CreateLocalAnchor) {
            createAnchor(hitResult);
        }
    }

    private void setupLocalCloudAnchor(AnchorVisual visual) {
        CloudSpatialAnchor cloudAnchor = new CloudSpatialAnchor();
        cloudAnchor.setLocalAnchor(visual.getLocalAnchor());
        visual.setCloudAnchor(cloudAnchor);

        // In this sample app we delete the cloud anchor explicitly, but you can also set it to expire automatically
        Date now = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        cal.add(Calendar.DATE, 7);
        Date oneWeekFromNow = cal.getTime();
        cloudAnchor.setExpiration(oneWeekFromNow);
    }

    private void startDemo() {
        saveCount = 0;
        startNewSession();
        runOnUiThread(() -> {
            scanProgressText.setVisibility(View.GONE);
            statusText.setText("Tap a surface to create an anchor");
            actionButton.setVisibility(View.INVISIBLE);

        });
        currentDemoStep = DemoStep.CreateLocalAnchor;
    }

    private void startNewSession() {
        destroySession();

        cloudAnchorManager = new AzureSpatialAnchorsManager(sceneView.getSession());
//        cloudAnchorManager.addAnchorLocatedListener(this::onAnchorLocated);
//        cloudAnchorManager.addLocateAnchorsCompletedListener(this::onLocateAnchorsCompleted);
        cloudAnchorManager.addSessionUpdatedListener(this::onSessionUpdated);
        cloudAnchorManager.start();
    }

    private void stopWatcher() {
        if (cloudAnchorManager != null) {
            cloudAnchorManager.stopLocating();
        }
    }

    private void onClick_branch(){
//        for (AnchorVisual visuals : anchorVisuals.values()){
//            visuals.getAnchorNode().setOnTapListener(this::onTapListener);
//        }
        if(!spinner_selected){
            runOnUiThread(() -> {
                statusText.setText("Select a cross node from Spinner");
                spinner_branch.setVisibility(View.VISIBLE);
                newBranchButton.setText("CrossNode Selected");
                newBranchButton.setVisibility(View.VISIBLE);
            });
            setupSpinner(AnchorNames_major);
            spinner_selected = true;
        } else{
            runOnUiThread(() -> {
                statusText.setText("Select a cross node from Spinner");
                spinner_branch.setVisibility(View.INVISIBLE);
                newBranchButton.setText("New Branch");
            });
            prev_anchorName = spinner_branch.getSelectedItem().toString();
            spinner_selected = false;
        }
    }

    private void onClick_finish(){
        finish = true;
        mylinearLayout_buttons.setVisibility(View.GONE);
//        finishButton.setVisibility(View.GONE);
        // connect the first and last anchor

//        anchorMap.addEdge((String) AnchorNames.get(AnchorNames.size()-1),
//                (String) AnchorNames.get(0));

        currentDemoStep = DemoStep.SaveMap;
        advanceDemo();
    }

    private void onClick_submit(){
        curr_anchorName = nameInput.getText().toString();
        if(AnchorNames.contains(curr_anchorName)){
            statusText.setText("Names repeated, please submit a new Name");
        }
        else{
            AnchorNames.add(curr_anchorName);
    //        AnchorNames.add(String.format("Anchor %d", saveCount - 1));
            NodeType nodetype = NodeType.Minor;
            if(curr_anchorName.contains("major") || curr_anchorName.contains("Major") ) {
                nodetype = NodeType.Major;
                AnchorNames_major.add(curr_anchorName);
            }
            else if(curr_anchorName.contains("Minor") || curr_anchorName.contains("Minor")){
                nodetype = NodeType.Minor;
            }
            addToMap(curr_anchorName, prev_anchorName, anchorID, current_anchorPose, nodetype);
            runOnUiThread(() -> {
                mylinearLayout.setVisibility(View.GONE);
                //Here we can choose if add new branch or finish or continue
                mylinearLayout_buttons.setVisibility(View.VISIBLE);
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText("Continue");
                statusText.setText("Continue mapping or start a new branch or finish mapping");
            });
            currentDemoStep = DemoStep.CreateAnotherLocalAnchor;

            prev_anchorName = curr_anchorName;//AnchorNames.get(AnchorNames.size()-1);

            Vector3 localPos = new Vector3(0.0f, 0.5f * 0.55f, 0.0f);
            AnchorBoard anchorBoard = new AnchorBoard(this, curr_anchorName, 0.5f, localPos);
            anchorBoard.setParent(anchorVisuals.get(anchorID).getAnchorNode());
        }
    }


    private void addToMap(String curr_AnchorName, String prev_anchorName, String AnchorID, Pose curr_pose, NodeType AnchorType){
        if (prev_anchorName == null){
//            anchorMap.addNode((String) AnchorNames.get(AnchorNames.size()-1), AnchorID, pose_last, AnchorType);
            anchorMap.addNode(curr_AnchorName, AnchorID, curr_pose, AnchorType);
        }
        else{
            //"" is the current located anchor
//            anchorMap.addNode(curr_AnchorName, AnchorID, curr_pose, AnchorType);
//            anchorMap.addEdge((String) AnchorNames.get(AnchorNames.size()-1),
//                    (String) AnchorNames.get(AnchorNames.size()-2));
            anchorMap.addNode(curr_AnchorName, AnchorID, curr_pose, AnchorType);
            anchorMap.addEdge(curr_AnchorName, prev_anchorName);

        }
    }

    public void setupSpinner(ArrayList<String> anchorNames_major){
        ArrayList<String> anchorlist = new ArrayList<String>();
        int n = 0;
        while(n<anchorNames_major.size()){
            anchorlist.add(anchorNames_major.get(n));
            n++;
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, anchorlist);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_branch.setAdapter(spinnerAdapter);
        spinnerAdapter.notifyDataSetChanged();

    }


    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    advanceDemo();
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    Toast.makeText(this,"No Permission", Toast.LENGTH_LONG).show();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    enum DemoStep {
        Start,                          ///< the start of the demo
        CreateLocalAnchor,      ///< the session will create a local anchor
        CreateAnotherLocalAnchor,
        SaveCloudAnchor,        ///< the session will save the cloud anchor
        SavingCloudAnchor,      ///< the session is in the process of saving the cloud anchor
        SaveMap,                ///< save the map after dropping and uploading all the anchors
        End,                            ///< the end of the demo
        Restart,                        ///< waiting to restart
    }
    enum NodeType {             ///< classify nodes into 3 types
        Major,                  ///< node that represents important and meaningful location
        Minor,                  ///< node that used for tracking and accuracy improve.
        Cross,                  ///< node where new graph branch is generated
    }
}