package com.microsoft.sampleandroid;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Graph {
    private String firstVertex;

    private HashMap<String, LinkedList<String>> adjacencyMap = new HashMap<>();
    private Map<String, List<String>> adj = new HashMap<>();
    private BreadthFirstSearch bfs;

    public Graph(BreadthFirstSearch bfs){
        this.bfs= bfs;
    }

    public void done(){
        bfs.perform(this, firstVertex);
    }

    public Stack<String> findPathTo(String vertex){
        Stack<String> stack = new Stack<>();
        stack.add(vertex);

        Map<String, String> path = bfs.getPath();
        for (String location = path.get(vertex) ; false == location.equals(firstVertex) ; location = path.get(location)) {
            stack.push(location);
        }
        stack.push(firstVertex);

        return stack;
    }


    public void addEdge(String fromVertex, String toVertex) {
        if (firstVertex == null) {
            firstVertex = fromVertex;
        }

        adj.get(fromVertex).add(toVertex);
        adj.get(toVertex).add(fromVertex);
    }

    /**
     * 添加一个顶点
     */
    public void addVertex(String vertex) {
        adj.put(vertex, new ArrayList<>());
    }

    public Map<String, List<String>> getAdj() {
        return adj;
    }
}

