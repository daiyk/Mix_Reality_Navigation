package com.microsoft.sampleandroid;

import android.content.Context;
import android.net.Uri;
import android.view.Gravity;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.ar.sceneform.ux.TransformationSystem;
import com.hbisoft.pickit.PickiTCallbacks;

import java.util.concurrent.CompletableFuture;


public class AnchorArrow extends TransformableNode {

    private static final float INFO_CARD_Y_POS_COEFF = 0.55f;
    private final Context context;
    private Vector3 localPos;
    private Node targetAnchor = new Node();
    private void addHighlightToNode(Node node) {
        CompletableFuture<Material> materialCompletableFuture =
                MaterialFactory.makeOpaqueWithColor(this.context, new Color(240, 0, 244));

        materialCompletableFuture.thenAccept(material -> {
            Renderable r2 = node.getRenderable().makeCopy();
            r2.setMaterial(material);
            node.setRenderable(r2);
        });
    }

    // context: the context of the application(this), as parent of anchorarrow
    //localPos: relative position of arrow to the camera
    //TransformationSystem: current scene transformation system
    public AnchorArrow(
            Context context,
            Vector3 localPos,
            TransformationSystem arTransform
    ){
        super(arTransform);
        this.context = context;
        this.localPos = localPos;
        this.getScaleController().setMaxScale(0.12f);
        this.getScaleController().setMinScale(0.1f);
        //initialize targetAnchor to this anchor
    }
    //@overide
    public void onActivate(){

        if (getScene() == null) {
            throw new IllegalStateException("Scene is null!");
        }

        //set origin targetAnchor point to origin
//        targetAnchor.setWorldPosition(new Vector3(0.0f,0.0f,0.0f));
//        targetAnchor = this;
        ModelRenderable.builder()
                .setSource(this.context, Uri.parse("scene.sfb"))
                .build()
                .thenAccept((renderable) -> {
                    this.setRenderable(renderable);
                    this.setLocalPosition(this.localPos);
                    addHighlightToNode(this);
                    this.select();
                })
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this.context, "Unable to load andy renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });
    }
    public void updateTargetPos(Vector3 pos)
    {
        //set anchor to point to the transformation position
        targetAnchor.setWorldPosition(pos);
    }
    public void updateTargetAnchor(AnchorNode Anchor)
    {
        //modify the target and set to the argument anchor
        if(Anchor.getAnchor()!=null)
        {
            targetAnchor = Anchor;
        }
    }

    public void onUpdate(FrameTime frameTime) {

        // Typically, getScene() will never return null because onUpdate() is only called when the node
        // is in the scene.
        // However, if onUpdate is called explicitly or if the node is removed from the scene on a
        // different thread during onUpdate, then getScene may be null.
//        if (getScene() == null) {
//            return;
//        }
        Vector3 cameraPosition = getScene().getCamera().getWorldPosition();
        Vector3 targetPosition = targetAnchor.getWorldPosition();
        targetPosition.y = targetPosition.y + 1.5f;
        Vector3 direction = Vector3.subtract(cameraPosition, targetPosition);
        Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
        this.setWorldRotation(lookRotation);

    }


}