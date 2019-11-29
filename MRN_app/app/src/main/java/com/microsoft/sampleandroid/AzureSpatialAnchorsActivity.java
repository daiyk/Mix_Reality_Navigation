// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package com.microsoft.sampleandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.ux.ArFragment;

//third-party lib to pick real path from url
import com.hbisoft.pickit.PickiT;
import com.hbisoft.pickit.PickiTCallbacks;

import com.microsoft.azure.spatialanchors.AnchorLocateCriteria;
import com.microsoft.azure.spatialanchors.AnchorLocatedEvent;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.microsoft.azure.spatialanchors.CloudSpatialException;
import com.microsoft.azure.spatialanchors.LocateAnchorStatus;
import com.microsoft.azure.spatialanchors.LocateAnchorsCompletedEvent;
import com.microsoft.azure.spatialanchors.NearAnchorCriteria;
import com.microsoft.azure.spatialanchors.SessionUpdatedEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import javax.vecmath.Matrix3d;
import javax.vecmath.Matrix3f;
import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;
import javax.vecmath.Vector3f;

public class AzureSpatialAnchorsActivity extends AppCompatActivity implements PickiTCallbacks
{
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 101;
    private static final int READ_REQUEST_CODE = 102;
    private static final int MY_PERMISSIONS_REQUEST_SAVE_CONTACTS=103;
    private String anchorID;
    private final ConcurrentHashMap<String, AnchorVisual> anchorVisuals = new ConcurrentHashMap<>();
    private boolean basicDemo = true;
    private AzureSpatialAnchorsManager cloudAnchorManager;
    private DemoStep currentDemoStep = DemoStep.Start;
    private boolean enoughDataForSaving;
    private static final int numberOfNearbyAnchors = 3;
    private final Object progressLock = new Object();
    private final Object renderLock = new Object();
    private int saveCount = 0;
    private int anchorFound = 0;

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
    private AnchorArrow arrow;


    private RadioGroup radioGroup;
    private RadioButton radioButton;
    private TextView textView;
    private Button navigateButton;
    private Spinner spinner;

    private float distance;
    private boolean update_anchor;
    private AnchorNode temptargetAnchor = null;
    private AnchorNode arrowAnchor = null;
    private float[] rotationMatrix = new float[16];
    Matrix4f rotationMatrix_4f = new Matrix4f();

    private AnchorNode final_targetAnchor;

    private AnchorMap anchorMap;
    ArrayList<String> optPath = new ArrayList<>();
    Stack<String> stack_id = new Stack<>();

    private String sourceName = null;
    private String targetName = null;
    private boolean reachTarget = false;
    private PickiT pickit;

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
        setContentView(R.layout.activity_anchors);

        basicDemo = getIntent().getBooleanExtra("BasicDemo", true);

        arFragment = (ArFragment)getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.setOnTapArPlaneListener(this::onTapArPlaneListener);

        sceneView = arFragment.getArSceneView();

        Scene scene = sceneView.getScene();
        String anchorCamera = "This is Camera";
        Vector3 camRelPose = new Vector3(0.0f, 0.2f, -1.5f);

        //set arrow
        arrow = new AnchorArrow(this,camRelPose,arFragment.getTransformationSystem());
        scene.getCamera().addChild(arrow);
        arrow.setEnabled(false);

        scene.addOnUpdateListener(frameTime -> {
            if (cloudAnchorManager != null) {
                // Pass frames to Spatial Anchors for processing.
                cloudAnchorManager.update(sceneView.getArFrame());
            }


//            if (currentDemoStep == DemoStep.NavigationEnd) {
//                Vector3 cameraPosition = sceneView.getScene().getCamera().getWorldPosition();
//                Vector3 targetPosition = targetAnchor.getWorldPosition();
//                distance = (float) ( Math.sqrt(targetPosition.x * targetPosition.x + targetPosition.z * targetPosition.z)-
//                                        Math.sqrt(cameraPosition.x * cameraPosition.x + cameraPosition.z * cameraPosition.z));
//                statusText.setVisibility(View.VISIBLE);
//                statusText.setText(String.valueOf(distance));
//                if (distance < 1) {
//                    arrow.updateTargetAnchor(final_targetAnchor);
//                }
//            }

        });

        backButton = findViewById(R.id.backButton);
        statusText = findViewById(R.id.statusText);
        scanProgressText = findViewById(R.id.scanProgressText);
        actionButton = findViewById(R.id.actionButton);
        actionButton.setOnClickListener((View v) -> advanceDemo());

        radioGroup = findViewById(R.id.radioGroup);
        textView = findViewById(R.id.anchor_Selected);
        navigateButton = findViewById(R.id.navigate);
        spinner = findViewById(R.id.spinner);
        navigateButton.setOnClickListener((View v) -> onClick());

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.RED))
                .thenAccept(material -> failedColor = material);

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.GREEN))
                .thenAccept(material -> savedColor = material);

        MaterialFactory.makeOpaqueWithColor(this, new Color(android.graphics.Color.YELLOW))
                .thenAccept(material -> {
                    readyColor = material;
                    foundColor = material;
                });
        //set variable to pick real path from url
        pickit = new PickiT(this, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            pickit.deleteTemporaryFile();
        }
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

            case CreateSessionForQuery:
                cloudAnchorManager.stop();
                cloudAnchorManager.reset();
                clearVisuals();

                runOnUiThread(() -> {
                    statusText.setText("");
                    actionButton.setText("Locate anchor");
                });

                currentDemoStep = DemoStep.LookForAnchor;

                break;

            case LookForAnchor:
                // We need to restart the session to find anchors we created.
                startNewSession();

                AnchorLocateCriteria criteria = new AnchorLocateCriteria();
                //criteria.setBypassCache(true);
                //不规定而是找到最近的anchor
                //String EMPTY_STRING = "";
                criteria.setIdentifiers(new String[]{anchorID});

                // Cannot run more than one watcher concurrently
                stopWatcher();

                cloudAnchorManager.startLocating(criteria);

                runOnUiThread(() -> {
                    actionButton.setVisibility(View.INVISIBLE);
                    statusText.setText("Look for anchor");
                });

                break;

            case LookForNearbyAnchors:
                if (anchorVisuals.isEmpty() || !anchorVisuals.containsKey(anchorID)){
                    runOnUiThread(() -> statusText.setText("Cannot locate nearby. Previous anchor not yet located."));
                    break;
                }

                AnchorLocateCriteria nearbyLocateCriteria = new AnchorLocateCriteria();
                NearAnchorCriteria nearAnchorCriteria = new NearAnchorCriteria();
                nearAnchorCriteria.setDistanceInMeters(10);
                nearAnchorCriteria.setSourceAnchor(anchorVisuals.get(anchorID).getCloudAnchor());
                nearbyLocateCriteria.setNearAnchor(nearAnchorCriteria);
                // Cannot run more than one watcher concurrently
                stopWatcher();
                cloudAnchorManager.startLocating(nearbyLocateCriteria);
                runOnUiThread(() -> {
                    actionButton.setVisibility(View.INVISIBLE);
                    statusText.setText("Locating...");
                });

                break;


            case LoadMap:

                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

                    // Permission is not granted
                    // Should we show an explanation?

                    // No explanation needed; request the permission
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_READ_CONTACTS);

                    // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                    // app-defined int constant. The callback method gets the
                    // result of the request.

                } else {

                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    String map_path = Environment.getExternalStorageDirectory().getAbsolutePath()
                            + File.separator + "MixRealityNavi" + File.separator + "Maps" + File.separator;
                    File map_dir = new File(map_path);
                    if (!map_dir.exists())
                    {
                        map_dir.mkdirs();
                        Toast.makeText(this,"No Map file found in this device!",Toast.LENGTH_LONG).show();
                    }
//                    intent.setDataAndType(Uri.fromFile(map_dir.getParentFile()), "*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    Uri uri = Uri.parse(map_path);

                    intent.setDataAndType(uri, "*/*");
                    startActivityForResult(intent, READ_REQUEST_CODE);
                    int kk = 0;
                }
                
                break;
            case ChooseStartPoint:

                ArrayList<Node> nodelist = anchorMap.getNodeList();
                setupSpinner(nodelist);
                runOnUiThread(() -> {
                    actionButton.setText("Select Start Point");
                    statusText.setText("");
                    backButton.setVisibility(View.VISIBLE);

//                    radioGroup.setVisibility(View.VISIBLE);
                    radioGroup.setVisibility(View.INVISIBLE);
                    textView.setVisibility(View.INVISIBLE);
                    navigateButton.setVisibility(View.VISIBLE);
                    navigateButton.setText("StartPointSeleted");
                    spinner.setVisibility(View.VISIBLE);
                });

                // Go to locate function to locate start anchor
//                currentDemoStep = DemoStep.LookForAnchor;

                break;

            case NavigationStart:
                for (AnchorVisual visuals : anchorVisuals.values()){
                    visuals.getAnchorNode().setOnTapListener(this::onTapListener);
                }
                break;

            case NavigationEnd:
                if(targetName == null)
                {
                    Toast.makeText(this, "\"ERROR: No target Selected!\"", Toast.LENGTH_LONG)
                            .show();
                    finish();
                }
                // Get Optimal path for navigation
                anchorMap.searchSP(sourceName, targetName, optPath);


                Stack<String> stack_path = new Stack<>();

                for(int i=optPath.size()-1;i>=0; i--){
                    stack_path.push(anchorMap.getNode(optPath.get(i)).AnchorName);
                    stack_id.push(anchorMap.getNode(optPath.get(i)).AnchorID);
                }

//                LookforAnchor_realtime(stack_id.pop());
//                final Vector3[] current_worldcoord = {anchorVisuals.get("").getAnchorNode().getWorldPosition()};
//                final Vector3[] origin_worldcoord = {anchorMap.getPos(sourceName)};
//
//                final Vector3f[] origin_worldcoord_v = {new Vector3f(origin_worldcoord[0].normalized().x, origin_worldcoord[0].normalized().y, origin_worldcoord[0].normalized().z)};
//                final Vector3f[] current_worldcoord_z = {new Vector3f(current_worldcoord[0].normalized().x, current_worldcoord[0].normalized().y, current_worldcoord[0].normalized().z)};
//                final Matrix3f[] rotationMatrix = {getTransformationMatrix(origin_worldcoord_v[0], current_worldcoord_z[0])};

                // Init as first direction
                final String[] nextAnchorName = {toNextAnchor(stack_path)};
                AnchorNode dummyNode = new AnchorNode();

//                final Vector3[] nextAnchorTransf = {Vector3.add(getTransformedCoordinates(rotationMatrix, anchorMap.getPos(nextAnchorName)), current_worldcoord)};//{Vector3.add(getTransformedCoordinates(rotationMatrix, anchorMap.getEdge(sourceName,nextAnchorName)),current_worldcoord)};
//                final Vector3[] nextAnchorTransf = {getTransformedCoordinates(rotationMatrix, anchorMap.getPos(nextAnchorName))};
//                final Vector3[] nextAnchorTransf = {Vector3.add(getTransformedCoordinates(rotationMatrix[0], anchorMap.getEdge(sourceName, nextAnchorName[0])),current_worldcoord[0])};
//                final Vector3[] nextAnchorTransf = {getTransformedCoordinates(rotationMatrix, anchorMap.getEdge(sourceName,nextAnchorName[0]))};
//                final Vector3[] nextAnchorTransf = {Vector3.add(getTransformedCoordinates(rotationMatrix[0], anchorMap.getPos(nextAnchorName[0])),Vector3.subtract(current_worldcoord[0], origin_worldcoord[0]))};

                final Vector3[] nextAnchorTransf = {getTransformedCoordinates_new(rotationMatrix_4f, anchorMap.getPos(nextAnchorName[0]))};

                arrow.setEnabled(true);
                arrow.updateTargetPos(nextAnchorTransf[0]);
//                dummyNode.setWorldPosition(nextAnchorTransf[0]);
//                arrow.updateTargetAnchor(dummyNode);
                final String[] nextAnchorID = {anchorMap.getNode(nextAnchorName[0]).AnchorID};
//                arrow.updateTargetAnchor(dummyNode);
                //arrow.updateTargetAnchor(temptargetAnchor);
//                stopWatcher();

                Scene scene = sceneView.getScene();
                scene.addOnUpdateListener(frameTime -> {

//                    if(temptargetAnchor != null){
//                        arrowAnchor = temptargetAnchor;
//                    }
//                    else{
//                        arrowAnchor = dummyNode;
//                    }
                    Vector3 targetPosition = nextAnchorTransf[0];//temptargetAnchor.getWorldPosition();
                    Vector3 cameraPosition = sceneView.getScene().getCamera().getWorldPosition();
                    //distance = (float) ( Math.abs(Math.sqrt(targetPosition.x * targetPosition.x + targetPosition.z * targetPosition.z)-
                    //        Math.sqrt(cameraPosition.x * cameraPosition.x + cameraPosition.z * cameraPosition.z)));
                    distance = (float) Math.sqrt((targetPosition.x - cameraPosition.x)*(targetPosition.x - cameraPosition.x) +
                            (targetPosition.z - cameraPosition.z)*(targetPosition.z - cameraPosition.z));
                    statusText.setText(String.valueOf(distance));

//                    current_worldcoord[0] = anchorVisuals.get("").getAnchorNode().getWorldPosition();
//                    origin_worldcoord[0] = anchorMap.getPos(sourceName);
//                    Vector3f origin_worldcoord_v_update = new Vector3f(origin_worldcoord[0].normalized().x, origin_worldcoord[0].normalized().y, origin_worldcoord[0].normalized().z);
//                    Vector3f current_worldcoord_z_update = new Vector3f(current_worldcoord[0].normalized().x, current_worldcoord[0].normalized().y, current_worldcoord[0].normalized().z);
//                    rotationMatrix[0] = getTransformationMatrix(origin_worldcoord_v_update,current_worldcoord_z_update);

                    if (distance < 0.3) {
                        temptargetAnchor = null;
                        String nextAnchorName_update = toNextAnchor(stack_path);
                        if (nextAnchorName_update !="Empty"){
//                            nextAnchorTransf[0] = getTransformedCoordinates(rotationMatrix, anchorMap.getPos(nextAnchorName_update));
//                            nextAnchorTransf[0] = Vector3.add(getTransformedCoordinates(rotationMatrix[0], anchorMap.getEdge(nextAnchorName[0],nextAnchorName_update)),nextAnchorTransf[0]);
//                            nextAnchorTransf[0] = getTransformedCoordinates(rotationMatrix, anchorMap.getEdge(nextAnchorName[0],nextAnchorName_update));
//                            nextAnchorTransf[0] = Vector3.add(getTransformedCoordinates(rotationMatrix[0], anchorMap.getPos(nextAnchorName_update)),Vector3.subtract(current_worldcoord[0], origin_worldcoord[0]));
                            nextAnchorTransf[0] = getTransformedCoordinates_new(rotationMatrix_4f, anchorMap.getPos(nextAnchorName_update));
                            nextAnchorName[0] = nextAnchorName_update;

                        }
                        else{
                            statusText.setText("Reach Final Destination");
                            actionButton.setVisibility(View.VISIBLE);
                            actionButton.setText("End Navigation");

                        }
                        arrow.updateTargetPos(nextAnchorTransf[0]);
//                        dummyNode.setWorldPosition(nextAnchorTransf[0]);
//                        arrow.updateTargetAnchor(dummyNode);
                        //getTransformedCoordinates(rotationMatrix, anchorMap.getPos(nextAnchorName_update));
                        //                        nextAnchorID[0] = anchorMap.getNode(nextAnchorName[0]).AnchorID;
                    }

                    if (reachTarget){
                        runOnUiThread(() -> {
                            actionButton.setText("End Navigation");
                            statusText.setText("Reach Final Destination");
                            backButton.setVisibility(View.VISIBLE);

                            radioGroup.setVisibility(View.INVISIBLE);
//                    textView.setVisibility(View.INVISIBLE);
                            navigateButton.setVisibility(View.INVISIBLE);
                        });
                    }
                });
//                arrow.updateTargetPos(nextAnchorTransf[0]);
//                arrow.setEnabled(true);

                currentDemoStep =DemoStep.End;
//                for (AnchorVisual toDeleteVisual : anchorVisuals.values()) {
//                    if(toDeleteVisual != targetAnchorVisual) {
//                        cloudAnchorManager.deleteAnchorAsync(toDeleteVisual.getCloudAnchor());
//                        toDeleteVisual.destroy();
//                    }
//                }
                break;
            case End:
                for (AnchorVisual toDeleteVisual : anchorVisuals.values()) {
                    cloudAnchorManager.deleteAnchorAsync(toDeleteVisual.getCloudAnchor());
                }
                //set arrow invisible
                arrow.setEnabled(false);
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

        anchorID = result.getIdentifier();
        Log.d("ASADemo:", "created anchor: " + anchorID);

        AnchorVisual visual = anchorVisuals.get("");

        //储存完毕并且把anchor删除
        visual.setColor(savedColor);
        anchorVisuals.put(anchorID, visual);
        anchorVisuals.remove("");

        if (basicDemo || saveCount == numberOfNearbyAnchors) {
            runOnUiThread(() -> {
                statusText.setText("");
                actionButton.setVisibility(View.VISIBLE);
            });

            currentDemoStep = DemoStep.CreateSessionForQuery;
            advanceDemo();
        }
        else {
            // Need to create more anchors for nearby demo
            runOnUiThread(() -> {
                statusText.setText("Tap a surface to create next anchor");
                actionButton.setVisibility(View.INVISIBLE);
            });

            currentDemoStep = DemoStep.CreateLocalAnchor;
        }
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

    @SuppressLint("SetTextI18n")
    private void onAnchorLocated(AnchorLocatedEvent event) {
        LocateAnchorStatus status = event.getStatus();

        runOnUiThread(() -> {
            switch (status) {
                case AlreadyTracked:
                    statusText.setText("AlreadyTracked");
                    break;

                case Located:
                    renderLocatedAnchor(event.getAnchor());
                    break;

                case NotLocatedAnchorDoesNotExist:
                    statusText.setText("Anchor does not exist");
                    break;
            }
        });
    }

    //当criteria定义的寻找目标全部完成时调用，不然一直调用onAnchorLocated（）
    private void onLocateAnchorsCompleted(LocateAnchorsCompletedEvent event) {

//        if (!basicDemo && currentDemoStep == DemoStep.LookForAnchor) {
//            runOnUiThread(() -> {
//                actionButton.setVisibility(View.VISIBLE);
//                actionButton.setText("Look for anchors nearby");
//            });
//            currentDemoStep = DemoStep.LookForNearbyAnchors;
//        } else {
//            stopWatcher();
//            runOnUiThread(() -> {
//                actionButton.setVisibility(View.VISIBLE);
//                //actionButton.setText("Cleanup anchors");
//                actionButton.setText("Start Navigation");
//            });
//            currentDemoStep = DemoStep.NavigationStart;
//        }
        // Here we only look for the source Anchor
        if(currentDemoStep == DemoStep.LookForAnchor){
//            stopWatcher();
            runOnUiThread(() -> {
                statusText.setText("Source Anchor located!");
                actionButton.setVisibility(View.VISIBLE);
                //actionButton.setText("Cleanup anchors");
                actionButton.setText("Start Navigation");
            });
            currentDemoStep = DemoStep.NavigationStart;
        }
        else if(currentDemoStep == DemoStep.NavigationEnd){
            runOnUiThread(() -> {
                statusText.setText("Asistant Anchor located!");
                actionButton.setVisibility(View.INVISIBLE);
            });
            LookforAnchor_realtime(stack_id.pop());
        }
    }

    @SuppressLint("SetTextI18n")
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

    private void renderLocatedAnchor(CloudSpatialAnchor anchor) {
        if(currentDemoStep == DemoStep.LookForAnchor){

            AnchorVisual foundVisual = new AnchorVisual(anchor.getLocalAnchor());
            temptargetAnchor = foundVisual.getAnchorNode();
            //String anchorName = String.format("%d", ++anchorFound);
            anchorVisuals.put("", foundVisual);
//            float[] q = anchor.getLocalAnchor().getPose().getRotationQuaternion();
//            float[] test = {(float) -0.2766, (float) -1.39, (float) -0.072};
//            float[] testresutl = anchor.getLocalAnchor().getPose().transformPoint(test);
//            float real = anchor.getLocalAnchor().getPose().tx();
//            float[] realrestul =
//            Pose pose = anchor.getLocalAnchor().getPose().extractRotation();
//            Pose trans = anchor.getLocalAnchor().getPose().extractTranslation();
//            Vector3 real = foundVisual.getAnchorNode().getWorldPosition();
//
//            Quaternion aaa = foundVisual.getAnchorNode().getWorldRotation();
            Vector3 test = foundVisual.getAnchorNode().getWorldPosition();
            Pose pose = anchor.getLocalAnchor().getPose();
            float[] a = new float[16];
            float[] b = new float[16];

            pose.toMatrix(a, 0);
            Pose pose_in = pose.inverse();
            pose_in.toMatrix(b, 0);
            float[] test_world = new float[]{test.x, test.y, test.z};
            float[] c = pose_in.transformPoint(test_world);
            //pose.toMatrix(rotationMatrix, 0);
            float[] rot = pose.getRotationQuaternion();
            float[] vec = pose.getTranslation();
            Quat4f rotation = new Quat4f(rot[0], rot[1], rot[2], rot[3]);
            Vector3f vector = new Vector3f(vec[0], vec[1], vec[2]);

            rotationMatrix_4f.set(rotation, vector, 1);
            //            foundVisual.setCloudAnchor(anchor);
//            foundVisual.getAnchorNode().setParent(arFragment.getArSceneView().getScene());
//
//
//            String cloudAnchorIdentifier = foundVisual.getCloudAnchor().getIdentifier();
//            //statusText.setText(String.format("cloud Anchor Identifier: %s",cloudAnchorIdentifier));
            foundVisual.setColor(foundColor);
//
//            String anchorName = String.format("Anchor %d", ++anchorFound);
//            anchorNamesIdentifier.put(anchorName, cloudAnchorIdentifier);
//            float anchorscale = 0.5f;
//            Vector3 localPos = new Vector3(0.0f, anchorscale * 0.55f, 0.0f);
//            AnchorBoard anchorBoard = new AnchorBoard(this, "test", 0.5f, localPos);
//            anchorBoard.setParent(foundVisual.getAnchorNode());

            foundVisual.render(arFragment);

            //record anchors with its name as key
//        if(currentDemoStep == DemoStep.LookForAnchor) {
//            anchorID = anchorName;
//        }
//            anchorVisuals.put(cloudAnchorIdentifier, foundVisual);
        }
        else if(currentDemoStep == DemoStep.NavigationStart){
            // Render anchors during the navigation process, can be deleted later
            AnchorVisual foundVisual = new AnchorVisual(anchor.getLocalAnchor());
            foundVisual.render(arFragment);
            // Here assign located anchor to temptargetAnchor, can put somewhere else later
            temptargetAnchor = foundVisual.getAnchorNode();
        }
        else if(currentDemoStep == DemoStep.NavigationEnd) {
            runOnUiThread(() -> {
                statusText.setText("233333333333");
            });
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
//            actionButton.setVisibility(View.INVISIBLE);
            actionButton.setVisibility(View.VISIBLE);

            navigateButton.setVisibility(View.INVISIBLE);
            radioGroup.setVisibility(View.INVISIBLE);
            textView.setVisibility(View.INVISIBLE);
            spinner.setVisibility(View.INVISIBLE);
        });
        currentDemoStep = DemoStep.LoadMap;
//        currentDemoStep = DemoStep.CreateLocalAnchor;
    }

    private void startNewSession() {
        destroySession();

        cloudAnchorManager = new AzureSpatialAnchorsManager(sceneView.getSession());
        cloudAnchorManager.addAnchorLocatedListener(this::onAnchorLocated);
        cloudAnchorManager.addLocateAnchorsCompletedListener(this::onLocateAnchorsCompleted);
        cloudAnchorManager.addSessionUpdatedListener(this::onSessionUpdated);
        cloudAnchorManager.start();
    }

    private void stopWatcher() {
        if (cloudAnchorManager != null) {
            cloudAnchorManager.stopLocating();
        }
    }

    private void onClick(){
        if (currentDemoStep == DemoStep.ChooseStartPoint){
            // Use RadioGroup
//            int radioId = radioGroup.getCheckedRadioButtonId();
//            radioButton = findViewById(radioId);
//            sourceName = (String)radioButton.getText();
//            // Get start point anchor ID for "LookForAnchor"
//            anchorID = anchorMap.getNode(sourceName).AnchorID;
//            runOnUiThread(() -> {
//                actionButton.setText("Look for Start Anchor");
//                statusText.setText("");
//
//            });

            // Use Spinner
            sourceName = spinner.getSelectedItem().toString();
            anchorID = anchorMap.getNode(sourceName).AnchorID;
            runOnUiThread(() -> {
                actionButton.setText("Look for Start Anchor");
//                statusText.setText("");
                statusText.setText(anchorID);
                spinner.setVisibility(View.INVISIBLE);
                navigateButton.setVisibility(View.INVISIBLE);
//                statusText.setText(text);
            });

            currentDemoStep = DemoStep.LookForAnchor;
            advanceDemo();
        }
        if (currentDemoStep == DemoStep.NavigationStart){
//            int radioId = radioGroup.getCheckedRadioButtonId();
//            radioButton = findViewById(radioId);
//            targetName = (String)radioButton.getText();
//            textView.setText("Navigate to "+ radioButton.getText());
            targetName = spinner.getSelectedItem().toString();
            runOnUiThread(() -> {
//                textView.setText((CharSequence) dictionary.keySet().toArray()[0]);
//                statusText.setText(radioButton.getText());
                statusText.setText("Destination selected");
                spinner.setVisibility(View.INVISIBLE);
                navigateButton.setVisibility(View.INVISIBLE);

            });
            currentDemoStep = DemoStep.NavigationEnd;
            advanceDemo();
        }

    }

    public void setupSpinner(ArrayList<Node> nodelist){
        ArrayList<String> anchorlist = new ArrayList<String>();
        int n = 0;
        while(n<nodelist.size()){
            anchorlist.add(nodelist.get(n).AnchorName);
            n++;
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, anchorlist);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinnerAdapter.notifyDataSetChanged();

    }

    private void onTapListener(HitTestResult hitTestResult, MotionEvent motionEvent) {

        if (currentDemoStep == DemoStep.NavigationStart) {
            runOnUiThread(() -> {
//                radioGroup.setVisibility(View.VISIBLE);
                radioGroup.setVisibility(View.INVISIBLE);
                textView.setVisibility(View.VISIBLE);
                navigateButton.setVisibility(View.VISIBLE);
                textView.setText("");
                spinner.setVisibility(View.VISIBLE);
            });
//            Iterator AnchorIterator = anchorNamesIdentifier.entrySet().iterator();
//            int count = radioGroup.getChildCount();
//            int i = 0;
//            while(AnchorIterator.hasNext()){
//                Map.Entry mapElement = (Map.Entry)AnchorIterator.next();
//                RadioButton b = (RadioButton)radioGroup.getChildAt(i++);
//                b.setText((CharSequence) mapElement.getKey());
//            };
        }
    }

    public Matrix3f getTransformationMatrix(Vector3f vec1, Vector3f vec2){
        //vec1.normalize();

        //vec2.normalize();
        Vector3f v = new Vector3f();
        v.cross(vec1, vec2);
        float sinAngle = v.length();
        float c = vec1.dot(vec2);

        //build matrix
        Matrix3f u = new Matrix3f(0.f,-v.z,v.y,v.z,0.f,-v.x,-v.y,v.x,0.f);
        Matrix3f u2 = new Matrix3f();
        u2.mul(u,u);

        //coeff
        float coeff = 1.f/(1+c);
        Matrix3f I = new Matrix3f();
        I.setIdentity();
        u.add(I);//I + u
        u2.mul(coeff);//u*c
        u.add(u2);//u+u2

        return u;
    }

    public Vector3 getTransformedCoordinates_new(Matrix4f R, Vector3 vec) {
//        float x = R[0]*vec.x + R[4]*vec.y + R[8]*vec.z + R[12];
//        float y = R[1]*vec.x + R[5]*vec.y + R[8]*vec.z + R[13];
//        float z = R[2]*vec.x + R[6]*vec.y + R[10]*vec.z + R[14];
//        float x = R[0]*vec.x + R[1]*vec.y + R[2]*vec.z + R[12];
//        float y = R[3]*vec.x + R[4]*vec.y + R[5]*vec.z + R[13];
//        float z = R[6]*vec.x + R[7]*vec.y + R[8]*vec.z + R[14];
        float x = R.m00 * vec.x + R.m01 * vec.y + R.m02 * vec.z + R.m03;
        float y = R.m10 * vec.x + R.m11 * vec.y + R.m12 * vec.z + R.m13;
        float z = R.m20 * vec.x + R.m21 * vec.y + R.m22 * vec.z + R.m23;
        Vector3 res = new Vector3(x, y, z);


        return res;
    }

//    public double[][] getTransformationMatrix(Vector3 vec1, Vector3 vec2){
//
//        Vector3 v = Vector3.cross(vec1, vec2);
//        float s = (float) Math.sqrt(v.x*v.x + v.y*v.y + v.z*v.z);
//        float c = Vector3.dot(vec1, vec2);
//        float scale = (float) 1/(1+c);//(1-c)/(s*s);
//
//        Matrix3d vx = new Matrix3d(0, -v.z, v.y, v.z, 0, -v.x, -v.y, v.x, 0);
//        Matrix3d eye = new Matrix3d(1,0,0,0,1,0,0,0,1);
//        Matrix3d vx2 = new Matrix3d();
//        vx2.mul(vx,vx);
//        vx2.mul(scale);
//        vx.add(vx2);
//        Matrix3d rotationMatrix = new Matrix3d();
//        rotationMatrix.add(eye, vx);
//
//        double[][] R = new double[3][3];
//        R[0][0] = 1 + 0 + scale * (-v.z*v.z-v.y*v.y);
//        R[0][1] = 0 + (-v.z) + scale * (v.x * v.y);
//        R[0][2] = 0 + v.y + scale * (v.x * v.z);
//
//        R[1][0] = 0 + v.z + scale * (v.x * v.y);
//        R[1][1] = 1 + 0 + scale * (-v.z*v.z-v.x*v.x);
//        R[1][2] = 0 + (-v.x) + scale * (v.y * v.z);
//
//        R[2][0] = 0 + (-v.y) + scale * (v.x * v.z);
//        R[2][1] = 0 + v.x + scale * (v.y * v.z);
//        R[2][2] = 1 + 0 + scale * (-v.y*v.y-v.x*v.x);
//        return R;
//    }

    public Vector3 getTransformedCoordinates(Matrix3f R, Vector3 vec){
//        float x = (float) (matrix[0][0]*vec.x + matrix[0][1]*vec.y + matrix[0][2]*vec.z);
//        float y = (float) (matrix[1][0]*vec.x + matrix[1][1]*vec.y + matrix[1][2]*vec.z);
//        float z = (float) (matrix[2][0]*vec.x + matrix[2][1]*vec.y + matrix[2][2]*vec.z);
//        Vector3d v1 = new Vector3d();
//        Vector3d v2 = new Vector3d();
//        Vector3d v3 = new Vector3d();
//        Vector3d v = new Vector3d(vec.x, vec.y, vec.z);
//        matrix.getColumn(0, v1);
//        matrix.getColumn(0, v2);
//        matrix.getColumn(0, v3);
//        float x = (float) v1.dot(v);
//        float y = (float) v2.dot(v);
//        float z = (float) v3.dot(v);
        Vector3f source = new Vector3f(vec.x, vec.y, vec.z);
        Vector3f row1 = new Vector3f();
        Vector3f row2 = new Vector3f();
        Vector3f row3 = new Vector3f();
        R.getRow(0, row1);
        R.getRow(1, row2);
        R.getRow(2, row3);
//        Vector3f result = new Vector3f();


        Vector3 vec_transf = new Vector3(row1.dot(source),row2.dot(source),row3.dot(source));
        return vec_transf;
    }




    public void checkButton(View v){
        int radioId = radioGroup.getCheckedRadioButtonId();
        radioButton = findViewById(radioId);
        Toast.makeText(this,"Select" + radioButton.getText(), Toast.LENGTH_SHORT).show();
    }

    public void LookforAnchor_realtime(String nextAnchorID){
        // Do we need startNewSession?
        cloudAnchorManager.stop();
        cloudAnchorManager.reset();
        startNewSession();
        anchorID = nextAnchorID;
        AnchorLocateCriteria criteria = new AnchorLocateCriteria();
        //criteria.setBypassCache(true);
        //不规定而是找到最近的anchor
        //String EMPTY_STRING = "";

        criteria.setIdentifiers(new String[]{anchorID});
        // Cannot run more than one watcher concurrently
        stopWatcher();
        cloudAnchorManager.startLocating(criteria);
        runOnUiThread(() -> {
            actionButton.setVisibility(View.INVISIBLE);
            statusText.setText("Look for anchor");
        });
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
                    Toast.makeText(this, "No Read Permission!", Toast.LENGTH_LONG).show();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_SAVE_CONTACTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    //change for further improvement
                    advanceDemo();
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    Toast.makeText(this, "No Save Permission!", Toast.LENGTH_LONG).show();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                break;

            }


            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    public String toNextAnchor(Stack stack_path){
        if(!stack_path.isEmpty()){
            return (String) stack_path.pop();
        }
        else
            return "Empty";
    }

//    public boolean navigationProcess(ArrayList<String> optPath, int idx){
//
//        while(!reachTarget){
//        }
//        while(!reachTarget){
//
////                 test continuous navigation
////                Iterator it = anchorNamesIdentifier.keySet().iterator();
////                it.next();
////                AnchorVisual tempAnchor = anchorVisuals.get(anchorNamesIdentifier.get(it.next()));
////                AnchorVisual targetAnchorVisual = tempAnchor;
//            idx ++;
//
//            // Vector3 targetPosition = anchorMap.getTransformation();
//
////            AnchorVisual targetAnchorVisual = anchorVisuals.get(anchorNamesIdentifier.get("Anchor 2"));
////            targetAnchor = targetAnchorVisual.getAnchorNode();
//            //arrow.updateTargetAnchor(targetAnchorVisual.getAnchorNode());
//
//
//            //targetAnchorVisual.render(arFragment);
//            //end navigation button
//            runOnUiThread(() -> {
//                statusText.setText("");
//                backButton.setVisibility(View.VISIBLE);
//                actionButton.setVisibility(View.INVISIBLE);
//                radioGroup.setVisibility(View.INVISIBLE);
//                textView.setVisibility(View.INVISIBLE);
//                navigateButton.setVisibility(View.INVISIBLE);
//            });
//        }
//
//    }

    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri = null;
            if (resultData != null) {
                pickit.getPath(resultData.getData(), Build.VERSION.SDK_INT);		                uri = resultData.getData();
            }
            else
            {
                Toast.makeText(this, "Error: content is Null! please reload!", Toast.LENGTH_SHORT).show();
                Log.d("LoadMap",":selected file is invalid.");
                return;
            }
        }
        else {
            Toast.makeText(this, "Error: Intent response failed!", Toast.LENGTH_SHORT).show();
            Log.d("LoadMap", ":Load Intent response failed.");
        }
    }
    //@following three functions are override of PickiT that get real path from uri
    //@following three functions are override of PickiT that get real path from uri
    @Override
    public void PickiTonStartListener() {

    }

    @Override
    public void PickiTonProgressUpdate(int progress) {

    }

    @Override
    public void PickiTonCompleteListener(String path, boolean wasDriveFile, boolean wasUnknownProvider, boolean wasSuccessful, String Reason) {

        //  Check if it was a Drive/local/unknown provider file and display a Toast
        if (wasDriveFile){
            Toast.makeText(this, "Drive file was selected", Toast.LENGTH_LONG).show();
        }else if (wasUnknownProvider){
            Toast.makeText(this, "File was selected from unknown provider", Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Local file was selected", Toast.LENGTH_LONG).show();
        }

        //check if text read successful
        if (wasSuccessful) {
            //  Set returned path to TextView
            FileManager file = new FileManager();
            AnchorMap LoadMap = file.loadMap(path);
            if(LoadMap !=null) {
                anchorMap = LoadMap;
                Toast.makeText(this, "Load map from: "+path, Toast.LENGTH_LONG).show();

                // Permission has already been granted
                currentDemoStep = DemoStep.ChooseStartPoint;

                //set button temporal invisiable
                actionButton.setVisibility(View.VISIBLE);
                statusText.setText("Map Loaded. Start Navigation");
                // Permission has already been granted

            }
        }else {
            Toast.makeText(this, "Cannot read the map file!", Toast.LENGTH_SHORT).show();
            Log.d("LoadMapFail"," :"+Toast.LENGTH_LONG);
        }
    }

    //delete temporal files if it is read from unknown sources or provider
    @Override
    public void onBackPressed() {
        pickit.deleteTemporaryFile();
        super.onBackPressed();
    }

    enum DemoStep {
        Start,                          ///< the start of the demo
        CreateLocalAnchor,      ///< the session will create a local anchor
        SaveCloudAnchor,        ///< the session will save the cloud anchor
        SavingCloudAnchor,      ///< the session is in the process of saving the cloud anchor
        CreateSessionForQuery,  ///< a session will be created to query for an anchor
        LookForAnchor,          ///< the session will run the query
        LookForNearbyAnchors,   ///< the session will run a query for nearby anchors
        LoadMap,                ///< load map
        ChooseStartPoint,
        NavigationStart,        ///< the session will run for navigation
        NavigationEnd,          ///< the navigation is end
        End,                            ///< the end of the demo
        Restart,                        ///< waiting to restart
    }
    enum NodeType {             ///< classify nodes into 3 types
        Major,                  ///< node that represents important and meaningful location
        Minor,                  ///< node that used for tracking and accuracy improve.
        Cross,                  ///< node where new graph branch is generated
    }
}