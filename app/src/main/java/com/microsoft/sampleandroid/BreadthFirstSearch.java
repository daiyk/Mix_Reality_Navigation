package com.microsoft.sampleandroid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class BreadthFirstSearch {
    private List<String> visitedVertex;
    private Map<String, String> path;

    public void perform(Graph g, String sourceVertex) {
        if (null == visitedVertex) {
            visitedVertex = new ArrayList<>();
        }
        if (null == path) {
            path = new HashMap<>();
        }

        BFS(g, sourceVertex);
    }

    public Map<String, String> getPath() {
        return path;
    }

    private void BFS(Graph g, String sourceVertex) {
        Queue<String> queue = new LinkedList<>();
        // 标记起点
        visitedVertex.add(sourceVertex);
        // 起点入列
        queue.add(sourceVertex);

        while (false == queue.isEmpty()) {
            String ver = queue.poll();

            List<String> toBeVisitedVertex = g.getAdj().get(ver);
            for (String v : toBeVisitedVertex) {
                if (false == visitedVertex.contains(v)) {
                    visitedVertex.add(v);
                    path.put(v, ver);
                    queue.add(v);
                }
            }
        }
    }

}
