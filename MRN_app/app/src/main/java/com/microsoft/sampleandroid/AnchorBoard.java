package com.microsoft.sampleandroid;

import android.content.Context;
import android.widget.TextView;

import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;

public class AnchorBoard extends Node {

    private static final float INFO_CARD_Y_POS_COEFF = 0.55f;

    private final String anchorName;
    private final float anchorScale;
    private final Context context;
    private Vector3 localPos;

    //private Node infoCard;

    public AnchorBoard(
            Context context,
            String anchorName,
            float anchorScale,
            Vector3 localPos
    ) {
        this.context = context;
        this.anchorName = anchorName;
        this.anchorScale = anchorScale;
        this.localPos = localPos;
    }

    public void onActivate() {

        if (getScene() == null) {
            throw new IllegalStateException("Scene is null!");
        }
        //infoCard.setLocalPosition(new Vector3(0.0f, anchorScale * INFO_CARD_Y_POS_COEFF, 0.0f));
        this.setLocalPosition(this.localPos);

        ViewRenderable.builder()
                .setView(context, R.layout.label_board)
                .build()
                .thenAccept(
                        (renderable) -> {
                            this.setRenderable(renderable);
                            TextView textView = (TextView) renderable.getView();
                            textView.setText(anchorName);
                        })
                .exceptionally(
                        (throwable) -> {
                            throw new AssertionError("Could not load plane card view.", throwable);
                        });

    }

    public void setBoardText(String text)
    {
        ViewRenderable.builder()
                .setView(context, R.layout.label_board)
                .build()
                .thenAccept(
                        (renderable) -> {
                            this.setRenderable(renderable);
                            TextView textView = (TextView) renderable.getView();
                            textView.setText(text);
                        })
                .exceptionally(
                        (throwable) -> {
                            throw new AssertionError("Could not load plane card view.", throwable);
                        });

    }
    public void onUpdate(FrameTime frameTime) {

        // Typically, getScene() will never return null because onUpdate() is only called when the node
        // is in the scene.
        // However, if onUpdate is called explicitly or if the node is removed from the scene on a
        // different thread during onUpdate, then getScene may be null.
        if (getScene() == null) {
            return;
        }
        Vector3 cameraPosition = getScene().getCamera().getWorldPosition();
        Vector3 cardPosition = this.getWorldPosition();
        Vector3 direction = Vector3.subtract(cameraPosition, cardPosition);
        Quaternion lookRotation = Quaternion.lookRotation(direction, Vector3.up());
        this.setWorldRotation(lookRotation);
    }
}