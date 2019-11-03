package com.microsoft.sampleandroid;

import android.content.Context;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class AnchorMap {
    static int NodesID = 0;
    private HashMap<String,Integer> mapping = new HashMap<>();
    private ArrayList<ArrayList<Integer>> adjacencyList = new ArrayList<ArrayList<Integer>>();
    private ArrayList<Node> NodeList = new ArrayList<>();
    private Context currcontext;

    //CONSTRUCTOR
    public AnchorMap(Context context){
        currcontext = context;
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
    public ArrayList<ArrayList<Integer>> getAdjList()
    {
        return this.adjacencyList;
    }

    //METHOD: add node
    //AnchorName: user-defined anchorname
    //AnchorID: Anchor identifier for retrieving from azure cloud
    //Type: Currently not used, for classifying anchors
    public boolean addNode(String AnchorName,
                           String AnchorID,
                           AzureSpatialAnchorsActivity.NodeType Type
    )
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
            Toast.makeText(currcontext, "Error: Try to add already existed anchor to graph!", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    //METHOD: add edge between two anchors
    //anchor1: user-defined anchorname 1
    //anchor2: user-defined anchorname 2
    public boolean addEdge(String anchor1, String anchor2)
    {
        //search on the hashmap for corresponding node
        if(!mapping.containsKey(anchor1)||!mapping.containsKey(anchor2))
        {
            Toast.makeText(currcontext, "Error: Invalid anchor name!", Toast.LENGTH_SHORT).show();
            return false;
        }
        Integer Idx1 = mapping.get(anchor1);
        Integer Idx2 = mapping.get(anchor2);

        //add edge to the adjacency list
        adjacencyList.get(Idx1).add(Idx2);
        adjacencyList.get(Idx2).add(Idx1);

        return true;
    }

    //METHOD: search shortest path between start and end nodes
    //start: start anchor name
    //end: end anchor name
    //mapQueue: queue to store return path. (!!! You have to define a empty ArrayList<String> and supply to it !!!)
    public boolean searchSP(String start, String end, ArrayList<String> mapQueue)
    //mapQueue: node list that contains the final path
    //start, end: names of start node and end node
    {
        if(!mapping.containsKey(start)||!mapping.containsKey(end))
        {
            Toast.makeText(currcontext, "Error: The request target doesn't existed!", Toast.LENGTH_SHORT).show();
            return false;
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
            Toast.makeText(currcontext, "Error: AnchorMap.getNode: Try to get non-existed anchor!", Toast.LENGTH_SHORT).show();
            return new Node();
        }
        Integer nodeID = mapping.get(anchorName);
        return NodeList.get(nodeID);
    }
}

class Node{

    public Node(){
        this.AnchorName = "Undefined";
        this.AnchorID = "Undefined";
    }
    public Node(String name, String ID, AzureSpatialAnchorsActivity.NodeType type){
        this.AnchorName = name;
        this.AnchorID = ID;
        this.Type = type;
    }
    public String AnchorName;
    public String AnchorID;
    public AzureSpatialAnchorsActivity.NodeType Type;
}