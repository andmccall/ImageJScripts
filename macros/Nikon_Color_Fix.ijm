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


list = getList("image.titles");	

for (i = 0; i < list.length; i++) {
	selectImage(list[i]);
	getDimensions(width, height, channels, slices, frames);
	if(channels !=3){
		continue;
	}
	Stack.setChannel(1);
	run("Blue");
	Stack.setChannel(3);
	run("Red");
	call("ij.ImagePlus.setDefault16bitRange", 10);
	File.makeDirectory(getDir("image") + File.separator + "color_fixed_tiffs");
	saveAs("Tiff", getDir("image") + File.separator + "color_fixed_tiffs" + File.separator + list[i]);
}		
