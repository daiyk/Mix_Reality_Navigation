package com.microsoft.sampleandroid;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.System;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

//XML writer dependency
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;


public final class FileManager {

    private static String TAG = "FileManager";

    private File parentFolder;

    private File MapFolder;
    private File imgSubFolder;
    private File MapFile;
    private String poseTextFile="";

    private File poseFolder;
    private File poseFile;

    private String fileId;

    private String STORAGE_PATH = Environment.getExternalStorageDirectory()
            + File.separator + "MixRealityNavi";

    private FileOutputStream myFileOutputStream;
    private OutputStreamWriter myOutputStreamWriter;

    public FileManager(){
        parentFolder = new File(STORAGE_PATH);
        MapFolder = new File(STORAGE_PATH, "Maps");
        //poseFolder = new File(STORAGE_PATH, "poseFolder");
        fileId = String.valueOf(System.currentTimeMillis());
        createDirectory();
    }


    public void saveMap(String mapName, AnchorMap map)
    {
        if(!isExternalStorageWritable())
        {
            Log.e(TAG,"No authority to wirte file to the device!");
            return;
        }
        MapFile = new File(MapFolder,mapName+fileId+".xml");
        try {
            //extract map information and save them separate for xml write
            ArrayList<ArrayList<Integer>> adj_list = map.getAdjList();
            ArrayList<com.microsoft.sampleandroid.Node> nodeList = map.getNodeList();
            Map<String,Integer> nodeIdPair = map.getNodeIdPair();

            //build xml document class
            DocumentBuilderFactory documentFactory = DocumentBuilderFactory.newInstance();

            DocumentBuilder documentBuilder = documentFactory.newDocumentBuilder();

            Document document = documentBuilder.newDocument();

            // root element
            Element root = document.createElement("Map");
            document.appendChild(root);

            //** Loop Node List and Write to XML file*/
            // nodelist element
            Element nodes = document.createElement("Nodes");
            root.appendChild(nodes);

            //loop over nodelist and add attributes
            for(int i = 0; i<nodeList.size();i++)
            {
                Element node = document.createElement("node");
                //create attri id/position of anchor in nodelist
                Attr id = document.createAttribute("id");
                id.setValue(String.valueOf(i));
                node.setAttributeNode(id);

                //create attri anchorname
                Attr anchorName = document.createAttribute("anchorName");
                anchorName.setValue(nodeList.get(i).AnchorName);
                node.setAttributeNode(anchorName);

                //create attri anchor Identifier
                Attr anchorID = document.createAttribute("anchorID");
                anchorID.setValue(nodeList.get(i).AnchorID);
                node.setAttributeNode(anchorID);

                //create attri anchor type
                Attr anchorType = document.createAttribute("type");
                anchorType.setValue(nodeList.get(i).Type.name());
                node.setAttributeNode(anchorType);

                nodes.appendChild(node);
            }

            ///** Loop Adjacency List and Write to XML file*/
            Element graph = document.createElement("Graph");
            root.appendChild(graph);

            //loop over adjacency list and add to graph
            for(int i = 0; i<adj_list.size();i++)
            {
                //loop over nodes
                ArrayList<Integer> nodeNeighs = adj_list.get(i);

                //set id attri
                Attr id = document.createAttribute("id");
                id.setValue(String.valueOf(i));

                //create node element
                Element node = document.createElement("node");
                node.setAttributeNode(id);
                graph.appendChild(node);
                //create adjacency list
                //loop to add neighbor
                for(int j = 0; j<nodeNeighs.size();j++)
                {
                    Integer neigh = nodeNeighs.get(j);
                    Element neighbor = document.createElement("neighbor");
                    neighbor.appendChild(document.createTextNode(String.valueOf(neigh)));

                    node.appendChild(neighbor);
                }
            }

            //**Loop over NodeID pairs and Write to XML */
            Element hashPair = document.createElement("HashPair");
            root.appendChild(hashPair);
            for (Map.Entry<String, Integer> entry : nodeIdPair.entrySet()) {
                Element pair = document.createElement("pair");
                Attr key = document.createAttribute("key");
                key.setValue(entry.getKey());
                pair.setAttributeNode(key);
                pair.appendChild(document.createTextNode(String.valueOf(entry.getValue())));

                hashPair.appendChild(pair);

            }
            //create the xml file
            //transform the DOM Object to an XML File
            try {
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.setOutputProperty(OutputKeys.INDENT, "yes");
                tr.setOutputProperty(OutputKeys.METHOD, "xml");
                tr.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                tr.transform(new DOMSource(document),
                        new StreamResult(new FileOutputStream(MapFile)));

            } catch (TransformerException | FileNotFoundException e) {
                System.out.println(e.getMessage());
            }
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        }
    }


    //Load xml file to xml
    public AnchorMap loadMap(String file_path)
    {
        AnchorMap map = new AnchorMap();
        try{
            File xmlfile = new File(file_path);
//            File xmlfile = file_path;
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlfile);

            //get root element
            doc.getDocumentElement().normalize();
            Element root = doc.getDocumentElement();

            //get Nodes element
            Node nodes = root.getElementsByTagName("Nodes").item(0);


            if(nodes.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eNodeList = (Element)nodes;
                NodeList nodeList = eNodeList.getElementsByTagName("node");

                for(int item = 0;item<nodeList.getLength();item++)
                {
                    Node node = nodeList.item(item);
                    if(node.getNodeType()==Node.ELEMENT_NODE)
                    {
                        Element eNode = (Element)node;
                        map.addNode(eNode.getAttribute("anchorName"),eNode.getAttribute("anchorID"), MapBuildingActivity.NodeType.valueOf(eNode.getAttribute("type")));
                    }
                }
            }

            //get Graph elements
            Node graph = root.getElementsByTagName("Graph").item(0);

            if(graph.getNodeType() == Node.ELEMENT_NODE)
            {
                Element eAdjList = (Element)graph;
                NodeList adjList = eAdjList.getElementsByTagName("node");
                for(int item = 0; item<adjList.getLength();item++)
                {
                    Node node = adjList.item(item);
                    if(node.getNodeType()==Node.ELEMENT_NODE)
                    {
                        Element eNeighbor = (Element)node;
                        int id = Integer.valueOf(eNeighbor.getAttribute("id"));

                        //loop over adjacency list
                        NodeList neighbors = eNeighbor.getElementsByTagName("neighbor");
                        for(int j = 0; j<neighbors.getLength();j++)
                        {
                            Node neighbor = neighbors.item(j);
                            if(neighbor.getNodeType()==Element.ELEMENT_NODE)
                            {
                                int neighborID = Integer.valueOf(neighbor.getTextContent());

                                //add edge
                                map.addEdge(map.getNodeList().get(id).AnchorName, map.getNodeList().get(neighborID).AnchorName);
                            }
                        }
                    }
                }

            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }


//    public void writeNewPose(long currTime, float[] camTrans, float[] camRot){
//
//        String str_line = String.format("%1$07d %2$13f %3$13f %4$13f %5$13f %6$13f %7$13f %8$13f",
//                currTime, camTrans[0], camTrans[1], camTrans[2], camRot[0], camRot[1], camRot[2],
//                camRot[3]);
//        poseTextFile += str_line + "\n";
//    }

    // public String savePose() {
    //     if (isExternalStorageWritable()) {
    //         try {
    //             myOutputStreamWriter.write(poseTextFile);
    //             myOutputStreamWriter.close();
    //             myFileOutputStream.flush();
    //             myFileOutputStream.close();
    //             return "file saved";
    //         } catch (FileNotFoundException e) {
    //             e.printStackTrace();
    //             return "File not found";
    //         } catch (IOException e) {
    //             e.printStackTrace();
    //             return "Error saving";
    //         } catch (Throwable t) {
    //             return "Exception: " + t.toString();
    //         }
    //     } else {
    //         Log.e(TAG, "External storage not available to store data!!");
    //     }
    //     return "Error in FileManager.savePose()";
    // }


    private void createDirectory(){
        if(!parentFolder.exists())
            parentFolder.mkdirs();
        if(!MapFolder.exists())
            MapFolder.mkdirs();
    }

    /*
     * Checks if external storage is available for read and write
     */
     private boolean isExternalStorageWritable(){

         String state = Environment.getExternalStorageState();
         return Environment.MEDIA_MOUNTED.equals(state);
     }

     /* Checks if external storage is available to at least read */
     public boolean isExternalStorageReadable() {
         String state = Environment.getExternalStorageState();
         if (Environment.MEDIA_MOUNTED.equals(state) ||
                 Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
             return true;
         }
         return false;
     }

}