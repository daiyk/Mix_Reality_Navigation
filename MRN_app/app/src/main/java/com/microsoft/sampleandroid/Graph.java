package com.microsoft.sampleandroid;


import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;

public class Map {
    private String firstVertex;

    private HashMap<String, LinkedList<String>> adjacencyMap = new HashMap<>();

    private BFS bfs;

    public Map(BFS bfs){
        this.bfs= bfs;
    }

    public void done(){
        bfs.perform(this, firstVertex);
    }

    public Stack<String> findPathTo(String vertex){
        Stack<String> stack = new Stack<>();
        stack.add(vertex);

        Map<String, String> path = bfs.getPath();
        for (String location = path.get(vertex) ; false == location.equals(firstVertax) ; location = path.get(location)) {
            stack.push(location);
        }
        stack.push(firstVertex);

        return stack;
    }
}

