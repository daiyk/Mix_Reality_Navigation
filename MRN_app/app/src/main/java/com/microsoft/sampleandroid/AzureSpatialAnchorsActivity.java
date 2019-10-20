// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package com.microsoft.sampleandroid;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class AzureSpatialAnchorsActivity extends AppCompatActivity
{
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

    private RadioGroup radioGroup;
    private RadioButton radioButton;
    private TextView textView;
    private Button navigateButton;

    private final HashMap<String,String> anchorNamesIdentifier = new HashMap<>();
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
        Vector3 camRelPose = new Vector3(0.0f, 0.55f, -1.0f);
        AnchorBoard cameraboard = new AnchorBoard(this, anchorCamera,0.5f,camRelPose);
        scene.getCamera().addChild(cameraboard);

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
            case NavigationStart:
                for (AnchorVisual visuals : anchorVisuals.values()){
                    visuals.getAnchorNode().setOnTapListener(this::onTapListener);
                }

                break;

            case End:
                for (AnchorVisual toDeleteVisual : anchorVisuals.values()) {
                    cloudAnchorManager.deleteAnchorAsync(toDeleteVisual.getCloudAnchor());
                }

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

    private void onAnchorLocated(AnchorLocatedEvent event) {
        LocateAnchorStatus status = event.getStatus();

        runOnUiThread(() -> {
            switch (status) {
                case AlreadyTracked:
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
        runOnUiThread(() -> statusText.setText("Source Anchor located!"));

        if (!basicDemo && currentDemoStep == DemoStep.LookForAnchor) {
            runOnUiThread(() -> {
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setText("Look for anchors nearby");
            });
            currentDemoStep = DemoStep.LookForNearbyAnchors;
        } else {
            stopWatcher();
            runOnUiThread(() -> {
                actionButton.setVisibility(View.VISIBLE);
                //actionButton.setText("Cleanup anchors");
                actionButton.setText("Start Navigation");
            });
            currentDemoStep = DemoStep.NavigationStart;
        }
    }

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
        AnchorVisual foundVisual = new AnchorVisual(anchor.getLocalAnchor());
        foundVisual.setCloudAnchor(anchor);
        foundVisual.getAnchorNode().setParent(arFragment.getArSceneView().getScene());


        String cloudAnchorIdentifier = foundVisual.getCloudAnchor().getIdentifier();
        statusText.setText(String.format("cloud Anchor Identifier: %s",cloudAnchorIdentifier));
        foundVisual.setColor(foundColor);

        String anchorName = String.format("Anchor %d",++anchorFound);
        float anchorscale = 0.5f;
        Vector3 localPos = new Vector3(0.0f, anchorscale * 0.55f, 0.0f);
        AnchorBoard anchorBoard = new AnchorBoard(this, anchorName,0.5f,localPos);
        anchorBoard.setParent(foundVisual.getAnchorNode());

        foundVisual.render(arFragment);

        //record anchors with its name as key
        anchorVisuals.put(cloudAnchorIdentifier, foundVisual);
//        if(currentDemoStep == DemoStep.LookForAnchor) {
//            anchorID = anchorName;
//        }
        anchorVisuals.put(cloudAnchorIdentifier, foundVisual);
        anchorNamesIdentifier.put(anchorName,cloudAnchorIdentifier);
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
            navigateButton.setVisibility(View.INVISIBLE);
            radioGroup.setVisibility(View.INVISIBLE);
            textView.setVisibility(View.INVISIBLE);
        });
        currentDemoStep = DemoStep.CreateLocalAnchor;
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
        if (currentDemoStep == DemoStep.NavigationStart){
            int radioId = radioGroup.getCheckedRadioButtonId();
            radioButton = findViewById(radioId);
            //textView.setText("Navigate to "+ radioButton.getText());

            runOnUiThread(() -> {
                //textView.setText((CharSequence) dictionary.keySet().toArray()[0]);
                statusText.setText(radioButton.getText());
            });
            //textView.setText(Integer.toString(radioId));
            //Integer.toString(radioId)
            //AnchorVisual visual = dictionary.get(radioButton.getText());
            //AnchorNode an = visual.getAnchorNode();

        }
    /*
    if (sceneRenderable!=null){
        TransformableNode scene = new TransformableNode(arFragment.getTransformationSystem());

        scene.getScaleController().setMaxScale(0.2f);
        scene.getScaleController().setMinScale(0.05f);
        scene.setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 00f));

        scene.setParent(visual.getAnchorNode());
        scene.setRenderable(sceneRenderable);
        scene.select();
    }
    */

    }

    private void onTapListener(HitTestResult hitTestResult, MotionEvent motionEvent) {
        //runOnUiThread(() -> {
        //    statusText.setText("Tap a surface to create next anchor");
        //actionButton.setVisibility(View.INVISIBLE);
        //});
        if (currentDemoStep == DemoStep.NavigationStart) {
            runOnUiThread(() -> {
                radioGroup.setVisibility(View.VISIBLE);
                textView.setVisibility(View.VISIBLE);
                navigateButton.setVisibility(View.VISIBLE);
                textView.setText("");
            });
        }


        //if (sceneRenderable != null) {
        //Anchor anchor = hitResult.createAnchor();

        // Create the Anchor.
        //AnchorNode anchorNode = new AnchorNode(anchor);
        //anchorNode.setParent(arFragment.getArSceneView().getScene());

        // Create the transformable node and add it to the anchor.
        //}
    }

    public void checkButton(View v){
        int radioId = radioGroup.getCheckedRadioButtonId();
        radioButton = findViewById(radioId);
        Toast.makeText(this,"Select" + radioButton.getText(), Toast.LENGTH_SHORT).show();
    }
    enum DemoStep {
        Start,                          ///< the start of the demo
        CreateLocalAnchor,      ///< the session will create a local anchor
        SaveCloudAnchor,        ///< the session will save the cloud anchor
        SavingCloudAnchor,      ///< the session is in the process of saving the cloud anchor
        CreateSessionForQuery,  ///< a session will be created to query for an anchor
        LookForAnchor,          ///< the session will run the query
        LookForNearbyAnchors,   ///< the session will run a query for nearby anchors
        NavigationStart,        ///< the session will run for navigation
        NavigationEnd,          ///< the navigation is end
        End,                            ///< the end of the demo
        Restart,                        ///< waiting to restart
    }
}