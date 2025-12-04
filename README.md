# About and Notices

These are scripts that I have developed for use in numerous research projects, primarily at the University at Buffalo. While these scripts are designed for specific projects, anyone is free to download and modify these scripts for their own purposes. However, I make no guarantees that the script will work perfectly as described, particularly for data that it was not specifically designed for. Additionally, these scripts represent my entire experience range in groovy script development, including some of my earliest scripts, with very few updates made to old scripts as I've formed better coding practices. 

If you are interested in hiring me for script writing services, you can contact me through GitHub, or via the image.sc forum (@amccall) to discuss further.

# General usage guide

The majority of the scripts have the same general workflow for execution, that is roughly as follows:

0. Download Fiji (imagej.net), and use the Fiji updater (Help > Update > Manage Update sites) to add any required plugins. Required plugins are almost always listed in the description section of the script, at the very top. Labkit is the most common requirement.
1. Select the script you wish to use above, and click the download button in the upper-right corner of the script preview window.
2. Open Fiji and drag and drop the downloaded script onto the main Fiji window, this will open the script editor. Then, hit the "Run" button on the script editor window (maximize the window if you can't see it). A few scripts work on the active Fiji image and will require an already open image in Fiji, though most do not. If an error comes up saying "A ImagePlus (or Dataset) is required but none exist", you'll need to open your image(s) first.
3. Generally, a script specific Dialog box will pop up. The typical layout for many scripts is:
- First section: Images to be processed. Simply drag and drop the image files from windows explorer to this input widget. A few scripts require directories instead of files, but this is fairly rare.
- Second section: A Labkit classifier file specific to the provided images. Simply drag and drop the classifier into the input widget.
- Checkbox for GPU acceleration: If you have an NVIDIA graphics card, and you install CLIJ and CLIJ2, you can check this box to speed up the Labkit classification process.
- Output directory: Some scripts will ask for an output directory to save the data to, others will save to the same location as the input files, or folders within. Which is used often depends on the complexity of the output, and preference of the lab the script was initially designed for.
- Some scripts will have additional parameters that need to be set. Usually these are intuitive as to their purpose.
4. After filling in the dialog box and hitting OK the script will start. The majority of the scripts I write will run fully automated in the background. At the bottom of the script editor is an output window where most scripts will give occasional updates, including a typical "All Finished!" update at the end. Some steps take a very long time, so be patient. If you are concerned that your script is not running, you can open up task manager (Activity Monitor on Mac) and check to make sure that Fiji is using some of the CPU. Some processing steps are not multi-core optimized, so even 2 to 5% usage can indicate normal script operation.
5. Most scripts auto-save their data, so typically when the "All Finished!" message pops up in the output of the script editor window, you can close Fiji and your data should be ready for you. 

# Typical Script structure

Coming soon...
