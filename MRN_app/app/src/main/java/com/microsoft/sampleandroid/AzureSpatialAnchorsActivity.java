// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package com.microsoft.sampleandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Pose;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;

//third-party lib to pick real path from url
import com.hbisoft.pickit.PickiT;
import com.hbisoft.pickit.PickiTCallbacks;

import com.microsoft.azure.spatialanchors.AnchorLocateCriteria;
import com.microsoft.azure.spatialanchors.AnchorLocatedEvent;
import com.microsoft.azure.spatialanchors.CloudSpatialAnchor;
import com.microsoft.azure.spatialanchors.LocateAnchorStatus;
import com.microsoft.azure.spatialanchors.LocateAnchorsCompletedEvent;
import com.microsoft.azure.spatialanchors.NearAnchorCriteria;
import com.microsoft.azure.spatialanchors.SessionUpdatedEvent;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;


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
    private final Object progressLock = new Object();
    private final Object renderLock = new Object();

    // Materials
    private static Material failedColor;
    private static Material foundColor;
    private static Material readyColor;
    private static Material savedColor;
    private static Material targetColor;

    // UI Elements
    private ArFragment arFragment;
    private Button actionButton;
    private Button backButton;
    private TextView scanProgressText;
    private ArSceneView sceneView;
    private TextView statusText;
    private AnchorArrow arrow;

    //layout element
    private RadioGroup radioGroup;
    private RadioButton radioButton;
    private TextView textView;
    private Button navigateButton;
    private Spinner spinner;
    private final Vector3 camRelPose = new Vector3(0.0f, 0.2f, -1.5f);
    private final Vector3 boardLocalPos = new Vector3(0.0f, 0.3f, 0.0f);

    //navigation relevent
    private float distance;
    private final float NAVI_LIMIT = 1.5f;
    private AnchorNode sourceAnchorNode = new AnchorNode();
    private boolean navigationInit;
    private AnchorMap anchorMap;
    ArrayList<String> optPath = new ArrayList<>();
    Stack<String> stack_id = new Stack<>();
    private String sourceName = null;
    private String targetName = null;
    private PickiT pickit;


    //back button detector
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

        sceneView = arFragment.getArSceneView();

        Scene scene = sceneView.getScene();

        //set arrow
        arrow = new AnchorArrow(this, camRelPose, arFragment.getTransformationSystem(),scene);
        scene.getCamera().addChild(arrow);
        arrow.setEnabled(false);
        arrow.setTargetEnabled(false);

        scene.addOnUpdateListener(
                new Scene.OnUpdateListener()
                {
                    @Override
                    public void onUpdate(FrameTime Frame)
                    {
                        if (cloudAnchorManager != null) {
                            // Pass frames to Spatial Anchors for processing.
                            cloudAnchorManager.update(sceneView.getArFrame());
                        }
                        if (currentDemoStep == DemoStep.NavigationStart) {
                            Vector3 cameraPosition = sceneView.getScene().getCamera().getWorldPosition();

                            Vector3 targetPosition = arrow.getTargetPos();
                            distance = (float) ( Math.sqrt(targetPosition.x * targetPosition.x + targetPosition.z * targetPosition.z)-
                                    Math.sqrt(cameraPosition.x * cameraPosition.x + cameraPosition.z * cameraPosition.z));
                            if(statusText.getVisibility()==statusText.INVISIBLE) {
                                statusText.setVisibility(View.VISIBLE);
                            }
                            statusText.setText(String.valueOf(distance));
//                            statusText.setText(String.format("%f, %f, %f",targetPosition.x,targetPosition.y,targetPosition.z));
                            if (distance < NAVI_LIMIT ) {
                                advanceDemo();
                            }
                        }
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
        backButton.setVisibility(View.VISIBLE);
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
        MaterialFactory.makeOpaqueWithColor(this, new Color(ContextCompat.getColor(this, R.color.SeaGreen)))
        .thenAccept(material -> {
                    targetColor=material;
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

                }
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


                break;

            case ChooseStartPoint:

                ArrayList<Node> nodelist = anchorMap.getNodeList();
                ArrayList<String> anchorlist = new ArrayList<String>();
                backButton.setVisibility(View.GONE);
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
                    actionButton.setVisibility(View.INVISIBLE);
                    statusText.setText("Select Start Point");

                    navigateButton.setVisibility(View.VISIBLE);
                    navigateButton.setText("Confirm");
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
                    statusText.setText("Look for anchor at your position, please walk around......");
                });

                break;
            case ChooseEndPoint:
                if(actionButton.getVisibility()==View.VISIBLE) {
                    actionButton.setVisibility(View.INVISIBLE);
                }
                navigateButton.setVisibility(View.VISIBLE);
                navigateButton.setText("Confirm");
                spinner.setVisibility(View.VISIBLE);

                break;
            case NavigationStart:
                //test target anchor

                if(navigationInit) {
                    if (targetName == null) {
                        Toast.makeText(this, "\"ERROR: No target Selected!\"", Toast.LENGTH_LONG)
                                .show();
                        // re-choose from list
                        runOnUiThread(() -> {
                            statusText.setText("please choose target from the list");
                            actionButton.setVisibility(View.INVISIBLE);
                            navigateButton.setVisibility(View.VISIBLE);
                            navigateButton.setText("Confirm");
                            spinner.setVisibility(View.VISIBLE);
                        });
                        return;
                    }
                    // Get Optimal path for navigation
                    anchorMap.searchSP(sourceName, targetName, optPath);
                    for (int i = optPath.size() - 1; i >= 0; i--) {
                        stack_id.push(anchorMap.getNode(optPath.get(i)).AnchorName);
                    }
                    navigationInit = false;
                    //init arrow target
                    Renderable nodeRenderable = ShapeFactory.makeSphere(0.08f, new Vector3(0.0f, 0.15f, 0.0f), readyColor);
                    arrow.setTargetRenderable(nodeRenderable);

//                    advanceDemo();
//                    return;
                }

                Pose sourceMapPos = anchorMap.getPos(sourceName);

                Pose sourceAnchorPos = sourceAnchorNode.getAnchor().getPose();
                sourceMapPos = sourceMapPos.inverse();
                if(!stack_id.isEmpty()){
                    //compute the position of next anchor
                    String nextTarget = stack_id.pop();
                    Pose nextAnchorPos = anchorMap.getPos(nextTarget);
                    float[] nextAnchorTranslationMap = nextAnchorPos.getTranslation();
                    float[] nextAnchorTranslation = sourceAnchorPos.transformPoint(sourceMapPos.transformPoint(nextAnchorTranslationMap));

                    arrow.updateTargetPos(new Vector3(nextAnchorTranslation[0],nextAnchorTranslation[1],nextAnchorTranslation[2]));
                    arrow.updateTargetBoard(nextTarget);
                    if(!arrow.isEnabled()) {
                        arrow.setEnabled(true);
                        arrow.setTargetEnabled(true);
                    }

                    // render the map next target as sphere and hold it in anchorvisuals
//                    startNewSession();
                    //use localizer to find the anchor and thus improve the accuracy
                    if(stack_id.isEmpty()) {
                        Renderable render = ShapeFactory.makeCylinder(0.2f, 0.5f, new Vector3(0.f, 0.25f, 0.f), targetColor);
                        arrow.updateTargetBoard("Your destination: " + nextTarget);
                        arrow.setTargetRenderable(render);
                    }
                    AnchorLocateCriteria nextCriteria = new AnchorLocateCriteria();
                    //criteria.setBypassCache(true);
                    //不规定而是找到最近的anchor
                    nextCriteria.setIdentifiers(new String[]{anchorMap.getNode(nextTarget).AnchorID});

                    // Cannot run more than one watcher concurrently
                    stopWatcher();

                    cloudAnchorManager.startLocating(nextCriteria);
                    break;
                }
                else{
                    // if empty then target is reached
                    //render targetnode
                    //create renderable for target shape
                    if (!arrow.isTargetEnabled())
                    {
                        arrow.setTargetEnabled(true);
                    }
                    currentDemoStep = DemoStep.NavigationEnd;
                    actionButton.setVisibility(View.VISIBLE);
                    statusText.setText("Target reached! Press to exit navigation");
                    actionButton.setText("End Navigation");
                    break;
                }

            case NavigationEnd:
                arrow.setTargetEnabled(false);
                arrow.setEnabled(false);
                //no need to continue search
                stopWatcher();
                currentDemoStep = DemoStep.End;
                if(actionButton.getVisibility()==View.INVISIBLE){
                    actionButton.setVisibility(View.VISIBLE);
                }
                actionButton.setText("Press to Refresh");

                break;
            case End:
//                for (AnchorVisual toDeleteVisual : anchorVisuals.values()) {
//                    cloudAnchorManager.deleteAnchorAsync(toDeleteVisual.getCloudAnchor());
//                }
                arrow.clear();
                destroySession();
                anchorMap.destory();

                runOnUiThread(() -> {
                    actionButton.setText("Restart");
                    statusText.setText("");
                    backButton.setVisibility(View.VISIBLE);
                });
                sourceAnchorNode = new AnchorNode();
                optPath = new ArrayList<>();
                stack_id = new Stack<>();
                sourceName = null;
                targetName = null;
                currentDemoStep = DemoStep.Restart;
                clearVisuals();
                break;

            case Restart:
                startDemo();
                break;
        }
    }


    private void clearVisuals() {
        for (AnchorVisual visual : anchorVisuals.values()) {
            arFragment.getArSceneView().getScene().removeChild(visual.getAnchorNode());
            visual.getLocalAnchor().detach();
            visual.getAnchorNode().setParent(null);
            visual.destroy();
        }
        anchorVisuals.clear();
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
                    if(currentDemoStep==DemoStep.LookForAnchor) {
                        statusText.setText("Source anchor found!");
                        Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            public void run() {
                                // Actions to do after 10 seconds
                            }
                        }, 2000);
                    }
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
                statusText.setText("Next choose and confirm the target place");
                if(actionButton.getVisibility()==View.INVISIBLE){
                    actionButton.setVisibility(View.VISIBLE);
                }
                //set button temporal invisiable
                currentDemoStep = DemoStep.ChooseEndPoint;
                actionButton.setText("choose target place");
                navigateButton.setVisibility(View.INVISIBLE);


            });
            return;
        }
        if (currentDemoStep == DemoStep.NavigationStart) {
//            runOnUiThread(() -> {
//                Toast.makeText(this, "Find one", Toast.LENGTH_SHORT).show();
//            });
            return;
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

    private void renderLocatedAnchor(CloudSpatialAnchor anchor) {
        if (currentDemoStep == DemoStep.LookForAnchor) {
            AnchorVisual foundVisual = new AnchorVisual(anchor.getLocalAnchor());
//            temptargetAnchor = foundVisual.getAnchorNode();
            anchorVisuals.put("", foundVisual);
            foundVisual.setCloudAnchor(anchor);
            foundVisual.getAnchorNode().setParent(arFragment.getArSceneView().getScene());

            foundVisual.setColor(foundColor);
            AnchorBoard anchorBoard = new AnchorBoard(this, sourceName, 0.5f, boardLocalPos);
            anchorBoard.setParent(foundVisual.getAnchorNode());
            foundVisual.render(arFragment);

            //store the source anchor
            sourceAnchorNode = foundVisual.getAnchorNode();

        } else if (currentDemoStep == DemoStep.NavigationStart) {
            // Render anchors during the navigation process, can be deleted later
            AnchorVisual foundVisual = new AnchorVisual(anchor.getLocalAnchor());
            anchorVisuals.put(anchor.getIdentifier(),foundVisual);

            float[] nextLocateAnchor = anchor.getLocalAnchor().getPose().getTranslation();
            arrow.updateTargetPos(new Vector3(nextLocateAnchor[0],nextLocateAnchor[1],nextLocateAnchor[2]));

        } else if (currentDemoStep == DemoStep.NavigationEnd) {
            AnchorVisual foundVisual = new AnchorVisual(anchor.getLocalAnchor());
            anchorVisuals.put(anchor.getIdentifier(),foundVisual);
            float[] nextLocateAnchor = foundVisual.getAnchorNode().getAnchor().getPose().getTranslation();
            arrow.updateTargetPos(new Vector3(nextLocateAnchor[0],nextLocateAnchor[1],nextLocateAnchor[2]));
        }
    }

    private void startDemo() {
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
        else if (currentDemoStep == DemoStep.ChooseEndPoint) {
            targetName = spinner.getSelectedItem().toString();
            runOnUiThread(() -> {
                statusText.setText("target place selected, navigation start soon");
                spinner.setVisibility(View.INVISIBLE);
                navigateButton.setVisibility(View.INVISIBLE);

            });
            currentDemoStep = DemoStep.NavigationStart;

            //init navigation process
            navigationInit =true;
            advanceDemo();
        }

    }

    public void checkButton(View v) {
        int radioId = radioGroup.getCheckedRadioButtonId();
        radioButton = findViewById(radioId);
        Toast.makeText(this, "Select" + radioButton.getText(), Toast.LENGTH_SHORT).show();
    }

    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

//                    advanceDemo();
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    Toast.makeText(this, "No Read Permission!", Toast.LENGTH_LONG).show();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    finish();
                }
                break;
            }
            case MY_PERMISSIONS_REQUEST_SAVE_CONTACTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    //change for further improvement
//                    advanceDemo();
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    Toast.makeText(this, "No Save Permission!", Toast.LENGTH_LONG).show();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    finish();
                }
                break;

            }
        }
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
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText("choose start point");
                statusText.setText("Map Loaded");
                // Permission has already been granted

            }
        } else {
            Toast.makeText(this, "Cannot read the map file! \n Please check and reload!", Toast.LENGTH_SHORT).show();
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
        SaveCloudAnchor,        ///< the session will save the cloud anchor
        LookForAnchor,          ///< the session will run the query
        LookForNearbyAnchors,   ///< the session will run a query for nearby anchors
        LoadMap,                ///< load map
        ChooseStartPoint,
        ChooseEndPoint,
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



//
//for (AnchorVisual visuals : anchorVisuals.values()){
//        visuals.getAnchorNode().setOnTapListener(this::onTapListener);
//        }
//        break;

//  ********onLocatedCompelete()***************
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

//    public static Matrix3f getTransformationMatrix(Vector3f vec1, Vector3f vec2) {
//
//        // vec1.normalize();
//        // vec2.normalize();
//        Vector3f v = new Vector3f();
//        v.cross(vec1, vec2);
//        float sinAngle = v.length();
//        float c = vec1.dot(vec2);
//
//        //build matrix
//        Matrix3f u = new Matrix3f(0.f, -v.z, v.y, v.z, 0.f, -v.x, -v.y, v.x, 0.f);
//        Matrix3f u2 = new Matrix3f();
//        u2.mul(u, u);
//
//        //coeff
//        float coeff = 1.f / (1 + c);
//        Matrix3f I = new Matrix3f();
//        I.setIdentity();
//        u.add(I);//I + u
//        u2.mul(coeff);//u*c
//        u.add(u2);//u+u2
//
//        return u;
//    }
//
//    public double[][] getTransformationMatrix(Vector3 vec1, Vector3 vec2) {
//
//        Vector3 v = Vector3.cross(vec1, vec2);
//        float s = (float) Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
//        float c = Vector3.dot(vec1, vec2);
//        float scale = (float) 1 / (1 + c);//(1-c)/(s*s);
//
//        Matrix3d vx = new Matrix3d(0, -v.z, v.y, v.z, 0, -v.x, -v.y, v.x, 0);
//        Matrix3d eye = new Matrix3d(1, 0, 0, 0, 1, 0, 0, 0, 1);
//        Matrix3d vx2 = new Matrix3d();
//        vx2.mul(vx, vx);
//        vx2.mul(scale);
//        vx.add(vx2);
//        Matrix3d rotationMatrix = new Matrix3d();
//        rotationMatrix.add(eye, vx);
//
//        double[][] R = new double[3][3];
//        R[0][0] = 1 + 0 + scale * (-v.z * v.z - v.y * v.y);
//        R[0][1] = 0 + (-v.z) + scale * (v.x * v.y);
//        R[0][2] = 0 + v.y + scale * (v.x * v.z);
//
//        R[1][0] = 0 + v.z + scale * (v.x * v.y);
//        R[1][1] = 1 + 0 + scale * (-v.z * v.z - v.x * v.x);
//        R[1][2] = 0 + (-v.x) + scale * (v.y * v.z);
//
//        R[2][0] = 0 + (-v.y) + scale * (v.x * v.z);
//        R[2][1] = 0 + v.x + scale * (v.y * v.z);
//        R[2][2] = 1 + 0 + scale * (-v.y * v.y - v.x * v.x);
//        return R;
//    }
//
//    public Vector3 getTransformedCoordinates(double[][] matrix, Vector3 vec) {
//        float x = (float) (matrix[0][0] * vec.x + matrix[0][1] * vec.y + matrix[0][2] * vec.z);
//        float y = (float) (matrix[1][0] * vec.x + matrix[1][1] * vec.y + matrix[1][2] * vec.z);
//        float z = (float) (matrix[2][0] * vec.x + matrix[2][1] * vec.y + matrix[2][2] * vec.z);
////        Vector3d v1 = new Vector3d();
////        Vector3d v2 = new Vector3d();
////        Vector3d v3 = new Vector3d();
////        Vector3d v = new Vector3d(vec.x, vec.y, vec.z);
////        matrix.getColumn(0, v1);
////        matrix.getColumn(0, v2);
////        matrix.getColumn(0, v3);
////        float x = (float) v1.dot(v);
////        float y = (float) v2.dot(v);
////        float z = (float) v3.dot(v);
//        Vector3 vec_transf = new Vector3(x, y, z);
//        return vec_transf;
//    }

//case CreateSessionForQuery:
//        cloudAnchorManager.stop();
//        cloudAnchorManager.reset();
//        clearVisuals();
//
//        runOnUiThread(() -> {
//        statusText.setText("");
//        actionButton.setText("Locate anchor");
//        });
//
//        currentDemoStep = DemoStep.LookForAnchor;
//
//        break;

//    public void LookforAnchor_realtime(String nextAnchorID) {
//        // Do we need startNewSession?
//        cloudAnchorManager.stop();
//        cloudAnchorManager.reset();
//        startNewSession();
//        anchorID = nextAnchorID;
//        AnchorLocateCriteria criteria = new AnchorLocateCriteria();
//        //criteria.setBypassCache(true);
//        //不规定而是找到最近的anchor
//        //String EMPTY_STRING = "";
//
//        criteria.setIdentifiers(new String[]{anchorID});
//        // Cannot run more than one watcher concurrently
//        stopWatcher();
//        cloudAnchorManager.startLocating(criteria);
//        runOnUiThread(() -> {
//            actionButton.setVisibility(View.INVISIBLE);
//            statusText.setText("Look for anchor");
//        });
//    }