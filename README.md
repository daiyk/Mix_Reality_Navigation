# Mix Reality Navigation with Azure Spatial Anchor

## Intro
This app is an android-based  Mix-Reality application that build graph-based map by placing anchors,  and  navigating  user  to  and  from  any  two  chosen anchors. Our application fully relies on the image recognition (SLAM) for anchor detection, where the anchor-related images  are  stored  in  the  Azure  cloud  application  - Azure Spatial Anchor.

## Environment
Build environment:
- Android 9.0 and higher;
- Android  Studio  3.5.3;
- Url lib [PickiT](https://github.com/HBiSoft/PickiT) 

## Set up Azure Spatial Anchor ##
Please following the Micorsoft [Azure Spatial Anchor Offical Tutorial](https://docs.microsoft.com/en-us/azure/spatial-anchors/quickstarts/get-started-android?tabs=openproject-java) to set up your Microsoft Azure account and open Azure Spatial Anchor service.

**Note: ASA is in preview stage, if you find ASA service is unavailable, it could be in maintaince period.**

## Configure account identifier and key ##
Follow [this link](https://docs.microsoft.com/en-us/azure/spatial-anchors/quickstarts/get-started-android?tabs=openproject-java#create-a-spatial-anchors-resource) to create Spatial Anchors resource, and copy the resource's **Account ID** and **Primary key** to  `app/src/main/java/com/microsoft/sampleandroid/AzureSpatialAnchorsManager.java` in their corresponding fields. 

Complie the app and follow the instruction in the app.

## Demo Videos
<a href="https://www.youtube.com/watch?v=rGzZLM_a3L0" target="_blank"><img src="http://img.youtube.com/vi/rGzZLM_a3L0/0.jpg" alt="Navigation" width="480" height="360" border="10" /></a>

<a href="https://www.youtube.com/watch?v=D6yCB5i1jSs" target="_blank"><img src="http://img.youtube.com/vi/D6yCB5i1jSs/0.jpg" alt="Map Building" width="480" height="360" border="10" /></a>

## Authors ##
* **[daiyk](https://github.com/daiyk)**
* **[louzi233](https://github.com/louzi233)**

## License ##
This project is licensed under the MIT License, see the LICENSE.md file for details
## Reference
[ETHz Mix Reality Lab](http://cvg.ethz.ch/teaching/mrlab/)

[Azure Spatial Anchor Sample](https://github.com/Azure/azure-spatial-anchors-samples)

[Micorsoft Azure Spatial Anchor](https://azure.microsoft.com/en-us/services/spatial-anchors/)
