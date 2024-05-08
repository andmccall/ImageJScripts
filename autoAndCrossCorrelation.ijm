/**
 * IJ1 macro that compares GPF positive and negative cells in regards
 * to their cross-correlation between two channels (including GFP), and auto-correlation 
 * of one. Cannot provide more details. 
 */

macro"Auto vs Cross-correlation of transfected cells" {
	imageList = getList("image.titles");
	for (currentImage = 0; currentImage < imageList.length; currentImage++) {
		selectImage(imageList[currentImage]);
	
		rename(replace(getTitle(), "/", "_"));
		title = getTitle();
		dir = getDirectory("image");
		File.makeDirectory(dir + title);
		ccOutput = dir + title + File.separator + "Cross-Correlation";
		acOutput = dir + title + File.separator + "Autocorrelation";
		
		File.makeDirectory(ccOutput);
		File.makeDirectory(acOutput);	
		File.makeDirectory(acOutput + File.separator + "GFPpositive");
		File.makeDirectory(acOutput + File.separator + "GFPnegative");
		
		run("Segment Image With Labkit", "input=" + title + " segmenter_file=[" + dir + "confocalGFPnegative.classifier] use_gpu=false");
		rename("GFPnegativeMask");
		selectImage(title);
		run("Segment Image With Labkit", "input=" + title + " segmenter_file=[" + dir + "confocalGFPpositive.classifier] use_gpu=false");
		rename(replace(getTitle(), "/", "_"));
		selectImage("segmentation of " + title);
		
		
		run("Duplicate...", "title=working duplicate");
		run("Multiply...", "value=255 stack");
		
		run("3D Simple Segmentation", "seeds=None low_threshold=128 min_size=50000 max_size=-1");
		
		selectImage("working");
		close();
		selectImage("Bin");
		close();
		selectImage("segmentation of " + title);
		close();
		
		selectImage(title);	
		run("Split Channels");
		
		selectImage("Seg");
		Stack.getStatistics(voxelCount, mean, min, max, stdDev);
		for (i = 1; i <= max; i++) {
			singleCCOutput = ccOutput + File.separator + i + File.separator;
			singleACOutput = acOutput + File.separator + "GFPpositive" + File.separator + i + File.separator;
			File.makeDirectory(singleCCOutput);
			File.makeDirectory(singleACOutput);
			
			selectImage("Seg");
			run("Select Label(s)", "label(s)=" + i);
			setThreshold(1, 65535, "raw");
			setOption("BlackBackground", true);
			run("Convert to Mask", "background=Dark black");
			
			//This macro will run both Coloc2 and CCC by default, to stop one from running, just add "//" in front to comment out the command (like the begining of this line). 
			//Coloc 2 is very slow
			
			//run("Coloc 2", "channel_1=[C2-"+title+"] channel_2=[C3-"+title+"] roi_or_mask=Seg-keepLabels threshold_regression=Costes show_save_pdf_dialog display_images_in_result spearman's_rank_correlation manders'_correlation kendall's_tau_rank_correlation costes'_significance_test psf=2 costes_randomisations=10 save=["+singleOutput+File.separator+title+".pdf]");
			
			run("CCC", "dataset1=[C3-"+title+"] dataset2=[C2-"+title+"] maskabsent=false maskdataset=Seg-keepLabels cycles=3 significantdigits=3 generatecontributionimages=true showintermediates=false savefolder=["+singleCCOutput+"]");
			close("Contribution of *");
			run("CCC", "dataset1=[C2-"+title+"] dataset2=[C2-"+title+"] maskabsent=false maskdataset=Seg-keepLabels cycles=3 significantdigits=3 generatecontributionimages=true showintermediates=false savefolder=["+singleACOutput+"]");
			close("Contribution of *");		
			selectImage("Seg-keepLabels");
			close();
		}
		
		selectImage("Seg");
		close();
		
		//GFPnegative section
		
			
		
/*		run("Duplicate...", "title=working duplicate");
		run("Multiply...", "value=255 stack");
		
		run("3D Simple Segmentation", "seeds=None low_threshold=128 min_size=50000 max_size=-1");
		
		selectImage("working");
		close();
		selectImage("Bin");
		close();
		selectImage("GFPnegativeMask");
		close();
		
		selectImage("Seg");
		Stack.getStatistics(voxelCount, mean, min, max, stdDev);
		for (i = 1; i <= max; i++) {
			singleACOutput = acOutput + File.separator + "GFPnegative" + File.separator + i + File.separator;
			File.makeDirectory(singleACOutput);
			
			selectImage("Seg");
			run("Select Label(s)", "label(s)=" + i);
			setThreshold(1, 65535, "raw");
			setOption("BlackBackground", true);
			run("Convert to Mask", "background=Dark black");
			
			//This macro will run both Coloc2 and CCC by default, to stop one from running, just add "//" in front to comment out the command (like the begining of this line). 
			//Coloc 2 is very slow
			
			//run("Coloc 2", "channel_1=[C2-"+title+"] channel_2=[C3-"+title+"] roi_or_mask=Seg-keepLabels threshold_regression=Costes show_save_pdf_dialog display_images_in_result spearman's_rank_correlation manders'_correlation kendall's_tau_rank_correlation costes'_significance_test psf=2 costes_randomisations=10 save=["+singleOutput+File.separator+title+".pdf]");
			
			run("Colocalization by Cross Correlation", "dataset1=[C2-"+title+"] dataset2=[C2-"+title+"] maskabsent=false maskdataset=Seg-keepLabels cycles=3 significantdigits=3 generatecontributionimages=true showintermediates=false savefolder=["+singleACOutput+"]");
			close("Contribution of *");		
			selectImage("Seg-keepLabels");
			close();
		}
		*/
		
		run("CCC", "dataset1=[C2-"+title+"] dataset2=[C2-"+title+"] maskabsent=false maskdataset=GFPnegativeMask cycles=3 significantdigits=3 generatecontributionimages=true showintermediates=false savefolder=[" + acOutput + File.separator + "GFPnegative" + File.separator + "]");
		close("Contribution of *");	
	
		selectImage("GFPnegativeMask");	
		close();
		close("*" + title);
	}
}
