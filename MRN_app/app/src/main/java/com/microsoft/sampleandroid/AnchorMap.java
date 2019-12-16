package com.microsoft.sampleandroid;

import android.content.Context;
import android.util.Log;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import com.google.ar.core.Pose;
import com.google.ar.sceneform.math.Vector3;

public class AnchorMap {
    private int NodesID = 0;
    static String TAG = "AnchorMap";
    private HashMap<String,Integer> mapping = new HashMap<>(); //anchor name to anchor index mapping
    private ArrayList<ArrayList<Integer>> adjacencyList = new ArrayList<ArrayList<Integer>>();
    private ArrayList<Node> NodeList = new ArrayList<>();
    private HashMap<Integer,Float[]> anchorPos = new HashMap<>();

    //CONSTRUCTOR
    public AnchorMap(){

    }
    //METHOD:ADD NODE
    //METHOD: return anchorNode List
    public ArrayList<Node> getNodeList()
    {
        return this.NodeList;
    }

    //METHOD: return hashmap
    public Map<String,Integer> getNodeIdPair()
    {
        return this.mapping;
    }

    //METHOD: return adjacency list
    public ArrayList<ArrayList<Integer>> getAdjList() { return this.adjacencyList; }

    //METHOD: return pose list
    public Map<Integer,Float[]> getPosList() { return this.anchorPos; }

    //METHOD: add node
    //AnchorName: user-defined anchorname
    //AnchorID: Anchor identifier for retrieving from azure cloud
    //Type: Currently not used, for classifying anchors
    public boolean addNode(String AnchorName,
                           String AnchorID,
                           MapBuildingActivity.NodeType Type
    ) throws UnsupportedOperationException
    {
        if(!mapping.containsKey(AnchorName))
        {
            //create node from arguments
            Node this_node = new Node(AnchorName,AnchorID,Type);

            //compute hashval corresponding to the anchorname
            mapping.put(AnchorName,NodesID);

            //add new neighbor list to the adjacency list
            adjacencyList.add(new ArrayList<Integer>());

            //add node to the node list
            NodeList.add(this_node);

            //step Node id
            NodesID++;
            return true;
        }
        else
        {
            Log.e(TAG,"Error: Try to add already existed anchor to graph!");
            throw new UnsupportedOperationException();
        }
    }

    //METHOD: add node
    //AnchorName: user-defined anchorname
    //AnchorID: Anchor identifier for retrieving from azure cloud
    //Type: Currently not used, for classifying anchors
    public boolean addNode(String AnchorName,
                           String AnchorID,
                           Pose nodePose,
                           MapBuildingActivity.NodeType Type
    ) throws UnsupportedOperationException
    {
        if(!mapping.containsKey(AnchorName))
        {
            //create node from arguments
            Node this_node = new Node(AnchorName,AnchorID,Type);

            //compute hashval corresponding to the anchorname
            mapping.put(AnchorName,NodesID);

            //add new neighbor list to the adjacency list
            adjacencyList.add(new ArrayList<Integer>());

            //add node to the node list
            NodeList.add(this_node);

            //store pose=translation + rotation
            Float[] anchorpos = new Float[7];

            float[] translation = nodePose.getTranslation();
            float[] rotation = nodePose.getRotationQuaternion();

            //pose value
            anchorpos[0] = translation[0];
            anchorpos[1] = translation[1];
            anchorpos[2] = translation[2];

            //rotation value
            anchorpos[3] = rotation[0];
            anchorpos[4] = rotation[1];
            anchorpos[5] = rotation[2];
            anchorpos[6] = rotation[3];

            //add to hash
            anchorPos.put(NodesID,anchorpos);

            //step Node id
            NodesID++;
            return true;
        }
        else
        {
            Log.e(TAG,"Error: Try to add already existed anchor to graph!");
            throw new UnsupportedOperationException();
        }
    }

    //overloading addEdge method
    public boolean addEdge(String anchor1, String anchor2) throws UnsupportedOperationException
    {
        //search on the hashmap for corresponding node
        if(!mapping.containsKey(anchor1)||!mapping.containsKey(anchor2))
        {
            //Toast.makeText(currcontext, "Error: Invalid anchor name!", Toast.LENGTH_SHORT).show();
            System.out.println( "Error: AnchorMap.addEdge(): Input anchors do not existed!");
            throw new UnsupportedOperationException();
        }
        Integer Idx1 = mapping.get(anchor1);
        Integer Idx2 = mapping.get(anchor2);

        //add edge to the adjacency list
        if(!adjacencyList.get(Idx1).contains(Idx2))
            adjacencyList.get(Idx1).add(Idx2);
        if(!adjacencyList.get(Idx2).contains(Idx1))
            adjacencyList.get(Idx2).add(Idx1);

        return true;
    }

    //METHOD： helper function for map loading and pos update
    public boolean addPos(Integer idx, Float[] pos)
    {
        anchorPos.put(idx,pos);
        return true;
    }

    //METHOD: get the anchor pos with name anchor
    //anchor: anchor name
    //return please check the return value, if return null then failed to retrieve
    public Pose getPos(String anchor)
    {
        if(!mapping.containsKey(anchor))
        {
            Log.d("GetPos: "," :input anchors doesn't exist!");
            return null;
        }
        Float[] posArray = anchorPos.get(mapping.get(anchor));

        float[] translation = {posArray[0],posArray[1],posArray[2]};
        float[] rotation = {posArray[3],posArray[4],posArray[5],posArray[6]};
        Pose foundPos = new Pose(translation,rotation);
        return foundPos;
    }


    //METHOD: search shortest path between start and end nodes
    //start: start anchor name
    //end: end anchor name
    //mapQueue: queue to store return path. (!!! You have to define a empty ArrayList<String> and supply to it !!!)
    public boolean searchSP(String start, String end, ArrayList<String> mapQueue) throws UnsupportedOperationException
    {
        if(!mapping.containsKey(start)||!mapping.containsKey(end))
        {
            Log.e(TAG,"Error: The request target doesn't existed!");
            throw new UnsupportedOperationException();
        }
        //find the first and end node
        int startID = mapping.get(start);
        int endID = mapping.get(end);

        //distance recorder
        int dist[] = new int[NodesID];
        Arrays.fill(dist,-1);
        dist[startID] = 0;
        //router tracker
        int pre[] = new int[NodesID];
        Arrays.fill(pre,-1);
        pre[startID] = startID;

        //start of Dijkstra's algorithm
        //create queue and start from startNode
        LinkedList<Integer> queue = new LinkedList<>();
        queue.add(startID);

        boolean findTarget = false;
        while(!queue.isEmpty())
        {
            Integer dequeueID = queue.poll();
            if(dequeueID == endID)
            {
                findTarget = true;
                break;
            }
            for (Integer nodeID : adjacencyList.get(dequeueID)) {
                if (dist[nodeID] == -1)
                    queue.add(nodeID);

                //this is not a weighted edge graph
                int relDist = dist[dequeueID] + 1;
                if (relDist < dist[nodeID] || dist[nodeID] == -1)
                {
                    dist[nodeID] = relDist;
                    pre[nodeID] = dequeueID;
                }

            }

        }
        if(!findTarget)
        {
            return false;
        }

        int reverse = endID;

        //retrieve path from source to the target[:except source point]
        while(pre[reverse] != reverse)
        {
            mapQueue.add(0, NodeList.get(reverse).AnchorName);
            reverse = pre[reverse];
        }
        return true;
    }

    //return the anchor for the specified anchor name
    public Node getNode(String anchorName)
    {
        if(!mapping.containsKey(anchorName))
        {
            Log.e(TAG,"Error: request node doesn't existed!");
            return null;
        }
        Integer nodeID = mapping.get(anchorName);
        return NodeList.get(nodeID);
    }
    public void destory(){
        mapping.clear();
        mapping = new HashMap<>();
        adjacencyList.clear();
        adjacencyList = new ArrayList<ArrayList<Integer>>();
        NodeList.clear();
        NodeList = new ArrayList<>();
        anchorPos.clear();
        anchorPos = new HashMap<>();
        NodesID = 0;
    }
}

class Node{

    public Node(){
        this.AnchorName = "Undefined";
        this.AnchorID = "Undefined";
    }
    public Node(String name, String ID, MapBuildingActivity.NodeType type){
        this.AnchorName = name;
        this.AnchorID = ID;
        this.Type = type;
    }
    public String AnchorName;
    public String AnchorID;
    public MapBuildingActivity.NodeType Type;
}

//    //deprecated! use addEdge(String 1, String 2) instead!
//    // METHOD: add edge between two anchors
//    //anchor1: user-defined anchorname 1
//    //anchor2: user-defined anchorname 2
//    //pos1,po2: two anchors world's coordinates
//    public boolean addEdge(String anchor1, String anchor2, Vector3 pos1, Vector3 pos2) throws UnsupportedOperationException
//    {
//        //search on the hashmap for corresponding node
//        if(!mapping.containsKey(anchor1)||!mapping.containsKey(anchor2))
//        {
//            Log.e(TAG, "Error: AnchorMap.addEdge(): Input anchors not existed!");
//            throw new UnsupportedOperationException();
//        }
//        Integer Idx1 = mapping.get(anchor1);
//        Integer Idx2 = mapping.get(anchor2);
//
//        //add edge to the adjacency list
//        if(!adjacencyList.get(Idx1).contains(Idx2))
//            adjacencyList.get(Idx1).add(Idx2);
//        if(!adjacencyList.get(Idx2).contains(Idx1))
//            adjacencyList.get(Idx2).add(Idx1);
//
//        //add pos to the map
////        anchorPos.put(Idx1,pos1);
////        anchorPos.put(Idx2,pos2);
//
//        // add transformation vector to the hash dataset
//        //compute transformation
//        Vector3 transf12 = Vector3.subtract(pos2,pos1);
//        Vector3 transf21 = Vector3.subtract(pos1,pos2);
//
//        //add transformation to hashmap
//        transf.put(Integer.toString(Idx1)+"_"+Integer.toString(Idx2),transf12);
//        transf.put(Integer.toString(Idx2)+"_"+Integer.toString(Idx1),transf21);
//
//        return true;
//    }


//    //METHOD: helper function for map loading and transformation update
//    public boolean updateTransf(Integer idx1, Integer idx2, Vector3 vec)
//    {
//
//        if(!adjacencyList.get(idx1).contains(idx2)) {
//            Log.d("UpdateTransf"," :error, update transformation to a non-existed edge!");
//            return false;
//        }
//        //add transformation to hashmap
//        transf.put(Integer.toString(idx1)+"_"+Integer.toString(idx2),vec);
//        return true;
//    }

//    public Vector3 getEdge(String anchor1, String anchor2)
//    {
//        Vector3 transformation = new Vector3();
//        if(!mapping.containsKey(anchor1)||!mapping.containsKey(anchor2))
//        {
//            Log.d("GetEdge: "," :input anchors doesn't exist!");
//            return null;
//        }
//        Integer idx1 = mapping.get(anchor1);
//        Integer idx2 = mapping.get(anchor2);
//
//        if(!transf.containsKey(Integer.toString(idx1) + "_" + Integer.toString(idx2)))
//        {
//            Log.d("Getedge: "," : request edge is recorded but doesn't initialized!");
//            return null;
//        }
//
//        //find the corresponding edge and return the vector
//        transformation = transf.get(Integer.toString(idx1) + "_" + Integer.toString(idx2));
//
//        return transformation;
//    }