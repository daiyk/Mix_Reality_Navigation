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

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3f;

public class AzureSpatialAnchorsActivity extends AppCompatActivity implements PickiTCallbacks {
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 101;
    private static final int READ_REQUEST_CODE = 102;
    private static final int MY_PERMISSIONS_REQUEST_SAVE_CONTACTS = 103;
    private String anchorID;
    private String startAnchorID;
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
    private float anchorBoaradScale = 0.5f;

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
    private Vector3 camRelPose = new Vector3(0.0f, 0.2f, -1.5f);
    private float distance;
    private AnchorNode sourceAnchorNode = new AnchorNode();
    private Pose sourceMapPos;
    private Pose sourceAnchorPos;


    private AnchorMap anchorMap;
    ArrayList<String> optPath = new ArrayList<>();
    Stack<String> stack_id = new Stack<>();

    private String sourceName = null;
    private String targetName = null;
    private boolean reachTarget = false;
    private PickiT pickit;

    private final LinkedHashMap<String, String> anchorNamesIdentifier = new LinkedHashMap<>();

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

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.setOnTapArPlaneListener(this::onTapArPlaneListener);

        sceneView = arFragment.getArSceneView();

        Scene scene = sceneView.getScene();

        //set arrow
        arrow = new AnchorArrow(this, camRelPose, arFragment.getTransformationSystem(), scene);
        scene.getCamera().addChild(arrow);
        arrow.setEnabled(false);

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

        radioGroup = findViewById(R.id.radioGroup);
        textView = findViewById(R.id.anchor_Selected);
        navigateButton = findViewById(R.id.navigate);
        spinner = findViewById(R.id.spinner);
        navigateButton.setOnClickListener((View v) -> onClickNavigateButton());

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

            case LookForNearbyAnchors:
                if (anchorVisuals.isEmpty() || !anchorVisuals.containsKey(anchorID)) {
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
                    if (!map_dir.exists()) {
                        map_dir.mkdirs();
                        Toast.makeText(this, "No Map file found in this device!", Toast.LENGTH_LONG).show();
                    }
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    Uri uri = Uri.parse(map_path);

                    intent.setDataAndType(uri, "*/*");
                    startActivityForResult(intent, READ_REQUEST_CODE);
                }

                break;
            case ChooseStartPoint:

                ArrayList<Node> nodelist = anchorMap.getNodeList();
                ArrayList<String> anchorlist = new ArrayList<String>();

                int n = 0;
                while (n < nodelist.size()) {
                    anchorlist.add(nodelist.get(n).AnchorName);
                    n++;
                }
                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this,
                        android.R.layout.simple_spinner_item, anchorlist);
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(spinnerAdapter);
                spinnerAdapter.notifyDataSetChanged();

                runOnUiThread(() -> {
                    actionButton.setText("Select Start Point");
                    statusText.setText("");
                    backButton.setVisibility(View.VISIBLE);

                    radioGroup.setVisibility(View.INVISIBLE);
                    textView.setVisibility(View.INVISIBLE);
                    navigateButton.setVisibility(View.VISIBLE);
                    navigateButton.setText("choose where you are");
                    spinner.setVisibility(View.VISIBLE);
                });
                break;

            case LookForAnchor:
                // We need to restart the session to find anchors we created.
                startNewSession();

                AnchorLocateCriteria criteria = new AnchorLocateCriteria();
                //criteria.setBypassCache(true);
                //不规定而是找到最近的anchor
                //String EMPTY_STRING = "";
                criteria.setIdentifiers(new String[]{startAnchorID});

                // Cannot run more than one watcher concurrently
                stopWatcher();

                cloudAnchorManager.startLocating(criteria);

                runOnUiThread(() -> {
                    actionButton.setVisibility(View.INVISIBLE);
                    statusText.setText("Look for anchor at your position, please walk around......");
                });

                break;

            case NavigationStart:
                //set target anchor

            case NavigationEnd:
                if (targetName == null) {
                    Toast.makeText(this, "\"ERROR: No target Selected!\"", Toast.LENGTH_LONG)
                            .show();
                    finish();
                }
                // Get Optimal path for navigation
                anchorMap.searchSP(sourceName, targetName, optPath);


                Stack<String> stack_path = new Stack<>();

                for (int i = optPath.size() - 1; i >= 0; i--) {
                    stack_path.push(anchorMap.getNode(optPath.get(i)).AnchorName);
                    stack_id.push(anchorMap.getNode(optPath.get(i)).AnchorID);
                }

                // Init as first direction
                final String nextAnchorName = toNextAnchor(stack_path);
                sourceMapPos = anchorMap.getPos(sourceName);
                sourceAnchorPos = sourceAnchorNode.getAnchor().getPose();
                sourceMapPos = sourceMapPos.inverse();
                final Pose[] nextAnchorPos = {anchorMap.getPos(nextAnchorName)};
                final float[][] nextAnchorTranslationMap = {nextAnchorPos[0].getTranslation()};
                final float[][] nextAnchorTranslation = {sourceAnchorPos.transformPoint(sourceMapPos.transformPoint(nextAnchorTranslationMap[0]))};
                if(!arrow.isEnabled()) {
                    arrow.setEnabled(true);
                }
                final Vector3[] nextAnchorTransf = {new Vector3(nextAnchorTranslation[0][0], nextAnchorTranslation[0][1], nextAnchorTranslation[0][2])};//{getTransformedCoordinates(rotationMatrix, anchorMap.getPos(nextAnchorName))};//{Vector3.add(getTransformedCoordinates(rotationMatrix, anchorMap.getEdge(sourceName,nextAnchorName)),current_worldcoord)};


                arrow.setEnabled(true);
                arrow.updateTargetPos(nextAnchorTransf[0]);

                Scene scene = sceneView.getScene();
                scene.addOnUpdateListener(frameTime -> {

                    Vector3 targetPosition = nextAnchorTransf[0];//temptargetAnchor.getWorldPosition();
                    Vector3 cameraPosition = sceneView.getScene().getCamera().getWorldPosition();
                    //distance = (float) ( Math.abs(Math.sqrt(targetPosition.x * targetPosition.x + targetPosition.z * targetPosition.z)-
                    //        Math.sqrt(cameraPosition.x * cameraPosition.x + cameraPosition.z * cameraPosition.z)));
                    distance = (float) Math.sqrt((targetPosition.x - cameraPosition.x) * (targetPosition.x - cameraPosition.x) +
                            (targetPosition.z - cameraPosition.z) * (targetPosition.z - cameraPosition.z));
                    statusText.setText(String.valueOf(distance));

                    if (distance < 0.3) {
                        String nextAnchorName_update = toNextAnchor(stack_path);
                        if (nextAnchorName_update != "Empty") {
                            nextAnchorPos[0] = anchorMap.getPos(nextAnchorName_update);
                            nextAnchorTranslationMap[0] = nextAnchorPos[0].getTranslation();
                            nextAnchorTranslation[0] = sourceAnchorPos.transformPoint(sourceMapPos.transformPoint(nextAnchorTranslationMap[0]));
                            nextAnchorTransf[0] = new Vector3(nextAnchorTranslation[0][0], nextAnchorTranslation[0][1], nextAnchorTranslation[0][2]);

                        } else {
                            statusText.setText("Reach Final Destination");
                            actionButton.setVisibility(View.VISIBLE);
                            actionButton.setText("End Navigation");

                        }
                        arrow.updateTargetPos(nextAnchorTransf[0]);
                    }

                    if (reachTarget) {
                        runOnUiThread(() -> {
                            actionButton.setText("End Navigation");
                            statusText.setText("Reach Final Destination");
                            backButton.setVisibility(View.VISIBLE);
                            navigateButton.setVisibility(View.INVISIBLE);
                        });
                    }
                });


                currentDemoStep = DemoStep.End;
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
        } else {
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
            } else {
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
        // Here we only look for the source Anchor
        if (currentDemoStep == DemoStep.LookForAnchor) {
//            stopWatcher();
            runOnUiThread(() -> {
                statusText.setText("Source Anchor located! please choose target from the list");
                actionButton.setVisibility(View.INVISIBLE);
                navigateButton.setVisibility(View.VISIBLE);
                navigateButton.setText("confirm");
                spinner.setVisibility(View.VISIBLE);
            });
//            currentDemoStep = DemoStep.NavigationStart;
            return;
        }

        // 后续需要real-time locate
        if (currentDemoStep == DemoStep.NavigationEnd) {
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
        if (currentDemoStep == DemoStep.LookForAnchor) {
            AnchorVisual foundVisual = new AnchorVisual(anchor.getLocalAnchor());
//            anchorVisuals.put("", foundVisual);

            foundVisual.setCloudAnchor(anchor);
            foundVisual.getAnchorNode().setParent(arFragment.getArSceneView().getScene());

            foundVisual.setColor(foundColor);
            Vector3 localPos = new Vector3(0.0f, anchorBoaradScale * 0.55f, 0.0f);
            AnchorBoard anchorBoard = new AnchorBoard(this, sourceName, 0.5f, localPos);
            anchorBoard.setParent(foundVisual.getAnchorNode());
            foundVisual.render(arFragment);

            //store the source anchor
            sourceAnchorNode = foundVisual.getAnchorNode();

        } else if (currentDemoStep == DemoStep.NavigationStart) {
            // Render anchors during the navigation process, can be deleted later
            AnchorVisual foundVisual = new AnchorVisual(anchor.getLocalAnchor());
            foundVisual.render(arFragment);
            // Here assign located anchor to temptargetAnchor, can put somewhere else later
        } else if (currentDemoStep == DemoStep.NavigationEnd) {
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
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText("Load Map");

            navigateButton.setVisibility(View.INVISIBLE);
            radioGroup.setVisibility(View.INVISIBLE);
            textView.setVisibility(View.INVISIBLE);
            spinner.setVisibility(View.INVISIBLE);
        });
        currentDemoStep = DemoStep.LoadMap;
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

    private void onClickNavigateButton() {
        if (currentDemoStep == DemoStep.ChooseStartPoint) {

            // Use Spinner
            sourceName = spinner.getSelectedItem().toString();
            startAnchorID = anchorMap.getNode(sourceName).AnchorID;
            runOnUiThread(() -> {
                actionButton.setVisibility(View.INVISIBLE);
                spinner.setVisibility(View.INVISIBLE);
                navigateButton.setVisibility(View.INVISIBLE);
            });
            currentDemoStep = DemoStep.LookForAnchor;
            advanceDemo();
        }
//        if (currentDemoStep == DemoStep.NavigationStart){
        else if (currentDemoStep == DemoStep.LookForAnchor) {
            targetName = spinner.getSelectedItem().toString();
            runOnUiThread(() -> {
                statusText.setText("target place selected, navigation start soon");
                spinner.setVisibility(View.INVISIBLE);
                navigateButton.setVisibility(View.INVISIBLE);

            });
            currentDemoStep = DemoStep.NavigationStart;
            advanceDemo();
        }

    }

    private void onTapListener(HitTestResult hitTestResult, MotionEvent motionEvent) {

        if (currentDemoStep == DemoStep.NavigationStart) {
            runOnUiThread(() -> {
                radioGroup.setVisibility(View.INVISIBLE);
                textView.setVisibility(View.VISIBLE);
                navigateButton.setVisibility(View.VISIBLE);
                textView.setText("");
                spinner.setVisibility(View.VISIBLE);
            });
        }
    }


    public void checkButton(View v) {
        int radioId = radioGroup.getCheckedRadioButtonId();
        radioButton = findViewById(radioId);
        Toast.makeText(this, "Select" + radioButton.getText(), Toast.LENGTH_SHORT).show();
    }

    public void LookforAnchor_realtime(String nextAnchorID) {
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
        }
    }

    public String toNextAnchor(Stack stack_path) {
        if (!stack_path.isEmpty()) {
            return (String) stack_path.pop();
        } else
            return "Empty";
    }

    //************* activity processor for load map function ****************************//
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
            if (resultData != null) {
                pickit.getPath(resultData.getData(), Build.VERSION.SDK_INT); // use pickit callback
            } else {
                Toast.makeText(this, "Error: content is Null! please reload!", Toast.LENGTH_SHORT).show();
                Log.d("LoadMap", ":selected file is invalid.");
                return;
            }
        } else {
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
        if (wasDriveFile) {
            Toast.makeText(this, "Drive file was selected", Toast.LENGTH_LONG).show();
        } else if (wasUnknownProvider) {
            Toast.makeText(this, "File was selected from unknown provider", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Local file was selected", Toast.LENGTH_LONG).show();
        }

        //check if text read successful
        if (wasSuccessful) {
            //  Set returned path to TextView
            FileManager file = new FileManager();
            AnchorMap LoadMap = file.loadMap(path);
            if (LoadMap != null) {
                anchorMap = LoadMap;
                Toast.makeText(this, "Load map from: " + path, Toast.LENGTH_LONG).show();

                // Permission has already been granted
                currentDemoStep = DemoStep.ChooseStartPoint;

                //set button temporal invisiable
                actionButton.setVisibility(View.VISIBLE);;
                statusText.setText("Map Loaded. Start Navigation");
                // Permission has already been granted

            }
        } else {
            Toast.makeText(this, "Cannot read the map file!", Toast.LENGTH_SHORT).show();
            Log.d("LoadMapFail", " :" + Toast.LENGTH_LONG);
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