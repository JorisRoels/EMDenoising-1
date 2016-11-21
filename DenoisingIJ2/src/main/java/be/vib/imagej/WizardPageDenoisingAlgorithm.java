package be.vib.imagej;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

public class WizardPageDenoisingAlgorithm extends WizardPage 
{
	private JPanel algoParamsPanel;
		
	private ImagePanel origImagePanel;
	private ImagePanel denoisedImagePanel;
	
	private PreviewPanel previewPanel;
	
    static final int maxPreviewSize = 256;
    
	public WizardPageDenoisingAlgorithm(Wizard wizard, WizardModel model, String name)
	{
		super(wizard, model, name);
		buildUI();		
	}
	
	private JPanel createAlgorithmChoicePanel()
	{
	    JRadioButton gaussianButton = createAlgorithmRadioButton("Gaussian", WizardModel.DenoisingAlgorithm.GAUSSIAN);   	    
	    JRadioButton diffusionButton = createAlgorithmRadioButton("Anisotropic Diffusion", WizardModel.DenoisingAlgorithm.ANISOTROPIC_DIFFUSION); 
		JRadioButton nlmeansButton = createAlgorithmRadioButton("Non-local means", WizardModel.DenoisingAlgorithm.NLMS);
	    JRadioButton waveletButton = createAlgorithmRadioButton("Wavelet Thresholding", WizardModel.DenoisingAlgorithm.WAVELET_THRESHOLDING);    	    
	    
	    // Add radio buttons to group so they are mutually exclusive
	    ButtonGroup group = new ButtonGroup();
	    group.add(nlmeansButton);
	    group.add(diffusionButton);
	    group.add(gaussianButton);
	    group.add(waveletButton);
				
		JPanel algoChoicePanel = new JPanel();
		algoChoicePanel.setLayout(new BoxLayout(algoChoicePanel, BoxLayout.Y_AXIS));
		algoChoicePanel.setBorder(BorderFactory.createTitledBorder("Denoising Algorithm"));
		algoChoicePanel.add(gaussianButton);
		algoChoicePanel.add(diffusionButton);
		algoChoicePanel.add(nlmeansButton);
		algoChoicePanel.add(waveletButton);
		algoChoicePanel.add(Box.createVerticalGlue());
		
		return algoChoicePanel;
	}
	
	private void buildUI()
	{
		JPanel algoChoicePanel = createAlgorithmChoicePanel();

		algoParamsPanel = createAlgorithmParametersPanel();
		
		JPanel algorithmPanel = new JPanel();
		algorithmPanel.setLayout(new BoxLayout(algorithmPanel, BoxLayout.X_AXIS));
		algorithmPanel.add(algoChoicePanel);
		algorithmPanel.add(algoParamsPanel);
		
		previewPanel = new PreviewPanel();
		
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));		
		add(previewPanel);
		add(algorithmPanel);
	}

	private JPanel createAlgorithmParametersPanel()
	{
		NonLocalMeansParamsPanel nonLocalMeansParamsPanel = new NonLocalMeansParamsPanel(model.nonLocalMeansParams);
		AnisotropicDiffusionParamsPanel anisotropicDiffusionParamsPanel = new AnisotropicDiffusionParamsPanel(model.anisotropicDiffusionParams);
		GaussianParamsPanel gaussianParamsPanel = new GaussianParamsPanel(model.gaussianParams);
		WaveletThresholdingParamsPanel waveletThresholdingParamsPanel = new WaveletThresholdingParamsPanel(model.waveletThresholdingParams);
		
		nonLocalMeansParamsPanel.addEventListener((DenoiseParamsChangeEvent) -> { recalculateDenoisedPreview(); });		
		anisotropicDiffusionParamsPanel.addEventListener((DenoiseParamsChangeEvent) -> { recalculateDenoisedPreview(); });
		gaussianParamsPanel.addEventListener((DenoiseParamsChangeEvent) -> { recalculateDenoisedPreview(); });
		waveletThresholdingParamsPanel.addEventListener((DenoiseParamsChangeEvent) -> { recalculateDenoisedPreview(); });
		
		CardLayout cardLayout = new CardLayout();
		JPanel panel = new JPanel(cardLayout);
		panel.add(nonLocalMeansParamsPanel, WizardModel.DenoisingAlgorithm.NLMS.name());
		panel.add(anisotropicDiffusionParamsPanel, WizardModel.DenoisingAlgorithm.ANISOTROPIC_DIFFUSION.name());
		panel.add(gaussianParamsPanel, WizardModel.DenoisingAlgorithm.GAUSSIAN.name());
		panel.add(waveletThresholdingParamsPanel, WizardModel.DenoisingAlgorithm.WAVELET_THRESHOLDING.name());
		
		cardLayout.show(panel, model.denoisingAlgorithm.name());
		return panel;
	}
	
	private JRadioButton createAlgorithmRadioButton(String text, WizardModel.DenoisingAlgorithm algorithm)
	{
	    JRadioButton button = new JRadioButton(text);
	    button.setSelected(model.denoisingAlgorithm == algorithm);
		
	    button.addActionListener(e -> {
	    	if (model.denoisingAlgorithm == algorithm) return;
    		((CardLayout)algoParamsPanel.getLayout()).show(algoParamsPanel, algorithm.name());
			model.denoisingAlgorithm = algorithm;
			recalculateDenoisedPreview();
	    });
	    
	    return button;
	}
	
	private class PreviewPanel extends JPanel
	{
		public PreviewPanel()
		{
			setBorder(BorderFactory.createTitledBorder("Denoising Preview"));
			setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
			
			origImagePanel = new ImagePanel(WizardPageDenoisingAlgorithm.this);
			denoisedImagePanel = new ImagePanel(WizardPageDenoisingAlgorithm.this);
			
			JPanel origImagePane = addTitle(origImagePanel, "Original ROI");
			JPanel denoisedImagePane = addTitle(denoisedImagePanel, "Denoised ROI");
			
			add(Box.createHorizontalGlue());
			add(origImagePane);
			add(Box.createRigidArea(new Dimension(20,0)));
			add(denoisedImagePane);
			add(Box.createHorizontalGlue());
		}
	}
	
	private void recalculateOrigPreview()
	{
		System.out.println("recalculateOrigPreview: setting model.previewOrigROI and its panels");
		model.previewOrigROI = cropImage(model.imagePlus, model.roi);
		origImagePanel.setImage(model.previewOrigROI.getBufferedImage(), maxPreviewSize);
	}
	
	private void recalculateDenoisedPreview()
	{		
		System.out.println("recalculateDenoisedPreview: will set model.previewDenoisedROI and denoisedImagePanel (via SwingWorker); current denoisedpreview=" + (model.previewDenoisedROI == null ? "null" : model.previewDenoisedROI));
//		denoisedImagePanel.setText("Calculating...");
		DenoiseSwingWorker worker = new DenoiseSwingWorker(newDenoiser(), model.previewDenoisedROI, denoisedImagePanel);
		
		// Run the denoising preview on a separate worker thread and return here immediately.
		// Once denoising has completed, the worker will automatically update the denoising
		// preview image in the Java Event Dispatch Thread (EDT).
		worker.execute();
	}
	
	private byte[] getPixelsCopy(ImageProcessor image)
	{
		Object pixelsObject = image.duplicate().getPixels();
		assert(pixelsObject instanceof byte[]);
		return (byte[])pixelsObject; 		
	}
	
	private Denoiser newDenoiser()
	{
		// Make an image denoiser. Since it will be used as a task that will be executed asynchronously,
		// we take a snapshot (deep copy) of the input image as well as the denoising
		// parameters as they are at this point in time.
		LinearImage image = new LinearImage(model.previewOrigROI.getWidth(), model.previewOrigROI.getHeight(), getPixelsCopy(model.previewOrigROI));
		
		switch (model.denoisingAlgorithm)
		{
			case NLMS:
				return new NonLocalMeansDenoiser(image, new NonLocalMeansParams(model.nonLocalMeansParams));
			case GAUSSIAN:
				return new GaussianDenoiser(image, new GaussianParams(model.gaussianParams));
			case WAVELET_THRESHOLDING:
				return new WaveletThresholdingDenoiser(image, new WaveletThresholdingParams(model.waveletThresholdingParams));
			case ANISOTROPIC_DIFFUSION:
				return new AnisotropicDiffusionDenoiser(image, new AnisotropicDiffusionParams(model.anisotropicDiffusionParams));
			default:
				return new NoOpDenoiser(image);
		}
	}

	private static JPanel addTitle(JPanel p, String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		
		JLabel titleLabel = new JLabel(title);
		titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		
		titleLabel.setAlignmentX(CENTER_ALIGNMENT);
		p.setAlignmentX(CENTER_ALIGNMENT);
		
		panel.add(titleLabel);
		panel.add(p);
		panel.add(Box.createVerticalStrut(10)); // title label introduces some space at the top, also leave some space at the bottom for symmetry
		
		return panel;
	}
	
	@Override
	public void aboutToShowPanel()
	{
		// ROI may have changed, update the previews
		recalculateOrigPreview();
		recalculateDenoisedPreview();
		
		// Ask layout manager to resize the dialog so it looks nice
		wizard.pack();
	}
	
	private ImageProcessor cropImage(ImagePlus image, Rectangle roi)
	{
		int slice = model.imagePlus.getCurrentSlice();
		ImageStack stack = image.getStack();
		ImageProcessor imp = stack.getProcessor(slice);
		
		if (model.roi != null)
		{
			imp.setRoi(model.roi);
			return imp.crop();
		}
		else
		{
			return imp.duplicate();
		}
	}
}
