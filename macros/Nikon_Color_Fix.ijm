/**
 * This script simply fixes an issue with the channel order
 * of Nikon NiE acquired color images. RGB -> BGR
 * 
 * After applying the fix, it saves the images as tiffs.
 * 
 * Place this file in Fiji.app/scripts/Image to have 
 * this show up in the Image menu. 
 * 
 * Or you can drag and drop it into Fiji and hit Run.
 */

#@ String (visibility = MESSAGE, value="Drag and drop a save folder to the field below.", required=false) msg
#@ File (label="Select save folder", style = "directory") saveDirectory

list = getList("image.titles");	

for (i = 0; i < list.length; i++) {
	selectImage(list[i]);
	getVoxelSize(width, height, depth, unit);
	setVoxelSize(width, height, depth, "um");
	getDimensions(width, height, channels, slices, frames);
	if(channels !=3){
		continue;
	}
	Stack.setDisplayMode("composite");
	call("ij.ImagePlus.setDefault16bitRange", 10);
	Stack.setChannel(1);
	run("Blue");
	setMinAndMax(0, 1023);
	Stack.setChannel(2);
	setMinAndMax(0, 1023);
	Stack.setChannel(3);
	run("Red");
	setMinAndMax(0, 1023);
	saveAs("Tiff", saveDirectory + File.separator + list[i]);
}		
